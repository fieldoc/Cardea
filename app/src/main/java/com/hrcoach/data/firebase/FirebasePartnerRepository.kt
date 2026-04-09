package com.hrcoach.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.PartnerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

class PartnerLimitException(message: String) : Exception(message)

private const val MAX_PARTNERS = 3

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
        withTimeout(10_000) {
            userRef.child("displayName").setValue(userProfileRepository.getDisplayName()).await()
            userRef.child("emblemId").setValue(userProfileRepository.getEmblemId()).await()
        }
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
        withTimeout(10_000) { activityRef.updateChildren(updates).await() }
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
        runCatching {
            withTimeout(10_000) { invitesRef.child(code).setValue(data).await() }
        }.getOrElse { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            throw Exception("Could not save invite code. Check your connection and try again.")
        }
        return code
    }

    suspend fun redeemInviteCode(code: String): PartnerActivity? = withTimeout(15_000) {
        val snapshot = invitesRef.child(code).get().await()
        if (!snapshot.exists()) return@withTimeout null

        val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: return@withTimeout null
        if (System.currentTimeMillis() > expiresAt) return@withTimeout null

        val partnerId = snapshot.child("userId").getValue(String::class.java) ?: return@withTimeout null
        val partnerName = snapshot.child("displayName").getValue(String::class.java) ?: "Runner"
        val partnerEmblem = snapshot.child("emblemId").getValue(String::class.java) ?: "pulse"

        val myUid = authManager.ensureSignedIn()
        if (partnerId == myUid) return@withTimeout null

        // Note: client-side cap check. A concurrent redemption can bypass this before the write
        // lands — true enforcement requires Firebase Security Rules or a Cloud Function.
        val currentCount = usersRef.child(myUid).child("partners").get().await().childrenCount
        if (currentCount >= MAX_PARTNERS) throw PartnerLimitException("You already have $MAX_PARTNERS partners. Remove one to add more.")

        val partnerCount = usersRef.child(partnerId).child("partners").get().await().childrenCount
        if (partnerCount >= MAX_PARTNERS) throw PartnerLimitException("Your partner has reached their $MAX_PARTNERS-partner limit.")

        // Bidirectional partner link — single atomic write so both sides appear together
        val linkUpdates = mapOf(
            "users/$myUid/partners/$partnerId" to true,
            "users/$partnerId/partners/$myUid" to true,
        )
        database.reference.updateChildren(linkUpdates).await()

        // Delete consumed invite (best-effort — rule allows delete by confirmed partners)
        runCatching { invitesRef.child(code).removeValue().await() }

        val partnerSnap = usersRef.child(partnerId).get().await()
        partnerSnap.toPartnerActivity(partnerId) ?: PartnerActivity(
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
        val updates = mapOf(
            "users/$myUid/partners/$partnerId" to null,
            "users/$partnerId/partners/$myUid" to null,
        )
        withTimeout(10_000) { database.reference.updateChildren(updates).await() }
    }

    fun observePartners(): Flow<List<PartnerActivity>> = callbackFlow {
        val uid = authManager.getCurrentUid() ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val partnersRef = usersRef.child(uid).child("partners")
        val flowScope = this

        // Emit empty list immediately so combine() doesn't stall
        // while waiting for the first Firebase callback
        trySend(emptyList())

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partnerIds = snapshot.children.mapNotNull { it.key }
                if (partnerIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }
                flowScope.launch {
                    val partners = partnerIds
                        .map { pid ->
                            async {
                                runCatching { withTimeout(10_000) { usersRef.child(pid).get().await().toPartnerActivity(pid) } }
                                    .onFailure { e -> if (e is CancellationException && e !is TimeoutCancellationException) throw e }
                                    .getOrNull()
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                        .sortedByDescending { it.lastRunDate }
                    trySend(partners)
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

        // Emit initial value so combine() doesn't stall
        // while waiting for the first Firebase callback
        trySend(0)

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
