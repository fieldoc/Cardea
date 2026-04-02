package com.hrcoach.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.PartnerActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePartnerRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: FirebaseAuthManager,
    private val userProfileRepository: UserProfileRepository,
) {
    private val usersRef get() = database.reference.child("users")
    private val invitesRef get() = database.reference.child("invites")

    suspend fun syncProfile() {
        val uid = authManager.ensureSignedIn()
        val userRef = usersRef.child(uid)
        userRef.child("displayName").setValue(userProfileRepository.getDisplayName()).await()
        userRef.child("emblemId").setValue(userProfileRepository.getEmblemId()).await()
    }

    suspend fun syncWorkoutActivity(
        currentStreak: Int,
        weeklyRunCount: Int,
        lastRunDurationMin: Int,
        lastRunPhase: String,
    ) {
        val uid = authManager.ensureSignedIn()
        val activityRef = usersRef.child(uid).child("activity")
        val updates = mapOf(
            "currentStreak" to currentStreak,
            "weeklyRunCount" to weeklyRunCount,
            "lastRunDate" to java.time.LocalDate.now().toString(),
            "lastRunDurationMin" to lastRunDurationMin,
            "lastRunPhase" to lastRunPhase,
        )
        activityRef.updateChildren(updates).await()
    }

    suspend fun createInviteCode(): String {
        val uid = authManager.ensureSignedIn()
        val code = generateInviteCode()
        val now = System.currentTimeMillis()
        val data = mapOf(
            "userId" to uid,
            "displayName" to userProfileRepository.getDisplayName(),
            "emblemId" to userProfileRepository.getEmblemId(),
            "createdAt" to now,
            "expiresAt" to now + 24 * 60 * 60 * 1000,
        )
        invitesRef.child(code).setValue(data).await()
        return code
    }

    suspend fun redeemInviteCode(code: String): PartnerActivity? {
        val snapshot = invitesRef.child(code).get().await()
        if (!snapshot.exists()) return null

        val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: return null
        if (System.currentTimeMillis() > expiresAt) return null

        val partnerId = snapshot.child("userId").getValue(String::class.java) ?: return null
        val partnerName = snapshot.child("displayName").getValue(String::class.java) ?: "Runner"
        val partnerEmblem = snapshot.child("emblemId").getValue(String::class.java) ?: "pulse"

        val myUid = authManager.ensureSignedIn()
        if (partnerId == myUid) return null

        // Bidirectional partner link
        usersRef.child(myUid).child("partners").child(partnerId).setValue(true).await()
        usersRef.child(partnerId).child("partners").child(myUid).setValue(true).await()

        // Delete consumed invite
        invitesRef.child(code).removeValue().await()

        return PartnerActivity(
            userId = partnerId,
            displayName = partnerName,
            emblemId = partnerEmblem,
            currentStreak = 0,
            weeklyRunCount = 0,
            lastRunDate = null,
            lastRunDurationMin = null,
            lastRunPhase = null,
        )
    }

    suspend fun removePartner(partnerId: String) {
        val myUid = authManager.ensureSignedIn()
        usersRef.child(myUid).child("partners").child(partnerId).removeValue().await()
        usersRef.child(partnerId).child("partners").child(myUid).removeValue().await()
    }

    fun observePartners(): Flow<List<PartnerActivity>> = callbackFlow {
        val uid = authManager.getCurrentUid() ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val partnersRef = usersRef.child(uid).child("partners")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partnerIds = snapshot.children.mapNotNull { it.key }
                if (partnerIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }
                val partners = mutableListOf<PartnerActivity>()
                var loaded = 0
                for (pid in partnerIds) {
                    usersRef.child(pid).get().addOnSuccessListener { partnerSnap ->
                        val activity = partnerSnap.toPartnerActivity(pid)
                        if (activity != null) partners.add(activity)
                        loaded++
                        if (loaded == partnerIds.size) {
                            trySend(partners.sortedByDescending { it.lastRunDate })
                        }
                    }.addOnFailureListener {
                        loaded++
                        if (loaded == partnerIds.size) {
                            trySend(partners.sortedByDescending { it.lastRunDate })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }

        partnersRef.addValueEventListener(listener)
        awaitClose { partnersRef.removeEventListener(listener) }
    }

    fun getPartnerCount(): Flow<Int> = callbackFlow {
        val uid = authManager.getCurrentUid() ?: run {
            trySend(0)
            close()
            return@callbackFlow
        }
        val partnersRef = usersRef.child(uid).child("partners")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.childrenCount.toInt())
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(0)
            }
        }
        partnersRef.addValueEventListener(listener)
        awaitClose { partnersRef.removeEventListener(listener) }
    }

    private fun DataSnapshot.toPartnerActivity(userId: String): PartnerActivity? {
        if (!exists()) return null
        val displayName = child("displayName").getValue(String::class.java) ?: return null
        val emblemId = child("emblemId").getValue(String::class.java) ?: "pulse"
        val activity = child("activity")
        return PartnerActivity(
            userId = userId,
            displayName = displayName,
            emblemId = emblemId,
            currentStreak = activity.child("currentStreak").getValue(Int::class.java) ?: 0,
            weeklyRunCount = activity.child("weeklyRunCount").getValue(Int::class.java) ?: 0,
            lastRunDate = activity.child("lastRunDate").getValue(String::class.java),
            lastRunDurationMin = activity.child("lastRunDurationMin").getValue(Int::class.java),
            lastRunPhase = activity.child("lastRunPhase").getValue(String::class.java),
        )
    }
}
