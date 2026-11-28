package com.example.heychat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.heychat.data.Chat
import com.example.heychat.ui.components.HeyChatBottomNavBar
import com.example.heychat.ui.components.MainTab
import com.example.heychat.ui.components.UpdateDialog
import com.example.heychat.ui.viewmodel.ChatListViewModel
import com.example.heychat.ui.viewmodel.SettingsViewModel
import com.example.heychat.ui.viewmodel.StatusViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(
    initialTab: MainTab = MainTab.CHATS,
    chatListViewModel: ChatListViewModel,
    statusViewModel: StatusViewModel,
    settingsViewModel: SettingsViewModel,
    onChatClick: (Chat) -> Unit,
    onNewChatFabClick: () -> Unit,
    onPrivacySettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onAccountClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onChatsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val initialPage = when (initialTab) {
        MainTab.CHATS -> 0
        MainTab.STATUS -> 1
        MainTab.CALLS -> 2
        MainTab.SETTINGS -> 3
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 4 }
    )

    val userChats = chatListViewModel.chats.collectAsState().value
    val appUpdateConfig = chatListViewModel.appUpdateConfig.collectAsState().value

    LaunchedEffect(Unit) {
        chatListViewModel.loadUserConversations()
    }

    if (appUpdateConfig != null) {
        UpdateDialog(
            appConfig = appUpdateConfig,
            onDismiss = { chatListViewModel.dismissUpdateDialog() }
        )
    }

    val onTabSelectedLambda: (MainTab) -> Unit = { tab ->
        val targetPage = when (tab) {
            MainTab.CHATS -> 0
            MainTab.STATUS -> 1
            MainTab.CALLS -> 2
            MainTab.SETTINGS -> 3
        }
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    val activeTab = when (pagerState.currentPage) {
        0 -> MainTab.CHATS
        1 -> MainTab.STATUS
        2 -> MainTab.CALLS
        3 -> MainTab.SETTINGS
        else -> MainTab.CHATS
    }

    Scaffold(
        bottomBar = {
            HeyChatBottomNavBar(
                selectedTab = activeTab,
                onTabSelected = onTabSelectedLambda
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> ChatListScreen(
                    chats = userChats,
                    chatListViewModel = chatListViewModel,
                    onChatClick = onChatClick,
                    onNewChatFabClick = onNewChatFabClick,
                    onTabSelected = onTabSelectedLambda,
                    showBottomBar = false
                )
                1 -> StatusScreen(
                    statusViewModel = statusViewModel,
                    onPrivacySettingsClick = onPrivacySettingsClick,
                    onSearchClick = onSearchClick,
                    onTabSelected = onTabSelectedLambda,
                    showBottomBar = false
                )
                2 -> CallsScreen(
                    onTabSelected = onTabSelectedLambda,
                    showBottomBar = false
                )
                3 -> ProfileSettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onEditProfileClick = onEditProfileClick,
                    onAccountClick = onAccountClick,
                    onPrivacyClick = onPrivacyClick,
                    onChatsClick = onChatsClick,
                    onNotificationsClick = onNotificationsClick,
                    onHelpClick = onHelpClick,
                    onLogoutClick = onLogoutClick,
                    onChatClick = onChatClick,
                    onTabSelected = onTabSelectedLambda,
                    showBottomBar = false
                )
            }
        }
    }
}
