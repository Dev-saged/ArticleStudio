package com.sajed.articlestudio.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sajed.articlestudio.engine.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** مصدر صورة المقال للمنصات التي لا تقبل نصاً وحده (إنستغرام). */
interface MediaSource {
    /** يعيد ملف صورة محلياً للمقال (يُنزَّل مرة واحدة ويُعاد استخدامه) أو null. */
    suspend fun image(articleId: Long, pageUrl: String, html: String): File?
    /** يحذف الصور الأقدم من 3 أيام. */
    fun clean(now: Long)
}

object MediaRules {
    const val KEEP_MS = 3 * 24 * 3_600_000L
    private val COVER = Regex("""(?:src|content)\s*=\s*["']([^"']+-cover\.jpe?g)["']""", RegexOption.IGNORE_CASE)
    private val OG = Regex("""<meta[^>]+property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']+)["']|<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]*property\s*=\s*["']og:image["']""", RegexOption.IGNORE_CASE)

    /** رابط الغلاف من HTML المقال المخزّن (…-cover.jpg). */
    fun coverFromHtml(html: String): String? = COVER.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }

    /** رابط og:image من صفحة المقال. */
    fun ogImage(page: String): String? = OG.find(page)?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] } }?.replace("&amp;", "&")?.takeIf { it.startsWith("http") }

    fun isStale(lastModified: Long, now: Long) = now - lastModified > KEEP_MS
}

@Singleton
class ShareMedia @Inject constructor(@ApplicationContext private val ctx: Context, private val http: OkHttpClient) : MediaSource {
    private val dir get() = File(ctx.filesDir, "share-media").also { it.mkdirs() }

    private suspend fun get(url: String): ByteArray? = runCatching {
        http.newCall(Request.Builder().url(url).build()).await().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }.getOrNull()

    override suspend fun image(articleId: Long, pageUrl: String, html: String): File? = withContext(Dispatchers.IO) {
        val f = File(dir, "$articleId.jpg")
        if (f.exists() && f.length() > 1_000) return@withContext f
        val src = MediaRules.coverFromHtml(html)
            ?: pageUrl.takeIf { it.startsWith("http") }?.let { get(it) }?.let { MediaRules.ogImage(String(it, Charsets.UTF_8)) }
            ?: return@withContext null
        val raw = get(src) ?: return@withContext null
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
        val scaled = if (bmp.width > 1080) Bitmap.createScaledBitmap(bmp, 1080, (bmp.height * 1080f / bmp.width).toInt().coerceAtLeast(1), true) else bmp
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (scaled !== bmp) scaled.recycle(); bmp.recycle()
        f.takeIf { it.length() > 1_000 }
    }

    override fun clean(now: Long) {
        dir.listFiles()?.forEach { if (MediaRules.isStale(it.lastModified(), now)) it.delete() }
    }
}
