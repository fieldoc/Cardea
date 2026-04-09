package com.hrcoach.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.hrcoach.data.repository.UserProfileRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend fun ensureSignedIn(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        try {
            val result = withTimeout(10_000) {
                auth.signInAnonymously().await()
            }
            val uid = result.user?.uid ?: throw IllegalStateException("Anonymous auth returned null UID")
            userProfileRepository.setUserId(uid)
            return uid
        } catch (e: TimeoutCancellationException) {
            throw Exception("Authentication timed out. Please check your connection.")
        }
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid
}
