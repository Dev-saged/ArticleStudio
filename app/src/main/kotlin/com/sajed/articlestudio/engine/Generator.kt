package com.sajed.articlestudio.engine

import com.sajed.articlestudio.BrandConfig
import com.sajed.articlestudio.data.HistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class PlanMode { News, Evergreen, Mine }

private val MOTIFS = listOf("trophy", "pitch", "screen", "shield", "stars", "duel", "flag", "stadium", "board", "ball", "fox", "pulse")
private val EVERGREEN = listOf(
    "قاعدة التسلل وكيف تُحتسب", "تقنية الفار: كيف تعمل ومتى يتدخل", "الأهداف المتوقعة xG: دليل المبتدئ", "الضغط العالي في كرة القدم",
    "الفرق بين 4-3-3 و4-4-2", "الكرات الثابتة: كيف تصنع الفرق الأهداف", "دور حارس المرمى الحديث", "اللعب المالي النظيف: ما هو؟",
    "أكاديميات الناشئين وكيف تصنع النجوم", "إشارات الحكم في الملعب", "كيف تُمنح الكرة الذهبية", "ركلات الترجيح: علم النفس والأرقام العامة",
    "قواعد الوقت الإضافي والهدف الذهبي", "نظام الانتقالات والإعارة", "كيف يُحسب التصنيف العالمي", "الفرق بين المهاجم الصريح والمهاجم الوهمي",
    "مراكز لاعبي خط الوسط", "فن الدفاع بالمنطقة والرجل لرجل", "تاريخ كأس العالم باختصار", "البطاقات الصفراء والحمراء والإيقاف",
    "لماذا تلعب الفرق بثلاثة مدافعين؟", "كيف يُختار الحكم لمباريات الكبار", "الفرق بين الدوري والكأس", "دور المدرب المساعد وتحليل الأداء",
    "تأثير الجماهير والملعب على النتائج", "دليل المشجع لمتابعة الدوريات الأوروبية", "كيف تقرأ جدول الترتيب", "التبديلات الخمسة وتأثيرها",
    "قواعد الإصابة والوقت بدل الضائع", "الفرق بين الاحتراف والهواية في الكرة",
)

private fun rules() = """أنت محرر رياضي عربي محترف في مدونة «${BrandConfig.BLOG_NAME}»، تكتب للقارئ العربي بالفصحى المبسطة بأسلوب بشري مباشر وغني بالمعلومة.
قواعد صارمة:
- لا تخترع أي رقم أو نتيجة أو تاريخ أو اقتباس أو اسم لاعب أو مدرب. استخدم فقط ما ورد في «المادة المرجعية» إن وُجدت. وإن لم توجد فاكتب شرحاً عاماً دائماً (قواعد، مفاهيم، تاريخ معروف جداً) بلا أرقام قابلة للتغير.
- ابدأ كل قسم بجملة تجيب مباشرة عن عنوانه، ثم فصّل بأمثلة وتشبيهات ومقارنات وفوائد عملية للمشجع.
- ممنوع الحشو والعبارات المستهلكة مثل: «في عالم كرة القدم»، «لا شك أن»، «يعد من أبرز». لا تكرر الفكرة بصياغة أخرى.
- حين تتعدد المواد المرجعية فاجمعها في سرد واحد متماسك، وقارن بين رواياتها، وإذا تعارضت فاذكر التعارض صراحةً ولا تختر رواية بصمت.
- ممنوع نقل جمل المصادر أو ترجمتها حرفياً. أعد صياغة كل شيء بأسلوبك، وأضف سياقاً وقراءة فنية وزاوية خاصة بالمدونة بدل الاكتفاء بسرد الخبر.
- اكتب لمحركات البحث ومحركات الإجابة التوليدية: جمل مستقلة قابلة للاقتباس تحمل الكيان والرقم والسياق معاً، وعرّف كل مصطلح أو اسم عند أول ذكر له بنصف جملة، واستخدم الأسماء الكاملة والتواريخ الواردة في المادة المرجعية، ولا تستخدم «هذا» و«ذلك» دون مرجع واضح.
- فقرات قصيرة (3 إلى 4 جمل). لا تستخدم Markdown ولا HTML. النقاط تبدأ بـ «- » في سطر مستقل."""

