package com.sajed.articlestudio.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.KeyBackup
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class V3Test {
    @Test fun siteBackupFormat() {
        val b = KeyBackup.parse("""{"app":"article-studio","v":1,"exported":"2026-01-01T00:00:00Z","providers":[
          {"id":"groq","name":"Groq","enabled":true,"key":"gsk_abcdef123456"},
          {"id":"cerebras","enabled":false,"key":"csk-12345678"},
          {"id":"pollinations","enabled":true,"key":""},
          {"id":"custom","enabled":true,"key":"sk-custom-123456","base":"https://x.example/v1","model":"m1"},
          {"id":"nope","key":"zzzzzzzzzz"}],
          "github":{"repo":"a/b","branch":"imgs","token":"ghp_xxxxxxxxxxxx"}}""")
        assertEquals(listOf("groq", "cerebras", "pollinations", "custom"), b.entries.map { it.id })
        assertEquals(false, b.entries[1].enabled)
        assertEquals("https://x.example/v1", b.entries[3].base)
        assertEquals("imgs", b.git?.branch)
        assertEquals(listOf("nope"), b.unknown)
    }

    @Test fun lenientInputs() {
        val fenced = "﻿هذا ملفي:\n```json\n{\"app\":\"article-studio\",\"providers\":[{\"id\":\"google\",\"key\":\" AIza-test-123456 \"}]}\n```"
        assertEquals("gemini", KeyBackup.parse(fenced).entries.single().id)
        assertEquals("AIza-test-123456", KeyBackup.parse(fenced).entries.single().key)
        assertEquals("groq", KeyBackup.parse("{\"groq\":\"gsk_abcdef123456\"}").entries.single().id)
        val lines = KeyBackup.parse("GROQ_API_KEY=gsk_abcdef123456\nOpenRouter: sk-or-v1-abcdef\nrandom text")
        assertEquals(setOf("groq", "openrouter"), lines.entries.map { it.id }.toSet())
    }

    @Test fun badInputsRejected() {
        for (t in listOf("", "   ", "{\"app\":\"x\"}", "hello world")) {
            try { KeyBackup.parse(t); throw AssertionError("accepted: $t") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.isNotEmpty()) }
        }
    }

    @Test fun arabicNaming() {
        val d = LocalDate.of(2026, 11, 15)
        assertEquals("5 مقالات سبورت لايف 24 - 15-11-2026.xml", ExportNaming.fileName(5, d) { false })
        assertEquals("مقال واحد سبورت لايف 24 - 15-11-2026.xml", ExportNaming.fileName(1, d) { false })
        assertEquals("مقالان سبورت لايف 24 - 15-11-2026.xml", ExportNaming.fileName(2, d) { false })
        assertEquals("20 مقالاً سبورت لايف 24 - 15-11-2026.xml", ExportNaming.fileName(20, d) { false })
        val taken = setOf("10 مقالات سبورت لايف 24 - 15-11-2026.xml", "10 مقالات سبورت لايف 24 - 15-11-2026 (2).xml")
        assertEquals("10 مقالات سبورت لايف 24 - 15-11-2026 (3).xml", ExportNaming.fileName(10, d) { it in taken })
    }

    @Test fun titleSimilarity() {
        assertTrue(similarTitles("كيف يعمل نظام الفار في كرة القدم", "شرح نظام الفار في كرة القدم وكيف يعمل"))
        assertTrue(similarTitles("ريال مدريد يتغلب على برشلونة في الكلاسيكو", "الكلاسيكو: ريال مدريد يتغلب على برشلونة"))
        assertFalse(similarTitles("قواعد التسلل في كرة القدم", "أفضل لاعبي الدوري السعودي هذا الموسم"))
        assertFalse(similarTitles("ريال مدريد يفوز على برشلونة", "ريال مدريد يفوز على أتلتيكو مدريد بثلاثية"))
    }

    @Test fun blogFeedParse() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val bi = BlogIndex(ctx, OkHttpClient())
        val posts = bi.parse("""{"feed":{"entry":[{"title":{"${'$'}t":"شرح نظام الفار في كرة القدم"},"link":[{"rel":"replies","href":"x"},{"rel":"alternate","href":"https://s.blogspot.com/2026/10/var-explained.html"}]},{"title":{"${'$'}t":"بدون رابط"}}]}}""")
        assertEquals(2, posts.size)
        assertEquals("var-explained", posts[0].slug)
        assertEquals(emptyList<BlogPost>(), bi.parse("{\"feed\":{}}"))
    }

    @Test fun rssParseAndMerge() {
        val ns = NewsSources(OkHttpClient())
        val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        val xml = """<rss><channel>
          <item><title><![CDATA[الأهلي يفوز بالدربي &amp; يتصدر - الأهرام]]></title><link>https://a.example/1</link><pubDate>$now</pubDate><source url="x">الأهرام</source><description>&lt;p&gt;وصف الخبر&lt;/p&gt;</description></item>
          <item><title>قديم</title><link>https://a.example/2</link><pubDate>Mon, 01 Jan 2001 00:00:00 GMT</pubDate></item>
          <item><title>مكرر</title><link>https://a.example/3</link><pubDate>$now</pubDate></item></channel></rss>"""
        val r = ns.parseRss(xml, "RSS", System.currentTimeMillis() - 86_400_000L)
        assertEquals(2, r.size)
        val first = r.first { it.url.endsWith("/1") }
        assertEquals("الأهلي يفوز بالدربي & يتصدر", first.h)
        assertEquals("الأهرام", first.s)
        assertEquals("وصف الخبر", first.d)
        val merged = ns.merge(r + r.map { Ref(it.h, it.url + "x", it.d, it.t, it.s) }, setOf("https://a.example/3"))
        assertEquals(2, merged.size)
        assertNull(merged.firstOrNull { it.url.endsWith("/3") })
        assertNotNull(merged.firstOrNull())
    }

    @Test fun markdownTableBecomesHtmlTable() {
        val h = toHtml("فقرة\n\n| النظام | الاستخدام |\n|---|---|\n| 4-3-3 | هجوم |\n| 4-4-2 | توازن |")
        assertTrue(h.contains("<table") && h.contains("<th") && h.contains("4-4-2"))
        assertFalse(h.contains("---"))
    }

    @Test fun seoBodyHasAnswerBoxFaqSchemaAndBlogLinks() {
        val a = XmlBuilder.assignSlugs(listOf(sampleArticle()), emptySet()).first()
        val blog = listOf(BlogPost("شرح نظام الفار في كرة القدم", "var", "/2026/10/var.html"), BlogPost("عنوان لا علاقة له أبداً", "x", "/2026/10/x.html"))
        val xml = XmlBuilder.build(listOf(a), "b/", 0, blog = blog)
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val html = XmlBuilder.bodyHtml(a, listOf(a), "b/", "2026/11", blog)
        assertTrue(html.contains("الجواب السريع"))
        assertTrue(html.contains("\"@type\":\"FAQPage\""))
        assertTrue(html.contains("<time datetime="))
        assertTrue(html.contains("<h2>أبرز النقاط</h2>"))
        assertFalse(html.contains("</script><script"))
    }
}
