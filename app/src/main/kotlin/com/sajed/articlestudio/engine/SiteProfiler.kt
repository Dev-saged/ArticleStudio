package com.sajed.articlestudio.engine

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** ما استُخلص من المدونة: عنوانها ووصفها وتصنيفاتها وعناوين آخر مقالاتها. */
class BlogSnapshot(val title: String, val description: String, val author: String, val categories: List<String>, val titles: List<String>)

/** هوية موقع مقترحة تُدمج في إعداداته بنقرة واحدة. */
class SiteProfile(
    val name: String, val niche: String, val topic: String, val author: String, val audience: String, val tone: String, val length: String,
    val keywords: List<String>, val labels: List<String>, val avoid: String, val rules: String, val usedAi: Boolean, val posts: Int,
) {
    fun applyTo(s: SiteConfig, keepName: Boolean = true): SiteConfig = s.copy(
        name = if (keepName && s.name.isNotBlank()) s.name else name, niche = niche, nicheLabel = if (topic == Niches.of(niche).label) "" else topic,
        author = s.author.ifBlank { author }, audience = audience, tone = tone, length = length, keywords = keywords, labels = labels,
        avoid = avoid, rules = rules,
    )
}

/** يقرأ المدونة (تغذية Blogger أو وسوم الصفحة) ويستنتج منها المجال والجمهور والنبرة والكلمات والتصنيفات. */
@Singleton
class SiteProfiler @Inject constructor(private val http: OkHttpClient, private val engine: ProviderEngine) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy { http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    suspend fun analyze(url: String): SiteProfile {
        val snap = fetch(url.trimEnd('/')) ?: throw IllegalStateException("تعذّر قراءة الموقع. تأكد من الرابط وأن المدونة عامة")
        val base = ProfileLogic.heuristic(snap)
        val ai = try { ask(snap) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        return if (ai == null) base else ProfileLogic.merge(base, ai)
    }

