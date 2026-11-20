package com.sajed.articlestudio.engine

import android.content.Context
import com.sajed.articlestudio.BrandConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

class FeedDef(val name: String, val url: String)

/** قالب مجال جاهز: مصادره ورموزه وتصنيفاته وصفة المحرر. المستخدم يعدّله لكل موقع. */
class Niche(
    val id: String, val label: String, val persona: String, val reader: String, val hue: Int,
    val motifs: List<String>, val labels: List<String>, val keywords: List<String>,
    val feeds: List<FeedDef> = emptyList(), val espn: Boolean = false,
)

object Niches {
    val all = listOf(
        Niche("sport", "كرة القدم والرياضة", "محرر رياضي", "المشجع", 20, listOf("trophy", "pitch", "screen", "shield", "stars", "duel", "flag", "stadium", "board", "ball", "fox", "pulse"),
            listOf("أخبار", "تحليل", "شروحات", "الدوريات"), listOf("كرة القدم", "الدوري السعودي", "الدوري المصري", "كأس العالم"),
            listOf(
                FeedDef("RT عربي", "https://rtarabic.com/rss/sport/"), FeedDef("CNN عربية", "https://arabic.cnn.com/api/v1/rss/sport/rss.xml"),
                FeedDef("بي بي سي سبورت", "https://feeds.bbci.co.uk/sport/football/rss.xml"), FeedDef("سكاي سبورتس", "https://www.skysports.com/rss/12040"),
                FeedDef("الغارديان", "https://www.theguardian.com/football/rss"),
            ), espn = true),
        Niche("news", "أخبار عامة", "محرر أخبار", "القارئ", 210, listOf("globe", "screen", "stars", "board", "pulse", "flag", "shield"),
            listOf("أخبار", "تقارير", "تحليل"), listOf("أخبار عاجلة", "تقارير", "العالم العربي"),
            listOf(FeedDef("بي بي سي عربي", "https://feeds.bbci.co.uk/arabic/rss.xml"), FeedDef("CNN عربية", "https://arabic.cnn.com/api/v1/rss/world/rss.xml"), FeedDef("RT عربي", "https://rtarabic.com/rss/"))),
        Niche("tech", "تقنية وذكاء اصطناعي", "محرر تقني", "المهتم بالتقنية", 215, listOf("chip", "bulb", "screen", "globe", "pulse", "board", "shield"),
            listOf("تقنية", "ذكاء اصطناعي", "تطبيقات", "شروحات"), listOf("ذكاء اصطناعي", "هواتف ذكية", "تطبيقات", "أمن رقمي"),
            listOf(FeedDef("عرب تقنية", "https://aitnews.com/feed/"), FeedDef("RT عربي تكنولوجيا", "https://rtarabic.com/rss/technology/"), FeedDef("TechCrunch", "https://techcrunch.com/feed/"), FeedDef("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml"))),
        Niche("health", "صحة ولياقة", "محرر صحي", "القارئ", 150, listOf("heart", "leaf", "pulse", "shield", "stars", "board", "bulb"),
            listOf("صحة", "تغذية", "لياقة", "وقاية"), listOf("صحة القلب", "التغذية الصحية", "النوم", "اللياقة"),
            listOf(FeedDef("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml"))),
        Niche("food", "غذاء وطبخ", "محرر طعام", "محب الطبخ", 28, listOf("bowl", "leaf", "heart", "stars", "board", "bulb"),
            listOf("وصفات", "طبخ", "تغذية", "نصائح"), listOf("وصفات سهلة", "مطبخ عربي", "حلويات", "طبخ صحي"),
            listOf(FeedDef("The Guardian Food", "https://www.theguardian.com/food/rss"))),
        Niche("business", "اقتصاد وأعمال", "محرر اقتصادي", "رائد الأعمال", 190, listOf("coin", "board", "globe", "pulse", "shield", "bulb", "stars"),
            listOf("اقتصاد", "أعمال", "استثمار", "ريادة"), listOf("الاقتصاد العربي", "الاستثمار", "ريادة الأعمال", "الأسواق"),
            listOf(FeedDef("CNN عربية اقتصاد", "https://arabic.cnn.com/api/v1/rss/business/rss.xml"), FeedDef("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml"))),
        Niche("travel", "سفر وسياحة", "محرر سفر", "المسافر", 175, listOf("plane", "globe", "stars", "flag", "leaf", "board"),
            listOf("سفر", "وجهات", "نصائح", "سياحة"), listOf("وجهات سياحية", "نصائح السفر", "فنادق", "تأشيرات"),
            listOf(FeedDef("CNN عربية سفر", "https://arabic.cnn.com/api/v1/rss/travel/rss.xml"))),
        Niche("education", "تعليم وثقافة", "محرر تعليمي", "الطالب والقارئ", 255, listOf("book", "bulb", "stars", "globe", "board", "shield"),
            listOf("تعليم", "ثقافة", "مهارات", "دراسة"), listOf("طرق الدراسة", "تعلم اللغات", "مهارات", "منح دراسية")),
        Niche("lifestyle", "أسلوب حياة", "محرر أسلوب حياة", "القارئ", 330, listOf("heart", "leaf", "stars", "bulb", "bowl", "plane", "board"),
            listOf("أسلوب حياة", "نصائح", "منزل", "علاقات"), listOf("نصائح يومية", "تنظيم المنزل", "العناية الشخصية", "الإنتاجية")),
        Niche("general", "عام", "محرر محتوى", "القارئ", 40, listOf("stars", "board", "bulb", "globe", "shield", "pulse"),
            listOf("مقالات", "شروحات", "نصائح"), emptyList()),
    )
    fun of(id: String) = all.firstOrNull { it.id == id } ?: all.last()
}

