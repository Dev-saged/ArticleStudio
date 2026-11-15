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

    @Test fun scriptBridgeFollowsRedirectAndSendsPayload() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/final").toString()))
        server.enqueue(json("""{"ok":true,"id":"77","url":"https://b.blogspot.com/p/77.html"}"""))
        val p = ScriptPublisher(http, server.url("/exec").toString(), "SECRET1", "https://b.blogspot.com")
        val r = p.publish(item, PubState.Draft)
        assertEquals("77", r.id); assertEquals("https://b.blogspot.com/p/77.html", r.url)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"secret\":\"SECRET1\"") && body.contains("\"draft\":true") && body.contains("\"action\":\"post\""))
        assertEquals("GET", server.takeRequest().method)
    }

    @Test fun scriptBridgeMapsErrors() = runBlocking {
        server.enqueue(json("""{"ok":false,"error":"السر غير مطابق (secret)"}"""))
        try { ScriptPublisher(http, server.url("/exec").toString(), "x", "https://b.blogspot.com").test(); fail() } catch (e: PublishException) { assertTrue(e.auth) }
        server.enqueue(MockResponse().setBody("<html>تسجيل الدخول</html>"))
        try { ScriptPublisher(http, server.url("/exec").toString(), "x", "https://b.blogspot.com").test(); fail() } catch (e: PublishException) { assertTrue(e.message!!.contains("exec")) }
    }

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

    @Test fun googleScheduledSendsFutureDateAndDraftSendsDraft() = runBlocking {
        val future = Instant.now().plusSeconds(7200)
        server.enqueue(json("""{"id":"1","name":"n"}""")); server.enqueue(json("""{"id":"2","url":"u"}""")); server.enqueue(json("""{"id":"3","url":"u"}"""))
        val g = GooglePublisher(http, { "T" }, "https://b.blogspot.com", server.url("/v3").toString().trimEnd('/'))
        g.publish(PublishItem("t", "c", emptyList(), future), PubState.Scheduled)
        g.publish(item, PubState.Draft)
        server.takeRequest()
        val s = server.takeRequest(); assertTrue(s.body.readUtf8().contains("\"published\""))
        val d = server.takeRequest(); assertTrue(d.path!!.contains("isDraft=true"))
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

    @Test fun bridgeScriptEmbedsSecretAndRequiredCalls() {
        val code = BridgeScript.TEMPLATE.replace("__SECRET__", "ABC123")
        assertTrue(code.contains("const SECRET = 'ABC123'"))
        assertTrue(code.contains("Blogger.Posts.insert") && code.contains("Blogger.Blogs.getByUrl") && code.contains("function doPost"))
        assertFalse(code.contains("__SECRET__"))
    }

    @Test fun siteValidationCoversPublishingFields() {
        val s = SiteConfig("s", "موقع", "https://t.example.com", pubMode = "script", scriptUrl = "http://evil.com/x")
        assertTrue(SiteRules.validate(s).containsKey("scriptUrl"))
        assertTrue(SiteRules.validate(s.copy(scriptUrl = "https://script.google.com/macros/s/AKfy/exec")).isEmpty())
        assertTrue(SiteRules.validate(s.copy(pubMode = "weird")).containsKey("pubMode"))
        assertTrue(SiteRules.validate(s.copy(pubMode = "google", scriptUrl = "")).isEmpty())
    }

    @Test fun managerGeneratesStableSecretPerSite() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = object : com.sajed.articlestudio.data.SecretStore(ctx) {
            private val m = HashMap<String, String>()
            override fun put(name: String, value: String) { m[name] = value }
            override fun get(name: String) = m[name].orEmpty()
            override fun has(name: String) = m.containsKey(name)
        }
        val sites = SiteStore(ctx)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, com.sajed.articlestudio.data.AppDb::class.java).allowMainThreadQueries().build()
        val pm = PublishManager(http, store, sites, db.articles(), GoogleAuth(ctx), BlogIndex(ctx, http))
        val a = pm.secretFor("default"); assertEquals(28, a.length); assertEquals(a, pm.secretFor("default"))
        assertTrue(pm.secretFor("other") != a)
        assertTrue(pm.script(sites.active()).contains(a))
    }
}
