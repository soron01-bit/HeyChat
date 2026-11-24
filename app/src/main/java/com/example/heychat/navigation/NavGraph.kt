package com.example.heychat.navigation

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.heychat.ui.components.MainTab
import com.example.heychat.ui.components.UpdateDialog
import com.example.heychat.ui.screens.AccountSettingsScreen
import com.example.heychat.ui.screens.CallsScreen
import com.example.heychat.ui.screens.ChatListScreen
import com.example.heychat.ui.screens.ChatsSettingsScreen
import com.example.heychat.ui.screens.ContactListScreen
import com.example.heychat.ui.screens.EditProfileScreen
import com.example.heychat.ui.screens.EmailVerificationScreen
import com.example.heychat.ui.screens.ForgotPasswordScreen
import com.example.heychat.ui.screens.HelpSettingsScreen
import com.example.heychat.ui.screens.IndividualChatScreen
import com.example.heychat.ui.screens.LoginScreen
import com.example.heychat.ui.screens.MainPagerScreen
import com.example.heychat.ui.screens.NotificationsSettingsScreen
import com.example.heychat.ui.screens.PrivacySettingsScreen
import com.example.heychat.ui.screens.ProfileSetupScreen
import com.example.heychat.ui.screens.ProfileSettingsScreen
import com.example.heychat.ui.screens.SignupScreen
import com.example.heychat.ui.screens.SplashScreen
import com.example.heychat.ui.screens.StatusPrivacySettingsScreen
import com.example.heychat.ui.screens.StatusScreen
import com.example.heychat.ui.screens.UserProfileDetailsScreen
import com.example.heychat.ui.viewmodel.AuthState
import com.example.heychat.ui.viewmodel.AuthViewModel
import com.example.heychat.ui.viewmodel.ChatListViewModel
import com.example.heychat.ui.viewmodel.ChatViewModel
import com.example.heychat.ui.viewmodel.SearchViewModel
import com.example.heychat.ui.viewmodel.SettingsViewModel
import com.example.heychat.ui.viewmodel.StatusViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val EMAIL_VERIFICATION = "email_verification"
    const val FORGOT_PASSWORD = "forgot_password"
    const val PROFILE_SETUP = "profile_setup"
    const val CHAT_LIST = "chat_list"
    const val STATUS = "status"
    const val STATUS_PRIVACY_SETTINGS = "status_privacy_settings"
    const val CALLS = "calls"
    const val INDIVIDUAL_CHAT = "individual_chat/{chatId}?name={name}&avatar={avatar}&userId={userId}"
    const val CONTACT_LIST = "contact_list"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val ACCOUNT_SETTINGS = "account_settings"
    const val PRIVACY_SETTINGS = "privacy_settings"
    const val CHATS_SETTINGS = "chats_settings"
    const val NOTIFICATIONS_SETTINGS = "notifications_settings"
    const val HELP_SETTINGS = "help_settings"
    const val USER_PROFILE_DETAILS = "user_profile_details/{userId}/{conversationId}"

    fun createUserProfileRoute(userId: String, conversationId: String): String {
        val cleanUserId = userId.replace("\"", "").replace("'", "").trim()
        val cleanConvId = conversationId.replace("\"", "").replace("'", "").trim()
        return "user_profile_details/$cleanUserId/$cleanConvId"
    }

    fun createChatRoute(chatId: String, name: String? = null, avatar: String? = null, userId: String? = null): String {
        val cleanConvId = chatId.replace("\"", "").replace("'", "").trim()
        val cleanUserId = userId?.replace("\"", "")?.replace("'", "")?.trim() ?: ""
        return if (!name.isNullOrBlank()) {
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val encodedAvatar = URLEncoder.encode(avatar ?: "", "UTF-8")
            "individual_chat/$cleanConvId?name=$encodedName&avatar=$encodedAvatar&userId=$cleanUserId"
        } else {
            "individual_chat/$cleanConvId?userId=$cleanUserId"
        }
    }
}

