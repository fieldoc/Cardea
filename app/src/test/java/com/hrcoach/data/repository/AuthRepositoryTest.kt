package com.hrcoach.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private val mockAuth = mockk<FirebaseAuth>(relaxed = true)
    private lateinit var repo: AuthRepository

    @Before
    fun setup() {
        every { mockAuth.currentUser } returns null
        repo = AuthRepository(mockAuth)
    }

    @Test
    fun `currentUserId returns null when not authenticated`() {
        assertNull(repo.currentUserId)
    }

    @Test
    fun `currentUserId returns uid when authenticated`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "test-uid-123"
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertEquals("test-uid-123", repo.currentUserId)
    }

    @Test
    fun `isAuthenticated returns false when no user`() {
        assertFalse(repo.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true when user present`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertTrue(repo.isAuthenticated())
    }

    @Test
    fun `effectiveUserId returns empty string when not authenticated`() {
        assertEquals("", repo.effectiveUserId)
    }

    @Test
    fun `effectiveUserId returns uid when authenticated`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "uid-abc"
        every { mockAuth.currentUser } returns mockUser
        repo = AuthRepository(mockAuth)
        assertEquals("uid-abc", repo.effectiveUserId)
    }
}
