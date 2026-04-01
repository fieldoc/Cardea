package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserProfileRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repo: UserProfileRepository

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true) {
            every { edit() } returns editor
            every { editor.putInt(any(), any()) } returns editor
            every { editor.putString(any(), any()) } returns editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences("hr_coach_user_profile", Context.MODE_PRIVATE) } returns prefs
        }
        repo = UserProfileRepository(context)
    }

    @Test
    fun `getAge returns null when unset`() {
        every { prefs.getInt("age", -1) } returns -1
        assertNull(repo.getAge())
    }

    @Test
    fun `setAge stores value and getAge returns it`() {
        every { prefs.getInt("age", -1) } returns 32
        repo.setAge(32)
        verify { editor.putInt("age", 32) }
        assertEquals(32, repo.getAge())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAge rejects value below 13`() {
        repo.setAge(12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAge rejects value above 99`() {
        repo.setAge(100)
    }

    @Test
    fun `getWeight returns null when unset`() {
        every { prefs.getInt("weight", -1) } returns -1
        assertNull(repo.getWeight())
    }

    @Test
    fun `setWeight stores value`() {
        repo.setWeight(165)
        verify { editor.putInt("weight", 165) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setWeight rejects value below 27`() {
        repo.setWeight(26)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setWeight rejects value above 400`() {
        repo.setWeight(401)
    }

    @Test
    fun `getWeightUnit defaults to lbs`() {
        every { prefs.getString("weight_unit", "lbs") } returns "lbs"
        assertEquals("lbs", repo.getWeightUnit())
    }

    @Test
    fun `setWeightUnit stores kg`() {
        repo.setWeightUnit("kg")
        verify { editor.putString("weight_unit", "kg") }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setWeightUnit rejects invalid unit`() {
        repo.setWeightUnit("stones")
    }
}
