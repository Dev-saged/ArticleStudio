package com.sajed.articlestudio.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class Oklch(val l: Float, val c: Float, val h: Float, val alpha: Float = 1f) {
    fun toColor(): Color {
        val rad = Math.toRadians(h.toDouble())
        val a = c * cos(rad)
        val b = c * sin(rad)
        val l1 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m1 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s1 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1
        val g = -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1
        val bl = -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1
        return Color(gamma(r), gamma(g), gamma(bl), alpha)
    }

    private fun gamma(v: Double): Float {
        val x = v.coerceIn(0.0, 1.0)
        return (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).toFloat()
    }
}
