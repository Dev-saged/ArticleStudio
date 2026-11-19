package com.sajed.articlestudio.social

/** صياغة نص المشاركة: اختيار أقوى عنوان صادق، وتركيبه مع الرابط والوسوم ضمن حدود كل منصة. */
object ShareText {
    private val marks = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    private fun norm(s: String) = marks.replace(s, "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه').lowercase()

    fun tokens(s: String): Set<String> = norm(s).split(splitter).filter { it.length >= 3 }.toSet()

    /** نسبة كلمات العنوان الأصلي الموجودة في الصياغة: حارس ضد صياغة مضلِّلة بعيدة عن المقال. */
    fun overlap(hook: String, title: String): Double {
        val t = tokens(title); if (t.isEmpty()) return 1.0
        val h = tokens(hook)
        return t.count { it in h }.toDouble() / t.size
    }

    private val curiosity = listOf("لماذا", "كيف", "سر", "اخطاء", "أخطاء", "حقيقه", "حقيقة", "الفرق", "قبل ان", "قبل أن", "ماذا", "هل")

    fun score(hook: String, title: String): Int {
        val n = hook.trim().length
        var s = 50
        if (n in 28..95) s += 15 else if (n > 140) s -= 20 else if (n < 18) s -= 15
        if (hook.any { it.isDigit() || it in '٠'..'٩' }) s += 10
        if (curiosity.any { hook.contains(it) }) s += 10
        if (hook.trim().endsWith("؟") || hook.trim().endsWith("?")) s += 5
        s += (overlap(hook, title) * 20).toInt()
        if (hook.count { it == '!' } > 1) s -= 10
        return s.coerceIn(0, 100)
    }

    fun parseHooks(raw: String): List<String> {
        val a = raw.indexOf('['); val b = raw.lastIndexOf(']')
        val body = if (a >= 0 && b > a) raw.substring(a, b + 1) else ""
        val fromJson = if (body.isEmpty()) emptyList() else runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(body).let { e -> (e as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content } }
        }.getOrDefault(emptyList())
        val l = fromJson.ifEmpty { raw.lines().map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "") } }
        return l.map { it.trim().trim('"') }.filter { it.length in 12..200 }.distinct()
    }

    /** أعلى صياغة اجتازت حارس التطابق؛ وإلا عنوان المقال نفسه. */
    private val digits = Regex("[0-9\u0660-\u0669]+")
    private fun latin(s: String) = digits.findAll(s).map { m -> m.value.map { c -> if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else c }.joinToString("") }.toSet()

    /** رقم في الصياغة غير موجود في مصدرها (العنوان والوصف): علامة اختلاق. */
    fun inventedNumber(hook: String, source: String): Boolean = (latin(hook) - latin(source)).isNotEmpty()

    fun pick(hooks: List<String>, title: String, source: String = title, minOverlap: Double = 0.4): String =
        hooks.filter { overlap(it, title) >= minOverlap && !inventedNumber(it, source) }.maxByOrNull { score(it, title) } ?: title.trim()

    fun hashtags(labels: List<String>, n: Int): String =
        labels.map { it.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_') }.filter { it.length in 2..24 }.distinct().take(n).joinToString(" ") { "#$it" }

    private fun len(s: String, url: String, urlLen: Int?): Int = if (urlLen != null && url.isNotEmpty()) s.length - url.length + urlLen else s.length

    private fun clip(hook: String, room: Int): String {
        if (hook.length <= room) return hook
        val cut = hook.take((room - 1).coerceAtLeast(1)).substringBeforeLast(' ', hook.take((room - 1).coerceAtLeast(1)))
        return cut.trimEnd('،', ',', ' ', ':') + "…"
    }

    /** النص الكامل (الصياغة + الرابط + وسوم)، بلا تجاوز حد المنصة. */
    fun compose(p: PlatformSpec, hook: String, url: String, labels: List<String>): String {
        val tags = hashtags(labels, p.tags)
        val tail = "\n\n$url" + if (tags.isEmpty()) "" else "\n$tags"
        val fixed = len(tail, url, p.urlLen)
        val h = clip(hook.trim(), (p.max - fixed).coerceAtLeast(20))
        val full = h + tail
        return if (len(full, url, p.urlLen) <= p.max) full else h + "\n\n$url"
    }

    /** الصياغة دون الرابط (لحقول المنصات التي تضيف الرابط وحدها، مثل نافذة مشاركة فيسبوك). */
    fun hookOnly(p: PlatformSpec, hook: String, labels: List<String>): String {
        val tags = hashtags(labels, p.tags)
        return clip(hook.trim(), p.max - tags.length - 2) + if (tags.isEmpty()) "" else "\n$tags"
    }
}

/** قواعد الإيقاع: سقف يومي وفاصل أدنى ونافذة ساعات، مع تفاوت طبيعي في التوقيت. */
object ShareRules {
    const val DAY = 24L * 3_600_000

    fun inWindow(from: Int, to: Int, hour: Int): Boolean = when {
        from == to || (from <= 0 && to >= 24) -> true
        from < to -> hour in from until to
        else -> hour >= from || hour < to
    }

    /** أقرب موعد مسموح للمهمة بعد `now`، أو `now` نفسه إن كانت مسموحة الآن. */
    fun allowedAt(now: Long, doneInDay: Int, cap: Int, lastDone: Long, gapMin: Int, winFrom: Int, winTo: Int, hourOf: (Long) -> Int, dayStart: Long): Long {
        var t = now
        if (cap > 0 && doneInDay >= cap) t = maxOf(t, dayStart + DAY)
        if (lastDone > 0) t = maxOf(t, lastDone + gapMin * 60_000L)
        var guard = 0
        while (!inWindow(winFrom, winTo, hourOf(t)) && guard++ < 48) t += 30 * 60_000L
        return t
    }

    fun jitter(baseMs: Long, pct: Int, r: kotlin.random.Random): Long = baseMs + (baseMs * pct / 100.0 * (r.nextDouble() * 2 - 1)).toLong()
    val backoffMin = longArrayOf(5, 15, 45)
}
