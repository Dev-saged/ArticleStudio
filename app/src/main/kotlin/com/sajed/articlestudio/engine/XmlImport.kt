package com.sajed.articlestudio.engine

import org.w3c.dom.Element
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/** يقرأ ملف XML بصيغة Blogger (المُصدَّر من التطبيق أو من بلوجر) ويحوّل كل تدوينة إلى عنصر نشر. */
object XmlImport {
    private val LINK = Regex("<li><a href=\"(/[^\"]+)\">[^<]*</a></li>")

    private fun Element.text(tag: String) = getElementsByTagName(tag).item(0)?.textContent.orEmpty()

    fun parse(xml: String): List<PublishItem> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false; isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = f.newDocumentBuilder().parse(xml.byteInputStream())
        val nodes = doc.getElementsByTagName("entry")
        val entries = (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.text("blogger:type").ifEmpty { "POST" } == "POST" }
        val paths = entries.map { it.text("blogger:filename") }.filter { it.isNotEmpty() }.toSet()
        return entries.mapNotNull { e ->
            val title = e.text("title").trim()
            val html = e.text("content")
            if (title.isEmpty() || html.isBlank()) return@mapNotNull null
            val cats = e.getElementsByTagName("category")
            val labels = (0 until cats.length).map { cats.item(it) as Element }.map { it.getAttribute("term") }
                .filter { it.isNotEmpty() && !it.startsWith("http://schemas.google.com") }
            val at = runCatching { Instant.parse(e.text("published").trim()) }.getOrNull()
            PublishItem(title, dropBatchLinks(html, paths), labels, at)
        }
    }

    /** روابط «اقرأ أيضاً» لمقالات الدفعة نفسها تنكسر عند النشر المباشر لأن بلوجر يولّد الرابط بنفسه، فنحذفها. */
    internal fun dropBatchLinks(html: String, paths: Set<String>): String {
        if (paths.isEmpty()) return html
        var out = LINK.replace(html) { m -> if (m.groupValues[1] in paths) "" else m.value }
        out = out.replace(Regex("<h2>اقرأ أيضاً</h2><ul></ul>"), "")
        return out
    }
}
