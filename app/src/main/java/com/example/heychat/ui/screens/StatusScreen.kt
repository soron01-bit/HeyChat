package com.example.heychat.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.heychat.data.models.StatusItem
import com.example.heychat.data.models.UserStatusGroup
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.ui.components.HeyChatBottomNavBar
import com.example.heychat.ui.components.MainTab
import com.example.heychat.ui.components.MainTopAppBar
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.viewmodel.StatusViewModel
import io.github.jan.supabase.gotrue.auth
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun StatusScreen(
    statusViewModel: StatusViewModel,
    onTabSelected: (MainTab) -> Unit = {},
    onPrivacySettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    showBottomBar: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val myStatuses by statusViewModel.myStatuses.collectAsStateWithLifecycle()
    val recentStatusGroups by statusViewModel.recentStatusGroups.collectAsStateWithLifecycle()
    val isUploading by statusViewModel.isUploading.collectAsStateWithLifecycle()

    var activeViewerGroup by remember { mutableStateOf<List<StatusItem>?>(null) }
    var selectedPreviewUri by remember { mutableStateOf<Uri?>(null) }

    // Photo Picker Launcher for instant 0ms status photo publication
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.d("HeyChat_Status", "Photo selected from Photo Picker (${bytes.size} bytes). Uploading status...")
                        statusViewModel.uploadStatusPhoto(bytes, "")
                    }
                }.onFailure { e ->
                    Log.e("HeyChat_Status", "Failed to read selected photo bytes: ${e.message}", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        statusViewModel.refreshStatuses()
    }

    Scaffold(
        topBar = {
            MainTopAppBar(
                title = "Status",
                onSearchClick = onSearchClick
            )
        },
        bottomBar = {
            if (showBottomBar) {
                HeyChatBottomNavBar(
                    selectedTab = MainTab.STATUS,
                    unseenStatusCount = recentStatusGroups.count { it.hasUnseen },
                    onTabSelected = onTabSelected
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = PrimaryEmerald,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add Status Photo"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // My Status Section Header & Privacy Link
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            modifier = Modifier.clickable { onPrivacySettingsClick() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Privacy",
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Privacy",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald
                                )
                            )
                        }
                    }
                }

                // My Status Card
                item {
                    val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                    val myLatest = myStatuses.firstOrNull()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (myStatuses.isNotEmpty()) {
                                    activeViewerGroup = myStatuses
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (myStatuses.isNotEmpty()) 2.5.dp else 0.dp,
                                        color = if (myStatuses.isNotEmpty()) PrimaryEmerald else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (myLatest != null && myLatest.mediaUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = myLatest.mediaUrl,
                                        contentDescription = "My Status",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "My Status",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            if (myStatuses.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "My Status",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (myStatuses.isNotEmpty()) "Tap to view your status updates" else "Tap to add status update",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isUploading) {
                            CircularProgressIndicator(
                                color = PrimaryEmerald,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Recent Updates Section (Unseen Statuses with Vibrant Emerald Ring)
                val unseenGroups = recentStatusGroups.filter { it.hasUnseen }
                if (unseenGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent updates",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(unseenGroups) { group ->
                        StatusGroupItemRow(
                            group = group,
                            isUnseen = true,
                            onClick = { activeViewerGroup = group.statuses }
                        )
                    }
                }

                // Viewed Updates Section (Viewed Statuses with Muted Grey Ring)
                val viewedGroups = recentStatusGroups.filter { !it.hasUnseen }
                if (viewedGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Viewed updates",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    items(viewedGroups) { group ->
                        StatusGroupItemRow(
                            group = group,
                            isUnseen = false,
                            onClick = { activeViewerGroup = group.statuses }
                        )
                    }
                }
            }
        }
    }

    // Full-Screen Story Viewer Overlay in dedicated Window Dialog
    if (activeViewerGroup != null) {
        Dialog(
            onDismissRequest = { activeViewerGroup = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            StatusViewerScreen(
                statuses = activeViewerGroup!!,
                initialIndex = 0,
                statusViewModel = statusViewModel,
                onClose = { activeViewerGroup = null }
            )
        }
    } else if (selectedPreviewUri != null) {
        // Pre-Publish Status Preview Screen Overlay
        StatusPrePublishPreviewScreen(
            imageUri = selectedPreviewUri!!,
            statusViewModel = statusViewModel,
            onBackClick = { selectedPreviewUri = null },
            onPublishSuccess = { selectedPreviewUri = null }
        )
    }
}

@Composable
fun StatusGroupItemRow(
    group: UserStatusGroup,
    isUnseen: Boolean,
    onClick: () -> Unit
) {
    val latest = group.statuses.lastOrNull() ?: return
    val ringColor = if (isUnseen) PrimaryEmerald else MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    color = ringColor,
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!group.userAvatar.isNullOrBlank()) {
                AsyncImage(
                    model = group.userAvatar,
                    contentDescription = group.userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = group.userName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.userName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = latest.createdAt?.substringAfter("T")?.take(5) ?: "Recently",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
