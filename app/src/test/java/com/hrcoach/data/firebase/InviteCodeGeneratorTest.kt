package com.hrcoach.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeGeneratorTest {

    @Test
    fun `generated code is 6 characters`() {
        val code = generateInviteCode()
        assertEquals(6, code.length)
    }

    @Test
    fun `generated code contains only allowed characters`() {
        val allowed = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        repeat(100) {
            val code = generateInviteCode()
            code.forEach { char ->
                assertTrue(
                    "Unexpected character '$char' in code '$code'",
                    char in allowed
                )
            }
        }
    }

    @Test
    fun `excluded ambiguous characters are never present`() {
        val ambiguous = setOf('0', 'O', '1', 'I', 'L')
        repeat(200) {
            val code = generateInviteCode()
            code.forEach { char ->
                assertTrue(
                    "Ambiguous character '$char' found in code '$code'",
                    char !in ambiguous
                )
            }
        }
    }

    @Test
    fun `two generated codes are different`() {
        val codes = (1..50).map { generateInviteCode() }.toSet()
        assertTrue("Expected at least 40 unique codes out of 50", codes.size >= 40)
    }
}
