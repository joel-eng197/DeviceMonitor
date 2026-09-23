package com.example.devicemonitor

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val dataBytes: Long
)
