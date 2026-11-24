package com.example.heychat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.heychat.data.local.AppTheme
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.ChatRepositoryImpl
import com.example.heychat.navigation.HeyChatNavGraph
import com.example.heychat.navigation.Routes
import com.example.heychat.service.FcmTokenManager
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.viewmodel.AuthViewModel
import com.example.heychat.ui.viewmodel.SettingsViewModel
import io.github.jan.supabase.gotrue.handleDeeplinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "HeyChat_Auth"
    }

    private var onNewIntentConvId by mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                FcmTokenManager.syncFcmTokenWithSupabase()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supabaseClient.handleDeeplinks(intent)

        requestPostNotificationsPermission()
        FcmTokenManager.syncFcmTokenWithSupabase()
        startPeriodicMessageCleanup()

        val notificationConvId = intent?.getStringExtra("conversation_id")

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val currentTheme by settingsViewModel.currentTheme.collectAsState(initial = AppTheme.SYSTEM)
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (currentTheme) {
                AppTheme.SYSTEM -> isSystemDark
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            HeyChatTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    Log.d(TAG, "App backgrounded")
                                }
                                Lifecycle.Event.ON_RESUME -> {
                                    Log.d(TAG, "Activity ON_RESUME -> Background message cleanup triggered")
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        ChatRepositoryImpl(applicationContext).cleanupOldMessages()
                                    }
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    HeyChatNavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        settingsViewModel = settingsViewModel,
                        notificationConvId = notificationConvId,
                        newIntentConvId = onNewIntentConvId,
                        onNewIntentHandled = { onNewIntentConvId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabaseClient.handleDeeplinks(intent)
        val convId = intent.getStringExtra("conversation_id")
        if (!convId.isNullOrBlank()) {
            FcmTokenManager.syncFcmTokenWithSupabase()
            onNewIntentConvId = convId
            Log.d(TAG, "onNewIntent received conversation_id: '$convId'")
        }
    }

    private fun startPeriodicMessageCleanup() {
        lifecycleScope.launch(Dispatchers.IO) {
            val chatRepo = ChatRepositoryImpl(applicationContext)
            while (isActive) {
                chatRepo.cleanupOldMessages()
                delay(30 * 60 * 1000L) // Runs every 30 minutes
            }
        }
    }

    private fun requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
