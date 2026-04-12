package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_user_profile"
        private const val PREF_MAX_HR = "max_hr"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_AVATAR_SYMBOL = "avatar_symbol"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_PARTNER_NUDGES_ENABLED = "partner_nudges_enabled"
        private const val PREF_AGE = "age"
        private const val PREF_WEIGHT = "weight"
        private const val PREF_WEIGHT_UNIT = "weight_unit" // "lbs" or "kg"
        private const val PREF_DISTANCE_UNIT = "distance_unit" // "km" or "mi"
        private const val UNSET = -1
        private const val DEFAULT_NAME = "Runner"
        private const val DEFAULT_EMBLEM = "pulse"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMaxHr(): Int? {
        val stored = prefs.getInt(PREF_MAX_HR, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setMaxHr(maxHr: Int) {
        require(maxHr in 100..220) { "maxHr must be in 100..220" }
        prefs.edit().putInt(PREF_MAX_HR, maxHr).apply()
    }

    @Synchronized
    fun getDisplayName(): String {
        return prefs.getString(PREF_DISPLAY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    }

    @Synchronized
    fun setDisplayName(name: String) {
        prefs.edit().putString(PREF_DISPLAY_NAME, sanitizeDisplayName(name)).apply()
    }

    @Synchronized
    fun getEmblemId(): String {
        return prefs.getString(PREF_AVATAR_SYMBOL, DEFAULT_EMBLEM) ?: DEFAULT_EMBLEM
    }

    @Synchronized
    fun setEmblemId(id: String) {
        prefs.edit().putString(PREF_AVATAR_SYMBOL, id).apply()
    }

    @Synchronized
    fun getUserId(): String {
        val existing = prefs.getString(PREF_USER_ID, null)
        if (existing != null) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(PREF_USER_ID, newId).apply()
        return newId
    }

    @Synchronized
    fun setUserId(id: String) {
        prefs.edit().putString(PREF_USER_ID, id).apply()
    }

    @Synchronized
    fun isPartnerNudgesEnabled(): Boolean {
        return prefs.getBoolean(PREF_PARTNER_NUDGES_ENABLED, true)
    }

    @Synchronized
    fun setPartnerNudgesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_PARTNER_NUDGES_ENABLED, enabled).apply()
    }

    @Synchronized
    fun getAge(): Int? {
        val stored = prefs.getInt(PREF_AGE, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setAge(age: Int) {
        require(age in 13..99) { "age must be in 13..99" }
        prefs.edit().putInt(PREF_AGE, age).apply()
    }

    @Synchronized
    fun getWeight(): Int? {
        val stored = prefs.getInt(PREF_WEIGHT, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setWeight(weight: Int) {
        require(weight in 27..400) { "weight must be in 27..400" }
        prefs.edit().putInt(PREF_WEIGHT, weight).apply()
    }

    @Synchronized
    fun getWeightUnit(): String {
        return prefs.getString(PREF_WEIGHT_UNIT, "lbs") ?: "lbs"
    }

    @Synchronized
    fun setWeightUnit(unit: String) {
        require(unit == "lbs" || unit == "kg") { "unit must be 'lbs' or 'kg'" }
        prefs.edit().putString(PREF_WEIGHT_UNIT, unit).apply()
    }

    @Synchronized
    fun getDistanceUnit(): String {
        return prefs.getString(PREF_DISTANCE_UNIT, "km") ?: "km"
    }

    @Synchronized
    fun setDistanceUnit(unit: String) {
        require(unit == "km" || unit == "mi") { "unit must be 'km' or 'mi'" }
        prefs.edit().putString(PREF_DISTANCE_UNIT, unit).apply()
    }
}

internal fun sanitizeDisplayName(name: String): String {
    val trimmed = name.trim().take(20)
    return trimmed.ifBlank { "Runner" }
}
