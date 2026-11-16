package com.sajed.articlestudio.engine

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderTest {
    @Test fun coverAndCardRenderToNonBlankImages() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val data = StudioData(ctx)
        val r = ImageRenderer(ctx, data)
        val out = File(System.getProperty("render.out") ?: "build/render").apply { mkdirs() }
        var i = 0
        for (m in data.motifs.keys.take(12)) {
            val a = sampleArticle(motif = m, title = "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم 2022 أمام فرنسا") .copy(hue = i * 30)
            val cover = r.coverBitmap(a)
            assertTrue(cover.width == 1200 && cover.height == 630)
            val px = IntArray(40) { cover.getPixel(30 * it + 5, 315) }
            assertTrue("غلاف فارغ للرمز $m", px.toSet().size > 3)
            if (i < 2) File(out, "cover-$m.png").outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i == 0) File(out, "card.png").outputStream().use { r.cardBitmap(a).compress(Bitmap.CompressFormat.PNG, 100, it) }
            i++
        }
        val j = r.render(sampleArticle())
        assertTrue(j.cover.size > 5_000 && j.info.size > 5_000)
    }

    @Test fun reviewSheetVariety() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val data = StudioData(ctx)
        val r = ImageRenderer(ctx, data)
        val out = File(System.getProperty("render.out") ?: "build/render").apply { mkdirs() }
        val titles = listOf("كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم", "أفضل هواتف 2026 للمصورين المحترفين", "دليل الاستثمار للمبتدئين خطوة بخطوة", "وصفة المنسف الأردني الأصيل", "٧ وجهات سياحية رخيصة لصيف هذا العام", "كيف تحافظ على صحة قلبك بعد الأربعين")
        val niches = listOf("sport", "tech", "business", "food", "travel", "health")
        val sums = HashSet<Int>()
        niches.forEachIndexed { i, n ->
            val site = SiteConfig(id = "t$i", name = "سبورت لايف 24", url = "https://sport1live24.blogspot.com", niche = n)
            val m = Niches.of(n).motifs.first()
            val a = sampleArticle(slug = "s$i", title = titles[i], motif = m).copy(hue = Niches.of(n).hue + i * 7)
            val cv = r.coverBitmap(a, site)
            sums += (0 until 60).map { cv.getPixel(it * 19, 40 + it * 7) }.hashCode()
            File(out, "rev-cover-$n.png").outputStream().use { cv.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i < 2) File(out, "rev-card-$n.png").outputStream().use { r.cardBitmap(a, site).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(sums.size == niches.size)
    }
}
