package com.hrcoach.data.firebase

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenManager @Inject constructor(
    private val database: FirebaseDatabase,
    private val messaging: FirebaseMessaging,
    private val authManager: FirebaseAuthManager,
) {
    suspend fun refreshToken() {
        val uid = authManager.ensureSignedIn()
        val token = messaging.token.await()
        database.reference.child("users").child(uid).child("fcmToken").setValue(token).await()
        Log.d("FcmTokenManager", "Token synced for $uid")
    }
}
