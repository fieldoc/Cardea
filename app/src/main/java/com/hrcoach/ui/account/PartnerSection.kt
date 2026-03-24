package com.hrcoach.ui.account

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneRed

@Composable
fun PartnerSection(
    isPaired: Boolean,
    partnerName: String?,
    partnerAvatar: String?,
    inviteCode: String?,
    isGeneratingCode: Boolean,
    isJoining: Boolean,
    pairError: String?,
    onGenerateInvite: () -> Unit,
    onAcceptInvite: (String) -> Unit,
    onDisconnect: () -> Unit,
    onShareCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showJoinInput by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* feature works without it */ }

    // Clipboard detection for join code
    LaunchedEffect(showJoinInput) {
        if (showJoinInput) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (clip != null && clip.matches(Regex("[A-Z0-9]{6}"))) {
                    clipboardSuggestion = clip
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Your Invite Code", color = CardeaTheme.colors.textPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isGeneratingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = GradientRed
                        )
                    } else if (inviteCode == null) {
                        if (pairError != null) {
                            Text(
                                text = pairError,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoneRed,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            ),
                            color = CardeaTheme.colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Share this code with your running partner. It expires in 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (inviteCode != null) {
                    TextButton(onClick = {
                        onShareCode(inviteCode)
                        showInviteDialog = false
                    }) { Text("Share") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("Close") }
            },
            containerColor = CardeaTheme.colors.bgSecondary
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Partner?", color = CardeaTheme.colors.textPrimary) },
            text = {
                Text(
                    "You'll stop seeing each other's runs. You can pair again later with a new code.",
                    color = CardeaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDisconnect()
                    showDisconnectConfirm = false
                }) { Text("Disconnect", color = ZoneRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            },
            containerColor = CardeaTheme.colors.bgSecondary
        )
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ACCOUNTABILITY PARTNER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))

        if (isPaired && partnerName != null) {
            // Paired state
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = partnerAvatar ?: "\u2665",
                    fontSize = 24.sp,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GradientRed, Color(0xFF5B5BFF))
                            )
                        )
                        .padding(4.dp)
                )
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Notification permission banner
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            if (!hasNotificationPermission) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x15FACC15))
                        .border(1.dp, Color(0x30FACC15), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Notifications disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFACC15),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) { Text("Enable", fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showDisconnectConfirm = true }) {
                Text(
                    text = "Disconnect Partner",
                    color = ZoneRed.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            // Unpaired state
            if (!showJoinInput) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardeaButton(
                        text = if (isGeneratingCode) "Generating..." else "Invite Partner",
                        onClick = {
                            onGenerateInvite()
                            showInviteDialog = true
                            // Request notification permission after pairing action
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showJoinInput = true },
                        modifier = Modifier
                            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "Join Partner",
                            color = CardeaTheme.colors.textSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                // Join code input
                Text(
                    text = "Enter your partner's 6-character code:",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(6) },
                        placeholder = { Text("K7X2M9") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CardeaTheme.colors.glassSurface,
                            unfocusedBorderColor = CardeaTheme.colors.glassBorder,
                            focusedTextColor = CardeaTheme.colors.textPrimary,
                            unfocusedTextColor = CardeaTheme.colors.textPrimary,
                            cursorColor = CardeaTheme.colors.textPrimary
                        )
                    )
                    CardeaButton(
                        text = if (isJoining) "Joining..." else "Join",
                        onClick = {
                            onAcceptInvite(joinCode)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                    )
                }
                if (clipboardSuggestion != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        joinCode = clipboardSuggestion!!
                        clipboardSuggestion = null
                    }) {
                        Text(
                            "Paste $clipboardSuggestion?",
                            style = MaterialTheme.typography.labelSmall,
                            color = CardeaTheme.colors.textSecondary
                        )
                    }
                }
                if (pairError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pairError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoneRed
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showJoinInput = false; joinCode = "" }) {
                    Text("Cancel", color = CardeaTheme.colors.textSecondary)
                }
            }
        }
    }
}
