package com.example.heychat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    STATUS("Status", Icons.Filled.DonutLarge, Icons.Outlined.DonutLarge),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun HeyChatBottomNavBar(
    selectedTab: MainTab,
    unreadChatCount: Int = 0,
    unseenStatusCount: Int = 0,
    onTabSelected: (MainTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val badgeCount = when (tab) {
                MainTab.CHATS -> unreadChatCount
                MainTab.STATUS -> unseenStatusCount
                else -> 0
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    runCatching {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onTabSelected(tab)
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge {
                                    Text(text = badgeCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            )
        }
    }
}
