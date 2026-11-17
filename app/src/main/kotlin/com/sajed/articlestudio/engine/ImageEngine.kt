package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.ProviderStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ImgReq(val prompt: String, val query: String, val seed: Int)
class ImgHit(val source: String, val bytes: ByteArray)
class ImgStat(val ok: Int, val fail: Int, val last: String, val hard: Boolean)

private class ImgFail(val code: Int, msg: String) : Exception(msg)

/** سلسلة مصادر الصور: تُجرَّب بالترتيب، وكل مصدر متعثّر يُجمَّد مؤقتاً فتنتقل السلسلة للتالي دون توقف. */
@Singleton
class ImageEngine @Inject constructor(private val http: OkHttpClient, private val store: ProviderStore) {
    internal val bases = mutableMapOf(
        "gemini" to "https://generativelanguage.googleapis.com/v1beta/models/",
        "together" to "https://api.together.xyz/v1/images/generations",
        "img-cloudflare" to "https://api.cloudflare.com/client/v4/accounts/",
        "img-pollinations" to "https://image.pollinations.ai/prompt/",
        "img-pexels" to "https://api.pexels.com/v1/search",
        "img-pixabay" to "https://pixabay.com/api/",
    )
    private val client by lazy { http.newBuilder().callTimeout(75, TimeUnit.SECONDS).readTimeout(70, TimeUnit.SECONDS).build() }
    private val cd = ConcurrentHashMap<String, Long>()
    private val ok = ConcurrentHashMap<String, Int>()
    private val bad = ConcurrentHashMap<String, Int>()
    private val last = ConcurrentHashMap<String, String>()
    private val hard = ConcurrentHashMap<String, Boolean>()
    internal var now: () -> Long = System::currentTimeMillis

    /** الترتيب: الأعلى جودة أولاً، ثم الصور الفوتوغرافية الجاهزة كملاذ أخير قبل رسم SVG. */
    val order = listOf("gemini", "together", "img-cloudflare", "img-pollinations", "img-pexels", "img-pixabay")

    fun ready(id: String): Boolean = when (id) {
        "gemini", "together" -> store.key(id).isNotEmpty()
        "img-pollinations" -> store.enabled(id)
        else -> store.enabled(id) && store.key(id).isNotEmpty()
    }

    fun active() = order.filter(::ready)
    fun stat(id: String) = ImgStat(ok[id] ?: 0, bad[id] ?: 0, last[id].orEmpty(), hard[id] == true)
    fun resetCooldown(id: String) { cd.remove(id); hard.remove(id) }
    private fun cooled(id: String) = (cd[id] ?: 0) > now()

