package com.hrcoach.data.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.hrcoach.data.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WEB_CLIENT_ID =
            "471429338135-21dh3trc6g8lqt47k5kmev47le8b23qb.apps.googleusercontent.com"
    }

    // ── Existing anonymous auth ──────────────────────────────────────────

    suspend fun ensureSignedIn(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        val authResult = runCatching {
            withTimeout(10_000) { auth.signInAnonymously().await() }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw Exception("Authentication timed out. Please check your connection.")
        }

        val uid = authResult.user?.uid ?: throw IllegalStateException("Anonymous auth returned null UID")
        userProfileRepository.setUserId(uid)
        return uid
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid

    // ── Google account helpers ───────────────────────────────────────────

    /** True when the current anonymous user has linked a Google account. */
    fun isGoogleLinked(): Boolean =
        auth.currentUser?.providerData?.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        } ?: false

    /** Returns the linked Google email, or null if not linked. */
    fun getLinkedEmail(): String? =
        auth.currentUser?.providerData
            ?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            ?.email

    /**
     * Link a Google account to the current anonymous user.
     *
     * If the Google account is already associated with an existing Cardea profile
     * (CREDENTIAL_ALREADY_IN_USE), this method transparently signs into that profile
     * and returns [LinkResult.ExistingAccount]. The caller should then restore from
     * the cloud backup rather than backing up the current (empty) device.
     *
     * Returns [LinkResult.FreshLink] when a new link was created.
     */
    suspend fun linkGoogleAccount(): LinkResult {
        val user = auth.currentUser
            ?: throw IllegalStateException("Must be signed in before linking")

        val idToken = getGoogleIdToken()
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        return try {
            withTimeout(15_000) { user.linkWithCredential(credential).await() }
            LinkResult.FreshLink
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            throw Exception("Timed out linking Google account. Please try again.")
        } catch (e: FirebaseAuthUserCollisionException) {
            // Google account already linked to an existing Cardea profile —
            // sign into that profile so the user gets their history back.
            //
            // Matching by exception type (not e.message substring) so the path
            // fires correctly on non-English locales, where the error message
            // is localized and won't contain the literal "CREDENTIAL_ALREADY_IN_USE".
            val signInResult = withTimeout(15_000) {
                auth.signInWithCredential(credential).await()
            }
            val uid = signInResult.user?.uid
                ?: throw IllegalStateException("signInWithCredential returned null user")
            userProfileRepository.setUserId(uid)
            LinkResult.ExistingAccount
        } catch (e: Exception) {
            throw Exception("Failed to link Google account: ${e.message}")
        }
    }

    /** Indicates what happened when [linkGoogleAccount] completes. */
    enum class LinkResult { FreshLink, ExistingAccount }

    /**
     * Sign in with Google on a new device (restore flow).
     * Returns the UID of the Google-linked account.
     */
    suspend fun signInWithGoogle(): String {
        val idToken = getGoogleIdToken()
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        val result = runCatching {
            withTimeout(15_000) { auth.signInWithCredential(credential).await() }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw Exception("Google sign-in failed: ${e.message}")
        }

        val uid = result.user?.uid
            ?: throw IllegalStateException("signInWithCredential returned null user")
        userProfileRepository.setUserId(uid)
        return uid
    }

    /** Clear Credential Manager state and sign out of Firebase. */
    suspend fun signOut() {
        val credentialManager = CredentialManager.create(context)
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        auth.signOut()
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private suspend fun getGoogleIdToken(): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)

        val response = runCatching {
            withTimeout(15_000) {
                credentialManager.getCredential(context, request)
            }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw Exception("Google credential request failed: ${e.message}")
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
        return googleCredential.idToken
    }
}
