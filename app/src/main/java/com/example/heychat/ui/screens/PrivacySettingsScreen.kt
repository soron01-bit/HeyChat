package com.example.heychat.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.viewmodel.SettingsViewModel
import io.github.jan.supabase.gotrue.auth

@Composable
fun PrivacySettingsScreen(
    profile: Profile? = null,
    settingsViewModel: SettingsViewModel? = null,
    onBackClick: () -> Unit = {}
) {
    var showLastSeen by remember(profile) { mutableStateOf(profile?.showLastSeen ?: true) }
    var disappearingDuration by remember(profile) { mutableStateOf(profile?.disappearingMessagesDuration ?: "OFF") }

    val currentUserId = profile?.id ?: supabaseClient.auth.currentUserOrNull()?.id ?: ""
    val blockedUsers = settingsViewModel?.blockedUsers?.collectAsState()?.value ?: emptyList()

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            settingsViewModel?.fetchBlockedUsers(currentUserId)
        }
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Privacy Settings",
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
            // Last Seen & Online Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last Seen & Online",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Show when you were last online to contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = showLastSeen,
                    onCheckedChange = { checked ->
                        showLastSeen = checked
                        if (profile != null) {
                            settingsViewModel?.updatePrivacySettings(
                                currentProfile = profile,
                                showLastSeen = checked,
                                disappearingDuration = disappearingDuration,
                                twoStep = profile.twoStepEnabled == true
                            )
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            // Disappearing Messages Default Duration
            Text(
                text = "Default Disappearing Messages Timer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set default duration for new chat messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val options = listOf("OFF" to "Off", "24H" to "24 Hours", "7D" to "7 Days", "90D" to "90 Days")
            options.forEach { (code, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = disappearingDuration == code,
                        onClick = {
                            disappearingDuration = code
                            if (profile != null) {
                                settingsViewModel?.updatePrivacySettings(
                                    currentProfile = profile,
                                    showLastSeen = showLastSeen,
                                    disappearingDuration = code,
                                    twoStep = profile.twoStepEnabled == true
                                )
                            }
                        }
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            // Blocked Contacts List
            Text(
                text = "Blocked Contacts (${blockedUsers.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (blockedUsers.isEmpty()) {
                Text(
                    text = "No blocked contacts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                blockedUsers.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = user.blockedName.ifBlank { user.blockedEmail },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = user.blockedEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (currentUserId.isNotBlank()) {
                                    settingsViewModel?.unblockUser(currentUserId, user.blockedId)
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unblock")
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Privacy Settings Light", showBackground = true)
@Composable
fun PrivacySettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        PrivacySettingsScreen()
    }
}
