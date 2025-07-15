package com.wx.rtc.utils

import kotlinx.serialization.json.Json

object JsonUtils {
    val JSON = Json {
        encodeDefaults = true
        isLenient = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}