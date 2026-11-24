package com.example.heychat.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import com.example.heychat.ui.components.ForwardContactPickerSheet
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.heychat.ui.components.ChatWallpaperBackground
import com.example.heychat.ui.components.ChatWallpaperTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.heychat.data.User
import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.service.PresenceManager
import com.example.heychat.ui.components.EmojiPickerContainer
import com.example.heychat.ui.components.IndividualChatTopBar
import com.example.heychat.ui.components.MessageBubble
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.viewmodel.ChatListViewModel
import com.example.heychat.ui.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.heychat.util.AudioRecorder
import com.example.heychat.util.NotificationHelper
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IndividualChatScreen(
    conversationId: String = "",
    chatViewModel: ChatViewModel? = null,
    onBackClick: () -> Unit = {},
    onProfileClick: (userId: String, conversationId: String) -> Unit = { _, _ -> }
) {
    var inputText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val replyingTo = chatViewModel?.replyingToMessage?.collectAsStateWithLifecycle()?.value

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val scaledBitmap = scaleBitmapToMax(originalBitmap, 1280)
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val compressedBytes = outputStream.toByteArray()

                    if (conversationId.isNotBlank()) {
                        val replyId = replyingTo?.id
                        chatViewModel?.sendPhotoMessage(conversationId, compressedBytes, replyId)
                    }
                }
            }
        }
    }

    var pendingCameraTempFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val file = pendingCameraTempFile
        pendingCameraTempFile = null

        if (success && file != null && file.exists()) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (originalBitmap != null) {
                        val scaledBitmap = scaleBitmapToMax(originalBitmap, 1280)
                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val compressedBytes = outputStream.toByteArray()

                        if (conversationId.isNotBlank()) {
                            val replyId = replyingTo?.id
                            chatViewModel?.sendPhotoMessage(conversationId, compressedBytes, replyId)
                        }
                    }
                }
                file.delete()
            }
        } else {
            file?.delete()
        }
    }

    val participant = chatViewModel?.participantProfile?.collectAsStateWithLifecycle()?.value
    val messages = chatViewModel?.messages?.collectAsStateWithLifecycle(initialValue = emptyList())?.value ?: emptyList()
    val messageReactions = chatViewModel?.messageReactions?.collectAsStateWithLifecycle()?.value ?: emptyMap()
    val sendError = chatViewModel?.sendError?.collectAsStateWithLifecycle()?.value
    val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""

    LaunchedEffect(messages) {
        val newestId = messages.firstOrNull()?.id ?: "none"
        Log.d("HeyChat_UI", "Compose received updated message list, size=${messages.size}, newestId=$newestId, at timestamp=${System.currentTimeMillis()}")
    }

    val messagesMap = remember(messages) {
        messages.filter { !it.id.isNullOrBlank() }.associateBy { it.id!! }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && conversationId.isNotBlank()) {
                chatViewModel?.runSafetyNetSync(conversationId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(conversationId) {
        if (conversationId.isNotBlank()) {
            NotificationHelper.activeConversationId = conversationId
        }
        onDispose {
            if (NotificationHelper.activeConversationId == conversationId) {
                NotificationHelper.activeConversationId = null
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isNotBlank()) {
            chatViewModel?.loadChat(conversationId)
        }
    }

    LaunchedEffect(sendError) {
        if (!sendError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(sendError)
            chatViewModel?.clearError()
        }
    }

    val density = LocalDensity.current
    var isInitialListLoaded by remember(conversationId) { mutableStateOf(false) }
    val alreadyAnimatedMessageIds = remember(conversationId) { mutableSetOf<String>() }

    var isMultiSelectMode by remember(conversationId) { mutableStateOf(false) }
    val selectedMessageIds = remember(conversationId) { mutableStateListOf<String>() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showForwardSheet by remember { mutableStateOf(false) }
    var singleForwardMessage by remember { mutableStateOf<MessageModel?>(null) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var currentVoiceFile by remember { mutableStateOf<File?>(null) }
    val audioRecorder = remember { AudioRecorder(context) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val voiceFile = File(context.cacheDir, "voice_temp_${System.currentTimeMillis()}.m4a")
            currentVoiceFile = voiceFile
            audioRecorder.start(voiceFile)
            isRecordingVoice = true
            recordingSeconds = 0
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice notes")
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            while (isActive && isRecordingVoice) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    val canDeleteForEveryone = remember(selectedMessageIds.toList(), messages) {
        val selectedMsgs = messages.filter { selectedMessageIds.contains(it.id) }
        selectedMsgs.isNotEmpty() && selectedMsgs.all { it.senderId == currentUserId && !it.isDeleted }
    }

    LaunchedEffect(messages) {
        val newestId = messages.firstOrNull()?.id ?: "none"
        val uiTimestamp = System.currentTimeMillis()
        Log.d("HeyChat_Latency", "[UI_STATE] Message '$newestId' reached Compose UI state at timestamp=$uiTimestamp (total messages: ${messages.size})")
        Log.d("HeyChat_UI", "Message list updated in UI state with ${messages.size} messages, newestId=$newestId at timestamp=$uiTimestamp")

        if (!isInitialListLoaded && messages.isNotEmpty()) {
            messages.forEach { msg ->
                msg.id?.let { alreadyAnimatedMessageIds.add(it) }
            }
            isInitialListLoaded = true
        } else if (isInitialListLoaded) {
            val newUnseenMsgs = messages.filter { msg -> msg.id != null && !alreadyAnimatedMessageIds.contains(msg.id) }
            if (newUnseenMsgs.size > 3) {
                newUnseenMsgs.forEach { msg ->
                    msg.id?.let { alreadyAnimatedMessageIds.add(it) }
                }
            }
        }
    }

    val isNearBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    // Smart scroll when a NEW message arrives or when self sends a message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val newestMsg = messages.firstOrNull()
            val isFromMe = newestMsg?.senderId == currentUserId

            if (isFromMe || isNearBottom) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val isOnline = chatViewModel?.isParticipantOnline?.collectAsStateWithLifecycle(initialValue = false)?.value ?: false
    val isBlocked = chatViewModel?.isBlocked?.collectAsStateWithLifecycle(initialValue = false)?.value ?: false
    val onlineSet = PresenceManager.onlineUsers.collectAsStateWithLifecycle(initialValue = emptySet()).value

    LaunchedEffect(participant?.id, isOnline, onlineSet) {
        Log.d("HeyChat_Presence", "Header checking status for otherUserId='${participant?.id}', onlineSet=$onlineSet, isOnline=$isOnline")
    }

    val displayContact = User(
        id = participant?.id ?: "",
        name = participant?.displayName ?: "Chat User",
        phoneNumber = participant?.email ?: "",
        status = participant?.about ?: "Hey there! I am using Hey Chat 💬",
        avatarUrl = participant?.avatarUrl,
        isOnline = isOnline
    )

    var showUnblockConfirmDialog by remember { mutableStateOf(false) }

    if (showUnblockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnblockConfirmDialog = false },
            title = { Text("Unblock ${displayContact.name}?") },
            text = { Text("They will be able to message you and see your online status.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnblockConfirmDialog = false
                        participant?.id?.let { chatViewModel?.unblockUser(it) }
                    }
                ) {
                    Text("Unblock")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnblockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val wallpaperPrefs = remember(conversationId) { context.getSharedPreferences("heychat_wallpaper_prefs", Context.MODE_PRIVATE) }
    var selectedWallpaperName by remember(conversationId) {
        mutableStateOf(wallpaperPrefs.getString("wallpaper_$conversationId", ChatWallpaperTheme.EMERALD.name) ?: ChatWallpaperTheme.EMERALD.name)
    }
    val currentWallpaperTheme = remember(selectedWallpaperName) {
        runCatching { ChatWallpaperTheme.valueOf(selectedWallpaperName) }.getOrDefault(ChatWallpaperTheme.EMERALD)
    }
    var showWallpaperDialog by remember { mutableStateOf(false) }

    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("Choose Wallpaper Theme", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChatWallpaperTheme.entries.forEach { theme ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentWallpaperTheme == theme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWallpaperName = theme.name
                                    wallpaperPrefs.edit().putString("wallpaper_$conversationId", theme.name).apply()
                                    showWallpaperDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.backgroundColorDark)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = theme.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedMessageIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedMessageIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showForwardSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Forward,
                                contentDescription = "Forward Selected",
                                tint = PrimaryEmerald
                            )
                        }
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                IndividualChatTopBar(
                    user = displayContact,
                    onBackClick = onBackClick,
                    onProfileClick = {
                        onProfileClick(displayContact.id, conversationId)
                    },
                    onWallpaperClick = {
                        showWallpaperDialog = true
                    }
                )
            }
        },
        bottomBar = {
            if (isBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .clickable {
                            showUnblockConfirmDialog = true
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Blocked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You blocked this contact. Tap to unblock.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Quoted Reply Preview Bar
                    if (replyingTo != null) {
                        val quotedText = if (replyingTo.isDeleted) "Original message was deleted" else replyingTo.content
                        val quotedSender = if (replyingTo.senderId == currentUserId) "You" else displayContact.name

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PrimaryEmerald)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to $quotedSender",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald
                                    )
                                )
                                Text(
                                    text = quotedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { chatViewModel?.setReplyingToMessage(null) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (isRecordingVoice) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    audioRecorder.stop()
                                    currentVoiceFile?.delete()
                                    isRecordingVoice = false
                                    recordingSeconds = 0
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel Recording",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                val mins = recordingSeconds / 60
                                val secs = recordingSeconds % 60
                                val formattedTimer = String.format(Locale.US, "%02d:%02d", mins, secs)

                                Text(
                                    text = "Recording $formattedTimer",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FloatingActionButton(
                                onClick = {
                                    audioRecorder.stop()
                                    val file = currentVoiceFile
                                    val duration = recordingSeconds
                                    isRecordingVoice = false
                                    recordingSeconds = 0

                                    if (file != null && file.exists() && file.length() > 0 && conversationId.isNotBlank()) {
                                        val bytes = file.readBytes()
                                        chatViewModel?.sendVoiceMessage(conversationId, bytes, duration)
                                        file.delete()
                                    }
                                },
                                shape = CircleShape,
                                containerColor = PrimaryEmerald,
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Voice Note",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        // Input Text Field Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = {
                                    inputText = it
                                    if (showEmojiPicker) showEmojiPicker = false
                                },
                                placeholder = { Text("Message...") },
                                leadingIcon = {
                                    IconButton(onClick = {
                                        if (showEmojiPicker) {
                                            showEmojiPicker = false
                                        } else {
                                            keyboardController?.hide()
                                            showEmojiPicker = true
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEmotions,
                                            contentDescription = "Emoji",
                                            tint = if (showEmojiPicker) PrimaryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = "Attachment",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            try {
                                                val tempFile = File.createTempFile("camera_photo_", ".jpg", context.cacheDir)
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                                pendingCameraTempFile = tempFile
                                                cameraLauncher.launch(uri)
                                            } catch (e: Exception) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("No camera app found")
                                                }
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Camera",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(26.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            FloatingActionButton(
                                onClick = {
                                    if (inputText.isNotBlank() && conversationId.isNotBlank()) {
                                        chatViewModel?.sendMessage(conversationId, inputText)
                                        inputText = ""
                                    } else if (inputText.isBlank()) {
                                        val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasMicPerm) {
                                            val voiceFile = File(context.cacheDir, "voice_temp_${System.currentTimeMillis()}.m4a")
                                            currentVoiceFile = voiceFile
                                            audioRecorder.start(voiceFile)
                                            isRecordingVoice = true
                                            recordingSeconds = 0
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (inputText.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (inputText.isBlank()) "Voice Note" else "Send Message",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Animated Emoji Picker Panel
                    AnimatedVisibility(
                        visible = showEmojiPicker,
                        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { it / 2 },
                        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { it / 2 }
                    ) {
                        EmojiPickerContainer(
                            onEmojiPicked = { emoji ->
                                inputText += emoji
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        ChatWallpaperBackground(
            wallpaperTheme = currentWallpaperTheme
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages yet.\nType a message below to start chatting!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { "${it.id ?: System.identityHashCode(it)}_${it.status.name}_${messageReactions[it.id]?.map { r -> "${r.userId}_${r.emoji}" } ?: it.reactionsJson}" },
                        contentType = { "message_bubble" }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            currentUserId = currentUserId,
                            messagesMap = messagesMap,
                            reactions = message.reactionsList.ifEmpty { messageReactions[message.id] ?: emptyList() },
                            wallpaperTheme = currentWallpaperTheme,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = message.id != null && selectedMessageIds.contains(message.id),
                            onMultiSelectToggle = {
                                message.id?.let { msgId ->
                                    if (selectedMessageIds.contains(msgId)) {
                                        selectedMessageIds.remove(msgId)
                                        if (selectedMessageIds.isEmpty()) {
                                            isMultiSelectMode = false
                                        }
                                    } else {
                                        selectedMessageIds.add(msgId)
                                        isMultiSelectMode = true
                                    }
                                }
                            },
                            onReplyClick = { targetMsg ->
                                chatViewModel?.setReplyingToMessage(targetMsg)
                            },
                            onForwardClick = { targetMsg ->
                                singleForwardMessage = targetMsg
                                showForwardSheet = true
                            },
                            onDeleteForMe = { targetMsgId ->
                                chatViewModel?.deleteMessageForMe(targetMsgId)
                            },
                            onDeleteForEveryone = { targetMsgId ->
                                chatViewModel?.deleteMessageForEveryone(targetMsgId)
                            },
                            onQuotedMessageClick = { targetId ->
                                coroutineScope.launch {
                                    val idx = messages.indexOfFirst { it.id == targetId }
                                    if (idx >= 0) {
                                        listState.animateScrollToItem(idx)
                                    }
                                }
                            },
                            onReactionClick = { emoji ->
                                message.id?.let { chatViewModel?.toggleReaction(it, emoji) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedMessageIds.size} item(s)?") },
            text = { Text("Selected items will be removed from your chat view.") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showBulkDeleteDialog = false
                            chatViewModel?.deleteMessagesForMeBulk(selectedMessageIds.toList())
                            isMultiSelectMode = false
                            selectedMessageIds.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for me")
                    }

                    if (canDeleteForEveryone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showBulkDeleteDialog = false
                                chatViewModel?.deleteMessagesForEveryoneBulk(selectedMessageIds.toList())
                                isMultiSelectMode = false
                                selectedMessageIds.clear()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for everyone")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForwardSheet) {
        val chatListViewModel: ChatListViewModel = viewModel()
        val userChats = chatListViewModel.chats.collectAsState().value

        ForwardContactPickerSheet(
            chats = userChats,
            onDismiss = {
                showForwardSheet = false
                singleForwardMessage = null
            },
            onForwardConfirmed = { targetContactIds ->
                val msgsToForward = if (singleForwardMessage != null) {
                    listOf(singleForwardMessage!!)
                } else {
                    messages.filter { selectedMessageIds.contains(it.id) }
                }
                chatViewModel?.forwardMessages(targetContactIds, msgsToForward)
                showForwardSheet = false
                singleForwardMessage = null
                isMultiSelectMode = false
                selectedMessageIds.clear()
            }
        )
    }
}
}

@Preview(name = "Individual Chat Light", showBackground = true)
@Composable
fun IndividualChatScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        IndividualChatScreen()
    }
}