private fun sysMeta() = """${rules()}
مهمتك الآن: هيكل المقال وأجزاؤه الأولى. أعد النتيجة بهذه الصيغة الوسمية فقط، كل وسم في سطر مستقل، بلا JSON ولا شرح خارجها:
[[title]] عنوان 40-62 حرفاً يبدأ بالكلمة المفتاحية الأهم ويعد بفائدة واضحة، بلا علامات تعجب
[[desc]] وصف 130-155 حرفاً يجيب عن سؤال القارئ ويدعوه للقراءة
[[slug]] english-lowercase-hyphen-slug
[[labels]] تصنيف1 | تصنيف2
[[kicker]] كلمتان للغلاف
[[motif]] واحد من: ${MOTIFS.joinToString(" ")}
[[hue]] رقم 0-360
[[infotitle]] عنوان بطاقة المعلومات
[[fact]] قيمة قصيرة حتى 9 أحرف | شرح حتى 26 حرفاً
[[fact]] (أربع حقائق مؤكدة فقط، من المادة المرجعية أو معلومات عامة ثابتة)
[[tldr]] خلاصة المقال في جملتين
[[intro]]
مقدمة من فقرتين، الأولى تجيب مباشرة عن سؤال القارئ.
[[outline]]
عنوان القسم الأول
(خمسة عناوين أقسام، كل عنوان في سطر، يفضّل صيغة سؤال أو عبارة محددة)
[[faq]] سؤال طبيعي يكتبه القارئ في جوجل؟ | جواب في جملتين
[[faq]] (ثلاثة أسئلة)
[[outro]]
خاتمة من فقرة أو فقرتين فيها ما يهم المشجع."""

private fun sysSec() = """${rules()}
مهمتك الآن: كتابة الأقسام المطلوبة فقط. لكل قسم اكتب 140 إلى 190 كلمة في فقرتين أو ثلاث. الصيغة بالضبط:
[[section]] عنوان القسم
فقرة أولى

فقرة ثانية
ولا شيء غير ذلك. في قسم واحد على الأقل، إن سمحت المادة، ضع بعد الفقرة الأولى جدول مقارنة صغيراً (3 أعمدة كحد أقصى و3 إلى 5 صفوف، السطر الأول عناوين الأعمدة) كل صف في سطر يبدأ بـ | وتفصل أعمدته | ، وإلا فاستخدم قائمة نقطية."""

private val LISTMARK = Regex("^([-*•]|\\d+[.)])\\s+")

