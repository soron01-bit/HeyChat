package com.example.heychat.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.theme.HeyChatTheme

@Composable
fun HelpSettingsScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Help & Support",
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Frequently Asked Questions",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            FaqItem(
                question = "How do I edit my profile?",
                answer = "Go to Settings and tap the top profile section containing your name and picture. There you can update your name, status bio, and profile avatar."
            )

            Spacer(modifier = Modifier.height(10.dp))

            FaqItem(
                question = "Is my chat data encrypted?",
                answer = "Yes! All chat messages sent over Hey Chat are encrypted end-to-end and stored securely using Supabase Postgres with strict Row Level Security (RLS) policies."
            )

            Spacer(modifier = Modifier.height(10.dp))

            FaqItem(
                question = "How do I change my app theme?",
                answer = "Navigate to Settings > Chats > App Theme and select your preferred theme mode (Light, Dark, or System Default)."
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Action Intent Buttons
            Button(
                onClick = {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@heychat.app")
                        putExtra(Intent.EXTRA_SUBJECT, "Hey Chat Support Request")
                    }
                    context.startActivity(Intent.createChooser(emailIntent, "Send Support Email"))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(imageVector = Icons.Default.Email, contentDescription = "Email Support")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Contact Support Team")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://heychat.app/privacy"))
                    context.startActivity(webIntent)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(imageVector = Icons.Default.PrivacyTip, contentDescription = "Privacy Policy")
                Spacer(modifier = Modifier.height(8.dp))
                Text("View Privacy Policy")
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle FAQ"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(name = "Help Settings Light", showBackground = true)
@Composable
fun HelpSettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        HelpSettingsScreen()
    }
}
