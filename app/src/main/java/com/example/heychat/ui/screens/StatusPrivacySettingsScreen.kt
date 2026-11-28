package com.example.heychat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.heychat.data.models.Profile
import com.example.heychat.data.models.StatusPrivacyOption
import com.example.heychat.data.models.StatusPrivacySettings
import com.example.heychat.ui.theme.PrimaryEmerald
import com.example.heychat.ui.viewmodel.StatusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPrivacySettingsScreen(
    statusViewModel: StatusViewModel,
    onBackClick: () -> Unit = {}
) {
    val currentSettings = statusViewModel.privacySettings.collectAsStateWithLifecycle().value
    val eligibleContacts = statusViewModel.eligibleContacts.collectAsStateWithLifecycle().value

    var selectedOption by remember(currentSettings) { mutableStateOf(currentSettings.option) }
    var showContactSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        statusViewModel.loadStatusPrivacySettings()
        statusViewModel.loadEligibleContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Status Privacy",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Who can see my status updates",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Option 1: My Contacts
            PrivacyOptionRow(
                title = "My Contacts",
                subtitle = "Shared with contacts you've previously exchanged messages with",
                isSelected = selectedOption == StatusPrivacyOption.MY_CONTACTS,
                onClick = {
                    selectedOption = StatusPrivacyOption.MY_CONTACTS
                    statusViewModel.saveStatusPrivacySettings(
                        currentSettings.copy(option = StatusPrivacyOption.MY_CONTACTS)
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 2: My Contacts Except...
            val excludedCount = currentSettings.excludedUserIds.size
            PrivacyOptionRow(
                title = "My Contacts Except...",
                subtitle = if (excludedCount > 0) "$excludedCount contacts excluded" else "Allow all eligible contacts except selected users",
                isSelected = selectedOption == StatusPrivacyOption.MY_CONTACTS_EXCEPT,
                onClick = {
                    selectedOption = StatusPrivacyOption.MY_CONTACTS_EXCEPT
                    statusViewModel.saveStatusPrivacySettings(
                        currentSettings.copy(option = StatusPrivacyOption.MY_CONTACTS_EXCEPT)
                    )
                    showContactSheet = true
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Only Share With...
            val selectedCount = currentSettings.selectedUserIds.size
            PrivacyOptionRow(
                title = "Only Share With...",
                subtitle = if (selectedCount > 0) "$selectedCount contacts selected" else "Allow status visibility only to specifically selected users",
                isSelected = selectedOption == StatusPrivacyOption.ONLY_SHARE_WITH,
                onClick = {
                    selectedOption = StatusPrivacyOption.ONLY_SHARE_WITH
                    statusViewModel.saveStatusPrivacySettings(
                        currentSettings.copy(option = StatusPrivacyOption.ONLY_SHARE_WITH)
                    )
                    showContactSheet = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Changes to your privacy settings will apply to your status updates.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Contact Selection Modal Bottom Sheet
    if (showContactSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isExceptOption = selectedOption == StatusPrivacyOption.MY_CONTACTS_EXCEPT

        val activeSelectedIds = remember(currentSettings, isExceptOption) {
            val list = if (isExceptOption) currentSettings.excludedUserIds else currentSettings.selectedUserIds
            mutableStateListOf<String>().apply {
                addAll(list.map { it.replace("\"", "").replace("'", "").trim().lowercase() })
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showContactSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (isExceptOption) "Hide status from..." else "Share status with...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (eligibleContacts.isEmpty()) {
                        Text(
                            text = "No eligible contacts found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(eligibleContacts) { contact ->
                                val cleanContactId = contact.id.replace("\"", "").replace("'", "").trim().lowercase()
                                val isChecked = activeSelectedIds.contains(cleanContactId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) {
                                                activeSelectedIds.remove(cleanContactId)
                                            } else {
                                                activeSelectedIds.add(cleanContactId)
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!contact.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = contact.avatarUrl,
                                                contentDescription = contact.fullName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = (contact.fullName ?: contact.email ?: "U").take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.fullName.orEmpty().ifBlank { contact.email?.substringBefore("@") ?: "User" },
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = contact.email ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) activeSelectedIds.add(contact.id) else activeSelectedIds.remove(contact.id)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = PrimaryEmerald)
                                    )
                                }
                            }
                        }
                    }
                }

                // Confirm Checkmark FAB Button
                FloatingActionButton(
                    onClick = {
                        val newSettings = if (isExceptOption) {
                            currentSettings.copy(
                                option = StatusPrivacyOption.MY_CONTACTS_EXCEPT,
                                excludedUserIds = activeSelectedIds.toList()
                            )
                        } else {
                            currentSettings.copy(
                                option = StatusPrivacyOption.ONLY_SHARE_WITH,
                                selectedUserIds = activeSelectedIds.toList()
                            )
                        }
                        statusViewModel.saveStatusPrivacySettings(newSettings)
                        showContactSheet = false
                    },
                    containerColor = PrimaryEmerald,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm"
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) PrimaryEmerald.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryEmerald)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
