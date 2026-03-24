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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneAmber
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

    // ── Invite code dialog ───────────────────────────────────────────────────
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Text(
                    "Your Invite Code",
                    color = CardeaTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGeneratingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = GradientPink,
                            strokeWidth = 3.dp
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
                        // Code display with gradient-tinted background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardeaTheme.colors.glassHighlight)
                                .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = inviteCode,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 6.sp
                                ),
                                color = CardeaTheme.colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(12.dp))
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
                    }) { Text("Share", color = GradientPink, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Close", color = CardeaTheme.colors.textSecondary)
                }
            },
            containerColor = CardeaTheme.colors.bgSecondary,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── Disconnect confirmation dialog ───────────────────────────────────────
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = {
                Text(
                    "Disconnect Partner?",
                    color = CardeaTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
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
                }) { Text("Disconnect", color = ZoneRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel", color = CardeaTheme.colors.textSecondary)
                }
            },
            containerColor = CardeaTheme.colors.bgSecondary,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── Main card ────────────────────────────────────────────────────────────
    GlassCard(modifier = modifier.fillMaxWidth()) {
        if (isPaired && partnerName != null) {
            // ── Paired state ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gradient ring avatar (matches ProfileHeroCard pattern)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardeaGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CardeaTheme.colors.bgPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partnerAvatar ?: "\u2665",
                            fontSize = 18.sp,
                            color = CardeaTheme.colors.accentPink
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = "Accountability partner",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
            }

            // Notification permission banner
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            if (!hasNotificationPermission) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ZoneAmber.copy(alpha = 0.08f))
                        .border(1.dp, ZoneAmber.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Enable notifications to see partner activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoneAmber,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Text(
                            "Enable",
                            fontSize = 12.sp,
                            color = ZoneAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
            // ── Unpaired state ───────────────────────────────────────────────
            Text(
                text = "Train with a friend — see each other's runs and stay on track together.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(Modifier.height(14.dp))

            val buttonHeight = 44.dp
            if (!showJoinInput) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(buttonHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardeaButton(
                        text = if (isGeneratingCode) "Generating..." else "Invite Partner",
                        onClick = {
                            onGenerateInvite()
                            showInviteDialog = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        },
                        enabled = !isGeneratingCode,
                        modifier = Modifier.weight(1f).height(buttonHeight)
                    )
                    Box(
                        modifier = Modifier
                            .height(buttonHeight)
                            .border(
                                width = 1.dp,
                                color = CardeaTheme.colors.glassSurface,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = { showJoinInput = true })
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Join",
                            color = CardeaTheme.colors.textSecondary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else {
                // ── Join code input ──────────────────────────────────────────
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
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(6) },
                        placeholder = { Text("K7X2M9") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CardeaTheme.colors.glassSurface,
                            unfocusedBorderColor = CardeaTheme.colors.glassBorder,
                            focusedTextColor = CardeaTheme.colors.textPrimary,
                            unfocusedTextColor = CardeaTheme.colors.textPrimary,
                            cursorColor = CardeaTheme.colors.accentPink
                        )
                    )
                    CardeaButton(
                        text = if (isJoining) "..." else "Join",
                        enabled = !isJoining && joinCode.length == 6,
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
