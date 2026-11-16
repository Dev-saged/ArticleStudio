package com.sajed.articlestudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.sajed.articlestudio.BrandConfig
import com.sajed.articlestudio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class RenderedImages(val cover: ByteArray, val info: ByteArray)

/** رسم الغلاف وبطاقة المعلومات (1200×630) بـ Canvas أصلي ثم ضغطها JPEG. */
@Singleton
class ImageRenderer @Inject constructor(@ApplicationContext private val ctx: Context, private val data: StudioData) {
    private val heavy: Typeface by lazy { ResourcesCompat.getFont(ctx, R.font.almarai_extrabold) ?: Typeface.DEFAULT_BOLD }
    private val bold: Typeface by lazy { ResourcesCompat.getFont(ctx, R.font.almarai_bold) ?: Typeface.DEFAULT_BOLD }
    private val gold = SvgDraw.GOLD
    private val goldB = hsl(34f, 0.92f, 0.52f)

    fun render(a: Article, site: SiteConfig? = null): RenderedImages {
        val bmp = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        cover(c, a, plan(a, site))
        val cover = jpeg(bmp)
        card(c, a, plan(a, site))
        val info = jpeg(bmp)
        bmp.recycle()
        return RenderedImages(cover, info)
    }

    fun coverBitmap(a: Article, site: SiteConfig? = null): Bitmap = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888).also { cover(Canvas(it), a, plan(a, site)) }
    fun cardBitmap(a: Article, site: SiteConfig? = null): Bitmap = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888).also { card(Canvas(it), a, plan(a, site)) }

    /** خطة تصميم ثابتة لكل مقالة: النمط والتخطيط واللون يُشتقّان من عنوانها ومجال الموقع فتتنوع الصور بلا عشوائية. */
    private class Plan(val name: String, val domain: String, val hue: Float, val accent: Int, val glow: Int, val style: String, val layout: Int, val angle: Float)

    private fun plan(a: Article, site: SiteConfig?): Plan {
        val seed = (a.title + a.slug).fold(7) { h, ch -> h * 31 + ch.code }.let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        val styles = STYLES[site?.niche ?: "sport"] ?: STYLES.getValue("general")
        val shift = listOf(0f, 38f, -38f, 150f)[(seed / 7) % 4]
        val h = a.hue.toFloat()
        return Plan(
            site?.name ?: BrandConfig.BLOG_NAME, site?.host ?: BrandConfig.BLOG_SITE.removePrefix("https://"), h,
            hsl(h + shift, .80f, .60f), hsl(h + shift, .72f, .50f, .34f),
            styles[(seed / 3) % styles.size], seed % 4, listOf(0f, 18f, -18f, 32f)[(seed / 5) % 4],
        )
    }

    private fun jpeg(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()

    private fun paint(tf: Typeface, px: Float, col: Int = Color.WHITE) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = px; color = col }

    private fun width(t: String, p: Paint) = p.getRunAdvance(t, 0, t.length, 0, t.length, true, t.length)

    /** نص RTL محاذى لليمين عند rightX. */
    private fun textR(c: Canvas, t: String, rightX: Float, y: Float, p: Paint, rtl: Boolean = true) {
        val w = p.getRunAdvance(t, 0, t.length, 0, t.length, rtl, t.length)
        if (rtl) c.drawText("\u200F$t", rightX - w, y, p) else c.drawTextRun(t, 0, t.length, 0, t.length, rightX - w, y, false, p)
    }

    private fun wrap(t: String, p: Paint, maxW: Float): List<String> {
        val out = ArrayList<String>(); var cur = ""
        for (w in t.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val n = if (cur.isEmpty()) w else "$cur $w"
            if (width(n, p) > maxW && cur.isNotEmpty()) { out += cur; cur = w } else cur = n
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private fun stroke(w: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; color = col; strokeCap = Paint.Cap.ROUND }
    private fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    private fun white(a: Int) = Color.argb(a, 255, 255, 255)

    /** يملأ العنوان أكبر خط يتّسع في المساحة. */
    private fun fitTitle(title: String, w: Float, h: Float, max: Float, min: Float = 38f): Pair<Float, List<String>> {
        var fs = max
        while (true) {
            val l = wrap(title, paint(heavy, fs), w)
            if (l.size * fs * 1.36f <= h || fs <= min) return fs to l
            fs -= 3f
        }
    }

    // ───────── الخلفيات ─────────
    private fun background(c: Canvas, p: Plan, bottom: Float) {
        val h = p.hue
        val a = Math.toRadians(p.angle.toDouble() + 40.0)
        val dx = (Math.cos(a) * 1300).toFloat(); val dy = (Math.sin(a) * 900).toFloat()
        c.drawRect(0f, 0f, 1200f, 630f, Paint().apply { shader = LinearGradient(600f - dx / 2, 315f - dy / 2, 600f + dx / 2, 315f + dy / 2, intArrayOf(hsl(h, .52f, .24f), hsl(h + 22, .48f, .13f), hsl(h + 44, .44f, .06f)), floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP) })
        // توهّج لوني خلف منطقة المحتوى
        val gx = if (p.layout % 2 == 0) 300f else 880f
        c.drawRect(0f, 0f, 1200f, 630f, Paint().apply { shader = RadialGradient(gx, 250f, 520f, p.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        val line = stroke(2.5f, white(22)); val soft = fill(white(14))
        when (p.style) {
            "pitch" -> {
                c.drawCircle(600f, 285f, 120f, line); c.drawLine(600f, 0f, 600f, bottom, line)
                c.drawRect(1040f, 140f, 1200f, 430f, line); c.drawRect(0f, 140f, 160f, 430f, line)
                for ((x0, x1) in listOf(980f to 1200f, 760f to 940f)) {
                    val beam = Path().apply { moveTo(x0, 0f); lineTo(x1, 0f); lineTo(x1 - 520f, 630f); lineTo(x0 - 700f, 630f); close() }
                    c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x0 - 400f, 630f, white(26), Color.TRANSPARENT, Shader.TileMode.CLAMP) })
                }
            }
            "grid" -> {
                var x = 0f; while (x <= 1200f) { c.drawLine(x, 0f, x, bottom, stroke(1.5f, white(16))); x += 60f }
                var y = 0f; while (y <= bottom) { c.drawLine(0f, y, 1200f, y, stroke(1.5f, white(16))); y += 60f }
                for (i in 0 until 6) c.drawCircle(60f * (3 + i * 3 + p.layout), 60f * (2 + (i * 2 + p.layout) % 7), 5f, fill(p.accent))
            }
            "hex" -> {
                val r = 40f; val hh = r * 0.866f
                var row = 0; var y = 0f
                while (y < bottom + r) {
                    var x = if (row % 2 == 0) 0f else hh; while (x < 1260f) {
                        val path = Path(); for (k in 0 until 6) { val ang = Math.toRadians(60.0 * k + 30.0); val px = x + r * 0.94f * Math.cos(ang).toFloat(); val py = y + r * 0.94f * Math.sin(ang).toFloat(); if (k == 0) path.moveTo(px, py) else path.lineTo(px, py) }
                        path.close(); c.drawPath(path, stroke(1.6f, white(if ((x / hh + row).toInt() % 7 == 0) 40 else 15))); x += hh * 2
                    }
                    y += r * 1.5f; row++
                }
            }
            "rays" -> {
                val ox = if (p.layout % 2 == 0) 1200f else 0f
                for (i in 0 until 14) {
                    val a0 = Math.toRadians(95.0 + i * 11.0); val a1 = Math.toRadians(95.0 + i * 11.0 + 5.5)
                    val s = if (ox == 0f) -1 else 1
                    val path = Path().apply { moveTo(ox, -40f); lineTo(ox + s * (Math.cos(a0) * 1700).toFloat(), (Math.sin(a0) * 1700).toFloat() - 40f); lineTo(ox + s * (Math.cos(a1) * 1700).toFloat(), (Math.sin(a1) * 1700).toFloat() - 40f); close() }
                    c.drawPath(path, fill(white(11)))
                }
            }
            "waves" -> for (k in 0 until 5) {
                val path = Path(); val base = bottom - 40f - k * 46f
                path.moveTo(0f, 630f); path.lineTo(0f, base)
                var x = 0f; while (x <= 1200f) { path.lineTo(x, base + Math.sin((x / 190.0) + k * 1.3 + p.layout).toFloat() * (22f + k * 5)); x += 12f }
                path.lineTo(1200f, 630f); path.close()
                c.drawPath(path, fill(white(8 + k * 3)))
                c.drawPath(path, stroke(2f, white(20)).apply { style = Paint.Style.STROKE })
            }
            "diag" -> { var x = -700f; while (x < 1300f) { c.drawLine(x, bottom, x + 560f, 0f, stroke(16f, white(if (((x + 700f) / 70f).toInt() % 3 == 0) 18 else 8))); x += 70f } }
            "dots" -> {
                var y = 22f; var row = 0
                while (y < bottom) { var x = if (row % 2 == 0) 22f else 44f; while (x < 1200f) { val d = Math.hypot((x - 1200.0), (y - 0.0)).toFloat(); val rr = (9f - d / 150f).coerceIn(1.2f, 8f); c.drawCircle(x, y, rr, soft); x += 44f }; y += 38f; row++ }
            }
            else -> for (k in 1..7) c.drawCircle(if (p.layout % 2 == 0) 270f else 930f, 260f, 90f + k * 62f, stroke(2f, white(26 - k * 2)))
        }
        c.drawRect(0f, 0f, 1200f, 630f, Paint().apply { shader = RadialGradient(600f, 300f, 780f, Color.TRANSPARENT, Color.argb(165, 0, 0, 0), Shader.TileMode.CLAMP) })
    }

    // ───────── لوحة القناة: الاسم والرابط ─────────
    private fun channelPlate(c: Canvas, p: Plan) {
        val top = 528f
        c.drawRect(0f, top, 1200f, 630f, Paint().apply { shader = LinearGradient(0f, top, 0f, 630f, Color.argb(170, 4, 8, 16), Color.argb(235, 2, 4, 10), Shader.TileMode.CLAMP) })
        c.drawRect(0f, top, 1200f, top + 4f, Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 0f, intArrayOf(Color.TRANSPARENT, gold, goldB, gold, Color.TRANSPARENT), null, Shader.TileMode.CLAMP) })
        val cy = top + 51f
        // شعار أحادي: دائرة ذهبية بحرف القناة الأول
        val cx = 1128f - 36f
        c.drawCircle(cx, cy, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(cx - 38f, cy - 38f, cx + 38f, cy + 38f, gold, goldB, Shader.TileMode.CLAMP); setShadowLayer(18f, 0f, 4f, Color.argb(120, 0, 0, 0)) })
        c.drawCircle(cx, cy, 31f, stroke(2f, Color.argb(120, 30, 18, 0)))
        val ini = p.name.trim().firstOrNull { it.isLetterOrDigit() }?.toString() ?: "•"
        val ip = paint(heavy, 40f, Color.parseColor("#1B1204"))
        val iw = ip.measureText(ini); val fm = ip.fontMetrics
        c.drawText(ini, cx - iw / 2, cy - (fm.ascent + fm.descent) / 2, ip)
        // الاسم
        val nameFs = if (p.name.length <= 16) 42f else if (p.name.length <= 26) 34f else 28f
        val np = paint(heavy, nameFs)
        textR(c, p.name, cx - 52f, cy - 2f, np)
        textR(c, "تابع القناة", cx - 52f, cy + 28f, paint(bold, 20f, Color.argb(165, 255, 255, 255)))
        // الرابط على اليسار داخل كبسولة
        val up = paint(bold, 27f, gold)
        val uw = up.measureText(p.domain)
        val pw = uw + 98f; val x0 = 56f; val ph = 56f; val y0 = cy - ph / 2
        val r = RectF(x0, y0, x0 + pw, y0 + ph)
        c.drawRoundRect(r, ph / 2, ph / 2, fill(white(14)))
        c.drawRoundRect(r, ph / 2, ph / 2, stroke(2.2f, gold))
        val gx = x0 + 34f
        c.drawCircle(gx, cy, 13f, stroke(2.4f, gold)); c.drawOval(RectF(gx - 6f, cy - 13f, gx + 6f, cy + 13f), stroke(2f, gold)); c.drawLine(gx - 13f, cy, gx + 13f, cy, stroke(2f, gold))
        c.drawText(p.domain, x0 + 62f, cy - (up.fontMetrics.ascent + up.fontMetrics.descent) / 2, up)
    }

    private fun kicker(c: Canvas, text: String, rightX: Float, y: Float, p: Plan): Float {
        val k = paint(bold, 26f, p.accent)
        val w = width(text, k) + 54f; val h = 52f; val x = rightX - w
        val r = RectF(x, y, x + w, y + h)
        c.drawRoundRect(r, h / 2, h / 2, fill(Color.argb(46, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent))))
        c.drawRoundRect(r, h / 2, h / 2, stroke(2f, p.accent))
        c.drawCircle(rightX - 26f, y + h / 2, 5f, fill(p.accent))
        textR(c, text, rightX - 42f, y + h / 2 - (k.fontMetrics.ascent + k.fontMetrics.descent) / 2, k)
        return h
    }

    private fun hero(c: Canvas, a: Article, p: Plan, cx: Float, cy: Float, size: Int, ring: Boolean = true) {
        val motif = SvgDraw.render(data.motifs[a.motif] ?: data.motifs.getValue("ball"), size, heavy)
        c.drawCircle(cx, cy, size * .56f, Paint().apply { shader = RadialGradient(cx, cy, size * .56f, p.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        c.drawBitmap(motif, cx - size / 2f, cy - size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(30f, 0f, 20f, Color.argb(120, 0, 0, 0)) })
        motif.recycle()
        if (ring) { c.drawCircle(cx, cy, size * .53f, stroke(3f, Color.argb(130, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)))); c.drawCircle(cx, cy, size * .60f, stroke(1.8f, white(34))) }
    }

    private fun titleBlock(c: Canvas, text: String, rightX: Float, top: Float, w: Float, h: Float, max: Float, p: Plan, centered: Boolean = false) {
        val (fs, lines) = fitTitle(text, w, h, max)
        val tp = paint(heavy, fs).apply { setShadowLayer(24f, 0f, 5f, Color.argb(140, 0, 0, 0)) }
        lines.forEachIndexed { i, l ->
            val y = top + fs * (1.05f + i * 1.36f)
            if (centered) textR(c, l, rightX - (w - width(l, tp)) / 2, y, tp) else textR(c, l, rightX, y, tp)
        }
        val bar = top + fs * (0.45f + lines.size * 1.36f) + 14f
        c.drawRoundRect(RectF(rightX - 120f, bar, rightX, bar + 7f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(rightX - 120f, 0f, rightX, 0f, goldB, gold, Shader.TileMode.CLAMP) })
    }

    private fun cover(c: Canvas, a: Article, p: Plan) {
        background(c, p, 528f)
        val title = iso(a.title)
        when (p.layout) {
            0 -> { // بطل يسار + عنوان يمين
                hero(c, a, p, 285f, 262f, 400)
                val kh = kicker(c, iso(a.kicker), 1130f, 52f, p)
                titleBlock(c, title, 1130f, 52f + kh + 20f, 600f, 528f - 52f - kh - 70f, 72f, p)
            }
            1 -> { // ملصق: رمز ضخم خافت خلف العنوان
                val big = SvgDraw.render(data.motifs[a.motif] ?: data.motifs.getValue("ball"), 560, heavy)
                c.drawBitmap(big, 40f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 62 })
                big.recycle()
                val kh = kicker(c, iso(a.kicker), 1130f, 50f, p)
                titleBlock(c, title, 1130f, 50f + kh + 22f, 980f, 528f - 50f - kh - 72f, 84f, p)
            }
            2 -> { // شريط مائل خلف العنوان + رمز صغير بحلقة
                val band = Path().apply { moveTo(1200f, 90f); lineTo(1200f, 470f); lineTo(330f, 470f); lineTo(400f, 90f); close() }
                c.drawPath(band, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(400f, 0f, 1200f, 0f, Color.argb(120, 3, 6, 14), Color.argb(190, 3, 6, 14), Shader.TileMode.CLAMP) })
                c.drawLine(400f, 90f, 330f, 470f, stroke(5f, p.accent))
                hero(c, a, p, 175f, 292f, 270)
                kicker(c, iso(a.kicker), 1130f, 30f, p)
                titleBlock(c, title, 1100f, 120f, 640f, 335f, 70f, p)
            }
            else -> { // إطار تحريري: عنوان مركزي واسع + رمز في الزاوية
                c.drawRoundRect(RectF(26f, 26f, 1174f, 504f), 28f, 28f, stroke(2.5f, white(44)))
                val g = stroke(6f, gold)
                c.drawLine(52f, 26f, 142f, 26f, g); c.drawLine(26f, 52f, 26f, 142f, g)
                c.drawLine(1148f, 504f, 1058f, 504f, g); c.drawLine(1174f, 478f, 1174f, 388f, g)
                hero(c, a, p, 190f, 330f, 250, false)
                val kh = kicker(c, iso(a.kicker), 1130f, 62f, p)
                titleBlock(c, title, 1130f, 62f + kh + 22f, 700f, 528f - 62f - kh - 90f, 70f, p)
            }
        }
        channelPlate(c, p)
    }

    private fun card(c: Canvas, a: Article, p: Plan) {
        background(c, p, 528f)
        val kh = kicker(c, iso(a.kicker), 1130f, 38f, p)
        val hp = paint(heavy, 46f).apply { setShadowLayer(16f, 0f, 3f, Color.argb(120, 0, 0, 0)) }
        textR(c, iso(a.infotitle), 1130f - 0f, 38f + kh + 46f, hp, true)
        val x0 = 56f; val x1 = 1144f; val y0 = 164f; val y1 = 510f; val gap = 20f
        val cw = (x1 - x0 - gap) / 2; val ch = (y1 - y0 - gap) / 2
        a.facts.take(4).forEachIndexed { i, f ->
            val col = i % 2; val row = i / 2
            val x = x1 - cw - col * (cw + gap); val y = y0 + row * (ch + gap)
            val r = RectF(x, y, x + cw, y + ch)
            c.drawRoundRect(r, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x, y, x + cw, y + ch, white(34), white(10), Shader.TileMode.CLAMP) })
            c.drawRoundRect(r, 28f, 28f, stroke(2f, white(40)))
            c.drawRoundRect(RectF(x + cw - 20f, y + 26f, x + cw - 12f, y + ch - 26f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y, 0f, y + ch, p.accent, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
            c.drawText("0${i + 1}".takeLast(2), x + 26f, y + 44f, paint(bold, 22f, white(70)))
            val v = iso(f.v); val arabic = v.any { it in '؀'..'ۿ' }
            var fs = if (v.length <= 4) 80f else if (v.length <= 6) 66f else if (v.length <= 9) 54f else 44f
            if (arabic && v.length > 3) fs = minOf(fs, 46f)
            textR(c, v, x + cw - 44f, y + 30f + fs * .86f, paint(heavy, fs, gold), arabic)
            val cp = paint(bold, 25f, Color.parseColor("#E9EEF5"))
            val ls = wrap(iso(f.c), cp, cw - 90f).take(2)
            ls.forEachIndexed { k, l -> textR(c, l, x + cw - 44f, y + ch - 22f - (ls.size - 1 - k) * 34f, cp) }
        }
        channelPlate(c, p)
    }

    private companion object {
        val STYLES = mapOf(
            "sport" to listOf("pitch", "rays", "rings"), "tech" to listOf("grid", "hex", "dots"), "news" to listOf("diag", "rays", "grid"),
            "business" to listOf("grid", "diag", "waves"), "health" to listOf("waves", "rings", "dots"), "food" to listOf("dots", "waves", "rays"),
            "travel" to listOf("waves", "rays", "rings"), "education" to listOf("dots", "hex", "rings"), "lifestyle" to listOf("waves", "dots", "rings"),
            "general" to listOf("rings", "diag", "dots"),
        )
    }
}
