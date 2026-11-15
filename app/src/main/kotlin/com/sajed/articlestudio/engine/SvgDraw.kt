package com.sajed.articlestudio.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Int {
    val c = (1 - Math.abs(2 * l - 1)) * s
    val hp = ((h % 360 + 360) % 360) / 60f
    val x = c * (1 - Math.abs(hp % 2 - 1))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2
    return Color.argb((a * 255).toInt(), ((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
}

/** راسم SVG مصغّر يغطي ما تحتاجه رموز الأغلفة: rect وcircle وellipse وpath وtext وg مع التحويلات والتدرج. */
object SvgDraw {
    val GOLD = hsl(45f, 0.95f, 0.62f)
    private val GRAD_A = hsl(48f, 0.96f, 0.70f)
    private val GRAD_B = hsl(34f, 0.92f, 0.52f)

    private fun color(v: String): Int = when {
        v.startsWith("rgba") -> {
            val p = v.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
            Color.argb((p[3] * 255).toInt(), p[0].toInt(), p[1].toInt(), p[2].toInt())
        }
        v.startsWith("#") && v.length == 4 -> Color.parseColor("#" + v.substring(1).map { "$it$it" }.joinToString(""))
        else -> Color.parseColor(v)
    }

    private fun nums(s: String) = Regex("-?\\d*\\.?\\d+").findAll(s).map { it.value.toFloat() }.toList()

    private fun transform(t: String): Matrix {
        val m = Matrix()
        Regex("(\\w+)\\(([^)]*)\\)").findAll(t).forEach { r ->
            val a = nums(r.groupValues[2])
            when (r.groupValues[1]) {
                "translate" -> m.preTranslate(a[0], a.getOrElse(1) { 0f })
                "scale" -> m.preScale(a[0], a.getOrElse(1) { a[0] })
                "rotate" -> if (a.size >= 3) m.preRotate(a[0], a[1], a[2]) else m.preRotate(a[0])
                "matrix" -> { val f = FloatArray(9); f[0] = a[0]; f[3] = a[1]; f[1] = a[2]; f[4] = a[3]; f[2] = a[4]; f[5] = a[5]; f[8] = 1f; val mm = Matrix(); mm.setValues(f); m.preConcat(mm) }
            }
        }
        return m
    }

    fun render(body: String, size: Int, font: Typeface): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(size / 400f, size / 400f)
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(StringReader("<svg>$body</svg>"))
        val opac = ArrayList<Float>().apply { add(1f) }
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val a: (String) -> String? = { n -> p.getAttributeValue(null, n) }
                val n = { k: String -> a(k)?.toFloatOrNull() ?: 0f }
                val op = a("opacity")?.toFloatOrNull() ?: 1f
                when (p.name) {
                    "g" -> { canvas.save(); a("transform")?.let { canvas.concat(transform(it)) }; opac.add(opac.last() * op) }
                    "rect", "circle", "ellipse", "path", "text" -> {
                        canvas.save(); a("transform")?.let { canvas.concat(transform(it)) }
                        val f = n("x"); val y = n("y")
                        val bounds = RectF()
                        val path = Path()
                        when (p.name) {
                            "rect" -> { val w = n("width"); val h = n("height"); val rx = n("rx"); bounds.set(f, y, f + w, y + h); path.addRoundRect(bounds, rx, rx, Path.Direction.CW) }
                            "circle" -> { val cx = n("cx"); val cy = n("cy"); val r = n("r"); path.addCircle(cx, cy, r, Path.Direction.CW) }
                            "ellipse" -> { bounds.set(n("cx") - n("rx"), n("cy") - n("ry"), n("cx") + n("rx"), n("cy") + n("ry")); path.addOval(bounds, Path.Direction.CW) }
                            "path" -> a("d")?.let { d -> PathParser.createPathFromPathData(d)?.let { path.set(it) } }
                        }
                        val alpha = (opac.last() * op).coerceIn(0f, 1f)
                        if (p.name == "text") {
                            val fs = n("font-size").takeIf { it > 0 } ?: 16f
                            val mid = a("text-anchor") == "middle"
                            val tf = a("fill") ?: "#000"
                            val txt = p.nextText()
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                typeface = font; textSize = fs
                                textAlign = if (mid) Paint.Align.CENTER else Paint.Align.LEFT
                                this.color = fillColor(tf); this.alpha = (this.alpha * alpha).toInt()
                            }
                            canvas.drawText(txt, f, y, paint)
                        } else {
                            path.computeBounds(bounds, true)
                            val fill = a("fill") ?: "#000"
                            if (fill != "none") canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                if (fill.startsWith("url(")) shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, GRAD_A, GRAD_B, Shader.TileMode.CLAMP)
                                else this.color = fillColor(fill)
                                this.alpha = (this.alpha * alpha).toInt()
                            })
                            val st = a("stroke")
                            if (st != null && st != "none") canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE; this.color = fillColor(st); this.alpha = (this.alpha * alpha).toInt()
                                strokeWidth = a("stroke-width")?.toFloatOrNull() ?: 1f
                                strokeCap = when (a("stroke-linecap")) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                                strokeJoin = if (a("stroke-linejoin") == "round") Paint.Join.ROUND else Paint.Join.MITER
                                a("stroke-dasharray")?.let { d -> val n = nums(d); if (n.size >= 2) pathEffect = DashPathEffect(n.toFloatArray(), 0f) }
                            })
                        }
                        canvas.restore()
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "g") { canvas.restore(); opac.removeAt(opac.size - 1) }
            ev = p.next()
        }
        return bmp
    }

    private fun fillColor(v: String) = if (v == "G") GOLD else color(v)
}
