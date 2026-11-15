package com.sajed.articlestudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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

    fun render(a: Article): RenderedImages {
        val bmp = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        cover(c, a)
        val cover = jpeg(bmp)
        card(c, a)
        val info = jpeg(bmp)
        bmp.recycle()
        return RenderedImages(cover, info)
    }

    fun coverBitmap(a: Article): Bitmap = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888).also { cover(Canvas(it), a) }
    fun cardBitmap(a: Article): Bitmap = Bitmap.createBitmap(1200, 630, Bitmap.Config.ARGB_8888).also { card(Canvas(it), a) }

    private fun jpeg(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 84, it) }.toByteArray()

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

    private fun bgBase(c: Canvas, h: Float) {
        c.drawRect(0f, 0f, 1200f, 630f, Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 630f, intArrayOf(hsl(h, .50f, .22f), hsl(h + 24, .46f, .12f), hsl(h + 40, .42f, .06f)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP) })
        // خطوط ملعب خافتة: خط المنتصف والدائرة ومنطقة الجزاء
        val pl = stroke(3f, Color.argb(20, 255, 255, 255))
        c.drawCircle(600f, 315f, 120f, pl); c.drawLine(600f, 0f, 600f, 630f, pl)
        c.drawRect(1040f, 175f, 1200f, 455f, pl); c.drawRect(0f, 175f, 160f, 455f, pl)
        // أشعة كشّافات ملعب
        for ((x0, x1) in listOf(980f to 1200f, 760f to 940f)) {
            val beam = android.graphics.Path().apply { moveTo(x0, 0f); lineTo(x1, 0f); lineTo(x1 - 520f, 630f); lineTo(x0 - 700f, 630f); close() }
            c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x0 - 400f, 630f, Color.argb(24, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        }
        // تظليل الحواف
        c.drawRect(0f, 0f, 1200f, 630f, Paint().apply { shader = RadialGradient(600f, 315f, 760f, Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP) })
        // إطار داخلي وزوايا ذهبية
        c.drawRoundRect(RectF(22f, 22f, 1178f, 608f), 26f, 26f, stroke(2f, Color.argb(34, 255, 255, 255)))
        val g = stroke(5f, gold)
        c.drawLine(44f, 22f, 110f, 22f, g); c.drawLine(22f, 44f, 22f, 110f, g)
        c.drawLine(1156f, 608f, 1090f, 608f, g); c.drawLine(1178f, 586f, 1178f, 520f, g)
        c.drawRect(1186f, 0f, 1200f, 630f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 630f, gold, goldB, Shader.TileMode.CLAMP) })
        c.drawRect(0f, 622f, 1186f, 630f, Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 0f, Color.TRANSPARENT, goldB, Shader.TileMode.CLAMP) })
    }

    private fun brandMark(c: Canvas, y: Float) {
        val p = paint(heavy, 30f)
        textR(c, BrandConfig.BLOG_NAME, 1130f, y, p)
        val w = width(BrandConfig.BLOG_NAME, p)
        c.drawCircle(1130f - w - 32, y - 11, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF3B30"); setShadowLayer(14f, 0f, 0f, Color.argb(90, 255, 59, 48)) })
        c.drawText(BrandConfig.BLOG_SITE.removePrefix("https://"), 70f, y, paint(bold, 24f, Color.argb(170, 255, 255, 255)))
    }

    private fun pill(c: Canvas, text: String, rightX: Float, y: Float, size: Float): Float {
        val p = paint(bold, size, gold)
        val w = width(text, p) + 52; val h = size + 24; val x = rightX - w
        val r = RectF(x, y, x + w, y + h)
        c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(13, 255, 255, 255) })
        c.drawRoundRect(r, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = gold })
        val fm = p.fontMetrics
        textR(c, text, rightX - 26, y + h / 2 - (fm.ascent + fm.descent) / 2, p)
        return h
    }

    private fun cover(c: Canvas, a: Article) {
        bgBase(c, a.hue.toFloat())
        c.drawCircle(110f, 315f, 320f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.argb(18, 255, 255, 255) })
        c.drawRect(0f, 60f, 600f, 620f, Paint().apply { shader = RadialGradient(290f, 340f, 270f, hsl(a.hue.toFloat(), .70f, .55f, .42f), Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        val motif = SvgDraw.render(data.motifs[a.motif] ?: data.motifs.getValue("ball"), 470, heavy)
        c.drawBitmap(motif, 55f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(34f, 0f, 22f, Color.argb(115, 0, 0, 0)) })
        motif.recycle()
        c.drawCircle(290f, 315f, 250f, stroke(3f, Color.argb(120, 245, 165, 36)))
        c.drawCircle(290f, 315f, 282f, stroke(2f, Color.argb(48, 245, 165, 36)))
        val ph = pill(c, iso(a.kicker), 1116f, 76f, 26f)
        c.drawRoundRect(RectF(1116f - 130f, 76f + ph + 22f, 1116f, 76f + ph + 30f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(986f, 0f, 1116f, 0f, goldB, gold, Shader.TileMode.CLAMP) })
        val title = iso(a.title)
        var fs = if (title.length <= 30) 74f else if (title.length <= 44) 62f else 54f
        var lines: List<String>
        while (true) {
            lines = wrap(title, paint(heavy, fs), 620f)
            if (lines.size * fs * 1.38f <= 330f || fs <= 36f) break
            fs -= 4f
        }
        val tp = paint(heavy, fs).apply { setShadowLayer(22f, 0f, 4f, Color.argb(115, 0, 0, 0)) }
        lines.forEachIndexed { i, l -> textR(c, l, 1116f, 76 + ph + 58 + fs * (1.1f + i * 1.38f), tp) }
        brandMark(c, 586f)
    }

    private fun card(c: Canvas, a: Article) {
        bgBase(c, a.hue.toFloat())
        textR(c, iso(a.infotitle), 1124f, 98f, paint(heavy, 44f))
        val kp = paint(bold, 24f, gold)
        val kw = width(a.kicker, kp) + 44
        c.drawRoundRect(RectF(62f, 60f, 62 + kw, 108f), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = gold })
        textR(c, iso(a.kicker), 62 + kw / 2 + width(iso(a.kicker), kp) / 2, 93f, kp)
        val x0 = 62f; val x1 = 1124f; val y0 = 134f; val y1 = 518f; val gap = 22f
        val cw = (x1 - x0 - gap) / 2; val ch = (y1 - y0 - gap) / 2
        a.facts.take(4).forEachIndexed { i, f ->
            val col = i % 2; val row = i / 2
            val x = x1 - cw - col * (cw + gap); val y = y0 + row * (ch + gap)
            val r = RectF(x, y, x + cw, y + ch)
            c.drawRoundRect(r, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x, y, x + cw, y + ch, Color.argb(28, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP) })
            c.drawRoundRect(r, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(36, 255, 255, 255) })
            c.drawRoundRect(RectF(x + cw - 96f, y, x + cw - 30f, y + 5f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
            val v = iso(f.v); val arabic = v.any { it in '؀'..'ۿ' }
            var fs = if (v.length <= 4) 84f else if (v.length <= 6) 70f else if (v.length <= 9) 56f else 46f
            if (arabic && v.length > 3) fs = minOf(fs, 48f)
            textR(c, v, x + cw - 30, y + 22 + fs * .86f, paint(heavy, fs, gold), arabic)
            val cp = paint(bold, 26f, Color.parseColor("#E9EEF5"))
            val ls = wrap(iso(f.c), cp, cw - 60).take(2)
            ls.forEachIndexed { k, l -> textR(c, l, x + cw - 30, y + ch - 24 - (ls.size - 1 - k) * 36, cp) }
        }
        brandMark(c, 590f)
    }
}
