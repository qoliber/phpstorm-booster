package com.qoliber.booster.cachehealth

object SizeFormat {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun humanReadable(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format("%.1f %s", value, units[unit])
    }
}
