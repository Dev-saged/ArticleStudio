package com.sajed.articlestudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** صياغة موجّه الصورة: مشهد واقعي عام مشتق من المقال، بلا أشخاص حقيقيين ولا نصوص. */
object PhotoPrompt {
    const val STYLE = "editorial photograph, photorealistic, natural cinematic lighting, shallow depth of field, rich detail, premium magazine quality, 16:9, no text, no letters, no watermark, no logo"

    private val SCENES = mapOf(
        "sport" to "a dramatic football stadium at night under bright floodlights, green pitch, blurred crowd, a football on the grass",
        "tech" to "a sleek modern workspace with glowing screens, circuit-like light trails, a smartphone and laptop, cool teal lighting",
        "health" to "fresh healthy lifestyle scene, bright natural morning light, fruits, vegetables and running shoes on a wooden surface",
        "food" to "an elegant plated dish on a rustic table, steam rising, warm natural window light, fresh ingredients around",
        "business" to "a modern city skyline at dusk with glass towers, a confident professional workspace, subtle golden light",
        "travel" to "a breathtaking travel landscape at golden hour, old town streets or coastline, a traveler's suitcase and passport",
        "education" to "a bright study desk with open books, notebook, lamp and a window, calm focused atmosphere",
        "news" to "a dynamic newsroom with city lights through the window, blurred screens and papers, serious tone",
        "lifestyle" to "a cozy modern home interior with plants, soft daylight, warm tones and a coffee cup",
    )

    fun scene(site: SiteConfig?, a: Article) = SCENES[site?.niche ?: "sport"] ?: "a clean, bright editorial scene related to ${a.kicker}"

    /** موجّه احتياطي بلا نموذج: مشهد المجال + كلمات المقال التصنيفية. */
    fun fallback(site: SiteConfig?, a: Article) = scene(site, a) + (if (a.labels.isNotEmpty()) ", theme: " + a.labels.first() else "") + ", " + STYLE

    val SYSTEM = "You write ONE English image prompt (35-60 words) for a photorealistic editorial cover photograph that visually fits an Arabic article. " +
        "Describe a concrete scene: subject, setting, lighting, camera. Rules: no real or named people, no celebrities, no identifiable faces (use silhouettes, backs, hands or empty scenes); " +
        "no text, letters, numbers, flags, logos or brands in the image; no violence. Reply with the prompt only."
}

/** يجلب صورة بارزة عبر سلسلة مصادر الصور مع تبديل تلقائي؛ يعيد null عند فشلها كلها فيعمل رسم SVG الاحتياطي. */
@Singleton
class PhotoService @Inject constructor(
    @ApplicationContext private val ctx: Context, private val images: ImageEngine, private val engine: ProviderEngine,
) {
    internal var promptWriter: Boolean = true
    private val dir by lazy { File(ctx.cacheDir, "photos").apply { mkdirs() } }

    suspend fun prompt(site: SiteConfig?, a: Article): String {
        if (!promptWriter) return PhotoPrompt.fallback(site, a)
        return try {
            val r = engine.chat(
                listOf(ChatMessage("system", PhotoPrompt.SYSTEM), ChatMessage("user", "العنوان: ${a.title}\nالوصف: ${a.desc}\nالمجال: ${site?.topicLabel.orEmpty()}")),
                ChatOpts(temp = 0.6, max = 220, timeoutMs = 40_000, noWait = true, validate = { if (it.trim().length !in 30..600) throw IllegalStateException("موجّه غير صالح") }),
            )
            r.text.trim().trim('"').replace(Regex("\\s+"), " ") + ", " + PhotoPrompt.STYLE
        } catch (e: CancellationException) { throw e } catch (e: Exception) { PhotoPrompt.fallback(site, a) }
    }

    suspend fun fetch(site: SiteConfig?, a: Article, note: (String) -> Unit = {}): Bitmap? = withContext(Dispatchers.IO) {
        val p = prompt(site, a)
        val cache = File(dir, sha(p) + ".jpg")
        if (cache.exists()) decode(cache.readBytes())?.let { return@withContext it }
        val seed = (a.slug + a.title).hashCode().let { Math.abs(it % 100000) }
        var bmp: Bitmap? = null
        val hit = images.generate(ImgReq(p, p.substringBefore(',').split(' ').take(7).joinToString(" "), seed), note) { b -> decode(b)?.also { bmp = it } != null }
        if (hit != null) runCatching { cache.writeBytes(hit.bytes) }
        bmp
    }

    /** يقبل الصورة إن فُكّت وكانت كبيرة بما يكفي وليست شبه فارغة. */
    internal fun decode(b: ByteArray): Bitmap? {
        if (b.size < 15_000) return null
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return null
        if (bmp.width < 640 || bmp.height < 360) { bmp.recycle(); return null }
        var sum = 0.0; var sq = 0.0; val n = 400
        for (i in 0 until n) {
            val c = bmp.getPixel((i * 37) % bmp.width, (i * 53) % bmp.height)
            val l = Color.red(c) * .3 + Color.green(c) * .59 + Color.blue(c) * .11
            sum += l; sq += l * l
        }
        val sd = Math.sqrt(sq / n - (sum / n) * (sum / n))
        if (sd < 10) { bmp.recycle(); return null }
        return bmp
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
}
