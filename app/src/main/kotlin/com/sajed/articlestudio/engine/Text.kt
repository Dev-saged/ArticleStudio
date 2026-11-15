package com.sajed.articlestudio.engine

fun esc(s: String?): String = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private val LI = Regex("^([-*•]|\\d+[.)])\\s+")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val HEADING = Regex("^#{1,4}\\s+")
private fun inl(s: String) = esc(s).replace(BOLD, "<strong>$1</strong>")

/** نص عادي إلى HTML آمن (يُهرَّب كل شيء أولاً فلا حاجة لمنقّي). */
fun toHtml(text: String?): String =
    (text ?: "").replace("\r", "").split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") { b ->
        val ls = b.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        when {
            ls.size >= 2 && ls.all { it.startsWith("|") } -> table(ls)
            ls.all { LI.containsMatchIn(it) } -> {
                val o = if (ls[0][0].isDigit()) "ol" else "ul"
                "<$o>" + ls.joinToString("") { "<li>${inl(it.replace(LI, ""))}</li>" } + "</$o>"
            }
            ls.size == 1 && HEADING.containsMatchIn(ls[0]) -> "<h3>${inl(ls[0].replace(Regex("^#+\\s+"), ""))}</h3>"
            else -> "<p>${inl(ls.joinToString(" "))}</p>"
        }
    }

private fun table(ls: List<String>): String {
    val rows = ls.filterNot { r -> r.replace(Regex("[|:\\-\\s]"), "").isEmpty() }
        .map { r -> r.trim().trim('|').split('|').map { it.trim() } }
    if (rows.size < 2) return "<p>" + inl(ls.joinToString(" ")) + "</p>"
    val w = rows.maxOf { it.size }.coerceAtMost(4)
    fun cells(r: List<String>, t: String) = (0 until w).joinToString("") { i -> "<$t style=\"padding:8px 10px;border:1px solid rgba(128,128,128,.35);text-align:right\">${inl(r.getOrElse(i) { "" })}</$t>" }
    return "<div style=\"overflow-x:auto;margin:0 0 1.2em\"><table style=\"border-collapse:collapse;width:100%\"><thead><tr>${cells(rows[0], "th")}</tr></thead><tbody>" +
        rows.drop(1).joinToString("") { "<tr>${cells(it, "td")}</tr>" } + "</tbody></table></div>"
}

class Tag(val tag: String, val text: String)

private val THINK = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
private val FENCE = Regex("```[a-z]*\\n?", RegexOption.IGNORE_CASE)
private val TAGRE = Regex("\\[\\[\\s*([a-zA-Z_]+)\\s*\\]\\]")

fun parseTags(txt: String?): List<Tag> {
    val s = (txt ?: "").replace(THINK, "").replace(FENCE, "")
    val ms = TAGRE.findAll(s).toList()
    return ms.mapIndexed { i, m ->
        val end = if (i + 1 < ms.size) ms[i + 1].range.first else s.length
        Tag(m.groupValues[1].lowercase(), s.substring(m.range.last + 1, end).trim())
    }
}

private val ONE_TRIM = Regex("^[#>*\\s\"«]+|[*\"»\\s]+$")
fun one(s: String?): String = ONE_TRIM.replace((s ?: "").split("\n")[0], "").trim()
fun List<Tag>.tg(t: String): String = firstOrNull { it.tag == t }?.text ?: ""
fun List<Tag>.tgAll(t: String): List<String> = filter { it.tag == t }.map { it.text }

fun wc(s: String?): Int = (s ?: "").split(Regex("\\s+")).count { it.isNotEmpty() }
fun clamp(s: String?, n: Int): String {
    val x = (s ?: "").trim()
    return if (x.length > n) x.take(n - 1).trim() + "…" else x
}

fun slugify(s: String?, fb: String): String {
    val x = (s ?: "").lowercase().replace(Regex("[^a-z0-9\\s-]"), " ").trim().replace(Regex("\\s+"), "-").replace(Regex("-+"), "-").trim('-').take(60)
    return if (x.length >= 4) x else fb
}

private val ISO = Regex("\\d+(?:[-\\u2013/:.]\\d+)+")
private val BDI = Regex("\\d+(?:[-\\u2013]\\d+)+")
fun iso(s: String) = s.replace(ISO) { "⁦${it.value}⁩" }
fun bdi(s: String) = s.replace(BDI) { "<bdi>${it.value}</bdi>" }
fun pad(n: Int, l: Int = 2) = n.toString().padStart(l, '0')
fun stripTags(h: String) = h.replace(Regex("<[^>]+>"), " ")

private val DIAC = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
fun normAr(s: String): String = s.lowercase().replace(DIAC, "").replace(Regex("[أإآٱ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
private val STOP = ("في من على الى إلى عن مع ما هل كيف لماذا هذا هذه ذلك بين بعد قبل امام أمام ضد ثم او أو كل هو هي التي الذي قد لن لم ان أن إن حتى خلال عند ماذا متى اين أين اهم أهم اكثر أكثر").split(" ").map { normAr(it) }.toSet()

/** كلمات العنوان المميِّزة بعد التطبيع وإزالة أدوات التعريف والكلمات الشائعة. */
fun titleTokens(s: String): Set<String> = normAr(s).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+"))
    .map { w -> w.removePrefix("وال").removePrefix("بال").removePrefix("لل").removePrefix("ال") }
    .filter { it.length >= 2 && it !in STOP }.toSet()

/** تشابه عنوانين: ثلاث كلمات مشتركة على الأقل مع تغطية كبيرة للأقصر أو تطابق عام. */
fun similarTitles(a: String, b: String): Boolean {
    val x = titleTokens(a); val y = titleTokens(b)
    if (x.size < 3 || y.size < 3) return normAr(a).trim() == normAr(b).trim()
    val c = x.intersect(y).size
    return c >= 3 && (c.toDouble() / minOf(x.size, y.size) >= 0.8 || c.toDouble() / x.union(y).size >= 0.6)
}