@Singleton
class Generator @Inject constructor(
    private val engine: ProviderEngine, private val http: OkHttpClient,
    private val bus: RunBus, private val history: HistoryStore, private val data: StudioData, private val news: NewsSources,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parsePlan(txt: String, news: List<Ref>, n: Int, known: List<String>): List<Topic> {
        val seen = HashSet<String>()
        val out = ArrayList<Topic>()
        for (raw in txt.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").lines()) {
            val line = raw.replace(Regex("^\\s*(\\d+[.)]|[-*•])\\s*"), "").trim()
            if (!line.contains('|')) continue
            val p = line.split('|').map { it.trim() }
            val topic = p.getOrNull(0).orEmpty()
            if (topic.length < 6) continue
            val refs = Regex("\\d+").findAll(p.getOrNull(3).orEmpty()).mapNotNull { news.getOrNull(it.value.toInt()) }.distinct().take(3).toList()
            val key = refs.firstOrNull()?.url ?: topic
            if (!seen.add(key)) continue
            if (known.any { similarTitles(topic, it) } || out.any { similarTitles(topic, it.topic) }) continue
            out += Topic(clamp(topic, 120), clamp(p.getOrNull(1), 200), p.getOrNull(2).orEmpty().split(Regex("[،,]")).map { clamp(it, 24) }.filter { it.isNotEmpty() }.take(3), refs.firstOrNull(), refs)
        }
        return out.take(n)
    }

    suspend fun plan(n: Int, mode: PlanMode, custom: String, known: List<String> = emptyList()): List<Topic> {
        val knownAll = (known + data.seed + history.load().topics).distinct()
        val avoid = knownAll.takeLast(120)
        if (mode == PlanMode.Mine) {
            val l = custom.lines().map { it.trim() }.filter { it.length > 5 }.take(n)
            if (l.isEmpty()) throw IllegalArgumentException("اكتب موضوعاً واحداً على الأقل في كل سطر")
            l.filter { t -> knownAll.any { similarTitles(t, it) } }.forEach { bus.log("قريب من مقال سابق: ${clamp(it, 40)}", "w") }
            return l.map { Topic(it) }
        }
        var news = emptyList<Ref>()
        if (mode == PlanMode.News) {
            news = this.news.fetch(history.load().urls.toSet()).take(56)
            val srcs = news.map { it.s }.distinct().size
            bus.log(if (news.isNotEmpty()) "جُلب ${news.size} خبراً من $srcs مصادر" else "تعذّر جلب الأخبار، سنكتب شروحات دائمة", if (news.isNotEmpty()) "" else "w")
        }
        val useNews = news.size >= 3
        val sys = "أنت رئيس تحرير مدونة رياضية عربية. أعد قائمة فقط بلا مقدمة ولا شرح، كل موضوع في سطر واحد بهذه الصيغة بالضبط:\nعنوان الموضوع بالعربية | زاوية المعالجة في جملة | تصنيف1، تصنيف2 | أرقام الأخبار المرجعية (1 إلى 3 مفصولة بفواصل) أو -"
        val usr = "المطلوب $n مواضيع متنوعة ومختلفة تماماً.\n" + (if (useNews)
            "اختر المواضيع من الأخبار الحديثة التالية. الخانة الأخيرة = رقم خبر أو عدة أرقام لأخبار تتناول القصة نفسها من مصادر مختلفة (الدمج يرفع جودة المقال)، ولا تكرر قصة. خصّص ثلث المواضيع على الأقل لكرة القدم العربية (الدوري السعودي والمصري والمنتخبات العربية واللاعبون العرب) متى توفرت أخبارها. يجوز أن يكون ربعها شروحاً دائمة وآخر خانتها -.\n" + news.mapIndexed { i, x -> "$i. [${x.s}] ${x.h} — ${clamp(x.d, 120)}" }.joinToString("\n")
        else "اجعلها شروحاً وتحليلات دائمة لا ترتبط بنتيجة أو خبر محدد (قواعد، تكتيك، مفاهيم، أدلة للمشجع)، وآخر خانة دائماً -.") +
            "\nتجنب هذه العناوين المنشورة سابقاً:\n" + avoid.take(60).joinToString(" | ")
        try {
            var plan = emptyList<Topic>()
            val r = engine.chat(listOf(ChatMessage("system", sys), ChatMessage("user", usr)), ChatOpts(temp = 0.8, max = 2500, validate = { t ->
                plan = parsePlan(t, if (useNews) news else emptyList(), n, knownAll)
                if (plan.size < minOf(3, n)) throw IllegalStateException("خطة قصيرة")
            }))
            bus.log("الخطة من ${r.provider}", "o")
            if (plan.size >= minOf(3, n)) return plan
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            bus.log("تعذّر التخطيط الذكي، نستخدم خطة احتياطية", "w")
        }
        val l = ArrayList<Topic>()
        if (useNews) news.take(Math.ceil(n * .6).toInt()).forEach { l += Topic(it.h, labels = listOf("أخبار"), ref = it, refs = listOf(it)) }
        for (t in EVERGREEN) { if (l.size >= n) break; if (knownAll.none { similarTitles(t, it) } && l.none { it.topic == t }) l += Topic(t, labels = listOf("شروحات")) }
        return l.take(n)
    }

    private class Meta(
        val title: String, val desc: String, val slug: String, val labels: List<String>, val kicker: String, val motif: String,
        val hue: Int, val infotitle: String, val facts: List<Fact>, val tldr: String, val intro: String,
        val outline: List<String>, val faq: List<Faq>, val outro: String,
    )

    private fun parseMeta(txt: String, t: Topic): Meta {
        val l = parseTags(txt)
        val title = one(l.tg("title"))
        if (title.length < 15) throw IllegalStateException("عنوان قصير")
        val facts = l.tgAll("fact").mapNotNull { x ->
            val p = one(x).split("|")
            Fact(clamp(p[0], 9), clamp(p.drop(1).joinToString("|"), 26)).takeIf { it.v.isNotEmpty() && it.c.isNotEmpty() }
        }.take(4)
        if (facts.size < 4) throw IllegalStateException("حقائق ناقصة")
        val outline = l.tg("outline").lines().map { it.replace(LISTMARK, "").replace(Regex("^#+\\s*"), "").trim() }.filter { it.length in 4..100 }.take(6)
        if (outline.size < 4) throw IllegalStateException("عناوين أقسام ناقصة")
        val faq = l.tgAll("faq").mapNotNull { x ->
            val s = one(x); val i = s.indexOf('|')
            if (i > 0) Faq(clamp(s.substring(0, i), 140), clamp(s.substring(i + 1), 500)).takeIf { it.q.isNotEmpty() && it.a.isNotEmpty() } else null
        }.take(4)
        if (faq.size < 2) throw IllegalStateException("أسئلة شائعة ناقصة")
        val intro = l.tg("intro")
        if (wc(intro) < 40) throw IllegalStateException("مقدمة قصيرة")
        val hue = one(l.tg("hue")).toIntOrNull()
        val motif = one(l.tg("motif")).lowercase()
        val labels = (one(l.tg("labels")).split(Regex("[|،,]")) + t.labels).map { clamp(it, 24) }.filter { it.isNotEmpty() }.distinct().take(3)
        return Meta(
            clamp(title, 95), clamp(one(l.tg("desc")), 160), slugify(one(l.tg("slug")), "post-" + System.currentTimeMillis().toString(36)),
            labels.ifEmpty { listOf(if (t.ref != null) "أخبار" else "شروحات") }, clamp(one(l.tg("kicker")), 18).ifEmpty { "تحليل" },
            if (motif in MOTIFS) motif else MOTIFS.random(), if (hue != null) ((hue % 360) + 360) % 360 else Random.nextInt(360),
            clamp(one(l.tg("infotitle")), 36).ifEmpty { "أهم الأرقام" }, facts, clamp(one(l.tg("tldr")).replace(Regex("\\s+"), " "), 320),
            intro, outline, faq, l.tg("outro"),
        )
    }

    private class Sec(val h: String, val body: String)

    private fun parseSections(txt: String): List<Sec> = parseTags(txt).filter { it.tag == "section" }.map { e ->
        val ls = e.text.lines()
        Sec(one(ls[0]), ls.drop(1).joinToString("\n").trim())
    }.filter { wc(it.body) >= 70 }

    suspend fun article(t: Topic): Article {
        val refs = t.all
        val ref = if (refs.isNotEmpty())
            "\nالمواد المرجعية (الوحيدة المسموح بالاستناد إليها للوقائع):" + refs.mapIndexed { i, r ->
                "\n[${i + 1}] المصدر: ${r.s.ifEmpty { "—" }}\nالعنوان: ${r.h}\nالتفصيل: ${r.d.ifEmpty { "—" }}\nالتاريخ: ${Instant.ofEpochMilli(r.t).toString().take(10)}"
            }.joinToString("\n")
        else "\nلا توجد مادة مرجعية: اكتب شرحاً عاماً دائماً دون أرقام أو وقائع حديثة."
        val base = "الموضوع: ${t.topic}" + (if (t.angle.isNotEmpty()) "\nالزاوية: ${t.angle}" else "") +
            (if (t.labels.isNotEmpty()) "\nتصنيفات مقترحة: ${t.labels.joinToString("، ")}" else "") + ref
        var meta: Meta? = null
        val used = LinkedHashSet<String>()
        val r1 = engine.chat(listOf(ChatMessage("system", sysMeta()), ChatMessage("user", "$base\nاكتب هيكل المقال بالصيغة الوسمية الآن.")),
            ChatOpts(max = 2600, validate = { meta = parseMeta(it, t) }))
        used += r1.provider
        val first = meta!!
        val secs = arrayOfNulls<Sec>(first.outline.size)
        var at = 0
        while (at < 3 && secs.any { it == null }) {
            val need = first.outline.withIndex().filter { secs[it.index] == null }
            var got = emptyList<Sec>()
            try {
                val r = engine.chat(
                    listOf(ChatMessage("system", sysSec()), ChatMessage("user",
                        "$base\n\nعنوان المقال: ${first.title}\nمقدمته: ${clamp(first.intro.replace(Regex("\\s+"), " "), 380)}\n\nاكتب هذه الأقسام فقط وبالترتيب:\n" + need.joinToString("\n") { "- ${it.value}" })),
                    ChatOpts(max = 4200, validate = { x -> got = parseSections(x); if (got.isEmpty()) throw IllegalStateException("لا أقسام صالحة") }),
                )
                used += r.provider
                got.take(need.size).forEachIndexed { k, g -> secs[need[k].index] = Sec(first.outline[need[k].index], g.body) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                bus.log("تعذّر استكمال الأقسام: " + clamp(e.message, 60), "w"); break
            }
            at++
        }
        val filled = secs.filterNotNull().map { Section(it.h, toHtml(it.body)) }
        val words = wc(first.intro) + wc(first.outro) + filled.sumOf { wc(stripTags(it.html)) } + first.faq.sumOf { wc(it.a) }
        if (filled.size < 3) throw IllegalStateException("أقسام قليلة")
        if (words < 360) throw IllegalStateException("مقال قصير ($words كلمة)")
        return Article(
            first.title, first.desc.ifEmpty { clamp(first.tldr, 155) }, first.slug, first.labels, first.kicker, first.motif, first.hue,
            first.infotitle, first.facts, first.tldr, toHtml(first.intro), filled, first.faq, toHtml(first.outro),
            refs.firstOrNull()?.let { Ref(it.h, it.url, "", 0, it.s) }, words, used.joinToString("+"), r1.model, t.topic, refs = refs.map { Ref(it.h, it.url, "", 0, it.s) },
        )
    }
}
