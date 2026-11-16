package com.sajed.articlestudio.engine

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val item = PublishItem("عنوان تجريبي", "<p>محتوى</p>", listOf("أخبار", "تحليل"), null)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun googleResolvesBlogThenInsertsWithBearerAndDraftFlag() = runBlocking {
        server.enqueue(json("""{"id":"123","name":"مدونتي"}"""))
        server.enqueue(json("""{"id":"9","url":"https://b.blogspot.com/2026/11/x.html"}"""))
        val g = GooglePublisher(http, { "TOKEN" }, "https://b.blogspot.com", server.url("/blogger/v3").toString().trimEnd('/'))
        val r = g.publish(item, PubState.Live)
        assertEquals("9", r.id)
        val a = server.takeRequest(); assertTrue(a.path!!.startsWith("/blogger/v3/blogs/byurl")); assertEquals("Bearer TOKEN", a.getHeader("Authorization"))
        val b = server.takeRequest(); assertTrue(b.path, b.path!!.contains("/blogs/123/posts") && b.path!!.contains("isDraft=false"))
        assertTrue(b.body.readUtf8().contains("\"labels\":[\"أخبار\",\"تحليل\"]"))
    }

    @Test fun googleDraftSendsDraftFlagOnly() = runBlocking {
        server.enqueue(json("""{"id":"1","name":"n"}""")); server.enqueue(json("""{"id":"2","url":"u"}"""))
        val g = GooglePublisher(http, { "T" }, "https://b.blogspot.com", server.url("/v3").toString().trimEnd('/'))
        g.publish(item, PubState.Draft)
        server.takeRequest()
        val d = server.takeRequest(); assertTrue(d.path!!.contains("isDraft=true")); assertEquals(2, server.requestCount)
    }

    @Test fun googleErrorsAreClassified() = runBlocking {
        server.enqueue(json("""{"error":{"message":"expired"}}""", 401))
        val g = GooglePublisher(http, { "T" }, "https://b.blogspot.com", server.url("/v3").toString().trimEnd('/'))
        try { g.test(); fail() } catch (e: PublishException) { assertTrue(e.auth); assertFalse(e.retry) }
    }

    @Test fun retryRecoversFromTransientFailureButNotFromAuth() = runBlocking {
        var n = 0
        val v = withRetry(3, 1) { if (++n < 3) throw PublishException("busy", retry = true) else "ok" }
        assertEquals("ok", v); assertEquals(3, n)
        var m = 0
        try { withRetry(3, 1) { m++; throw PublishException("no", auth = true) }; fail() } catch (e: PublishException) { assertEquals(1, m) }
    }

    private fun feed(vararg e: String) = "<?xml version='1.0'?><feed xmlns='http://www.w3.org/2005/Atom' xmlns:blogger='http://schemas.google.com/blogger/2008'>" + e.joinToString("") + "</feed>"

    @Test fun xmlImportReadsPostsLabelsAndDropsBatchLinks() {
        val list = listOf(sampleArticle("a-one", "عنوان أول مفصل للمقال"), sampleArticle("b-two", "عنوان ثان مفصل للمقال").copy(labels = listOf("أخبار")))
            .mapIndexed { i, a -> a.copy(finalSlug = a.slug) }
        val xml = XmlBuilder.build(list, "https://cdn.example/", 0, site = SiteConfig("s", "تقنية", "https://t.example.com", niche = "tech"))
        val items = XmlImport.parse(xml)
        assertEquals(2, items.size)
        assertEquals("عنوان أول مفصل للمقال", items[0].title)
        assertTrue(items[0].labels.containsAll(listOf("أخبار", "كأس العالم")))
        assertFalse("روابط الدفعة تُحذف", items[0].html.contains("/b-two.html"))
        assertTrue(items[0].html.contains("cdn.example"))
    }

    @Test fun xmlImportSkipsNonPostsAndRejectsGarbage() {
        val xml = feed(
            "<entry><title>صفحة</title><content type='html'>x</content><blogger:type>PAGE</blogger:type></entry>",
            "<entry><title>تدوينة</title><content type='html'>&lt;p&gt;نص&lt;/p&gt;</content><blogger:type>POST</blogger:type><published>2030-01-01T10:00:00Z</published></entry>",
        )
        val items = XmlImport.parse(xml)
        assertEquals(1, items.size); assertEquals("<p>نص</p>", items[0].html); assertEquals(Instant.parse("2030-01-01T10:00:00Z"), items[0].at)
        try { XmlImport.parse("not xml"); fail() } catch (e: Exception) { }
    }

    @Test fun siteValidationCoversPublishingFields() {
        val s = SiteConfig("s", "موقع", "https://t.example.com", pubMode = "google")
        assertTrue(SiteRules.validate(s).isEmpty())
        assertTrue(SiteRules.validate(s.copy(pubMode = "weird")).containsKey("pubMode"))
        assertTrue(SiteRules.validate(s.copy(pubMode = "google")).isEmpty())
    }

    @Test fun scheduledPostIsCreatedAsDraftThenPublishedWithDate() = runBlocking {
        server.enqueue(json("""{"id":"B1","name":"مدونة"}"""))
        server.enqueue(json("""{"id":"P9","url":"https://b.blogspot.com/p9.html"}"""))
        server.enqueue(json("""{"id":"P9","url":"https://b.blogspot.com/p9.html"}"""))
        val api = server.url("/blogger/v3").toString().trimEnd('/')
        val g = GooglePublisher(http, { "TOKEN" }, "https://b.blogspot.com", api)
        val at = Instant.parse("2030-05-01T08:00:00Z")
        val r = g.publish(PublishItem(item.title, item.html, item.labels, at), PubState.Scheduled)
        assertEquals("P9", r.id)
        server.takeRequest()
        val ins = server.takeRequest()
        assertTrue(ins.path!!, ins.path!!.contains("isDraft=true"))
        val pub = server.takeRequest()
        assertTrue(pub.path!!, pub.path!!.contains("/posts/P9/publish") && pub.path!!.contains("publishDate=2030-05-01T08"))
    }
}
