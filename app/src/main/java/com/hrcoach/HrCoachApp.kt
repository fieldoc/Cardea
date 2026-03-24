package com.hrcoach

import android.app.Application
import com.hrcoach.data.firebase.FirebaseAuthManager
import com.hrcoach.data.repository.WorkoutRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HrCoachApp : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun workoutRepository(): WorkoutRepository
        fun firebaseAuthManager(): FirebaseAuthManager
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                this@HrCoachApp, AppEntryPoint::class.java
            )
            entryPoint.workoutRepository().cleanupOrphanedWorkouts()
            // Fire-and-forget anonymous sign-in + FCM token sync
            runCatching { entryPoint.firebaseAuthManager().ensureSignedIn() }
        }
    }
}
