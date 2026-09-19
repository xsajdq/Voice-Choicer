package com.voicechoicer.app.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDurationMs(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

fun formatDate(epochMs: Long): String = dateFormat.format(Date(epochMs))
