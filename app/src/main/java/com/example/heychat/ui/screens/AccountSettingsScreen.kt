package com.example.heychat.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.heychat.data.models.Profile
import com.example.heychat.ui.components.SimpleTopAppBar
import com.example.heychat.ui.theme.HeyChatTheme
import com.example.heychat.ui.viewmodel.SettingsViewModel

@Composable
fun AccountSettingsScreen(
    profile: Profile? = null,
    settingsViewModel: SettingsViewModel? = null,
    onBackClick: () -> Unit = {}
) {
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var twoStepEnabled by remember(profile) { mutableStateOf(profile?.twoStepEnabled ?: false) }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = "Account Settings",
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Change Password Section
            Text(
                text = "Change Password",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newPassword.length >= 6) {
                        settingsViewModel?.changePassword(newPassword)
                        newPassword = ""
                    }
                },
                enabled = newPassword.length >= 6,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Password")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Change Email Section
            Text(
                text = "Change Email Address",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text("New Email Address") },
                placeholder = { Text(profile?.email ?: "newemail@example.com") },
                leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newEmail.isNotBlank()) {
                        settingsViewModel?.changeEmail(newEmail)
                        newEmail = ""
                    }
                },
                enabled = newEmail.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Email")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Two-Step Verification Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Two-Step Verification",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Require an extra security PIN when logging in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = twoStepEnabled,
                    onCheckedChange = { checked ->
                        twoStepEnabled = checked
                        if (profile != null) {
                            settingsViewModel?.updatePrivacySettings(
                                currentProfile = profile,
                                showLastSeen = profile.showLastSeen == true,
                                disappearingDuration = profile.disappearingMessagesDuration ?: "OFF",
                                twoStep = checked
                            )
                        }
                    }
                )
            }
        }
    }
}

@Preview(name = "Account Settings Light", showBackground = true)
@Composable
fun AccountSettingsScreenPreviewLight() {
    HeyChatTheme(darkTheme = false) {
        AccountSettingsScreen()
    }
}
