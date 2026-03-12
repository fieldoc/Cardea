package com.hrcoach.domain.model

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import org.json.JSONArray
import org.json.JSONObject

data class ShareableBootcampConfig(
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: List<DayPreference>,
    val tierIndex: Int,
    val sharerUserId: String,
    val sharerDisplayName: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("goalType", goalType)
        put("targetMinutesPerRun", targetMinutesPerRun)
        put("runsPerWeek", runsPerWeek)
        put("tierIndex", tierIndex)
        put("sharerUserId", sharerUserId)
        put("sharerDisplayName", sharerDisplayName)
        put("preferredDays", JSONArray().apply {
            preferredDays.forEach { dp ->
                put(JSONObject().apply {
                    put("day", dp.day)
                    put("level", dp.level.name)
                })
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ShareableBootcampConfig {
            val daysArray = json.getJSONArray("preferredDays")
            val days = (0 until daysArray.length()).map { i ->
                val obj = daysArray.getJSONObject(i)
                DayPreference(
                    day = obj.getInt("day"),
                    level = DaySelectionLevel.valueOf(obj.getString("level"))
                )
            }
            return ShareableBootcampConfig(
                goalType = json.getString("goalType"),
                targetMinutesPerRun = json.getInt("targetMinutesPerRun"),
                runsPerWeek = json.getInt("runsPerWeek"),
                preferredDays = days,
                tierIndex = json.getInt("tierIndex"),
                sharerUserId = json.getString("sharerUserId"),
                sharerDisplayName = json.getString("sharerDisplayName"),
            )
        }
    }
}

fun BootcampEnrollmentEntity.toShareable(
    userId: String,
    displayName: String,
): ShareableBootcampConfig = ShareableBootcampConfig(
    goalType = goalType,
    targetMinutesPerRun = targetMinutesPerRun,
    runsPerWeek = runsPerWeek,
    preferredDays = preferredDays,
    tierIndex = tierIndex,
    sharerUserId = userId,
    sharerDisplayName = displayName,
)
