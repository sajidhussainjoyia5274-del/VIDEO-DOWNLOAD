package com.smartvideodownloader.util

import java.util.Locale

object FormatUtils {
    fun bytes(b: Long): String {
        if (b < 0) return "Unknown size"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "$b B" else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun speed(bps: Long): String = bytes(bps) + "/s"
}
