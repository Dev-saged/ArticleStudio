package com.sajed.articlestudio.engine

import com.sajed.articlestudio.BrandConfig
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** بناء ملف استيراد بلوجر (Atom/Blogger) من المقالات الجاهزة. */
object XmlBuilder {
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun xe(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;")

    fun assignSlugs(list: List<Article>, used0: Set<String>): List<Article> {
        val used = HashSet(used0)
        return list.map { a ->
            var s = a.slug; var i = 2
            while (s in used) s = a.slug.take(56) + "-" + i++
            used += s
            a.copy(finalSlug = s)
        }
    }

    private fun fig(a: Article, base: String, kind: String, alt: String, cap: String?) =
        "<figure style=\"margin:0 0 1.2em;text-align:center\"><img src=\"$base${a.finalSlug}-$kind.jpg\" alt=\"${esc(alt)}\" width=\"1200\" height=\"630\" style=\"max-width:100%;height:auto;border-radius:18px\" ${if (kind == "cover") "fetchpriority=\"high\"" else "loading=\"lazy\""}/>${if (cap != null) "<figcaption>${esc(cap)}</figcaption>" else ""}</figure>"

    fun bodyHtml(a: Article, list: List<Article>, base: String, ym: String): String {
        val p = ArrayList<String>()
        p += "<p><strong>الخلاصة السريعة:</strong> ${esc(a.tldr)}</p>"
        p += a.intro
        p += fig(a, base, "cover", a.title, null)
        a.sections.forEachIndexed { i, s ->
            p += "<h2>${esc(s.h)}</h2>${s.html}"
            if (i == 1) p += fig(a, base, "info", "${a.infotitle} — ${BrandConfig.BLOG_NAME}", a.infotitle)
        }
        p += "<h2>أسئلة شائعة</h2>" + a.faq.joinToString("") { "<h3>${esc(it.q)}</h3><p>${esc(it.a)}</p>" }
        p += "<h2>الخلاصة</h2>" + a.outro
        val d = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn")).format(Date())
        if (a.sources.isNotEmpty()) p += "<h2>المصادر</h2><ul>" + a.sources.joinToString("") { r -> "<li><a href=\"${esc(r.url)}\" rel=\"noopener nofollow\" target=\"_blank\">${esc(r.s.ifEmpty { "المصدر" })}: ${esc(r.h)}</a></li>" } + "</ul><p><em>آخر مراجعة للمعلومات: ${esc(d)}. الأرقام الجارية تتغير مع كل جولة.</em></p>"
        else p += "<p><em>شرح عام يستند إلى معرفة متداولة في كرة القدم، والأرقام المحددة قابلة للتحديث. آخر مراجعة: ${esc(d)}.</em></p>"
        val rel = list.filter { it !== a }.map { x -> x to x.labels.count { it in a.labels } }.sortedByDescending { it.second }.take(3)
        if (rel.isNotEmpty()) p += "<h2>اقرأ أيضاً</h2><ul>" + rel.joinToString("") { (x, _) -> "<li><a href=\"/$ym/${x.finalSlug}.html\">${esc(x.title)}</a></li>" } + "</ul>"
        return bdi(p.joinToString(""))
    }

    fun build(list: List<Article>, base: String, scheduleHours: Int, now: Instant = Instant.now()): String {
        val head = """<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom' xmlns:blogger='http://schemas.google.com/blogger/2008' xmlns:gd='http://schemas.google.com/g/2005' xmlns:georss='http://www.georss.org/georss' xmlns:openSearch='http://a9.com/-/spec/opensearchrss/1.0/' xmlns:thr='http://purl.org/syndication/thread/1.0'>
<id>tag:blogger.com,1999:blog-8100000000000000001.archive</id>
<updated>${ISO.format(now)}</updated>
<title type='text'>${xe(BrandConfig.BLOG_NAME)}</title>
<generator version='7.00' uri='http://www.blogger.com'>Blogger</generator>
"""
        val zone = ZoneId.systemDefault()
        val entries = list.mapIndexed { i, a ->
            val d = if (scheduleHours <= 0) now.minusSeconds((list.size - i) * 9L * 60) else now.plusSeconds((i + 1) * scheduleHours * 3600L)
            val ld = d.atZone(zone)
            val ym = "${ld.year}/${pad(ld.monthValue)}"
            val id = "43" + now.toEpochMilli() + pad(i, 3)
            val cats = a.labels.joinToString("") { "<category scheme='http://www.blogger.com/atom/ns#' term='${xe(it)}'/>" }
            """<entry>
<id>tag:blogger.com,1999:blog-8100000000000000001.post-$id</id>
<published>${ISO.format(d)}</published>
<updated>${ISO.format(d)}</updated>
<category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/blogger/2008/kind#post'/>
$cats
<title type='text'>${xe(a.title)}</title>
<content type='html'>${xe(bodyHtml(a, list, base, ym))}</content>
<author><name>${xe(BrandConfig.BLOG_AUTHOR)}</name></author>
<blogger:type>POST</blogger:type>
<blogger:status>LIVE</blogger:status>
<blogger:filename>/$ym/${a.finalSlug}.html</blogger:filename>
<blogger:metaDescription>${xe(a.desc)}</blogger:metaDescription>
</entry>"""
        }.joinToString("\n")
        return head + entries + "\n</feed>\n"
    }
}
