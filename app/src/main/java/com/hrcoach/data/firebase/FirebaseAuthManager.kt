package com.hrcoach.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.hrcoach.data.repository.UserProfileRepository
import kotlinx.coroutines.tasks.await
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

        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: throw IllegalStateException("Anonymous auth returned null UID")
        userProfileRepository.setUserId(uid)
        return uid
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid
}
