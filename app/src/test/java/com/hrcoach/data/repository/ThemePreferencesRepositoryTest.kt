package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hrcoach.domain.model.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemePreferencesRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repo: ThemePreferencesRepository

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        prefs = mockk {
            every { getString("theme_mode", "SYSTEM") } returns "SYSTEM"
            every { edit() } returns editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences("cardea_theme_prefs", Context.MODE_PRIVATE) } returns prefs
        }
        repo = ThemePreferencesRepository(context)
    }

    @Test
    fun `default theme mode is SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }

    @Test
    fun `setThemeMode persists DARK`() {
        every { editor.putString(any(), any()) } returns editor
        repo.setThemeMode(ThemeMode.DARK)
        verify { editor.putString("theme_mode", "DARK") }
        verify { editor.apply() }
    }

    @Test
    fun `getThemeMode reads stored LIGHT`() {
        every { prefs.getString("theme_mode", "SYSTEM") } returns "LIGHT"
        assertEquals(ThemeMode.LIGHT, repo.getThemeMode())
    }

    @Test
    fun `invalid stored value falls back to SYSTEM`() {
        every { prefs.getString("theme_mode", "SYSTEM") } returns "GARBAGE"
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }
}
