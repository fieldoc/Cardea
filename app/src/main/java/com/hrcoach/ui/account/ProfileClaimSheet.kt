package com.hrcoach.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.hrcoach.R
import com.hrcoach.ui.auth.AuthViewModel
import com.hrcoach.ui.components.EmblemPicker
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileClaimSheet(
    viewModel: AccountViewModel = hiltViewModel(),
    onClaimed: () -> Unit,
    onDismiss: () -> Unit
) {
    val accountState by viewModel.uiState.collectAsStateWithLifecycle()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    var displayName by remember { mutableStateOf(accountState.displayName) }
    var selectedEmblem by remember { mutableStateOf(accountState.avatarEmblemId) }
    var bio by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val isAuthenticated = currentUser != null

    val context = LocalContext.current
    val webClientId = stringResource(id = R.string.default_web_client_id)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    authViewModel.signInWithGoogle(idToken)
                }
            } catch (_: ApiException) {
                // Sign-in failed
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgSecondary,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Claim Your Profile",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set up your runner identity",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Display name field (required, 3-20 chars)
            Text("Display Name", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    if (it.length <= 20) {
                        displayName = it
                        nameError = null
                    }
                },
                singleLine = true,
                isError = nameError != null,
                placeholder = { Text("Your runner name", color = CardeaTheme.colors.textTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CardeaTheme.colors.accentPink,
                    unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                    cursorColor = CardeaTheme.colors.accentPink,
                    focusedTextColor = CardeaTheme.colors.textPrimary,
                    unfocusedTextColor = CardeaTheme.colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (nameError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = nameError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoneRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emblem picker
            Text("Avatar", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            EmblemPicker(
                selected = selectedEmblem,
                onSelect = { selectedEmblem = it },
                modifier = Modifier.heightIn(max = 300.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bio field (optional, max 40 chars)
            Text("Bio", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 40) bio = it },
                singleLine = true,
                placeholder = { Text("Short bio (optional)", color = CardeaTheme.colors.textTertiary) },
                supportingText = { Text("${bio.length}/40", color = CardeaTheme.colors.textTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CardeaTheme.colors.accentPink,
                    unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                    cursorColor = CardeaTheme.colors.accentPink,
                    focusedTextColor = CardeaTheme.colors.textPrimary,
                    unfocusedTextColor = CardeaTheme.colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Auth section (if not authenticated)
            if (!isAuthenticated) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Sign In (Optional)", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Email field
                    OutlinedTextField(
                        value = authState.email,
                        onValueChange = authViewModel::setEmail,
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !authState.isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CardeaTheme.colors.accentPink,
                            unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                            cursorColor = CardeaTheme.colors.accentPink,
                            focusedTextColor = CardeaTheme.colors.textPrimary,
                            unfocusedTextColor = CardeaTheme.colors.textPrimary,
                            focusedLabelColor = CardeaTheme.colors.accentPink,
                            unfocusedLabelColor = CardeaTheme.colors.textTertiary,
                            disabledTextColor = CardeaTheme.colors.textTertiary,
                            disabledBorderColor = CardeaTheme.colors.textTertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password field
                    OutlinedTextField(
                        value = authState.password,
                        onValueChange = authViewModel::setPassword,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !authState.isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CardeaTheme.colors.accentPink,
                            unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                            cursorColor = CardeaTheme.colors.accentPink,
                            focusedTextColor = CardeaTheme.colors.textPrimary,
                            unfocusedTextColor = CardeaTheme.colors.textPrimary,
                            focusedLabelColor = CardeaTheme.colors.accentPink,
                            unfocusedLabelColor = CardeaTheme.colors.textTertiary,
                            disabledTextColor = CardeaTheme.colors.textTertiary,
                            disabledBorderColor = CardeaTheme.colors.textTertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Auth error
                    if (authState.error != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = authState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoneRed
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Google Sign-In button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = if (authState.isLoading) CardeaTheme.colors.textTertiary else CardeaTheme.colors.glassBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .background(Color.Transparent)
                            .clickable(
                                enabled = !authState.isLoading,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(webClientId)
                                        .requestEmail()
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (authState.isLoading) CardeaTheme.colors.textTertiary else CardeaTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Claim Profile CTA button
            if (authState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CardeaTheme.colors.accentPink,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardeaCtaGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val name = displayName.trim()
                                if (name.length < 3) {
                                    nameError = "Name must be at least 3 characters"
                                    return@clickable
                                }
                                // If auth fields filled but not signed in, attempt sign in first
                                if (!isAuthenticated && authState.email.isNotBlank() && authState.password.isNotBlank()) {
                                    authViewModel.submit()
                                    // Auth state listener will handle the rest
                                }
                                // Save profile
                                viewModel.setDisplayName(name)
                                viewModel.setAvatarEmblemId(selectedEmblem)
                                viewModel.setBio(bio)
                                viewModel.saveProfile()
                                onClaimed()
                            }
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Claim Profile",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.onGradient
                    )
                }
            }
        }
    }
}
