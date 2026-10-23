@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal typealias Translate = (String) -> String

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

internal fun formatEpoch(millis: Long): String {
    if (millis <= 0) return "—"
    return runCatching { timeFormatter.format(Instant.ofEpochMilli(millis)) }.getOrDefault("—")
}

internal fun formatKb(value: Long): String {
    if (value <= 0) return "—"
    val gb = value / 1024.0 / 1024.0
    return if (gb >= 1) "%.1f GB".format(gb) else "${value / 1024} MB"
}

internal fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

internal val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

internal fun formatTime(iso: String): String = runCatching { timeFormatter.format(Instant.parse(iso)) }.getOrDefault(iso)