    private suspend fun get(u: String): String? = try {
        client.newCall(Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()).await().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    private suspend fun fetch(base: String): BlogSnapshot? {
        get("$base/feeds/posts/default?alt=json&max-results=40")?.let { t -> ProfileLogic.parseFeed(t)?.let { return it } }
        return get(base)?.let { ProfileLogic.parseHtml(it) }
    }

    private suspend fun ask(s: BlogSnapshot): SiteProfile? {
        val ids = Niches.all.joinToString("|") { it.id }
        val sys = "أنت محلل محتوى. أعد كائن JSON واحداً فقط بلا شرح ولا Markdown."
        val usr = "حلّل هذه المدونة واستنتج هويتها.\nالعنوان: ${s.title}\nالوصف: ${s.description}\nالتصنيفات: ${s.categories.take(15).joinToString("، ")}\nآخر المقالات:\n" +
            s.titles.take(25).joinToString("\n") { "- $it" } + "\n\nالمفاتيح المطلوبة: niche (واحد من: $ids)، topic (وصف المجال بالعربية في 2-5 كلمات)، audience (الجمهور المستهدف في جملة قصيرة)، " +
            "tone (واحد من: friendly|formal|analytic|simple بحسب أسلوب المقالات)، length (short|medium|long بحسب طول المقالات المعتاد)، keywords (حتى 10 كلمات مفتاحية)، " +
            "labels (حتى 6 تصنيفات مناسبة)، avoid (ما يجب تجنبه إن ظهر من المحتوى، أو نص فارغ)، rules (قاعدتان قصيرتان على الأكثر تحفظان أسلوب المدونة، أو نص فارغ)."
        var out: SiteProfile? = null
        engine.chat(listOf(ChatMessage("system", sys), ChatMessage("user", usr)), ChatOpts(temp = 0.3, max = 1200, validate = { out = ProfileLogic.parseAi(it, s) ?: throw IllegalStateException("JSON غير صالح") }))
        return out
    }

}

/** منطق التحليل الصرف (بلا شبكة) ليسهل اختباره. */
internal object ProfileLogic {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseFeed(txt: String): BlogSnapshot? = try {
        val feed = json.parseToJsonElement(txt).jsonObject["feed"]?.jsonObject ?: return null
        fun t(o: JsonObject?, k: String) = o?.get(k)?.jsonObject?.get("\$t")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val titles = (feed["entry"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { e -> t(e, "title").ifEmpty { null } } }
        val cats = (feed["category"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["term"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null } }
        val author = (feed["author"] as? JsonArray)?.firstOrNull()?.jsonObject?.let { t(it, "name") }.orEmpty()
        BlogSnapshot(t(feed, "title"), t(feed, "subtitle"), author, cats, titles).takeIf { it.title.isNotEmpty() || it.titles.isNotEmpty() }
    } catch (e: Exception) { null }

    fun parseHtml(h: String): BlogSnapshot? {
        val title = Regex("<title[^>]*>([^<]{2,120})</title>", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.trim().orEmpty()
        val desc = Regex("<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']{5,300})[\"']", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.trim().orEmpty()
        val heads = Regex("<h[23][^>]*>\\s*(?:<a[^>]*>)?([^<]{12,120})", RegexOption.IGNORE_CASE).findAll(h).map { it.groupValues[1].trim() }.distinct().take(25).toList()
        return BlogSnapshot(title, desc, "", emptyList(), heads).takeIf { title.isNotEmpty() }
    }

    fun pickNiche(s: BlogSnapshot): String {
        val text = titleTokens((listOf(s.title, s.description) + s.categories + s.titles).joinToString(" "))
        var best = "general"; var bestScore = 0
        for (n in Niches.all) {
            val vocab = titleTokens((n.keywords + n.labels + n.label).joinToString(" "))
            val score = text.count { w -> vocab.any { v -> w == v || (w.length > 3 && v.length > 3 && (w.startsWith(v) || v.startsWith(w))) } }
            if (score > bestScore) { bestScore = score; best = n.id }
        }
        return if (bestScore >= 2) best else "general"
    }

    fun heuristic(s: BlogSnapshot): SiteProfile {
        val n = Niches.of(pickNiche(s))
        val labels = s.categories.map { it.take(24) }.distinct().take(6).ifEmpty { n.labels }
        val kws = (labels + n.keywords).distinct().take(10)
        return SiteProfile(s.title.ifBlank { "موقعي" }.take(60), n.id, n.label, s.author.ifBlank { n.persona }, n.reader, "friendly", "medium", kws, labels, "", "", false, s.titles.size)
    }

    fun parseAi(txt: String, s: BlogSnapshot): SiteProfile? {
        val clean = txt.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        val body = clean.substringAfter('{', "").let { if (it.isEmpty()) return null else "{" + it }.substringBeforeLast('}', "").let { if (it.isEmpty()) return null else "$it}" }
        val o = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
        fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun list(k: String, max: Int) = (o[k] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.take(30)?.ifEmpty { null } }?.distinct()?.take(max).orEmpty()
        val niche = str("niche").takeIf { id -> Niches.all.any { it.id == id } } ?: pickNiche(s)
        val n = Niches.of(niche)
        val kws = list("keywords", 10).ifEmpty { return null }
        return SiteProfile(
            s.title.ifBlank { "موقعي" }.take(60), niche, str("topic").take(40).ifEmpty { n.label }, s.author.ifBlank { n.persona },
            str("audience").take(120).ifEmpty { n.reader }, str("tone").takeIf { it in setOf("friendly", "formal", "analytic", "simple") } ?: "friendly",
            str("length").takeIf { it in setOf("short", "medium", "long") } ?: "medium", kws, list("labels", 6).ifEmpty { s.categories.take(6).ifEmpty { n.labels } },
            str("avoid").take(300), str("rules").take(500), true, s.titles.size,
        )
    }

    /** التصنيفات الفعلية للمدونة أدق من تخمين النموذج، فتُقدَّم متى وُجدت. */
    fun merge(base: SiteProfile, ai: SiteProfile) = SiteProfile(
        base.name, ai.niche, ai.topic, base.author, ai.audience, ai.tone, ai.length, ai.keywords,
        if (base.niche == ai.niche || base.labels.size >= 3) base.labels.ifEmpty { ai.labels } else ai.labels, ai.avoid, ai.rules, true, base.posts,
    )
}
