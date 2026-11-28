package com.example.heychat.ui.screens

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.heychat.data.models.StatusItem
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.viewmodel.StatusViewModel
import io.github.jan.supabase.gotrue.auth
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewerScreen(
    statuses: List<StatusItem>,
    initialIndex: Int = 0,
    statusViewModel: StatusViewModel,
    onClose: () -> Unit
) {
    if (statuses.isEmpty()) {
        return
    }

    val safeLastIndex = maxOf(0, statuses.lastIndex)
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, safeLastIndex)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var showViewerSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    val currentStatus = statuses.getOrNull(currentIndex) ?: statuses.first()
    val currentUserId = (supabaseClient.auth.currentUserOrNull()?.id ?: "").replace("\"", "").replace("'", "").trim().lowercase()
    val isMyStatus = currentStatus.userId.replace("\"", "").replace("'", "").trim().lowercase() == currentUserId

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                isPaused = false
            },
            title = { Text("Delete status update?") },
            text = { Text("This status update will be deleted for you and everyone who received it.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        statusViewModel.deleteStatus(currentStatus.id)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteDialog = false
                        isPaused = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val statusViewers by statusViewModel.statusViewers.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentStatus.id) {
        if (!isMyStatus && currentStatus.id.isNotBlank()) {
            statusViewModel.markStatusAsViewed(currentStatus.id)
        }
        if (isMyStatus && currentStatus.id.isNotBlank()) {
            statusViewModel.loadStatusViewers(currentStatus.id)
        }
    }

    // Auto-advance progress bar (5s per photo status slide)
    LaunchedEffect(currentIndex, isPaused) {
        progress = 0f
        val stepMs = 50L
        val totalMs = 5000L
        while (progress < 1f && isActive) {
            if (!isPaused) {
                delay(stepMs)
                progress += stepMs.toFloat() / totalMs.toFloat()
            } else {
                delay(100L)
            }
        }
        if (progress >= 1f) {
            if (currentIndex < statuses.lastIndex) {
                currentIndex++
            } else {
                onClose()
            }
        }
    }

    val cleanMediaUrl = currentStatus.mediaUrl.trim()
    val isLocalFile = cleanMediaUrl.startsWith("/") || cleanMediaUrl.startsWith("file://")
    val localFilePath = cleanMediaUrl.removePrefix("file://")
    val localBitmap = remember(cleanMediaUrl) {
        if (isLocalFile && File(localFilePath).exists()) {
            runCatching { BitmapFactory.decodeFile(localFilePath) }.getOrNull()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full Screen Photo Image or Fallback Card
        if (localBitmap != null) {
            Image(
                bitmap = localBitmap.asImageBitmap(),
                contentDescription = "Status Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    if (currentIndex > 0) currentIndex--
                                } else {
                                    if (currentIndex < statuses.lastIndex) currentIndex++ else onClose()
                                }
                            }
                        )
                    }
            )
        } else if (cleanMediaUrl.isNotBlank() && cleanMediaUrl.startsWith("http")) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleanMediaUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Status Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    if (currentIndex > 0) currentIndex--
                                } else {
                                    if (currentIndex < statuses.lastIndex) currentIndex++ else onClose()
                                }
                            }
                        )
                    }
            )
        } else {
            // Fallback Full-Screen Story Card if mediaUrl is missing/empty
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(PrimaryEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStatus.userName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = currentStatus.userName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    if (!currentStatus.caption.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentStatus.caption,
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 2. Top Controls: Segmented Progress Bars & Author Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            // Segmented Progress Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                statuses.forEachIndexed { idx, _ ->
                    val segmentProgress = when {
                        idx < currentIndex -> 1f
                        idx == currentIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Author Info Header & Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentStatus.userAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = currentStatus.userAvatar,
                            contentDescription = currentStatus.userName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = currentStatus.userName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentStatus.userName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = currentStatus.createdAt?.substringAfter("T")?.take(5) ?: "Recently",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                if (isMyStatus) {
                    IconButton(
                        onClick = {
                            isPaused = true
                            showDeleteDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Status",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Story",
                        tint = Color.White
                    )
                }
            }
        }

        // 3. Bottom Caption Text overlay
        if (!currentStatus.caption.isNullOrBlank() && localBitmap != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isMyStatus) 60.dp else 70.dp)
            ) {
                Text(
                    text = currentStatus.caption,
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // 4. Bottom Controls: Views Sheet Trigger for Owner OR Reply Bar for Friend
        if (isMyStatus) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable {
                        isPaused = true
                        showViewerSheet = true
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Viewers",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = "Views",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${statusViewers.size} views",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        } else {
            // Friend's Status: Reply Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Reply to status...", color = Color.White.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            statusViewModel.sendStatusReply(currentStatus, replyText)
                            replyText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Reply",
                        tint = PrimaryEmerald
                    )
                }
            }
        }
    }

    // 5. Views Bottom Sheet Modal for My Status
    if (showViewerSheet && isMyStatus) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showViewerSheet = false
                isPaused = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Viewed by ${statusViewers.size}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (statusViewers.isEmpty()) {
                    Text(
                        text = "No views yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        items(statusViewers) { viewer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!viewer.viewerAvatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model = viewer.viewerAvatar,
                                            contentDescription = viewer.viewerName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = viewer.viewerName.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = viewer.viewerName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        if (!viewer.reactionEmoji.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = viewer.reactionEmoji, fontSize = 16.sp)
                                        }
                                    }
                                    Text(
                                        text = viewer.viewedAt?.substringAfter("T")?.take(5) ?: "Recently",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
