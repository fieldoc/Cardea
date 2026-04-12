package com.hrcoach.ui.account

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.data.firebase.PartnerLimitException
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.domain.model.PartnerActivity
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import kotlinx.coroutines.launch

// Private gradients removed — use CardeaTheme.colors.gradient / .ctaGradient instead
private val DisabledButtonGradient = Brush.linearGradient(
    listOf(Color(0xFF4B5563), Color(0xFF374151))
)

// ── Partner Section ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSection(
    partners: List<PartnerActivity>,
    partnerCount: Int,
    onCreateInviteCode: suspend () -> String,
    onRedeemCode: suspend (String) -> PartnerActivity?,
    onRemovePartner: (String) -> Unit,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var showEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(partners) {
        if (partners.isEmpty()) {
            delay(500)
            showEmpty = true
        } else {
            showEmpty = false
        }
    }

    if (showAddSheet) {
        AddPartnerBottomSheet(
            onCreateInviteCode = onCreateInviteCode,
            onRedeemCode = onRedeemCode,
            onDismiss = { showAddSheet = false }
        )
    }

    // Section header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Partners ${partnerCount} of 3",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        // Gradient "+ Add" button — disabled at 3/3
        val isFull = partnerCount >= 3
        val addButtonGradient = if (isFull)
            DisabledButtonGradient
        else
            CardeaTheme.colors.ctaGradient
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(addButtonGradient)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isFull,
                    onClick = { showAddSheet = true }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFull) "Full (3/3)" else "+ Add",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (isFull) CardeaTheme.colors.textTertiary else Color.White
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        if (partners.isEmpty() && showEmpty) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No partners yet",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CardeaTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add a running partner to see their activity and stay accountable together.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            partners.forEachIndexed { index, partner ->
                PartnerRow(
                    partner = partner,
                    onRemove = { onRemovePartner(partner.userId) }
                )
                if (index < partners.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = CardeaTheme.colors.glassBorder
                    )
                }
            }
        }
    }
}

// ── Partner Row ────────────────────────────────────────────────────────────────

@Composable
fun PartnerRow(
    partner: PartnerActivity,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emblem avatar with gradient ring
        EmblemIconWithRing(
            emblem = Emblem.fromId(partner.emblemId),
            size = 40.dp,
            ringWidth = 2.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = partner.displayName,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Stats line
            val runsLabel = "${partner.weeklyRunCount} runs"
            val streakLabel = "${partner.currentStreak}-day streak \uD83D\uDD25"
            Text(
                text = "$runsLabel · $streakLabel",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Status badge
        val (statusText, statusColor) = when {
            partner.ranToday() -> "Ran today" to ZoneGreen
            partner.isRecentlyActive() -> "Yesterday" to CardeaTheme.colors.textTertiary
            else -> "Inactive" to CardeaTheme.colors.textTertiary
        }

        Text(
            text = statusText,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = statusColor,
            modifier = Modifier.widthIn(max = 80.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove partner",
                tint = CardeaTheme.colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Add Partner Bottom Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartnerBottomSheet(
    onCreateInviteCode: suspend () -> String,
    onRedeemCode: suspend (String) -> PartnerActivity?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardeaTheme.colors.bgSecondary,
        windowInsets = WindowInsets(0),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaTheme.colors.textTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Add a Partner",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = CardeaTheme.colors.textPrimary,
                edgePadding = 16.dp,
                divider = {}
            ) {
                listOf("Share my code", "Enter a code").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedTab == index)
                                    CardeaTheme.colors.textPrimary
                                else
                                    CardeaTheme.colors.textSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                0 -> ShareCodeTab(onCreateInviteCode = onCreateInviteCode)
                1 -> EnterCodeTab(onRedeemCode = onRedeemCode, onDone = onDismiss)
            }
        }
    }
}

// ── Share Code Tab ─────────────────────────────────────────────────────────────

@Composable
private fun ShareCodeTab(
    onCreateInviteCode: suspend () -> String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Share your invite code with a friend. Once they enter it, you'll both see each other's activity.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (inviteCode.isEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        CardeaTheme.colors.ctaGradient
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!isLoading) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        inviteCode = onCreateInviteCode()
                                    } catch (ex: Exception) {
                                        errorMessage = if (ex is IllegalStateException || ex.message.isNullOrBlank())
                                            "Something went wrong. Please try again."
                                        else
                                            ex.message
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "Generating..." else "Generate my code",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = ZoneRed,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Code display with gradient text effect
            Text(
                text = "Your invite code",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardeaTheme.colors.glassHighlight)
                    .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = inviteCode,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp,
                        brush = CardeaTheme.colors.gradient
                    ),
                    color = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        CardeaTheme.colors.ctaGradient
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join me on Cardea! My invite code is: $inviteCode")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share invite code"))
                        }
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Share invite code",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
    }
}

// ── Enter Code Tab ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterCodeTab(
    onRedeemCode: suspend (String) -> PartnerActivity?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectedPartner by remember { mutableStateOf<PartnerActivity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (connectedPartner == null) {
            Text(
                text = "Enter a 6-character code from your partner to connect.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { newValue ->
                    if (newValue.length <= 6) {
                        code = newValue.uppercase()
                        errorMessage = null
                    }
                },
                singleLine = true,
                placeholder = { Text("XXXXXX") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters
                ),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it, color = ZoneRed) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GradientPink,
                    unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                    cursorColor = GradientPink,
                    focusedTextColor = CardeaTheme.colors.textPrimary,
                    unfocusedTextColor = CardeaTheme.colors.textPrimary,
                    errorBorderColor = ZoneRed,
                ),
                textStyle = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            val canConnect = code.length == 6 && !isConnecting
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (canConnect)
                            CardeaTheme.colors.ctaGradient
                        else
                            DisabledButtonGradient
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (canConnect) {
                                isConnecting = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val partner = onRedeemCode(code)
                                        if (partner != null) {
                                            connectedPartner = partner
                                        } else {
                                            errorMessage = "Code not found or already used."
                                        }
                                    } catch (ex: PartnerLimitException) {
                                        errorMessage = ex.message
                                    } catch (ex: Exception) {
                                        errorMessage = if (ex is IllegalStateException || ex.message.isNullOrBlank())
                                            "Something went wrong. Please try again."
                                        else
                                            ex.message
                                    } finally {
                                        isConnecting = false
                                    }
                                }
                            }
                        }
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isConnecting) "Connecting..." else "Connect",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        } else {
            // Success state
            val partner = connectedPartner!!
            Text(
                text = "\uD83C\uDF89",
                style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You're connected!",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Partner preview card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                PartnerRow(partner = partner, onRemove = {})
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "What you'll see" list
            Text(
                text = "What you'll see",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "Their weekly run count",
                "Current streak and fire badge",
                "Whether they ran today or yesterday"
            ).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "•",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = GradientPink,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Done button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardeaTheme.colors.ctaGradient)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDone
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
    }
}
