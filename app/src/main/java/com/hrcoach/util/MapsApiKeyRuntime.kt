package com.hrcoach.util

import android.content.Context
import android.os.Bundle

object MapsApiKeyRuntime {
    private const val MAPS_META_KEY = "com.google.android.geo.API_KEY"

    fun applyIfPresent(context: Context, key: String?) {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isBlank()) return
        val appInfo = context.applicationInfo
        val metaData = appInfo.metaData ?: Bundle().also { appInfo.metaData = it }
        metaData.putString(MAPS_META_KEY, trimmed)
    }
}