@Serializable
data class SiteConfig(
    val id: String,
    val name: String,
    val url: String,
    val niche: String = "general",
    val nicheLabel: String = "",
    val author: String = "",
    val audience: String = "",
    val tone: String = "friendly",
    val keywords: List<String> = emptyList(),
    val avoid: String = "",
    val rules: String = "",
    val feeds: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val length: String = "medium",
    val faq: Boolean = true,
    val tables: Boolean = true,
    val sources: Boolean = true,
    val count: Int = 10,
    val mode: String = "News",
    val schedule: Int = 0,
    val perSlot: Int = 1,
    val share: Boolean = true,
    val repo: String = "",
    val branch: String = "",
    val pubMode: String = "off",
    val pubState: String = "draft",
    val googleAccount: String = "",
    val autoExport: Boolean = true,
    val photos: Boolean = true,
    val autoEvery: Int = 0,
    val autoCount: Int = 3,
    val autoFrom: Int = 0,
    val autoTo: Int = 24,
    val legacySeed: Boolean = false,
    val createdAt: Long = 0,
) {
    val nicheDef get() = Niches.of(niche)
    val topicLabel get() = nicheLabel.ifBlank { nicheDef.label }
    val host get() = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val toneText get() = when (tone) { "formal" -> "رسمية رصينة"; "analytic" -> "تحليلية معمّقة"; "simple" -> "مبسّطة جداً وقريبة من القارئ"; else -> "ودّية مباشرة" }
    val sectionWords get() = when (length) { "short" -> 100 to 140; "long" -> 200 to 280; else -> 140 to 190 }
    val minWords get() = when (length) { "short" -> 280; "long" -> 520; else -> 360 }
    val motifs get() = nicheDef.motifs
    val labelPool get() = labels.ifEmpty { nicheDef.labels }
}

private val TONES = setOf("friendly", "formal", "analytic", "simple")
val PUB_MODES = setOf("off", "google")
val PUB_STATES = setOf("draft", "live", "scheduled")

