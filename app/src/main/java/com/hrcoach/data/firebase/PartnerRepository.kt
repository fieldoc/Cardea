package com.hrcoach.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class PartnerInfo(
    val uid: String,
    val displayName: String,
    val avatarSymbol: String
)

@Singleton
class PartnerRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    val authManager: FirebaseAuthManager
) {
    suspend fun syncUserProfile(displayName: String, avatarSymbol: String) {
        val uid = authManager.uid ?: return
        firestore.collection("users").document(uid)
            .set(
                mapOf("displayName" to displayName, "avatarSymbol" to avatarSymbol),
                SetOptions.merge()
            ).await()
    }

    suspend fun createInvite(): String {
        val uid = authManager.uid ?: throw IllegalStateException("Not signed in")
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val expiresAt = Timestamp(System.currentTimeMillis() / 1000 + 24 * 60 * 60, 0)
        repeat(3) {
            val code = (1..6).map { chars.random() }.joinToString("")
            val docRef = firestore.collection("pairings").document(code)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(mapOf(
                    "creatorId" to uid,
                    "createdAt" to Timestamp.now(),
                    "expiresAt" to expiresAt,
                )).await()
                return code
            }
        }
        throw IllegalStateException("Could not generate a unique code, please try again")
    }

    suspend fun acceptInvite(code: String) {
        val uid = authManager.uid ?: throw IllegalStateException("Not signed in")
        firestore.runTransaction { tx ->
            val pairingRef = firestore.collection("pairings").document(code)
            val pairing = tx.get(pairingRef)
            if (!pairing.exists()) throw IllegalArgumentException("Code not found")
            val expiresAt = pairing.getTimestamp("expiresAt")?.toDate()
            if (expiresAt != null && expiresAt.before(java.util.Date())) {
                throw IllegalArgumentException("Code has expired")
            }
            val creatorId = pairing.getString("creatorId")
                ?: throw IllegalArgumentException("Invalid code")
            if (creatorId == uid) throw IllegalArgumentException("Cannot pair with yourself")
            tx.update(firestore.collection("users").document(creatorId), "partnerId", uid)
            tx.update(firestore.collection("users").document(uid), "partnerId", creatorId)
            tx.delete(pairingRef)
        }.await()
    }

    suspend fun disconnect() {
        val uid = authManager.uid ?: return
        val userDoc = firestore.collection("users").document(uid)
        firestore.runTransaction { tx ->
            val snapshot = tx.get(userDoc)
            val partnerId = snapshot.getString("partnerId") ?: return@runTransaction
            tx.update(userDoc, "partnerId", null)
            tx.update(firestore.collection("users").document(partnerId), "partnerId", null)
        }.await()
    }

    fun observePartnerId(): Flow<String?> = callbackFlow {
        val uid = authManager.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.getString("partnerId"))
            }
        awaitClose { registration.remove() }
    }

    suspend fun getPartnerInfo(partnerUid: String): PartnerInfo? {
        val doc = firestore.collection("users").document(partnerUid).get().await()
        if (!doc.exists()) return null
        return PartnerInfo(
            uid = partnerUid,
            displayName = doc.getString("displayName") ?: "Runner",
            avatarSymbol = doc.getString("avatarSymbol") ?: "\u2665"
        )
    }

    suspend fun postRunCompletion(payload: RunCompletionPayload) {
        firestore.collection("runCompletions")
            .add(payload.toMap())
            .await()
    }

    suspend fun getPartnerCompletions(partnerUid: String, sinceDaysAgo: Int = 7): List<RunCompletionPayload> {
        val cutoff = System.currentTimeMillis() - (sinceDaysAgo * 86_400_000L)
        val docs = firestore.collection("runCompletions")
            .whereEqualTo("userId", partnerUid)
            .whereGreaterThan("timestamp", cutoff)
            .get()
            .await()
        return docs.map { doc ->
            RunCompletionPayload(
                userId = doc.getString("userId") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                distanceMeters = doc.getDouble("distanceMeters") ?: 0.0,
                routePolyline = doc.getString("routePolyline") ?: "",
                streakCount = (doc.getLong("streakCount") ?: 0).toInt(),
                programPhase = doc.getString("programPhase"),
                sessionLabel = doc.getString("sessionLabel"),
                wasScheduled = doc.getBoolean("wasScheduled") ?: false,
                originalScheduledWeekDay = doc.getLong("originalScheduledWeekDay")?.toInt(),
                weekDay = (doc.getLong("weekDay") ?: 1).toInt()
            )
        }
    }
}
