package com.sajed.articlestudio.engine

/** حارس المجال: يمنع دخول مقال رياضي إلى موقع تقني وهكذا، بمعجم جذور لكل مجال. */
object NicheGuard {
    private val LEX: Map<String, List<String>> = mapOf(
        "sport" to listOf("مباراة", "الدوري", "لاعب", "مدرب", "هدف", "كرة القدم", "كأس العالم", "فريق", "نادي", "ملعب", "حارس المرمى", "التسلل", "المنتخب", "الانتقالات", "بطولة", "تشكيلة", "ركلة", "مهاجم", "مدافع", "سباق"),
        "tech" to listOf("تطبيق", "هاتف", "ذكاء اصطناعي", "برمجة", "معالج", "نظام التشغيل", "أندرويد", "آيفون", "خوارزمية", "بيانات", "سحابي", "شبكة", "أمن رقمي", "تحديث", "برنامج", "حاسوب", "روبوت", "نموذج لغوي", "تقنية", "إنترنت"),
        "health" to listOf("صحة", "مرض", "علاج", "فيتامين", "تغذية", "طبيب", "أعراض", "ضغط الدم", "سكري", "لياقة", "نوم", "مناعة", "دواء", "جسم", "وقاية", "قلب", "سعرات", "تمارين"),
        "food" to listOf("وصفة", "طبخ", "مكونات", "فرن", "طهي", "حلويات", "مطبخ", "توابل", "شوربة", "خبز", "لحم", "دجاج", "أرز", "عجينة", "مقادير", "سلطة"),
        "business" to listOf("اقتصاد", "استثمار", "أسهم", "سوق", "شركة", "تضخم", "ريادة", "أرباح", "بنك", "عملة", "ميزانية", "تجارة", "تمويل", "مبيعات", "أسعار الفائدة", "بورصة"),
        "travel" to listOf("سفر", "سياحة", "فندق", "تأشيرة", "رحلة", "وجهة", "مطار", "حجز", "جولة", "شاطئ", "سائح", "طيران", "جواز"),
        "education" to listOf("طالب", "دراسة", "جامعة", "منحة", "تعلم", "مدرسة", "امتحان", "مهارات", "شهادة", "مناهج", "لغة", "معلم", "تحصيل"),
    )

    private fun norm(s: String) = s.replace(Regex("[\\u064B-\\u0652\\u0640]"), "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')

    private fun hits(text: String, words: List<String>) = words.count { norm(text).contains(norm(it)) }

    /** اسم المجال الغريب إن غلب على النص مجال آخر، وإلا null. */
    fun foreign(site: SiteConfig, text: String): String? {
        if (text.length < 40 || site.niche !in LEX) return null
        val own = hits(text, LEX.getValue(site.niche)) + hits(text, site.keywords)
        val other = LEX.filterKeys { it != site.niche }.mapValues { hits(text, it.value) }.maxByOrNull { it.value } ?: return null
        return if (other.value >= 3 && other.value > own * 2) Niches.of(other.key).label else null
    }

    /** للعناوين القصيرة: غريب إن ظهرت فيه مفردة مجال آخر ولا مفردة من مجال الموقع. */
    fun foreignTopic(site: SiteConfig, title: String): String? {
        if (site.niche !in LEX || title.length < 8) return null
        val own = hits(title, LEX.getValue(site.niche)) + hits(title, site.keywords)
        val other = LEX.filterKeys { it != site.niche }.mapValues { hits(title, it.value) }.maxByOrNull { it.value } ?: return null
        return if (other.value >= 1 && own == 0) Niches.of(other.key).label else null
    }

    fun fits(site: SiteConfig, text: String) = foreign(site, text) == null
}

/** حارس الوقائع: لا أرقام ولا اقتباسات غير مسندة للمادة المرجعية. */
object FactGuard {
    private val NUM = Regex("[0-9\\u0660-\\u0669]+(?:[.,\\u066B][0-9\\u0660-\\u0669]+)?")
    private val SPLIT = Regex("(?<=[.!؟?۔])\\s+")
    private val SAID = Regex("قال|صرّح|صرح|أعلن|أكد|وفق|بحسب")

    private fun digits(s: String) = buildString { for (c in s) append(if (c in '٠'..'٩') ('0' + (c - '٠')) else if (c == '٫' || c == ',') '.' else c) }

    private fun nums(s: String) = NUM.findAll(s).map { digits(it.value).trimEnd('.') }.toList()

    /** الأرقام المسموحة: ما ورد في المصادر والموضوع؛ والأعداد 0-10 مسموحة دائماً. */
    fun allowed(vararg src: String): Set<String> = src.flatMap { nums(it) }.toSet()

    fun unsupported(text: String, allowed: Set<String>): List<String> =
        nums(text).filter { n -> n !in allowed && !(n.toDoubleOrNull()?.let { it <= 10.0 && it == Math.floor(it) } ?: false) }.distinct()

    /** يحذف الجمل المشتملة على أرقام غير مسندة (وعلى اقتباسات منسوبة عند غياب المراجع). يعيد النص وعدد المحذوف. */
    fun sanitize(body: String, allowed: Set<String>, hasRefs: Boolean): Pair<String, Int> {
        var removed = 0
        val out = body.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull line
            val kept = line.split(SPLIT).filter { s ->
                val bad = unsupported(s, allowed).isNotEmpty() || (!hasRefs && s.contains('«') && SAID.containsMatchIn(s))
                if (bad) removed++
                !bad
            }
            kept.joinToString(" ").ifBlank { null }
        }
        return out.joinToString("\n") to removed
    }
}

/** نبرة وأسلوب خاصان بكل مجال. */
internal fun toneGuide(niche: String) = when (niche) {
    "sport" -> "نبرة محرر رياضي حيوي: جمل قصيرة نابضة، تحليل تكتيكي بسيط، بلا تعصب لفريق."
    "tech" -> "نبرة محرر تقني دقيق: عرّف المصطلح بنصف جملة، فرّق بين المؤكد والمتوقع، ولا تروّج لمنتج."
    "health" -> "نبرة صحية مسؤولة: معلومات عامة فقط، لا تشخّص ولا تصف جرعات، وانصح بمراجعة الطبيب عند الحاجة."
    "food" -> "نبرة طهاة ودودة: خطوات واضحة وتوقيتات تقريبية ونصائح بدائل، دون ادعاءات صحية."
    "business" -> "نبرة اقتصادية رصينة: وضّح المخاطر، ولا تقدّم توصية استثمار، وميّز الرأي عن الواقعة."
    "travel" -> "نبرة مسافر خبير: نصائح عملية، وذكّر بأن الأسعار والتأشيرات تتغير فتُراجع المصدر الرسمي."
    "education" -> "نبرة معلم ملهم: تدرّج من البسيط للمعقد وأمثلة تطبيقية."
    "news" -> "نبرة خبرية محايدة: الواقعة أولاً ثم السياق، وانسب كل ادعاء لمصدره."
    else -> "نبرة واضحة محترفة."
}
