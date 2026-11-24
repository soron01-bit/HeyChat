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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.heychat.data.local.AppTheme
import com.example.heychat.data.models.Profile
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.viewmodel.SettingsViewModel

@Composable
fun ChatsSettingsScreen(
    profile: Profile? = null,
    settingsViewModel: SettingsViewModel? = null,
    onBackClick: () -> Unit = {}
) {
    val selectedTheme = settingsViewModel?.currentTheme?.collectAsState(initial = AppTheme.SYSTEM)?.value ?: AppTheme.SYSTEM
    val selectedWallpaper = settingsViewModel?.currentWallpaper?.collectAsState(initial = "DEFAULT")?.value ?: "DEFAULT"
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Chat History?") },
            text = { Text("This action will permanently delete all your message history. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        if (profile != null) {
                            settingsViewModel?.clearChatHistory(profile.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Chats Settings",
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
            // Theme Selector
            Text(
                text = "App Theme",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AppTheme.entries.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = selectedTheme == theme,
                        onClick = { settingsViewModel?.setTheme(theme) }
                    )
                    Text(
                        text = when (theme) {
                            AppTheme.SYSTEM -> "System Default"
                            AppTheme.LIGHT -> "Light Theme"
                            AppTheme.DARK -> "Dark Theme"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            // Chat Wallpaper Selection
            Text(
                text = "Chat Wallpaper",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val wallpapers = listOf("DEFAULT" to "Default Clean", "TEAL" to "Solid Emerald Teal", "DARK" to "Midnight Dark")
            wallpapers.forEach { (code, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = selectedWallpaper == code,
                        onClick = { settingsViewModel?.setWallpaper(code) }
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            // Clear Chat History Section
            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Chat History")
            }
        }
    }
}

@Preview(name = "Chats Settings Light", showBackground = true)
@Composable
fun ChatsSettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        ChatsSettingsScreen()
    }
}
