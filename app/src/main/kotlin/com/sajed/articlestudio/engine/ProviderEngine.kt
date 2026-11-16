package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.ProviderSpec
import com.sajed.articlestudio.data.ProviderStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, message: String, val retry: Int = 0, val empty: Boolean = false) : Exception(message)
class ChatMessage(val role: String, val content: String)
class ChatOpts(
    val temp: Double = 0.7, val max: Int = 4000, val timeoutMs: Long = 150_000,
    val validate: ((String) -> Unit)? = null, val noWait: Boolean = false,
)
class ChatResult(val text: String, val provider: String, val model: String)
class ProviderStat(@Volatile var ok: Int = 0, @Volatile var fail: Int = 0, @Volatile var last: String = "", @Volatile var hard: Boolean = false)

/** ردّ المزود لم يجتز فحص المحتوى (JSON/جودة)؛ ليس عطلاً في المزود فلا يُحتسب إخفاقاً ظاهراً. */
class ValidationFail(message: String) : Exception(message)

/** رسالة عربية مفهومة بدل نص الخادم الخام. */
internal fun friendlyApi(status: Int?, raw: String?): String = when (status) {
    401, 403 -> "المفتاح مرفوض أو بلا صلاحية"
    402 -> "الحساب يحتاج وسيلة دفع عند المزود"
    404, 410 -> "النموذج لم يعد متاحاً، يُجرَّب غيره تلقائياً"
    400, 413 -> "طلب غير مقبول لهذا النموذج"
    429 -> "الحد مستهلك أو الخادم مزدحم مؤقتاً"
    in 500..599 -> "خطأ مؤقت من خادم المزود"
    else -> clamp(raw.orEmpty(), 70)
}

private val BAD = Regex("omni|\\bvl\\b|-vl|vision|lightning|thinking|guard|embed|image|tts|audio|live|ocr|moderation|robot|whisper|orpheus|playai|safeguard|lyria|veo|imagen|translate|rerank", RegexOption.IGNORE_CASE)
private val PREF3 = Regex("gpt-oss-120b|llama-3\\.3-70b|qwen3?.{0,8}(235|72|32|30)b|qwen-3-235|glm|kimi|minimax|longcat|command-a|mistral-(large|medium)", RegexOption.IGNORE_CASE)
private val PREF2 = Regex("gemma|mistral|nemotron|llama|qwen|gpt-oss|flash", RegexOption.IGNORE_CASE)
private fun prefScore(id: String) = when {
    Regex("deepseek", RegexOption.IGNORE_CASE).containsMatchIn(id) -> 4
    PREF3.containsMatchIn(id) -> 3
    PREF2.containsMatchIn(id) -> 2
    else -> 1
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    c.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { c.resume(response) { response.close() } }
    })
}

