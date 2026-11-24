package com.example.heychat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.heychat.util.AudioPlayerManager
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageReactionModel
import com.example.heychat.ui.theme.LocalChatColors
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.theme.ReceivedMessageShape
import com.example.heychat.ui.theme.SentMessageShape

@Composable
fun QuickReactionPill(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickEmojis = remember { listOf("👍", "❤️", "😂", "😮", "😢", "🙏") }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickEmojis.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun MessageReactionBadge(
    reactions: List<MessageReactionModel>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val distinctReactions = remember(reactions) {
        reactions.distinctBy { it.userId }
    }

    if (distinctReactions.isEmpty()) return

    val grouped = remember(distinctReactions) {
        distinctReactions.groupBy { it.emoji }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.clickable {
            val firstEmoji = distinctReactions.firstOrNull()?.emoji ?: "👍"
            onReactionClick(firstEmoji)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            grouped.keys.take(2).forEach { emoji ->
                Text(text = emoji, fontSize = 16.sp)
            }
            if (distinctReactions.size > 1) {
                Text(
                    text = "${distinctReactions.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageModel,
    currentUserId: String,
    messagesMap: Map<String, MessageModel> = emptyMap(),
    reactions: List<MessageReactionModel> = emptyList(),
    wallpaperTheme: ChatWallpaperTheme = ChatWallpaperTheme.EMERALD,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onMultiSelectToggle: () -> Unit = {},
    onReplyClick: (MessageModel) -> Unit = {},
    onForwardClick: (MessageModel) -> Unit = {},
    onDeleteForMe: (String) -> Unit = {},
    onDeleteForEveryone: (String) -> Unit = {},
    onQuotedMessageClick: (String) -> Unit = {},
    onReactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFromMe = message.senderId == currentUserId
    val alignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isFromMe) SentMessageShape else ReceivedMessageShape
    val formattedTime = remember(message.createdAt) { formatMessageTime(message.createdAt) }

    val isDark = isSystemInDarkTheme()

    // Outgoing Message Sent Gradient Brush
    val sentGradientBrush = remember(wallpaperTheme) {
        when (wallpaperTheme) {
            ChatWallpaperTheme.SAPPHIRE -> Brush.linearGradient(colors = listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6)))
            ChatWallpaperTheme.SUNSET -> Brush.linearGradient(colors = listOf(Color(0xFFBE123C), Color(0xFFFB7185)))
            ChatWallpaperTheme.OLED -> Brush.linearGradient(colors = listOf(Color(0xFF047857), Color(0xFF10B981)))
            else -> Brush.linearGradient(colors = listOf(Color(0xFF047857), Color(0xFF10B981)))
        }
    }

    // Incoming Message Glassmorphic Colors
    val receivedGlassColor = remember(isDark) {
        if (isDark) Color(0xFF1E293B).copy(alpha = 0.90f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    }
    val receivedGlassBorder = remember(isDark) {
        if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1).copy(alpha = 0.8f)
    }

    val contentColor = if (isFromMe) Color.White else MaterialTheme.colorScheme.onSurface

    var showOptionDialog by remember { mutableStateOf(false) }
    var showConfirmForMeDialog by remember { mutableStateOf(false) }
    var showConfirmForEveryoneDialog by remember { mutableStateOf(false) }

    val canDeleteForEveryone = isFromMe && !message.isDeleted
    val quotedMessage = remember(message.replyToMessageId, messagesMap) {
        message.replyToMessageId?.let { messagesMap[it] }
    }

    val haptic = LocalHapticFeedback.current

    val onMessageLongClick = {
        if (!message.id.isNullOrBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (isMultiSelectMode) {
                onMultiSelectToggle()
            } else {
                showOptionDialog = true
            }
        }
    }

    if (showOptionDialog) {
        AlertDialog(
            onDismissRequest = { showOptionDialog = false },
            title = {
                QuickReactionPill(
                    onEmojiSelected = { emoji ->
                        showOptionDialog = false
                        message.id?.let { onReactionClick(emoji) }
                    }
                )
            },
            text = {
                Column {
                    // Reply Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionDialog = false
                            onReplyClick(message)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Reply",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Reply", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Forward Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionDialog = false
                            onForwardClick(message)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Forward,
                                contentDescription = "Forward",
                                tint = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Forward", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Select Multiple Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionDialog = false
                            onMultiSelectToggle()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select Multiple",
                                tint = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Select Multiple", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Delete for Me Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionDialog = false
                            showConfirmForMeDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete for Me",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete for Me")
                        }
                    }

                    if (canDeleteForEveryone) {
                        Spacer(modifier = Modifier.height(2.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                showOptionDialog = false
                                showConfirmForEveryoneDialog = true
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Delete for Everyone",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showOptionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmForMeDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmForMeDialog = false },
            title = { Text("Delete for Me?") },
            text = { Text("This message will be removed from this device, but will remain visible to other chat participants.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmForMeDialog = false
                        message.id?.let { onDeleteForMe(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete for Me")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmForMeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmForEveryoneDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmForEveryoneDialog = false },
            title = { Text("Delete for Everyone?") },
            text = { Text("This will unsend and replace the message for everyone in this conversation.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmForEveryoneDialog = false
                        message.id?.let { onDeleteForEveryone(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete for Everyone")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmForEveryoneDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    if (fullScreenImageUrl != null) {
        FullScreenImageViewer(
            imageUrl = fullScreenImageUrl!!,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var hasTriggeredReply by remember { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SwipeToReplyOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) PrimaryEmerald.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled = isMultiSelectMode) { onMultiSelectToggle() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = alignment
    ) {
        // Revealed Reply Icon on Swipe
        if (animatedOffsetX > 10f) {
            val replyIconAlpha = (animatedOffsetX / 60f).coerceIn(0f, 1f)
            val replyIconScale = (animatedOffsetX / 60f).coerceIn(0.5f, 1.2f)

            Box(
                modifier = Modifier
                    .align(if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 12.dp)
                    .graphicsLayer(
                        alpha = replyIconAlpha,
                        scaleX = replyIconScale,
                        scaleY = replyIconScale
                    )
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryEmerald.copy(alpha = 0.9f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Box(contentAlignment = Alignment.BottomStart) {
            Box(modifier = Modifier.padding(bottom = if (reactions.isNotEmpty()) 16.dp else 0.dp)) {
                Surface(
                    shape = shape,
                    color = if (isFromMe) Color.Transparent else receivedGlassColor,
                    shadowElevation = if (isDark) 0.dp else 1.5.dp,
                    border = if (isFromMe) null else BorderStroke(1.dp, receivedGlassBorder),
                    modifier = Modifier
                        .widthIn(min = 80.dp, max = 310.dp)
                        .then(
                            if (isFromMe) {
                                Modifier
                                    .clip(shape)
                                    .background(sentGradientBrush)
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer(translationX = animatedOffsetX)
                .pointerInput(message.id, message.isDeleted) {
                    if (message.isDeleted) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = 0f
                            hasTriggeredReply = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                            hasTriggeredReply = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 1.5f || offsetX > 0f) {
                                change.consume()
                                val newOffset = (offsetX + dragAmount * 0.5f).coerceIn(0f, 120f)
                                offsetX = newOffset

                                if (newOffset >= 60f && !hasTriggeredReply) {
                                    hasTriggeredReply = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReplyClick(message)
                                }
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (isMultiSelectMode) {
                            onMultiSelectToggle()
                        }
                    },
                    onLongClick = onMessageLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
            ) {
                // Render Quoted Box if Replying
                val cleanReplyId = message.replyToMessageId?.replace("\"", "")?.replace("'", "")?.trim()
                val hasValidReply = !cleanReplyId.isNullOrBlank() && cleanReplyId != "null" && cleanReplyId != "00000000-0000-0000-0000-000000000000"

                if (hasValidReply) {
                    val targetId = cleanReplyId
                    val quotedText = if (quotedMessage?.isDeleted == true) "Original message was deleted" else quotedMessage?.content ?: "Quoted message"
                    val quotedSender = if (quotedMessage?.senderId == currentUserId) "You" else "Participant"

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        onMultiSelectToggle()
                                    } else {
                                        targetId?.let { onQuotedMessageClick(it) }
                                    }
                                },
                                onLongClick = onMessageLongClick
                            )
                    ) {
                        Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PrimaryEmerald)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = quotedSender,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = PrimaryEmerald
                                )
                                Text(
                                    text = quotedText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontStyle = if (quotedMessage?.isDeleted == true) FontStyle.Italic else FontStyle.Normal
                                    ),
                                    color = contentColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                val isVoiceNote = !message.mediaUrl.isNullOrBlank() && (message.mediaUrl.contains(".m4a") || message.mediaUrl.contains(".aac") || message.content.contains("🎤"))
                val isPhoto = !message.mediaUrl.isNullOrBlank() && !isVoiceNote && !message.isDeleted

                // Render Voice Note Player Card if Voice Note
                if (isVoiceNote && !message.isDeleted) {
                    val currentlyPlayingUrl by AudioPlayerManager.currentlyPlayingUrl.collectAsState()
                    val isPlaying = currentlyPlayingUrl == message.mediaUrl && AudioPlayerManager.isPlaying.collectAsState().value
                    val currentPosMs by AudioPlayerManager.currentPositionMs.collectAsState()
                    val totalDurationMs by AudioPlayerManager.durationMs.collectAsState()

                    val activeProgress = if (currentlyPlayingUrl == message.mediaUrl && totalDurationMs > 0) {
                        (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isFromMe) Color.White.copy(alpha = 0.25f) else PrimaryEmerald,
                            onClick = {
                                if (isMultiSelectMode) {
                                    onMultiSelectToggle()
                                } else {
                                    message.mediaUrl?.let { AudioPlayerManager.playOrPause(it) }
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause Voice Note",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { activeProgress },
                                color = if (isFromMe) Color.White else PrimaryEmerald,
                                trackColor = if (isFromMe) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = message.content.ifBlank { "🎤 Voice message" },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = contentColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Render Media Photo Image if Present
                if (isPhoto) {
                    val isLocalPreview = isFromMe && message.mediaUrl?.contains("pending_photo") == true

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        onMultiSelectToggle()
                                    } else if (!message.mediaUrl.isNullOrBlank()) {
                                        fullScreenImageUrl = message.mediaUrl
                                    }
                                },
                                onLongClick = onMessageLongClick
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Chat Photo Attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLocalPreview) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = message.isDeleted,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "DeletedMessageTransition"
                ) { isDeletedState ->
                    if (isDeletedState) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Deleted",
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "This message was deleted",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        val hasMedia = !message.mediaUrl.isNullOrBlank()
                        val isPlaceholderText = message.text.isBlank() ||
                            message.text.equals("Photo", ignoreCase = true) ||
                            message.text.equals("📷 Photo", ignoreCase = true)

                        val showTextMessage = !hasMedia || !isPlaceholderText

                        if (showTextMessage) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp),
                                color = contentColor
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp, start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = contentColor.copy(alpha = 0.7f)
                    )

                    if (isFromMe && !message.isDeleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIndicator(status = message.status)
                    }
                }
            }

            }
        }

        if (reactions.isNotEmpty()) {
            MessageReactionBadge(
                reactions = reactions,
                onReactionClick = { emoji ->
                    message.id?.let { onReactionClick(emoji) }
                },
                modifier = Modifier.offset(x = 12.dp, y = 6.dp)
            )
        }
    }
}
}

fun formatMessageTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return "Just now"
    return runCatching {
        val cleanIso = createdAt.replace("Z", "").substringBefore(".")
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = utcFormat.parse(cleanIso) ?: return "Just now"

        val localFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        localFormat.format(date)
    }.getOrDefault("Just now")
}