    /** يجرّب المصادر؛ accept يتحقق من الصورة فيُعدّ الرفض إخفاقاً للمصدر وينتقل للتالي. */
    suspend fun generate(r: ImgReq, note: (String) -> Unit = {}, accept: (ByteArray) -> Boolean): ImgHit? {
        val act = active()
        val fresh = act.filterNot(::cooled)
        for (id in fresh.ifEmpty { act.filter { it == "img-pollinations" } }) {
            note(label(id))
            val b = try { run(id, r) } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(id, e); null }
            if (b == null) continue
            if (!accept(b)) { fail(id, ImgFail(0, "صورة غير صالحة")); continue }
            ok.merge(id, 1, Int::plus); last[id] = ""; hard.remove(id); cd.remove(id)
            return ImgHit(id, b)
        }
        return null
    }

    suspend fun test(id: String): String {
        if (!ready(id)) return "غير جاهز: فعّل المصدر وأدخل المفتاح."
        val hit = try { generate1(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(id, e); return last[id].orEmpty() }
        ok.merge(id, 1, Int::plus); last[id] = ""; hard.remove(id); cd.remove(id)
        return "يعمل (${hit / 1024}KB)"
    }

    private fun generate1(id: String): Int {
        val b = run(id, ImgReq("a calm mountain lake at sunrise, photorealistic, no text", "mountain lake sunrise", 7)) ?: throw ImgFail(0, "لم تصل صورة")
        if (b.size < 5_000) throw ImgFail(0, "صورة فارغة")
        return b.size
    }

    private fun fail(id: String, e: Exception) {
        bad.merge(id, 1, Int::plus)
        val code = (e as? ImgFail)?.code ?: 0
        val msg = when (code) {
            401, 403 -> "المفتاح مرفوض أو لا يملك صلاحية الصور."
            402 -> "انتهى رصيد المصدر."
            429 -> "تجاوز الحد المسموح مؤقتاً."
            404 -> "الخدمة أو النموذج غير متاح."
            in 500..599 -> "الخادم مشغول."
            else -> e.message?.take(80) ?: "تعذّر الاتصال."
        }
        last[id] = msg
        hard[id] = code == 401 || code == 403
        cd[id] = now() + when (code) { 401, 403, 402 -> 3_600_000L; 429 -> 600_000L; else -> 120_000L }
    }

    private fun label(id: String) = when (id) {
        "gemini" -> "صورة من Gemini"; "together" -> "صورة من Together"; "img-cloudflare" -> "صورة من Cloudflare"
        "img-pollinations" -> "صورة من Pollinations"; "img-pexels" -> "صورة من Pexels"; else -> "صورة من Pixabay"
    }

    private fun run(id: String, r: ImgReq): ByteArray? = when (id) {
        "gemini" -> gemini(r.prompt, store.key("gemini"))
        "together" -> together(r.prompt, store.key("together"))
        "img-cloudflare" -> cloudflare(r.prompt, store.key(id))
        "img-pollinations" -> pollinations(r)
        "img-pexels" -> pexels(r, store.key(id))
        else -> pixabay(r, store.key(id))
    }

    private fun exec(rq: Request): String = client.newCall(rq).execute().use { r ->
        if (!r.isSuccessful) throw ImgFail(r.code, "HTTP ${r.code}")
        r.body?.string() ?: throw ImgFail(0, "رد فارغ")
    }

    private fun bytes(url: String, hd: Map<String, String> = emptyMap()): ByteArray = client.newCall(Request.Builder().url(url).apply { hd.forEach { (k, v) -> header(k, v) } }.build()).execute().use { r ->
        if (!r.isSuccessful) throw ImgFail(r.code, "HTTP ${r.code}")
        r.body?.bytes() ?: throw ImgFail(0, "رد فارغ")
    }

    private fun json(s: String) = runCatching { AppJson.parseToJsonElement(s) }.getOrNull() ?: throw ImgFail(0, "رد غير مفهوم")
    private fun b64(s: String?) = runCatching { Base64.getMimeDecoder().decode(s ?: error("")) }.getOrNull() ?: throw ImgFail(0, "صورة تالفة")
    private fun jsonBody(o: JsonObject) = o.toString().toRequestBody("application/json".toMediaType())

    private fun pollinations(r: ImgReq): ByteArray {
        var err: Exception? = null
        for ((model, sd) in listOf("flux" to r.seed, "flux" to r.seed + 17, "turbo" to r.seed)) {
            try {
                return bytes(bases.getValue("img-pollinations") + URLEncoder.encode(r.prompt, "UTF-8").replace("+", "%20") + "?width=1344&height=768&model=$model&nologo=true&seed=$sd")
            } catch (e: ImgFail) { err = e; if (e.code == 401 || e.code == 403) break } catch (e: java.io.IOException) { err = e }
        }
        throw err ?: ImgFail(0, "تعذّر الاتصال")
    }

    private fun gemini(prompt: String, key: String): ByteArray {
        var err: Exception = ImgFail(0, "لا نموذج صور متاح")
        for (model in listOf("gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation")) {
            val body = buildJsonObject {
                put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", "Generate an image: $prompt") }) }) }) })
                put("generationConfig", buildJsonObject { put("responseModalities", buildJsonArray { add(JsonPrimitive("IMAGE")); add(JsonPrimitive("TEXT")) }) })
            }
            try {
                val txt = exec(Request.Builder().url("${bases.getValue("gemini")}$model:generateContent").header("x-goog-api-key", key).post(jsonBody(body)).build())
                val parts = json(txt).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: throw ImgFail(0, "لا صورة في الرد")
                for (p in parts) {
                    val d = ((p as? JsonObject)?.get("inlineData") as? JsonObject)?.get("data")?.jsonPrimitive?.contentOrNull ?: continue
                    return b64(d)
                }
                err = ImgFail(0, "لا صورة في الرد")
            } catch (e: ImgFail) { err = e; if (e.code == 401 || e.code == 403 || e.code == 429) break }
        }
        throw err
    }

    private fun together(prompt: String, key: String): ByteArray {
        val body = buildJsonObject {
            put("model", "black-forest-labs/FLUX.1-schnell-Free"); put("prompt", prompt)
            put("width", 1344); put("height", 768); put("steps", 4); put("n", 1); put("response_format", "b64_json")
        }
        val txt = exec(Request.Builder().url(bases.getValue("together")).header("Authorization", "Bearer $key").post(jsonBody(body)).build())
        val d = json(txt).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
        d?.get("b64_json")?.jsonPrimitive?.contentOrNull?.let { return b64(it) }
        d?.get("url")?.jsonPrimitive?.contentOrNull?.let { return bytes(it) }
        throw ImgFail(0, "لا صورة في الرد")
    }

    /** المفتاح بصيغة ACCOUNT_ID:API_TOKEN. */
    private fun cloudflare(prompt: String, key: String): ByteArray {
        val acc = key.substringBefore(':', "").trim(); val tok = key.substringAfter(':', "").trim()
        if (!Regex("[0-9a-fA-F]{32}").matches(acc) || tok.isEmpty()) throw ImgFail(0, "الصيغة: ACCOUNT_ID:API_TOKEN")
        val body = buildJsonObject { put("prompt", prompt); put("steps", 6) }
        val txt = exec(Request.Builder().url("${bases.getValue("img-cloudflare")}$acc/ai/run/@cf/black-forest-labs/flux-1-schnell").header("Authorization", "Bearer $tok").post(jsonBody(body)).build())
        val img = json(txt).jsonObject["result"]?.jsonObject?.get("image")?.jsonPrimitive?.contentOrNull
        return b64(img)
    }

    private fun pexels(r: ImgReq, key: String): ByteArray {
        val url = bases.getValue("img-pexels") + "?query=" + URLEncoder.encode(r.query, "UTF-8") + "&orientation=landscape&size=large&per_page=12"
        val photos = json(exec(Request.Builder().url(url).header("Authorization", key).build())).jsonObject["photos"] as? JsonArray ?: throw ImgFail(0, "لا نتائج")
        if (photos.isEmpty()) throw ImgFail(0, "لا نتائج للبحث")
        val src = photos[r.seed % photos.size].jsonObject["src"]?.jsonObject
        val u = src?.get("large2x")?.jsonPrimitive?.contentOrNull ?: src?.get("large")?.jsonPrimitive?.contentOrNull ?: throw ImgFail(0, "رد غير مفهوم")
        return bytes(u)
    }

    private fun pixabay(r: ImgReq, key: String): ByteArray {
        val url = bases.getValue("img-pixabay") + "?key=" + URLEncoder.encode(key, "UTF-8") + "&q=" + URLEncoder.encode(r.query, "UTF-8") + "&image_type=photo&orientation=horizontal&min_width=1280&safesearch=true&per_page=15"
        val hits = json(exec(Request.Builder().url(url).build())).jsonObject["hits"] as? JsonArray ?: throw ImgFail(0, "لا نتائج")
        if (hits.isEmpty()) throw ImgFail(0, "لا نتائج للبحث")
        val u = hits[r.seed % hits.size].jsonObject["largeImageURL"]?.jsonPrimitive?.contentOrNull ?: throw ImgFail(0, "رد غير مفهوم")
        return bytes(u)
    }
}
