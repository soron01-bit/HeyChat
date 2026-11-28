package com.example.heychat.ui.screens

import android.widget.Toast
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heychat.data.models.Profile
import com.example.heychat.data.Chat
import com.example.heychat.data.Message
import com.example.heychat.data.User
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.SearchRepositoryImpl
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import com.example.heychat.ui.components.HeyChatBottomNavBar
import com.example.heychat.ui.components.MainTab
import com.example.heychat.ui.components.MyQrCodeDialog
import com.example.heychat.ui.components.QrScannerDialog
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.components.getInitials
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.theme.PrimaryCyan
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.theme.PrimaryIndigo

import androidx.compose.runtime.collectAsState
import com.example.heychat.ui.viewmodel.SettingsViewModel

@Composable
fun ProfileSettingsScreen(
    profile: Profile? = null,
    settingsViewModel: SettingsViewModel? = null,
    onTabSelected: (MainTab) -> Unit = {},
    onChatClick: (Chat) -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onChatsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    showBottomBar: Boolean = true
) {
    val currentProfile = settingsViewModel?.profileFlow?.collectAsState(initial = profile)?.value ?: profile
    val name = currentProfile?.fullName?.takeIf { it.isNotBlank() } ?: currentProfile?.displayName ?: "User Profile"
    val email = currentProfile?.email ?: ""
    val about = currentProfile?.about?.takeIf { it.isNotBlank() } ?: "Hey there! I am using Hey Chat 💬"

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?") },
            text = { Text("Are you sure you want to sign out from Hey Chat on this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrDialog) {
        MyQrCodeDialog(
            profile = currentProfile,
            onDismiss = { showQrDialog = false },
            onOpenScanner = {
                showQrDialog = false
                showQrScanner = true
            }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onUserScanned = { scannedUserId ->
                showQrScanner = false
                val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id ?: "").replace("\"", "").replace("'", "").trim().lowercase()
                val cleanScannedId = scannedUserId.replace("\"", "").replace("'", "").trim().lowercase()

                if (cleanScannedId.isNotBlank() && currentUserId.isNotBlank()) {
                    if (cleanScannedId == currentUserId) {
                        Toast.makeText(context, "This is your own QR code! Share it with friends so they can chat with you.", Toast.LENGTH_LONG).show()
                    } else {
                        coroutineScope.launch {
                            val searchRepo = SearchRepositoryImpl(context)
                            val convResult = searchRepo.getOrCreateConversation(currentUserId, cleanScannedId)
                            val convId = convResult.getOrNull()
                            if (!convId.isNullOrBlank()) {
                                onChatClick(Chat(id = convId, contact = User(id = cleanScannedId, name = "User", phoneNumber = ""), lastMessage = Message(id = "", senderId = "", text = "", timestamp = "", isFromMe = false)))
                            }
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Settings",
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            if (showBottomBar) {
                HeyChatBottomNavBar(
                    selectedTab = MainTab.SETTINGS,
                    onTabSelected = onTabSelected
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Card Header with Real Authenticated Owner Information
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditProfileClick() },
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(PrimaryEmerald, PrimaryCyan, PrimaryIndigo)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentProfile?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentProfile?.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = getInitials(name),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = about,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (email.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val phone = currentProfile?.phoneNumber
                        if (!phone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "📱 $phone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Show QR Code",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Options List
            SettingsMenuItem(
                icon = Icons.Default.Key,
                title = "Account",
                subtitle = "Security notifications, change password, email",
                onClick = onAccountClick
            )

            SettingsMenuItem(
                icon = Icons.Default.Lock,
                title = "Privacy",
                subtitle = "Block contacts, disappearing messages, last seen",
                onClick = onPrivacyClick
            )

            SettingsMenuItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Chats",
                subtitle = "Theme, wallpapers, chat history",
                onClick = onChatsClick
            )

            SettingsMenuItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Message, group & call tones",
                onClick = onNotificationsClick
            )

            SettingsMenuItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help",
                subtitle = "Help center, contact us, privacy policy",
                onClick = onHelpClick
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Logout Option
            SettingsMenuItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Log Out",
                subtitle = "Sign out from Hey Chat on this device",
                isDestructive = true,
                onClick = { showLogoutDialog = true }
            )
        }
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Settings Light", showBackground = true)
@Composable
fun ProfileSettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        ProfileSettingsScreen()
    }
}
