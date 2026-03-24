package com.hrcoach.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.hrcoach.R
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneRed

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    // Navigate on auth success
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onAuthSuccess()
        }
    }

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
                    viewModel.signInWithGoogle(idToken)
                }
            } catch (_: ApiException) {
                // Sign-in failed silently; errors surfaced through viewModel.state if needed
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Logo
        CardeaLogo(
            size = 100.dp,
            animate = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "Welcome to Cardea",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = "Sign in to claim your profile",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Form card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Email field
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !state.isLoading,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Password field
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.isLoading,
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

            // Error message
            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoneRed
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit button
            if (state.isLoading) {
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
                            onClick = viewModel::submit
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isSignUp) "Create Account" else "Sign In",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.onGradient
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle sign-in / sign-up
            Text(
                text = if (state.isSignUp)
                    "Already have an account? Sign in"
                else
                    "Don't have an account? Create one",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.accentPink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !state.isLoading,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = viewModel::toggleSignUp
                    )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Google Sign-In button (outline style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (state.isLoading) CardeaTheme.colors.textTertiary else CardeaTheme.colors.glassBorder,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(Color.Transparent)
                .clickable(
                    enabled = !state.isLoading,
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
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Continue with Google",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (state.isLoading) CardeaTheme.colors.textTertiary else CardeaTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
