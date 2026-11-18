package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ReviewDao
import com.sajed.articlestudio.data.ReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

/** وسوم المراجعة المتاحة، وما يقابل كلاً منها من توجيه يُضاف لقواعد الموقع حين يتكرر. */
object ReviewTags {
    val guide = linkedMapOf(
        "عنوان ضعيف" to "اجعل العنوان محدداً وجذاباً دون مبالغة",
        "أرقام ناقصة" to "أضف أرقاماً وتواريخ موثّقة من المصدر",
        "تكرار" to "تجنّب تكرار الجمل والأفكار بين الأقسام",
        "قصير" to "وسّع الأقسام بتفاصيل مفيدة وأمثلة",
        "أسلوب آلي" to "اكتب بأسلوب بشري طبيعي وتجنّب العبارات الجاهزة المكررة",
        "خارج المجال" to "التزم بمجال الموقع فقط ولا تخرج عنه",
        "غلاف ضعيف" to "",
    )
    val all get() = guide.keys.toList()
}

/** تحليل آلي نقي للمقال: مقاييس وأعلام ضعف ودرجة 0..100. */
object ReviewAnalyzer {
    class Result(val score: Int, val metrics: Map<String, String>, val flags: List<String>)

    private val tag = Regex("<[^>]+>")
    private val num = Regex("[0-9\u0660-\u0669]+")
    private fun plain(h: String) = h.replace(tag, " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()
    private fun norm(s: String) = s.replace(Regex("[\\p{Punct}\u060C\u061B\u061F\\s]+"), " ").trim().lowercase()

    fun analyze(a: Article): Result {
        val body = (listOf(a.intro) + a.sections.map { plain(it.html) } + a.outro).joinToString(" ")
        val sentences = body.split(Regex("[.!?\u061F\\n]+")).map { norm(it) }.filter { it.length > 25 }
        val repeat = if (sentences.isEmpty()) 0 else (sentences.size - sentences.toSet().size) * 100 / sentences.size
        val numbers = num.findAll(body).count()
        val links = a.sections.sumOf { Regex("<a ").findAll(it.html).count() }
        val tables = a.sections.count { "<table" in it.html }
        val flags = ArrayList<String>()
        if (a.words < 350) flags += "المقال قصير"
        if (a.sections.size < 3) flags += "أقسام قليلة"
        if (a.facts.size < 3) flags += "حقائق قليلة"
        if (a.faq.size < 2) flags += "أسئلة شائعة ناقصة"
        if (a.sources.isEmpty()) flags += "بلا مصدر"
        if (numbers < 3) flags += "أرقام قليلة"
        if (repeat >= 8) flags += "تكرار جمل"
        if (a.title.length !in 35..75) flags += "طول العنوان"
        if (a.desc.length !in 110..160) flags += "طول الوصف"
        var score = a.quality
        if (repeat >= 8) score -= 8
        if (a.sources.isEmpty()) score -= 6
        if (numbers < 3) score -= 4
        val m = linkedMapOf(
            "الكلمات" to a.words.toString(), "الأقسام" to a.sections.size.toString(), "الحقائق" to a.facts.size.toString(),
            "الأسئلة" to a.faq.size.toString(), "المصادر" to a.sources.size.toString(), "الأرقام" to numbers.toString(),
            "الروابط" to links.toString(), "الجداول" to tables.toString(), "تكرار الجمل" to "$repeat%",
        )
        return Result(score.coerceIn(0, 100), m, flags)
    }
}

class Group(val name: String, val n: Int, val auto: Int, val rating: Float)

/** خلاصة المرجع: متوسطات وأكثر نقاط الضعف تكراراً. */
class Insights(
    val count: Int, val rated: Int, val avgAuto: Int, val avgRating: Float,
    val byProvider: List<Group>, val bySite: List<Group>, val topFlags: List<Pair<String, Int>>, val topTags: List<Pair<String, Int>>,
) {
    fun markdown(siteNames: Map<String, String>): String = buildString {
        appendLine("# مرجع جودة المقالات")
        appendLine("- مقالات منشورة: $count · مُقيَّمة: $rated · متوسط الدرجة الآلية: $avgAuto · متوسط تقييمي: ${"%.1f".format(avgRating)}")
        if (topFlags.isNotEmpty()) appendLine("\n## أكثر نقاط الضعف الآلية\n" + topFlags.joinToString("\n") { "- ${it.first}: ${it.second}" })
        if (topTags.isNotEmpty()) appendLine("\n## ملاحظاتي المتكررة\n" + topTags.joinToString("\n") { "- ${it.first}: ${it.second}" })
        if (byProvider.isNotEmpty()) appendLine("\n## حسب المزود\n" + byProvider.joinToString("\n") { "- ${it.name}: ${it.n} مقالاً · آلي ${it.auto} · تقييم ${"%.1f".format(it.rating)}" })
        if (bySite.isNotEmpty()) appendLine("\n## حسب الموقع\n" + bySite.joinToString("\n") { "- ${siteNames[it.name] ?: it.name}: ${it.n} · آلي ${it.auto} · تقييم ${"%.1f".format(it.rating)}" })
    }

    companion object {
        private fun csv(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        private fun groups(l: List<ReviewEntity>, key: (ReviewEntity) -> String) = l.groupBy(key).filterKeys { it.isNotBlank() }.map { (k, v) ->
            val r = v.filter { it.rating > 0 }
            Group(k, v.size, v.sumOf { it.auto } / v.size, if (r.isEmpty()) 0f else r.sumOf { it.rating }.toFloat() / r.size)
        }.sortedByDescending { it.n }

        fun build(l: List<ReviewEntity>): Insights {
            val rated = l.filter { it.rating > 0 }
            return Insights(
                l.size, rated.size, if (l.isEmpty()) 0 else l.sumOf { it.auto } / l.size,
                if (rated.isEmpty()) 0f else rated.sumOf { it.rating }.toFloat() / rated.size,
                groups(l) { it.provider }, groups(l) { it.siteId },
                l.flatMap { csv(it.flags) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                l.flatMap { csv(it.tags) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            )
        }
    }
}

/** يسجّل مراجعة لكل مقال يُنشر، ويحفظ تقييم ساجد، ويحوّل نقاط الضعف المتكررة إلى توجيه للتوليد القادم. */
@Singleton
class ReviewStore @Inject constructor(private val dao: ReviewDao, private val arts: ArticleDao) {
    private val ser = MapSerializer(String.serializer(), String.serializer())
    val all: Flow<List<ReviewEntity>> = dao.observeAll()

    suspend fun record(articleId: Long, now: Long = System.currentTimeMillis()) {
        if (dao.byArticle(articleId) != null) return
        val e = arts.byId(articleId) ?: return
        val a = runCatching { AppJson.decodeFromString(Article.serializer(), e.data) }.getOrNull() ?: return
        val r = ReviewAnalyzer.analyze(a)
        dao.insert(ReviewEntity(
            articleId = articleId, siteId = e.siteId, title = e.title, remoteUrl = e.remoteUrl.takeIf { it.startsWith("http") }.orEmpty(),
            publishedAt = now, words = a.words, provider = a.provider, auto = r.score,
            metrics = AppJson.encodeToString(ser, r.metrics), flags = r.flags.joinToString(","),
        ))
    }

    /** مقالات نُشرت قبل هذه الميزة. */
    suspend fun backfill(): Int = arts.publishedWithoutReview().onEach { record(it) }.size

    suspend fun rate(id: Long, rating: Int, tags: List<String>, notes: String) =
        dao.rate(id, rating.coerceIn(0, 5), tags.filter { it in ReviewTags.guide }.joinToString(","), notes.trim().take(600), System.currentTimeMillis())

    fun metricsOf(r: ReviewEntity): Map<String, String> = runCatching { AppJson.decodeFromString(ser, r.metrics) }.getOrDefault(emptyMap())

    /** يضاف لقواعد الموقع: وسم تكرر في مراجعتين منخفضتين فأكثر لنفس الموقع. */
    suspend fun guidance(siteId: String): String {
        val low = dao.lowRated(siteId)
        val lines = low.flatMap { it.tags.split(",") }.groupingBy { it }.eachCount()
            .filter { it.value >= 2 }.keys.mapNotNull { ReviewTags.guide[it]?.takeIf { g -> g.isNotEmpty() } }
        return if (lines.isEmpty()) "" else "ملاحظات من مراجعاتي السابقة لهذا الموقع: " + lines.joinToString("؛ ")
    }
}