@Composable
fun HeyChatNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    notificationConvId: String? = null,
    newIntentConvId: String? = null,
    onNewIntentHandled: () -> Unit = {}
) {
    val authState = authViewModel.authState.collectAsState().value
    var pendingConvId by remember { mutableStateOf(notificationConvId) }

    val onTabNavigate: (MainTab) -> Unit = { tab ->
        val route = when (tab) {
            MainTab.CHATS -> Routes.CHAT_LIST
            MainTab.STATUS -> Routes.STATUS
            MainTab.CALLS -> Routes.CALLS
            MainTab.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(authState) {
        val stateTypeName = authState.javaClass.simpleName
        val currentRoute = navController.currentDestination?.route ?: "none"
        Log.d("HeyChat_Auth", "NavGraph observed AuthState changed -> $stateTypeName (currentRoute: '$currentRoute')")

        when (authState) {
            is AuthState.Loading -> {
                Log.d("HeyChat_Auth", "AuthState is Loading -> Staying on SPLASH while session check is in progress")
            }
            is AuthState.SignedUpUnverified -> {
                Log.d("HeyChat_Auth", "Navigation decision: EMAIL_VERIFICATION (Reason: unverified email)")
                navController.navigate(Routes.EMAIL_VERIFICATION) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.ProfileSetupRequired -> {
                Log.d("HeyChat_Auth", "Navigation decision: PROFILE_SETUP (Reason: profile setup required)")
                navController.navigate(Routes.PROFILE_SETUP) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.Authenticated -> {
                val activePendingId = pendingConvId
                if (!activePendingId.isNullOrBlank()) {
                    Log.d("HeyChat_Auth", "Cold start notification tap -> pendingConvId='$activePendingId'. Building backstack: CHAT_LIST then INDIVIDUAL_CHAT")
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(0) { inclusive = true }
                    }
                    navController.navigate(Routes.createChatRoute(activePendingId))
                    pendingConvId = null
                } else {
                    val isOuterAuthRoute = currentRoute == Routes.SPLASH ||
                        currentRoute == Routes.LOGIN ||
                        currentRoute == Routes.SIGNUP ||
                        currentRoute == Routes.EMAIL_VERIFICATION ||
                        currentRoute == Routes.PROFILE_SETUP

                    if (isOuterAuthRoute) {
                        Log.d("HeyChat_Auth", "Navigation decision: CHAT_LIST (Reason: authenticated user session settled from $currentRoute)")
                        navController.navigate(Routes.CHAT_LIST) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        Log.d("HeyChat_Auth", "User already inside app at route '$currentRoute' -> Skipping navigation on AuthState.Authenticated re-emit")
                    }
                }

                // Safety Net: Always pop SPLASH from backstack if present
                navController.popBackStack(Routes.SPLASH, inclusive = true)
            }
            is AuthState.Idle -> {
                val isOuterAuthRoute = currentRoute == Routes.SPLASH ||
                    currentRoute == Routes.LOGIN ||
                    currentRoute == Routes.SIGNUP ||
                    currentRoute == Routes.EMAIL_VERIFICATION ||
                    currentRoute == Routes.PROFILE_SETUP

                if (isOuterAuthRoute) {
                    Log.d("HeyChat_Auth", "Navigation decision: LOGIN (Reason: no session found or explicit token rejection)")
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    // Handle hot-start notification tap when app is already running in background
    LaunchedEffect(newIntentConvId) {
        if (!newIntentConvId.isNullOrBlank() && authState is AuthState.Authenticated) {
            Log.d("HeyChat_Auth", "onNewIntent notification tap -> newIntentConvId='$newIntentConvId'. Navigating INDIVIDUAL_CHAT on top")
            navController.navigate(Routes.createChatRoute(newIntentConvId))
            onNewIntentHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(animationSpec = tween(100)) },
        exitTransition = { fadeOut(animationSpec = tween(100)) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen()
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToSignup = { navController.navigate(Routes.SIGNUP) },
                onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) }
            )
        }

        composable(Routes.SIGNUP) {
            SignupScreen(
                viewModel = authViewModel,
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }

        composable(Routes.EMAIL_VERIFICATION) {
            val email = (authState as? AuthState.SignedUpUnverified)?.email ?: "your email"
            EmailVerificationScreen(
                email = email,
                viewModel = authViewModel,
                onBackClick = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                viewModel = authViewModel,
                onContinueClick = { name, username, status ->
                    authViewModel.completeProfileSetup(name, username, status)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_LIST) {
            val chatListViewModel: ChatListViewModel = viewModel()
            val statusViewModel: StatusViewModel = viewModel()

            MainPagerScreen(
                initialTab = MainTab.CHATS,
                chatListViewModel = chatListViewModel,
                statusViewModel = statusViewModel,
                settingsViewModel = settingsViewModel,
                onChatClick = { chat ->
                    chatListViewModel.clearUnreadOnChatOpened(chat.id)
                    navController.navigate(Routes.createChatRoute(chat.id, chat.contact.name, chat.contact.avatarUrl, chat.contact.id))
                },
                onNewChatFabClick = { navController.navigate(Routes.CONTACT_LIST) },
                onPrivacySettingsClick = { navController.navigate(Routes.STATUS_PRIVACY_SETTINGS) },
                onSearchClick = { navController.navigate(Routes.CONTACT_LIST) },
                onEditProfileClick = { navController.navigate(Routes.EDIT_PROFILE) },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                onChatsClick = { navController.navigate(Routes.CHATS_SETTINGS) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS_SETTINGS) },
                onHelpClick = { navController.navigate(Routes.HELP_SETTINGS) },
                onLogoutClick = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.STATUS) {
            val chatListViewModel: ChatListViewModel = viewModel()
            val statusViewModel: StatusViewModel = viewModel()

            MainPagerScreen(
                initialTab = MainTab.STATUS,
                chatListViewModel = chatListViewModel,
                statusViewModel = statusViewModel,
                settingsViewModel = settingsViewModel,
                onChatClick = { chat ->
                    chatListViewModel.clearUnreadOnChatOpened(chat.id)
                    navController.navigate(Routes.createChatRoute(chat.id, chat.contact.name, chat.contact.avatarUrl, chat.contact.id))
                },
                onNewChatFabClick = { navController.navigate(Routes.CONTACT_LIST) },
                onPrivacySettingsClick = { navController.navigate(Routes.STATUS_PRIVACY_SETTINGS) },
                onSearchClick = { navController.navigate(Routes.CONTACT_LIST) },
                onEditProfileClick = { navController.navigate(Routes.EDIT_PROFILE) },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                onChatsClick = { navController.navigate(Routes.CHATS_SETTINGS) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS_SETTINGS) },
                onHelpClick = { navController.navigate(Routes.HELP_SETTINGS) },
                onLogoutClick = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.STATUS_PRIVACY_SETTINGS) {
            val statusViewModel: StatusViewModel = viewModel()
            StatusPrivacySettingsScreen(
                statusViewModel = statusViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.CALLS) {
            val chatListViewModel: ChatListViewModel = viewModel()
            val statusViewModel: StatusViewModel = viewModel()

            MainPagerScreen(
                initialTab = MainTab.CALLS,
                chatListViewModel = chatListViewModel,
                statusViewModel = statusViewModel,
                settingsViewModel = settingsViewModel,
                onChatClick = { chat ->
                    chatListViewModel.clearUnreadOnChatOpened(chat.id)
                    navController.navigate(Routes.createChatRoute(chat.id, chat.contact.name, chat.contact.avatarUrl, chat.contact.id))
                },
                onNewChatFabClick = { navController.navigate(Routes.CONTACT_LIST) },
                onPrivacySettingsClick = { navController.navigate(Routes.STATUS_PRIVACY_SETTINGS) },
                onSearchClick = { navController.navigate(Routes.CONTACT_LIST) },
                onEditProfileClick = { navController.navigate(Routes.EDIT_PROFILE) },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                onChatsClick = { navController.navigate(Routes.CHATS_SETTINGS) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS_SETTINGS) },
                onHelpClick = { navController.navigate(Routes.HELP_SETTINGS) },
                onLogoutClick = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(
            route = Routes.INDIVIDUAL_CHAT,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true },
                navArgument("avatar") { type = NavType.StringType; nullable = true },
                navArgument("userId") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val rawName = backStackEntry.arguments?.getString("name")
            val rawAvatar = backStackEntry.arguments?.getString("avatar")
            val rawUserId = backStackEntry.arguments?.getString("userId")

            val name = rawName?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            val avatar = rawAvatar?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

            val chatViewModel: ChatViewModel = viewModel()

            LaunchedEffect(chatId) {
                chatViewModel.setInitialParticipant(name, avatar, rawUserId)
            }

            IndividualChatScreen(
                conversationId = chatId,
                chatViewModel = chatViewModel,
                onBackClick = { navController.popBackStack() },
                onProfileClick = { targetUserId, convId ->
                    navController.navigate(Routes.createUserProfileRoute(targetUserId, convId))
                }
            )
        }

        composable(
            route = Routes.USER_PROFILE_DETAILS,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val targetUserId = backStackEntry.arguments?.getString("userId") ?: ""
            val convId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val chatViewModel: ChatViewModel = viewModel()
            val chatListViewModel: ChatListViewModel = viewModel()

            UserProfileDetailsScreen(
                userId = targetUserId,
                conversationId = convId,
                chatViewModel = chatViewModel,
                chatListViewModel = chatListViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.CONTACT_LIST) {
            val searchViewModel: SearchViewModel = viewModel()
            ContactListScreen(
                searchViewModel = searchViewModel,
                onConversationCreated = { convId ->
                    navController.navigate(Routes.createChatRoute(convId)) {
                        popUpTo(Routes.CHAT_LIST)
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            val chatListViewModel: ChatListViewModel = viewModel()
            val statusViewModel: StatusViewModel = viewModel()

            MainPagerScreen(
                initialTab = MainTab.SETTINGS,
                chatListViewModel = chatListViewModel,
                statusViewModel = statusViewModel,
                settingsViewModel = settingsViewModel,
                onChatClick = { chat ->
                    chatListViewModel.clearUnreadOnChatOpened(chat.id)
                    navController.navigate(Routes.createChatRoute(chat.id, chat.contact.name, chat.contact.avatarUrl, chat.contact.id))
                },
                onNewChatFabClick = { navController.navigate(Routes.CONTACT_LIST) },
                onPrivacySettingsClick = { navController.navigate(Routes.STATUS_PRIVACY_SETTINGS) },
                onSearchClick = { navController.navigate(Routes.CONTACT_LIST) },
                onEditProfileClick = { navController.navigate(Routes.EDIT_PROFILE) },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY_SETTINGS) },
                onChatsClick = { navController.navigate(Routes.CHATS_SETTINGS) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS_SETTINGS) },
                onHelpClick = { navController.navigate(Routes.HELP_SETTINGS) },
                onLogoutClick = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(
                settingsViewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.ACCOUNT_SETTINGS) {
            AccountSettingsScreen(
                settingsViewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PRIVACY_SETTINGS) {
            PrivacySettingsScreen(
                settingsViewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.CHATS_SETTINGS) {
            ChatsSettingsScreen(
                settingsViewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.NOTIFICATIONS_SETTINGS) {
            NotificationsSettingsScreen(
                settingsViewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.HELP_SETTINGS) {
            HelpSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
