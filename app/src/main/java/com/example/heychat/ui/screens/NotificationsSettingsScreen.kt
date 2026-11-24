package com.example.heychat.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.heychat.data.local.SettingsDataStore
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.viewmodel.SettingsViewModel

@Composable
fun NotificationsSettingsScreen(
    settingsViewModel: SettingsViewModel? = null,
    onBackClick: () -> Unit = {}
) {
    val notifMessage = settingsViewModel?.notifMessage?.collectAsState(initial = true)?.value ?: true
    val notifGroup = settingsViewModel?.notifGroup?.collectAsState(initial = true)?.value ?: true
    val notifCall = settingsViewModel?.notifCall?.collectAsState(initial = true)?.value ?: true
    val notifSound = settingsViewModel?.notifSound?.collectAsState(initial = true)?.value ?: true
    val notifVibrate = settingsViewModel?.notifVibrate?.collectAsState(initial = true)?.value ?: true

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Notifications",
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
            NotificationToggleRow(
                title = "Message Notifications",
                subtitle = "Show notification popups for new 1-on-1 messages",
                checked = notifMessage,
                onCheckedChange = { settingsViewModel?.setNotifSetting(SettingsDataStore.NOTIF_MESSAGE_KEY, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NotificationToggleRow(
                title = "Group Notifications",
                subtitle = "Show notification popups for new group chat messages",
                checked = notifGroup,
                onCheckedChange = { settingsViewModel?.setNotifSetting(SettingsDataStore.NOTIF_GROUP_KEY, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NotificationToggleRow(
                title = "Call Ringtone",
                subtitle = "Play incoming audio and video call ringtones",
                checked = notifCall,
                onCheckedChange = { settingsViewModel?.setNotifSetting(SettingsDataStore.NOTIF_CALL_KEY, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NotificationToggleRow(
                title = "In-App Sounds",
                subtitle = "Play audio feedback for sent & received messages",
                checked = notifSound,
                onCheckedChange = { settingsViewModel?.setNotifSetting(SettingsDataStore.NOTIF_SOUND_KEY, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NotificationToggleRow(
                title = "Vibration",
                subtitle = "Vibrate on new incoming notification",
                checked = notifVibrate,
                onCheckedChange = { settingsViewModel?.setNotifSetting(SettingsDataStore.NOTIF_VIBRATE_KEY, it) }
            )
        }
    }
}

@Composable
fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(name = "Notifications Light", showBackground = true)
@Composable
fun NotificationsSettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        NotificationsSettingsScreen()
    }
}
