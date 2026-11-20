package com.sajed.articlestudio.social

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.ArticleEntity
import com.sajed.articlestudio.data.SecretStore
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.Article
import com.sajed.articlestudio.engine.PubState
import com.sajed.articlestudio.engine.RunBus
import com.sajed.articlestudio.engine.SiteConfig
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.engine.sampleArticle
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareTest {
    private val server = MockWebServer()
    private lateinit var db: AppDb
    private lateinit var store: ShareStore
    private lateinit var secrets: SecretStore
    private lateinit var sites: SiteStore
    private val url = "https://b.example/p/messi.html"

    class FakePub(var onShare: (Post) -> ShareResult = { ShareResult("https://p/1") }) : SocialPublisher {
        val posts = ArrayList<Post>()
        override suspend fun check(acc: SocialAccountEntity) = "ok"
        override suspend fun share(acc: SocialAccountEntity, post: Post): ShareResult { posts += post; return onShare(post) }
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mem = HashMap<String, String>()
        secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        sites = SiteStore(ctx)
        sites.upsert(SiteConfig(id = "default", name = "رياضة", url = "https://b.example", share = true, pubState = "live"))
        store = ShareStore(ctx, db.social(), sites, secrets, db.articles())
    }
    @After fun tearDown() { db.close(); server.shutdown() }

    private fun runner(pub: SocialPublisher, hooks: HookSource = object : HookSource { override suspend fun hooks(title: String, desc: String, labels: List<String>) = emptyList<String>() }) =
        ShareRunner(db.social(), db.articles(), hooks, { pub }, ShareNotifier(ApplicationProvider.getApplicationContext()), RunBus()).also { it.zone = ZoneId.of("UTC"); it.pauseBetweenMs = { 0 } }

    private var seq = 0
    private fun article(title: String = "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم"): Long = runBlocking {
        val a = sampleArticle(title = title)
        db.articles().upsert(ArticleEntity(jobId = 1, idx = seq++, title = a.title, slug = a.slug, status = "exported", updatedAt = 1, data = AppJson.encodeToString(Article.serializer(), a), siteId = "default", remoteUrl = url))
    }

    private suspend fun account(platform: String = "telegram", cap: Int = 3, gap: Int = 20, extra: Map<String, String> = mapOf("chat" to "@c")) =
        store.addAccount(platform, "حسابي", extra, "tok").also { db.social().markOk(it, 1) }.also { id -> db.social().account(id)!!.let { db.social().upsertAccount(it.copy(dailyCap = cap, gapMin = gap, winFrom = 0, winTo = 24)) } }

    // الصياغة
    @Test fun overlapGuardRejectsHookUnrelatedToTitle() {
        val title = "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم"
        assertTrue(ShareText.overlap("ميسي يحطم رقماً قياسياً في كأس العالم: كيف حدث؟", title) >= 0.4)
        assertEquals(title, ShareText.pick(listOf("اربح المال بسرعة من البيت الآن"), title))
    }

    @Test fun inventedNumbersAreRejected() {
        val title = "ميسي يسجل هدفاً في كأس العالم"
        assertTrue(ShareText.inventedNumber("ميسي يسجل 50 هدفاً في كأس العالم", title))
        assertFalse(ShareText.inventedNumber("ميسي يسجل ٢٦ هدفاً", "ميسي 26 هدفاً"))
        assertEquals(title, ShareText.pick(listOf("ميسي يسجل 50 هدفاً في كأس العالم"), title))
    }

    @Test fun pickPrefersStrongerHook() {
        val title = "أخطاء شائعة عند شحن الهاتف بالليل"
        val best = ShareText.pick(listOf("شحن الهاتف بالليل", "لماذا تضر أخطاء شحن الهاتف بالليل بالبطارية؟ أخطاء شائعة"), title)
        assertTrue(best.startsWith("لماذا"))
    }

    @Test fun composeRespectsPlatformLimitsAndKeepsUrl() {
        val long = "عنوان طويل جداً ".repeat(30)
        for (p in Platforms.all) {
            val t = ShareText.compose(p, long, url, listOf("أخبار", "كأس العالم", "رياضة"))
            val len = if (p.urlLen != null) t.length - url.length + p.urlLen else t.length
            assertTrue("${p.id} $len > ${p.max}", len <= p.max)
            assertTrue("${p.id} فقد الرابط", t.contains(url))
        }
        assertTrue(ShareText.compose(Platforms.of("x")!!, "عنوان", url, listOf("أخبار عاجلة")).contains("#أخبار_عاجلة"))
        assertFalse(ShareText.compose(Platforms.of("facebook")!!, "عنوان", url, listOf("أخبار")).contains("#"))
    }

    @Test fun parseHooksReadsJsonAndLines() {
        assertEquals(2, ShareText.parseHooks("هاك: [\"صياغة أولى طويلة كفاية\", \"صياغة ثانية طويلة كفاية\"]").size)
        assertEquals(2, ShareText.parseHooks("1. صياغة أولى طويلة كفاية\n2) صياغة ثانية طويلة كفاية").size)
    }

    // القواعد
    @Test fun rulesDeferWhenCapReachedAndKeepWindow() {
        val h = { t: Long -> Instant.ofEpochMilli(t).atZone(ZoneId.of("UTC")).hour }
        val now = 10 * 3_600_000L
        assertEquals(now, ShareRules.allowedAt(now, 0, 3, 0, 20, 0, 24, h, 0))
        assertEquals(24 * 3_600_000L, ShareRules.allowedAt(now, 3, 3, 0, 20, 0, 24, h, 0))
        assertEquals(now + 20 * 60_000L, ShareRules.allowedAt(now, 1, 3, now, 20, 0, 24, h, 0))
        assertEquals(12 * 3_600_000L, ShareRules.allowedAt(now, 0, 3, 0, 20, 12, 20, h, 0))
        assertTrue(ShareRules.inWindow(22, 6, 23) && ShareRules.inWindow(22, 6, 2) && !ShareRules.inWindow(22, 6, 12))
    }

    // الناشرون
    @Test fun telegramSendsMessageAndMapsErrors() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"message_id":7,"chat":{"username":"mych"}}}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"ok":false}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val id = account("telegram"); val acc = db.social().account(id)!!
        val p = TelegramPublisher(OkHttpClient(), secrets, server.url("").toString().trimEnd('/'))
        val r = p.share(acc, Post("نص\n\n$url", "نص", url))
        assertEquals("https://t.me/mych/7", r.postUrl)
        val rq = server.takeRequest(); assertTrue(rq.path!!.startsWith("/bottok/sendMessage")); assertTrue(rq.body.readUtf8().contains("\"chat_id\":\"@c\""))
        assertEquals("login_required", runCatching { p.share(acc, Post("x", "x", url)) }.exceptionOrNull().let { (it as ShareException).code })
        val e = runCatching { p.share(acc, Post("x", "x", url)) }.exceptionOrNull() as ShareException
        assertTrue(e.code == "rate" && e.retry)
    }

    @Test fun blueskyCreatesLinkFacetWithUtf8ByteOffsets() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"accessJwt":"jwt","did":"did:plc:1"}"""))
        server.enqueue(MockResponse().setBody("""{"uri":"at://did:plc:1/app.bsky.feed.post/abc","cid":"c"}"""))
        val id = store.addAccount("bluesky", "بلو", mapOf("handle" to "me.bsky.social"), "app-pass"); val acc = db.social().account(id)!!
        val p = BlueskyPublisher(OkHttpClient(), secrets, server.url("").toString().trimEnd('/'))
        val text = "عنوان عربي\n\n$url"
        val r = p.share(acc, Post(text, "عنوان عربي", url))
        assertEquals("https://bsky.app/profile/me.bsky.social/post/abc", r.postUrl)
        server.takeRequest()
        val body = AppJson.parseToJsonElement(server.takeRequest().body.readUtf8()).toString()
        val start = text.toByteArray().let { b -> String(b, Charsets.ISO_8859_1).indexOf(String(url.toByteArray(), Charsets.ISO_8859_1)) }
        assertTrue(body.contains("\"byteStart\":$start") && body.contains("\"byteEnd\":${start + url.length}"))
    }

    // الطابور
    @Test fun enqueueDedupesSkipsDraftAndSchedulesAfterPublishTime() = runBlocking {
        val acc = account(); val art = article()
        assertEquals(0, store.enqueue(art, "default", url, "ع", null, PubState.Draft))
        assertEquals(1, store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 1_000, r = Random(1)))
        assertEquals(0, store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 2_000))
        val j = db.social().jobsOfArticle(art).single()
        assertTrue(j.runAt in 1_000 + 120_000..1_000 + 480_000)
        val art2 = article("عنوان آخر")
        store.enqueue(art2, "default", url, "ع", Instant.ofEpochMilli(10_000_000), PubState.Scheduled, now = 1_000)
        assertTrue(db.social().jobsOfArticle(art2).single().runAt >= 10_000_000 + 5 * 60_000L)
        assertNotNull(acc)
    }

    @Test fun enqueueRespectsSiteSwitchAndAccountSites() = runBlocking {
        val id = account(); val art = article()
        sites.upsert(sites.get("default").copy(share = false))
        assertEquals(0, store.enqueue(art, "default", url, "ع", null, PubState.Live))
        sites.upsert(sites.get("default").copy(share = true))
        db.social().upsertAccount(db.social().account(id)!!.copy(siteIds = "other"))
        assertEquals(0, store.enqueue(art, "default", url, "ع", null, PubState.Live))
    }

    @Test fun runnerPublishesComposedTextOnce() = runBlocking {
        account(); val art = article(); store.enqueue(art, "default", url, "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم", null, PubState.Live, now = 0, r = Random(2))
        val pub = FakePub(); val r = runner(pub)
        assertEquals(1, r.tick(now = 10 * 60_000L))
        assertEquals(0, r.tick(now = 20 * 60_000L))
        assertEquals(1, pub.posts.size)
        assertTrue(pub.posts[0].text.contains(url) && pub.posts[0].hook.isNotBlank())
        val j = db.social().jobsOfArticle(art).single(); assertEquals("done", j.status); assertEquals("https://p/1", j.postUrl)
    }

    @Test fun runnerUsesBestAiHookThatPassesGuards() = runBlocking {
        account(); val art = article(); store.enqueue(art, "default", url, "كيف حطم ميسي رقماً قياسياً جديداً في كأس العالم", null, PubState.Live, now = 0, r = Random(3))
        val hooks = object : HookSource { override suspend fun hooks(title: String, desc: String, labels: List<String>) = listOf("لماذا حطم ميسي رقماً قياسياً جديداً في كأس العالم؟", "ميسي يسجل 99 هدفاً قياسياً في كأس العالم") }
        val pub = FakePub(); runner(pub, hooks).tick(now = 10 * 60_000L)
        assertTrue(pub.posts.single().text.startsWith("لماذا"))
    }

    @Test fun runnerHonorsDailyCapAndGap() = runBlocking {
        val id = account(cap = 1, gap = 60)
        val a1 = article("مقال أول عن ميسي"); val a2 = article("مقال ثان عن رونالدو")
        store.enqueue(a1, "default", url, "مقال أول عن ميسي", null, PubState.Live, now = 0, r = Random(1))
        store.enqueue(a2, "default", url, "مقال ثان عن رونالدو", null, PubState.Live, now = 0, r = Random(1))
        val pub = FakePub(); val r = runner(pub)
        val t = 10 * 60_000L
        assertEquals(2, r.tick(now = t))
        assertEquals(1, pub.posts.size)
        val waiting = db.social().jobsOfArticle(a2).single().takeIf { it.status == "pending" } ?: db.social().jobsOfArticle(a1).single()
        assertEquals("pending", waiting.status); assertTrue(waiting.runAt > t + 24 * 3_600_000L - t)
        assertEquals(id.isNotEmpty(), true)
    }

    @Test fun networkErrorsRetryWithBackoffThenFail() = runBlocking {
        account(); val art = article(); store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 0, r = Random(1))
        val r = runner(FakePub { throw ShareException("network", "انقطع", retry = true) })
        var now = 10 * 60_000L
        repeat(3) { r.tick(now); val j = db.social().jobsOfArticle(art).single(); assertEquals("pending", j.status); assertTrue(j.runAt > now); now = j.runAt + 1 }
        r.tick(now)
        assertEquals("failed", db.social().jobsOfArticle(art).single().status)
    }

    @Test fun loginRequiredParksJobsAndRelogRequeues() = runBlocking {
        val id = account(); val art = article(); store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 0, r = Random(1))
        runner(FakePub { throw ShareException("login_required", "الجلسة منتهية") }).tick(10 * 60_000L)
        assertEquals("needs_login", db.social().account(id)!!.status)
        assertEquals("needs_you", db.social().jobsOfArticle(art).single().status)
        store.markLoggedIn(id, now = 50)
        val j = db.social().jobsOfArticle(art).single()
        assertEquals("pending", j.status); assertEquals("ok", db.social().account(id)!!.status)
    }

    @Test fun uiChangeKeepsDiagnosticsAndDoesNotRetryBlindly() = runBlocking {
        account(); val art = article(); store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 0, r = Random(1))
        val pub = FakePub { throw ShareException("ui_changed", "لم أجد زر النشر", diag = """{"buttons":["Share"]}""") }
        runner(pub).tick(10 * 60_000L)
        val j = db.social().jobsOfArticle(art).single()
        assertEquals("needs_you", j.status); assertTrue(j.error.contains("Share")); assertEquals(1, pub.posts.size)
    }

    @Test fun interruptedSendIsParkedNotReposted() = runBlocking {
        account(); val art = article(); store.enqueue(art, "default", url, "ع", null, PubState.Live, now = 0, r = Random(1))
        db.social().updateJob(db.social().jobsOfArticle(art).single().copy(status = "running"))
        val pub = FakePub(); runner(pub).tick(10 * 60_000L)
        assertEquals(0, pub.posts.size); assertEquals("needs_you", db.social().jobsOfArticle(art).single().status)
    }

    @Test fun deletingAccountRemovesJobsAndSecret() = runBlocking {
        val id = account(); store.enqueue(article(), "default", url, "ع", null, PubState.Live, now = 0)
        store.remove(id)
        assertNull(db.social().account(id)); assertEquals(0, db.social().pendingCount()); assertFalse(secrets.has("soc.$id"))
    }

    // تيليغرام: أخطاء واضحة
    @Test fun telegramMapsForbiddenAndChatNotFoundToRejectedNotLogin() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"ok":false,"description":"Forbidden: bot is not a member of the channel chat"}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"ok":false,"description":"Bad Request: chat not found"}"""))
        val acc = db.social().account(account("telegram"))!!
        val p = TelegramPublisher(OkHttpClient(), secrets, server.url("").toString().trimEnd('/'))
        val e1 = runCatching { p.share(acc, Post("x", "x", url)) }.exceptionOrNull() as ShareException
        val e2 = runCatching { p.share(acc, Post("x", "x", url)) }.exceptionOrNull() as ShareException
        assertEquals("rejected", e1.code); assertTrue(e1.message!!.contains("مشرف"))
        assertEquals("rejected", e2.code); assertTrue(e2.message!!.contains("المعرّف"))
    }

    @Test fun telegramCheckRequiresAdminInChannels() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"id":99,"username":"b_bot"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"type":"channel"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"status":"member"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"id":99,"username":"b_bot"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"type":"channel"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"status":"administrator"}}"""))
        val acc = db.social().account(account("telegram"))!!
        val p = TelegramPublisher(OkHttpClient(), secrets, server.url("").toString().trimEnd('/'))
        assertEquals("rejected", (runCatching { p.check(acc) }.exceptionOrNull() as ShareException).code)
        assertTrue(p.check(acc).contains("@b_bot"))
    }

    @Test fun setCredentialsUpdatesTargetAndSecretAndResetsStatus() = runBlocking {
        val id = account("telegram"); db.social().setStatus(id, "needs_login", "x")
        store.setCredentials(id, mapOf("chat" to "@new"), "newtok")
        val a = db.social().account(id)!!
        assertTrue(a.extra.contains("@new")); assertEquals("new", a.status); assertEquals("newtok", secrets.get("soc.$id"))
        store.setCredentials(id, mapOf("chat" to ""), "")
        assertTrue(db.social().account(id)!!.extra.contains("@new")); assertEquals("newtok", secrets.get("soc.$id"))
    }

    // السجل والنشر التلقائي للقديم
    @Test fun catchUpQueuesOldestFirstWithinDailyCapAndNeverDuplicates() = runBlocking {
        val id = account(cap = 2); val a1 = article("مقال أول"); val a2 = article("مقال ثان"); val a3 = article("مقال ثالث")
        assertEquals(2, store.catchUp(now = 1_000_000, r = Random(1)))
        assertEquals(listOf(a1, a2), db.social().articleIdsOf(id).sorted().take(2))
        assertEquals(0, db.social().jobsOfArticle(a3).size)
        assertEquals(0, store.catchUp(now = 1_100_000, r = Random(1)))
        db.social().jobsOfArticle(a1).single().let { db.social().updateJob(it.copy(status = "done", doneAt = 1_200_000)) }
        db.social().jobsOfArticle(a2).single().let { db.social().updateJob(it.copy(status = "done", doneAt = 1_210_000)) }
        assertEquals(0, store.catchUp(now = 1_300_000, r = Random(1)))
        assertEquals(1, store.catchUp(now = 1_300_000 + 24 * 3_600_000L, r = Random(1)))
        assertEquals(1, db.social().jobsOfArticle(a3).size)
    }

    @Test fun catchUpHonorsSwitchesDraftAndLoginState() = runBlocking {
        val id = account(cap = 3); article("مقال")
        store.setBackfill(false); assertEquals(0, store.catchUp(now = 1_000_000)); store.setBackfill(true)
        db.social().setStatus(id, "needs_login", "x"); assertEquals(0, store.catchUp(now = 1_000_000)); db.social().markOk(id, 1)
        sites.upsert(sites.get("default").copy(pubState = "draft")); assertEquals(0, store.catchUp(now = 1_000_000))
        sites.upsert(sites.get("default").copy(pubState = "live", share = false)); assertEquals(0, store.catchUp(now = 1_000_000))
        sites.upsert(sites.get("default").copy(share = true)); assertEquals(1, store.catchUp(now = 1_000_000))
    }

    @Test fun shareNowQueuesOnceAndRequeuesFailures() = runBlocking {
        account(); val art = article()
        assertEquals(1, store.shareNow(art, now = 5_000)); assertEquals(0, store.shareNow(art, now = 6_000))
        db.social().jobsOfArticle(art).single().let { db.social().updateJob(it.copy(status = "failed", error = "x")) }
        assertEquals(1, store.shareNow(art, now = 7_000))
        assertEquals("pending", db.social().jobsOfArticle(art).single().status)
        db.social().jobsOfArticle(art).single().let { db.social().updateJob(it.copy(status = "done")) }
        assertEquals(0, store.shareNow(art, now = 8_000))
    }

    // الوصفات
    @Test fun bundledRecipesAreValidAndRemoteOnesAreRestrictedToKnownOps() {
        val f = listOf(File("src/main/assets/social/recipes.json"), File("app/src/main/assets/social/recipes.json")).first { it.exists() }
        assertNull(RecipeRules.validate(f.readText()))
        assertNotNull(RecipeRules.validate("""{"platforms":{"x":{"variants":[{"url":"http://x","steps":[]}]}}}"""))
        assertNotNull(RecipeRules.validate("""{"platforms":{"x":{"variants":[{"url":"https://x","steps":[{"op":"eval","final":true}]}]}}}"""))
        assertNotNull(RecipeRules.validate("not json"))
        assertEquals("https://x.com/i?t=%D8%B9&u=https%3A%2F%2Fa.b%2Fc", RecipeRules.expand("https://x.com/i?t={text_enc}&u={url_enc}", mapOf("text" to "ع", "url" to "https://a.b/c")))
    }
}
