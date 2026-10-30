package cn.hautpocket.app.domain

import kotlin.math.pow

object TextColors {
    val presets = listOf("auto" to "自动", "contrast" to "高对比", "warm" to "暖色", "cool" to "冷色")
    fun validCustom(value: String) = Regex("^#[0-9a-fA-F]{6}$").matches(value.trim())
    fun normalize(value: String): String = when {
        value in presets.map { it.first } -> value
        validCustom(value) -> value.trim().uppercase()
        else -> "auto"
    }
    fun palette(value: String, dark: Boolean): Pair<String, String> = when (val color = normalize(value)) {
        "contrast" -> if (dark) "#FFFFFF" to "#EDF4F2" else "#101820" to "#303B42"
        "warm" -> if (dark) "#FFE4C2" to "#E2CDB4" else "#513D2A" to "#695343"
        "cool" -> if (dark) "#D5EAFF" to "#B4CEE8" else "#24425A" to "#476078"
        "auto" -> if (dark) "#E0EBE7" to "#B6C8C0" else "#192C2B" to "#61716F"
        else -> color to color
    }
    fun contrast(foreground: String, background: String): Double {
        fun luminance(hex: String): Double {
            val channels = listOf(1, 3, 5).map { start ->
                val c = hex.substring(start, start + 2).toInt(16) / 255.0
                if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
            }
            return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
        }
        val a = luminance(foreground); val b = luminance(background)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
