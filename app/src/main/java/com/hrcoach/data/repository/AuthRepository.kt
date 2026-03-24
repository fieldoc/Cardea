package com.hrcoach.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    /** Returns UID if authenticated, empty string if not (for Room userId columns). */
    val effectiveUserId: String
        get() = currentUserId ?: ""

    fun isAuthenticated(): Boolean = firebaseAuth.currentUser != null

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            firebaseAuth.signInWithEmailAndPassword(email, password).await().user!!
        }

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            firebaseAuth.createUserWithEmailAndPassword(email, password).await().user!!
        }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().user!!
        }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
