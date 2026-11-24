package com.example.heychat.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heychat.R
import com.example.heychat.data.Chat
import com.example.heychat.data.Message
import com.example.heychat.data.User
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.SearchRepositoryImpl
import com.example.heychat.ui.components.ChatListItem
import com.example.heychat.ui.components.HeyChatBottomNavBar
import com.example.heychat.ui.components.MainTab
import com.example.heychat.ui.components.QrScannerDialog
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.viewmodel.ChatListViewModel
import androidx.compose.runtime.rememberCoroutineScope
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

enum class ChatFilter {
    ALL,
    UNREAD,
    FAVOURITES,
    GROUPS
}

@Composable
fun ChatFilterRow(
    selectedFilter: ChatFilter,
    totalUnreadCount: Int = 0,
    totalGroupCount: Int = 0,
    onFilterSelected: (ChatFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = when (filter) {
                ChatFilter.UNREAD -> totalUnreadCount
                ChatFilter.GROUPS -> totalGroupCount
                else -> 0
            }

            val labelText = when (filter) {
                ChatFilter.ALL -> "All"
                ChatFilter.UNREAD -> if (count > 0) "Unread $count" else "Unread"
                ChatFilter.FAVOURITES -> "Favourites"
                ChatFilter.GROUPS -> if (count > 0) "Groups $count" else "Groups"
            }

            val backgroundColor by animateColorAsState(
                targetValue = when {
                    isSelected && isDark -> Color(0xFF104A3A)
                    isSelected && !isDark -> PrimaryEmerald.copy(alpha = 0.15f)
                    isDark -> Color(0xFF1E293B).copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                animationSpec = tween(200),
                label = "FilterBgColor"
            )

            val textColor by animateColorAsState(
                targetValue = when {
                    isSelected && isDark -> Color(0xFF25D366)
                    isSelected && !isDark -> PrimaryEmerald
                    isDark -> Color(0xFF94A3B8)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "FilterTextColor"
            )

            val borderColor = if (isSelected) {
                if (isDark) Color(0xFF10B981).copy(alpha = 0.4f) else PrimaryEmerald.copy(alpha = 0.4f)
            } else {
                if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    chats: List<Chat> = emptyList(),
    chatListViewModel: ChatListViewModel? = null,
    onChatClick: (Chat) -> Unit = {},
    onNewChatFabClick: () -> Unit = {},
    onTabSelected: (MainTab) -> Unit = {},
    showBottomBar: Boolean = true
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var selectedOptionChat by remember { mutableStateOf<Chat?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedChatIds = remember { mutableStateListOf<String>() }
    var showQrScanner by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filteredChats = remember(chats, searchQuery, selectedFilter) {
        val baseList = if (searchQuery.isBlank()) {
            chats
        } else {
            chats.filter {
                it.contact.name.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.text.contains(searchQuery, ignoreCase = true)
            }
        }

        when (selectedFilter) {
            ChatFilter.ALL -> baseList
            ChatFilter.UNREAD -> baseList.filter { it.unreadCount > 0 }
            ChatFilter.FAVOURITES -> baseList.filter { it.isFavorite }
            ChatFilter.GROUPS -> baseList.filter { it.contact.name.contains("Group", ignoreCase = true) }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (showOptionsDialog && selectedOptionChat != null) {
        val targetChat = selectedOptionChat!!
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text(targetChat.contact.name, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    // Favorite / Unfavorite Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionsDialog = false
                            chatListViewModel?.toggleFavoriteConversation(targetChat.id, !targetChat.isFavorite)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (targetChat.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                                contentDescription = "Favourites",
                                tint = PrimaryEmerald
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (targetChat.isFavorite) "Remove from Favourites" else "Add to Favourites", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Select Multiple Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionsDialog = false
                            isMultiSelectMode = true
                            selectedChatIds.add(targetChat.id)
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

                    Spacer(modifier = Modifier.height(2.dp))

                    // Mute / Unmute Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionsDialog = false
                            chatListViewModel?.toggleMuteConversation(targetChat.id, !targetChat.isMuted)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (targetChat.isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (targetChat.isMuted) "Unmute" else "Mute",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (targetChat.isMuted) "Unmute Notifications" else "Mute Notifications")
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Delete Chat Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionsDialog = false
                            showDeleteConfirmDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Chat",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete Chat", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Block User Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showOptionsDialog = false
                            showBlockConfirmDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block User",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Block User", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockConfirmDialog && selectedOptionChat != null) {
        val targetChat = selectedOptionChat!!
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Block ${targetChat.contact.name}?") },
            text = { Text("Blocked users cannot find you in search, message you, or see your online status.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        chatListViewModel?.blockUser(targetChat.contact.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && selectedOptionChat != null) {
        val targetChat = selectedOptionChat!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete chat with ${targetChat.contact.name}?") },
            text = { Text("This will delete the conversation and messages from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        chatListViewModel?.deleteConversation(targetChat.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedChatIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedChatIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                chatListViewModel?.toggleFavoriteConversationsBulk(selectedChatIds.toList(), true)
                                isMultiSelectMode = false
                                selectedChatIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Add to Favourites",
                                tint = PrimaryEmerald
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedChatIds.forEach { id -> chatListViewModel?.deleteConversation(id) }
                                isMultiSelectMode = false
                                selectedChatIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = isSearchActive,
                            label = "SearchTopBarTransition"
                        ) { active ->
                            if (active) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search chats...", style = MaterialTheme.typography.bodyMedium) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .focusRequester(focusRequester)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = "Hey Chat Brand Logo",
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Hey Chat",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQrScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera / Scan QR Code"
                            )
                        }
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                HeyChatBottomNavBar(
                    selectedTab = MainTab.CHATS,
                    onTabSelected = onTabSelected
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = "New Chat"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // WhatsApp-Style Horizontal Filter Chips Row (All, Unread, Favourites, Groups)
                ChatFilterRow(
                    selectedFilter = selectedFilter,
                    totalUnreadCount = chats.count { it.unreadCount > 0 },
                    totalGroupCount = chats.count { it.contact.name.contains("Group", ignoreCase = true) },
                    onFilterSelected = { selectedFilter = it }
                )

                if (filteredChats.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "No Chats",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "No conversations yet",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tap the message button below to start a new chat with your contacts!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredChats, key = { it.id }) { chat ->
                            val isSelected = selectedChatIds.contains(chat.id)
                            Box(modifier = Modifier.animateItemPlacement(animationSpec = tween(220))) {
                                ChatListItem(
                                    chat = chat,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (selectedChatIds.contains(chat.id)) {
                                                selectedChatIds.remove(chat.id)
                                                if (selectedChatIds.isEmpty()) {
                                                    isMultiSelectMode = false
                                                }
                                            } else {
                                                selectedChatIds.add(chat.id)
                                            }
                                        } else {
                                            onChatClick(chat)
                                        }
                                    },
                                    onLongClick = {
                                        if (isMultiSelectMode) {
                                            if (selectedChatIds.contains(chat.id)) {
                                                selectedChatIds.remove(chat.id)
                                                if (selectedChatIds.isEmpty()) {
                                                    isMultiSelectMode = false
                                                }
                                            } else {
                                                selectedChatIds.add(chat.id)
                                            }
                                        } else {
                                            selectedOptionChat = chat
                                            showOptionsDialog = true
                                        }
                                    }
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
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
                                onChatClick(
                                    Chat(
                                        id = convId,
                                        contact = User(id = cleanScannedId, name = "User", phoneNumber = ""),
                                        lastMessage = Message(id = "", senderId = "", text = "", timestamp = "", isFromMe = false)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Preview(name = "Chat List Light", showBackground = true)
@Composable
fun ChatListScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        ChatListScreen()
    }
}
