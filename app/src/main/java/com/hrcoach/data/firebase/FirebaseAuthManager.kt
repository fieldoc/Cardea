package com.hrcoach.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /** Current Firebase UID, or null if not yet signed in. */
    val uid: String? get() = auth.currentUser?.uid

    /**
     * Sign in anonymously if not already signed in.
     * Writes/updates FCM token to Firestore user doc.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val currentUid = uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        firestore.collection("users").document(currentUid)
            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /** Update FCM token in Firestore (called from MessagingService.onNewToken). */
    suspend fun updateFcmToken(token: String) {
        val currentUid = uid ?: return
        firestore.collection("users").document(currentUid)
            .update("fcmToken", token)
            .await()
    }
}
