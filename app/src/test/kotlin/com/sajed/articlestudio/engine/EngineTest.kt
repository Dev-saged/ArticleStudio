package com.sajed.articlestudio.engine

import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

class EngineTest {
    @Test fun tagsParseAndStripThinkAndFences() {
        val t = parseTags("<think>x</think>```text\n[[title]] عنوان طويل بما يكفي\n[[fact]] 5|هدف\n[[fact]] 6|مباراة\n```")
        assertEquals("عنوان طويل بما يكفي", t.tg("title"))
        assertEquals(2, t.tgAll("fact").size)
    }

    @Test fun oneStripsMarkdownNoise() {
        assertEquals("نص", one("## **نص**\nسطر ثان"))
    }

    @Test fun htmlIsEscapedAgainstInjection() {
        val h = toHtml("<script>alert(1)</script> **غامق**")
        assertFalse(h.contains("<script"))
        assertTrue(h.contains("<strong>غامق</strong>"))
    }

    @Test fun listsBecomeHtmlLists() {
        assertEquals("<ul><li>أ</li><li>ب</li></ul>", toHtml("- أ\n- ب"))
        assertEquals("<ol><li>أ</li><li>ب</li></ol>", toHtml("1. أ\n2. ب"))
    }

    @Test fun slugFallsBackWhenNotLatin() {
        assertEquals("post-x", slugify("عنوان عربي", "post-x"))
        assertEquals("real-madrid-vs-barca", slugify("Real Madrid vs Barca!", "fb"))
    }

    @Test fun wordCountAndClamp() {
        assertEquals(3, wc("  واحد  اثنان\nثلاثة "))
        assertEquals(10, clamp("x".repeat(40), 10).length)
    }

    @Test fun qualityIsBoundedAndMonotonic() {
        val a = sampleArticle()
        assertTrue(a.quality in 1..100)
        assertTrue(a.copy(words = 100, faq = emptyList(), facts = emptyList()).quality < a.quality)
    }

    @Test fun articleJsonRoundTrip() {
        val a = sampleArticle()
        assertEquals(a, AppJson.decodeFromString(Article.serializer(), AppJson.encodeToString(Article.serializer(), a)))
    }

    @Test fun slugsAreDeduplicatedAgainstHistoryAndBatch() {
        val l = XmlBuilder.assignSlugs(listOf(sampleArticle(), sampleArticle(), sampleArticle("other-slug-here")), setOf("messi-world-cup-record"))
        assertEquals(3, l.map { it.finalSlug }.toSet().size)
        assertEquals("messi-world-cup-record-2", l[0].finalSlug)
    }

    @Test fun exportedXmlIsWellFormedAndComplete() {
        val l = XmlBuilder.assignSlugs(listOf(sampleArticle(title = "عنوان & «خاص» <مهم> 'اختبار' كامل"), sampleArticle("second-article-slug")), emptySet())
        val xml = XmlBuilder.build(l, "https://cdn.jsdelivr.net/gh/o/r@abc/images/articles/", 0, Instant.parse("2026-11-15T10:00:00Z"))
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals(2, doc.getElementsByTagName("entry").length)
        assertTrue(xml.contains("messi-world-cup-record-cover.jpg"))
        assertFalse(xml.contains("<script"))
    }

    @Test fun backdatedEntriesAreInThePast() {
        val now = Instant.parse("2026-11-15T10:00:00Z")
        val l = XmlBuilder.assignSlugs(listOf(sampleArticle()), emptySet())
        assertTrue(XmlBuilder.build(l, "b/", 0, now).contains("2026-11-15T09:51"))
        assertTrue(XmlBuilder.build(l, "b/", 6, now).contains("2026-11-15T16:00"))
    }

    @Test fun newSchemasValidate() {
        assertTrue(SchemaRules.validate(Schemas.providerCustom, mapOf("base" to "", "model" to "")).keys.containsAll(listOf("base", "model")))
        assertTrue(SchemaRules.validate(Schemas.github, mapOf("repo" to "a/b", "sched" to "100")).containsKey("sched"))
        assertTrue(SchemaRules.validate(Schemas.articleEdit, mapOf("title" to "x")).isEmpty())
    }
}
