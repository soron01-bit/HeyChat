package com.example.heychat.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.heychat.data.local.database.HeyChatDatabase
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.models.Profile
import com.example.heychat.data.remote.supabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.firstOrNull
import com.example.heychat.service.PresenceManager
import com.example.heychat.ui.components.FullScreenImageViewer
import com.example.heychat.ui.components.getInitials
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.theme.PrimaryCyan
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.theme.PrimaryIndigo
import com.example.heychat.ui.viewmodel.ChatListViewModel
import com.example.heychat.ui.viewmodel.ChatViewModel

@Composable
fun UserProfileDetailsScreen(
    userId: String = "",
    conversationId: String = "",
    chatViewModel: ChatViewModel? = null,
    chatListViewModel: ChatListViewModel? = null,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
    val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()

    val profile = chatViewModel?.participantProfile?.collectAsState()?.value
    val isOnline = chatViewModel?.isParticipantOnline?.collectAsState(initial = false)?.value ?: false
    val isBlocked = chatViewModel?.isBlocked?.collectAsState(initial = false)?.value ?: false

    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    // Media list query from Room DB
    val db = remember { HeyChatDatabase.getInstance(context) }
    val mediaEntities = db.messageDao().getMediaMessagesForConversation(cleanConvId).collectAsState(initial = emptyList()).value

    var userProfile by remember { mutableStateOf<Profile?>(chatViewModel?.participantProfile?.value) }

    LaunchedEffect(cleanUserId, cleanConvId) {
        if (cleanUserId.isNotBlank()) {
            chatViewModel?.checkBlockedStatus(cleanUserId)

            // 1. Read Room ProfileDao FIRST for 0ms instant display name
            val cachedProfile = db.profileDao().getProfile(cleanUserId)?.toProfile()
            if (cachedProfile != null) {
                userProfile = cachedProfile
            }

            // 2. Read conversation header name from Room DB
            val convEntity = db.conversationDao().getConversationById(cleanConvId).firstOrNull()
            if (convEntity != null && !convEntity.otherUserName.isNullOrBlank()) {
                val name = convEntity.otherUserName
                userProfile = (userProfile ?: Profile(id = cleanUserId, fullName = name)).copy(
                    fullName = if (userProfile?.fullName.isNullOrBlank()) name else userProfile?.fullName,
                    avatarUrl = convEntity.otherUserAvatar ?: userProfile?.avatarUrl
                )
            }

            // 3. Fetch remote profile from Supabase
            runCatching {
                val list = supabaseClient.from("profiles")
                    .select {
                        filter {
                            eq("id", cleanUserId)
                        }
                    }.decodeList<Profile>()
                val remote = list.firstOrNull()
                if (remote != null) {
                    userProfile = remote
                    db.profileDao().upsertProfile(ProfileEntity.fromProfile(remote))
                }
            }
        }
    }

    if (fullScreenPhotoUrl != null) {
        FullScreenImageViewer(
            imageUrl = fullScreenPhotoUrl!!,
            onDismiss = { fullScreenPhotoUrl = null }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(if (isBlocked) "Unblock ${profile?.displayName ?: "User"}?" else "Block ${profile?.displayName ?: "User"}?") },
            text = { Text(if (isBlocked) "They will be able to message you and see your online status." else "Blocked users cannot find you in search, message you, or see your online status.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        if (isBlocked) {
                            chatViewModel?.unblockUser(cleanUserId)
                        } else {
                            chatViewModel?.blockUser(cleanUserId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isBlocked) "Unblock" else "Block")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock Contact" else "Block Contact") },
                                onClick = {
                                    showMenu = false
                                    showBlockDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = "Block",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            val activeProfile = userProfile ?: profile
            val activeAvatar = activeProfile?.avatarUrl ?: profile?.avatarUrl
            val activeName = activeProfile?.displayName?.takeIf { it != "User" && it != "Chat User" }
                ?: profile?.displayName?.takeIf { it != "User" && it != "Chat User" }
                ?: "User"

            // Large Avatar Circle
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clickable {
                        if (!activeAvatar.isNullOrBlank()) {
                            fullScreenPhotoUrl = activeAvatar
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(PrimaryEmerald, PrimaryCyan, PrimaryIndigo)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!activeAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(activeAvatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = getInitials(activeName),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 38.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full Name
            Text(
                text = activeName.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Username Handle / Email
            val handleOrEmail = when {
                !activeProfile?.handle.isNullOrBlank() -> activeProfile!!.handle
                !activeProfile?.email.isNullOrBlank() -> activeProfile!!.email!!
                else -> ""
            }

            if (handleOrEmail.isNotBlank()) {
                Text(
                    text = handleOrEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Online / Offline Status Subtitle
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isOnline) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Actions Circle Row (Audio, Video, Search)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = Icons.Default.Call,
                    label = "Audio",
                    onClick = {}
                )
                QuickActionButton(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    onClick = {}
                )
                QuickActionButton(
                    icon = Icons.Default.Search,
                    label = "Search",
                    onClick = onBackClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Media, Links, and Docs Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Media, links, and docs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${mediaEntities.size} >",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (mediaEntities.isEmpty()) {
                    Text(
                        text = "No shared media yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.width(12.dp)) }
                        items(mediaEntities, key = { it.id }) { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .size(90.dp)
                                    .clickable {
                                        if (!msg.mediaUrl.isNullOrBlank()) {
                                            fullScreenPhotoUrl = msg.mediaUrl
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(msg.mediaUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Media Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.width(12.dp)) }
                    }
                }
            }

            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // About / Status Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile?.about ?: "Hey there! I am using Hey Chat 💬",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Action Options List
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileOptionRow(
                    icon = Icons.Default.Block,
                    label = if (isBlocked) "Unblock ${profile?.displayName ?: "User"}" else "Block ${profile?.displayName ?: "User"}",
                    textColor = if (isBlocked) PrimaryEmerald else MaterialTheme.colorScheme.error,
                    onClick = { showBlockDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfileOptionRow(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = textColor
            )
        }
    }
}

@Preview(name = "User Profile Details Light", showBackground = true)
@Composable
fun UserProfileDetailsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        UserProfileDetailsScreen()
    }
}
