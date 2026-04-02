package com.hrcoach.data.firebase

import kotlin.random.Random

private val ALLOWED_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

fun generateInviteCode(): String {
    return (1..6)
        .map { ALLOWED_CHARS[Random.nextInt(ALLOWED_CHARS.length)] }
        .joinToString("")
}
