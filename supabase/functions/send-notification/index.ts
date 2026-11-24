import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { GoogleAuth } from 'https://esm.sh/google-auth-library@9'

const FIREBASE_SERVICE_ACCOUNT = JSON.parse(
  Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '{}'
)
const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

async function getAccessToken(): Promise<string> {
  const auth = new GoogleAuth({
    credentials: FIREBASE_SERVICE_ACCOUNT,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  })
  const client = await auth.getClient()
  const token = await client.getAccessToken()
  return token.token ?? ''
}

Deno.serve(async (req) => {
  try {
    const payload = await req.json()
    const record = payload.record

    const { conversation_id, sender_id, content } = record

    const { data: participants, error: participantsError } = await supabase
      .from('conversation_participants')
      .select('user_id')
      .eq('conversation_id', conversation_id)
      .neq('user_id', sender_id)

    if (participantsError || !participants || participants.length === 0) {
      return new Response(JSON.stringify({ error: 'Recipient not found' }), { status: 404 })
    }

    const recipientId = participants[0].user_id

    const { data: recipientProfile } = await supabase
      .from('profiles')
      .select('fcm_token')
      .eq('id', recipientId)
      .single()

    const { data: senderProfile } = await supabase
      .from('profiles')
      .select('full_name')
      .eq('id', sender_id)
      .single()

    if (!recipientProfile?.fcm_token) {
      return new Response(JSON.stringify({ error: 'No FCM token for recipient' }), { status: 200 })
    }

    const accessToken = await getAccessToken()
    const projectId = FIREBASE_SERVICE_ACCOUNT.project_id

    const fcmResponse = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token: recipientProfile.fcm_token,
            notification: {
              title: senderProfile?.full_name ?? 'New message',
              body: content ?? 'You have a new message',
            },
            data: {
              conversation_id: conversation_id,
            },
          },
        }),
      }
    )

    const fcmResult = await fcmResponse.json()

    return new Response(JSON.stringify({ success: true, fcmResult }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 })
  }
})