object SiteRules {
    /** يتحقق من بيانات الموقع ويعيد أسماء الحقول الخاطئة ورسائلها. */
    fun validate(s: SiteConfig): Map<String, String> {
        val e = LinkedHashMap<String, String>()
        if (s.name.trim().length < 2) e["name"] = "اسم الموقع قصير"
        val u = s.url.trim()
        if (!Regex("^https?://[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/.*)?$").matches(u)) e["url"] = "رابط غير صالح، مثال: https://example.blogspot.com"
        if (s.tone !in TONES) e["tone"] = "نبرة غير معروفة"
        if (s.pubMode !in PUB_MODES) e["pubMode"] = "طريقة نشر غير معروفة"
        if (s.pubState !in PUB_STATES) e["pubState"] = "حالة نشر غير معروفة"
        if (s.repo.isNotBlank() && !Regex("^[\\w.-]+/[\\w.-]+$").matches(s.repo.trim())) e["repo"] = "الصيغة owner/repo"
        s.feeds.firstOrNull { !it.startsWith("http") }?.let { e["feeds"] = "كل مصدر RSS يبدأ بـ http: $it" }
        return e
    }

    fun clean(s: SiteConfig): SiteConfig = s.copy(
        name = s.name.trim().take(60), url = s.url.trim().trimEnd('/'), author = s.author.trim().take(60), audience = s.audience.trim().take(120),
        keywords = s.keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12), avoid = s.avoid.trim().take(400), rules = s.rules.trim().take(1200),
        feeds = s.feeds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8), labels = s.labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8),
        googleAccount = s.googleAccount.trim(), count = s.count.coerceIn(3, 30), schedule = s.schedule.coerceIn(0, 72), perSlot = s.perSlot.coerceIn(1, 6), share = s.share,
        autoEvery = s.autoEvery.coerceIn(0, 24), autoCount = s.autoCount.coerceIn(1, 10), autoFrom = s.autoFrom.coerceIn(0, 23), autoTo = s.autoTo.coerceIn(1, 24),
    )

    fun newId(name: String): String = slugify(name, "site") + "-" + System.currentTimeMillis().toString(36).takeLast(4)
}

/** مخزن المواقع (JSON صغير في التفضيلات) والموقع النشط. الموقع الأول هو سبورت لايف 24 الأصلي. */
@Singleton
class SiteStore @Inject constructor(@ApplicationContext c: Context) {
    private val sp = c.getSharedPreferences("sites", Context.MODE_PRIVATE)
    private val ser = ListSerializer(SiteConfig.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _sites = MutableStateFlow(load())
    val sites: StateFlow<List<SiteConfig>> = _sites.asStateFlow()
    private val _active = MutableStateFlow(sp.getString("active", "default") ?: "default")
    val activeId: StateFlow<String> = _active.asStateFlow()

    private fun legacy() = SiteConfig(
        id = "default", name = BrandConfig.BLOG_NAME, url = BrandConfig.BLOG_SITE, niche = "sport", author = BrandConfig.BLOG_AUTHOR,
        keywords = Niches.of("sport").keywords, legacySeed = true, createdAt = 1,
    )

    private fun load(): List<SiteConfig> {
        val l = runCatching { json.decodeFromString(ser, sp.getString("list", "") ?: "") }.getOrDefault(emptyList())
        val fixed = l.map { if (it.pubMode == "script") it.copy(pubMode = "off") else it }
        return fixed.ifEmpty { listOf(legacy()).also { sp.edit().putString("list", json.encodeToString(ser, it)).apply() } }
    }

    private fun save(l: List<SiteConfig>) { _sites.value = l; sp.edit().putString("list", json.encodeToString(ser, l)).apply() }

    fun get(id: String): SiteConfig = _sites.value.firstOrNull { it.id == id } ?: _sites.value.first()
    fun active(): SiteConfig = get(_active.value)

    fun setActive(id: String) { if (_sites.value.any { it.id == id }) { _active.value = id; sp.edit().putString("active", id).apply() } }

    fun upsert(s: SiteConfig) {
        val c = SiteRules.clean(s)
        val l = _sites.value
        save(if (l.any { it.id == c.id }) l.map { if (it.id == c.id) c else it } else l + c.copy(createdAt = System.currentTimeMillis()))
    }

    /** يحذف الموقع (لا يُسمح بحذف الأخير) ويعيد true عند النجاح. */
    fun remove(id: String): Boolean {
        val l = _sites.value
        if (l.size <= 1 || l.none { it.id == id }) return false
        save(l.filterNot { it.id == id })
        if (_active.value == id) setActive(_sites.value.first().id)
        return true
    }
}