/** محرك المزودين: تبديل تلقائي، تبريد لكل نموذج ومزود، وذاكرة صحة دائمة. */
@Singleton
class ProviderEngine @Inject constructor(
    private val http: OkHttpClient, private val store: ProviderStore,
    private val health: HealthStore, private val bus: RunBus,
) {
    private val cd = ConcurrentHashMap<String, Long>()
    private val models = ConcurrentHashMap<String, Pair<Long, List<String>>>()
    private val stats = ConcurrentHashMap<String, ProviderStat>()
    private val json = Json { ignoreUnknownKeys = true }

    private fun now() = System.currentTimeMillis()
    private fun hk(p: String, m: String) = "$p|$m"
    private fun isCd(k: String) = (cd[k] ?: 0) > now() || health.blockedUntil(k) > now()
    private fun coolDown(k: String, ms: Long) { cd[k] = now() + ms }

    fun stat(id: String) = stats.getOrPut(id) { ProviderStat() }

    /** ثواني الراحة المتبقية للمزود (0 إن كان متاحاً). */
    fun coolSeconds(id: String): Int {
        val p = cd["p|$id"] ?: 0
        val ms = models[id]?.second.orEmpty()
        val allCool = ms.isNotEmpty() && ms.all { isCd(hk(id, it)) }
        val m = if (allCool) ms.minOf { maxOf(cd[hk(id, it)] ?: 0, health.blockedUntil(hk(id, it))) } else 0L
        val until = maxOf(p, m)
        return if (until > now()) ((until - now() + 999) / 1000).toInt() else 0
    }

    fun resetCooldown(id: String) { cd.remove("p|$id"); models.remove(id) }

    private suspend fun get(url: String, headers: Map<String, String>, ms: Long = 20_000): JsonElement {
        val rq = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        http.newBuilder().callTimeout(ms, TimeUnit.MILLISECONDS).build().newCall(rq).await().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            return json.parseToJsonElement(r.body?.string().orEmpty())
        }
    }

    private fun ids(j: JsonElement, name: Boolean = false) =
        (j.jsonObject["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            (e as? JsonObject)?.get(if (name) "name" else "id")?.jsonPrimitive?.contentOrNull
        }

    private fun good(a: List<String>) = a.filter { !BAD.containsMatchIn(it) }
    private fun by(a: List<String>) = a.sortedByDescending(::prefScore)

    suspend fun listModels(s: ProviderSpec): List<String> {
        models[s.id]?.let { if (it.first > now() - 3_600_000) return it.second }
        val key = store.key(s.id)
        val hd = if (key.isNotEmpty()) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val base = store.base(s.id)
        var v: List<String> = emptyList()
        try {
            v = when (s.id) {
                "custom" -> listOf(store.model("custom"))
                "openrouter" -> {
                    val j = get("$base/models", emptyMap())
                    val arr = (j.jsonObject["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    by(good(arr.filter { m ->
                        val id = m["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        id.endsWith(":free") && !BAD.containsMatchIn(m["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }.map { it["id"]!!.jsonPrimitive.content })).take(5)
                }
                "groq" -> by(good(ids(get("$base/models", hd)))).take(4)
                "cerebras" -> by(good(ids(get("$base/models", hd)))).take(3)
                "mistral" -> listOf("mistral-small-latest", "mistral-medium-latest", "open-mistral-nemo")
                "cohere" -> runCatching { good(ids(get("$base/models", hd))).filter { Regex("command-(a|r)").containsMatchIn(it) }.take(2) }
                    .getOrDefault(emptyList()).ifEmpty { listOf("command-a-03-2025", "command-r-plus-08-2024") }
                "gemini" -> runCatching {
                    val all = good(ids(get("$base/models", hd)).map { it.removePrefix("models/") }).filter { it.contains("flash") }
                    val stable = all.filter { !Regex("preview|exp").containsMatchIn(it) }
                    (stable.ifEmpty { all }).sortedWith(
                        compareByDescending<String> { if (Regex("2\\.5-flash$|flash-latest$").containsMatchIn(it)) 1 else 0 }.thenByDescending { it },
                    ).take(3)
                }.getOrDefault(emptyList()).ifEmpty { listOf("gemini-flash-latest", "gemini-2.5-flash") }
                "pollinations" -> runCatching {
                    val j = get("https://text.pollinations.ai/models", emptyMap())
                    by(good((j as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull })
                        .filter { Regex("openai|deepseek|mistral|llama|qwen|gemini|gpt|glm", RegexOption.IGNORE_CASE).containsMatchIn(it) && !Regex("search|roblox|unity|midijourney|evil|rtist|fast", RegexOption.IGNORE_CASE).containsMatchIn(it) }).take(3)
                }.getOrDefault(emptyList()).ifEmpty { listOf("openai", "mistral") }
                "sambanova" -> by(good(ids(get("$base/models", emptyMap())))).take(3)
                "nvidia" -> by(good(ids(get("$base/models", hd))).filter { !Regex("coder|code|safety|reward|parse|embed|mixtral|-\\d{1,2}b\\b|\\b\\d{1,2}b-|nano|bolt|guard|v0\\.", RegexOption.IGNORE_CASE).containsMatchIn(it) }).take(4)
                "zai" -> runCatching { good(ids(get("$base/models", hd))).filter { it.contains("flash", true) }.take(2) }.getOrDefault(emptyList())
                "longcat" -> runCatching { good(ids(get("$base/models", hd))).take(3) }.getOrDefault(emptyList()).ifEmpty { listOf("LongCat-Flash-Chat") }
                "llm7" -> by(good(ids(get("$base/models", emptyMap()))).filter { !Regex("code|pro|:").containsMatchIn(it) }).take(3)
                "zen" -> by(good(ids(get("$base/models", emptyMap()))).filter { it.endsWith("-free") }).take(3)
                else -> emptyList()
            }
        } catch (e: Exception) { if (e is CancellationException) throw e; v = emptyList() }
        if (v.isEmpty()) v = when (s.id) {
            "openrouter" -> listOf("deepseek/deepseek-chat-v3.1:free", "openai/gpt-oss-20b:free")
            "groq" -> listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile")
            "cerebras" -> listOf("gpt-oss-120b", "llama-3.3-70b")
            "sambanova" -> listOf("gpt-oss-120b", "Meta-Llama-3.3-70B-Instruct")
            "nvidia" -> listOf("mistralai/mistral-large", "nvidia/nemotron-3-super-120b-a12b")
            "zai" -> listOf("glm-4.7-flash", "glm-4.5-flash")
            "llm7" -> listOf("glm-5.3-flash", "deepseek-v4.1-flash")
            else -> emptyList()
        }
        models[s.id] = now() to v
        return v
    }

    private suspend fun callOne(s: ProviderSpec, model: String, messages: List<ChatMessage>, o: ChatOpts): String {
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray { messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) } })
            put("temperature", o.temp)
            put("max_tokens", o.max)
        }.toString()
        val key = store.key(s.id)
        val rq = Request.Builder().url(store.base(s.id).trimEnd('/') + "/chat/completions")
            .header("Content-Type", "application/json").apply {
                if (key.isNotEmpty()) header("Authorization", "Bearer $key") else if (s.id == "llm7") header("Authorization", "Bearer unused")
                if (s.id == "openrouter") header("X-Title", "Article Studio")
            }.post(body.toRequestBody("application/json".toMediaType())).build()
        http.newBuilder().callTimeout(o.timeoutMs, TimeUnit.MILLISECONDS).readTimeout(o.timeoutMs, TimeUnit.MILLISECONDS).build().newCall(rq).await().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code} ${txt.take(160)}", r.header("retry-after")?.toIntOrNull() ?: 0)
            val msg = runCatching { json.parseToJsonElement(txt).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"] }.getOrNull()
            val c = when (msg) {
                is JsonPrimitive -> msg.contentOrNull.orEmpty()
                is JsonArray -> msg.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty() }
                else -> ""
            }
            if (c.trim().length < 20) throw ApiException(0, "رد فارغ", empty = true)
            return c
        }
    }

    /** يعيد أول رد صالح؛ validate يرمي عند رد غير صالح فينتقل المحرك للتالي. */
    suspend fun chat(messages: List<ChatMessage>, o: ChatOpts = ChatOpts()): ChatResult {
        val tried = HashSet<String>()
        var lastErr = ""
        for (round in 0 until 3) {
            val act = store.readySpecs()
            if (act.isEmpty()) throw IllegalStateException("لا يوجد مزود مفعّل. أضف مفتاحاً أو فعّل Pollinations")
            var progressed = false
            for (p in act) {
                if (isCd("p|${p.id}")) continue
                val ms = try { listModels(p) } catch (e: Exception) { if (e is CancellationException) throw e; emptyList() }
                    .withIndex().sortedWith(compareByDescending<IndexedValue<String>> { health.score(hk(p.id, it.value)) }.thenBy { it.index }).map { it.value }
                for (m in ms) {
                    val k = hk(p.id, m)
                    if (isCd(k) || !tried.add("$k#$round")) continue
                    progressed = true
                    bus.setActive(p.id)
                    try {
                        bus.log("${p.name} ← ${m.substringAfterLast('/')}")
                        val text = callOne(p, m, messages, o)
                        try { o.validate?.invoke(text) } catch (e: CancellationException) { throw e } catch (e: Exception) { throw ValidationFail(e.message ?: "رد غير صالح") }
                        stat(p.id).ok++; stat(p.id).last = ""; health.reward(k)
                        bus.setActive(null)
                        return ChatResult(text, p.name, m)
                    } catch (e: Exception) {
                        bus.setActive(null)
                        if (e is CancellationException) throw e
                        if (e is ValidationFail) { lastErr = "رد غير صالح: " + clamp(e.message, 40); coolDown(k, 120_000); health.penal(k, now()); bus.log("${p.name}: رد غير صالح، التالي", "w"); continue }
                        val st = stat(p.id); st.fail++
                        val status = (e as? ApiException)?.status ?: -2
                        val msg = if (e is InterruptedIOException) "انتهت المهلة" else friendlyApi((e as? ApiException)?.status, e.message ?: e.javaClass.simpleName)
                        st.last = msg; st.hard = status == 401 || status == 403; lastErr = msg
                        when {
                            status == 401 || status == 403 -> { coolDown("p|${p.id}", 3_600_000); bus.log("${p.name}: المفتاح مرفوض", "e"); break }
                            status == 429 || status == 402 -> { coolDown(k, ((e as ApiException).retry * 1000L).coerceIn(60_000, 600_000)); bus.log("${p.name}: الحد مستهلك، التالي", "w") }
                            status == 404 || status == 400 || status == 413 -> { coolDown(k, 3_600_000); health.penal(k, now()); bus.log("${p.name}/${m.substringAfterLast('/')}: $status", "w") }
                            status >= 500 -> { coolDown(k, 120_000); bus.log("${p.name}: خطأ خادم $status", "w") }
                            e is InterruptedIOException -> { coolDown(k, 120_000); health.penal(k, now()); bus.log("${p.name}: انتهت المهلة", "w") }
                            e is IOException -> { coolDown("p|${p.id}", 300_000); bus.log("${p.name}: تعذّر الاتصال", "e"); break }
                            else -> { coolDown(k, if ((e as? ApiException)?.empty == true) 900_000 else 120_000); health.penal(k, now()); bus.log("${p.name}: ${clamp(msg, 60)}", "w") }
                        }
                    }
                }
            }
            if (!progressed) {
                val fut = store.readySpecs().flatMap { p -> listOfNotNull(cd["p|${p.id}"]) + models[p.id]?.second.orEmpty().mapNotNull { cd[hk(p.id, it)] } }.filter { it > now() }
                val w = fut.minOrNull()
                val ms = if (w != null) (w - now()).coerceIn(3_000, 90_000) else 20_000
                bus.log("كل المزودين في استراحة، انتظار ${ms / 1000} ثانية", "w")
                if (o.noWait) break
                delay(ms)
            }
        }
        throw IllegalStateException("فشل كل المزودين: " + clamp(lastErr, 80))
    }

    /** اختبار مزود واحد: يعيد وصف النتيجة. */
    suspend fun test(s: ProviderSpec): String {
        if (!store.ready(s)) return "أدخل المفتاح أولاً"
        val t0 = now()
        return try {
            resetCooldown(s.id)
            val ms = listModels(s)
            if (ms.isEmpty()) return "لا نماذج متاحة"
            var used = ms[0]; var err: Exception? = null
            for (m in ms.take(3)) {
                try { callOne(s, m, listOf(ChatMessage("user", "اكتب جملة عربية واحدة عن كرة القدم.")), ChatOpts(max = 80, timeoutMs = 40_000)); used = m; err = null; break }
                catch (e: Exception) { if (e is CancellationException) throw e; err = e; if ((e as? ApiException)?.status in listOf(401, 403)) break }
            }
            err?.let { throw it }
            stat(s.id).last = ""; stat(s.id).hard = false
            "يعمل (${used.substringAfterLast('/')}) ${"%.1f".format((now() - t0) / 1000.0)}ث"
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val st = (e as? ApiException)?.status
            val m = if (e is InterruptedIOException) "انتهت المهلة" else friendlyApi(st, e.message)
            stat(s.id).last = m; stat(s.id).hard = st == 401 || st == 403
            m
        }
    }
}
