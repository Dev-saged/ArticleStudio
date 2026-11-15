package com.sajed.articlestudio.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.HistoryStore
import com.sajed.articlestudio.data.JobEntity
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretStore
import com.sajed.articlestudio.ui.LiveStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobRunnerTest {
    private val server = MockWebServer()
    private lateinit var db: AppDb
    private lateinit var runner: JobRunner
    private var metaCalls = 0

    private fun words(n: Int) = List(n) { "كلمة" }.joinToString(" ")
    private val heads = listOf("كيف تبدو المواجهة هذا الموسم؟", "ما أبرز نقاط القوة عند الفريقين؟", "من يملك أفضلية خط الوسط؟", "كيف يؤثر الغياب على الخطة؟")
    private fun meta() = """
[[title]] عنوان تجريبي رقم ${++metaCalls} لمقال رياضي طويل بما يكفي
[[desc]] وصف تجريبي.
[[slug]] test-article-slug-$metaCalls
[[labels]] تحليل
[[kicker]] تحليل
[[motif]] ball
[[hue]] 100
[[infotitle]] أرقام
[[fact]] 1|أ
[[fact]] 2|ب
[[fact]] 3|ج
[[fact]] 4|د
[[tldr]] خلاصة.
[[intro]]
${words(60)}
[[outline]]
${heads.joinToString("\n")}
[[faq]] س1؟ | ج1 جواب كامل
[[faq]] س2؟ | ج2 جواب كامل
[[outro]]
${words(30)}
""".trimIndent()
    private fun sections() = heads.joinToString("\n") { "[[section]] $it\n${words(80)}\n\n${words(40)}" }
    private fun reply(t: String) = MockResponse().setBody("""{"choices":[{"message":{"content":${JsonPrimitive(t)}}}]}""")

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mem = HashMap<String, String>()
        val secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if ("اكتب هذه الأقسام" in request.body.readUtf8()) reply(sections()) else reply(meta())
        }
        server.start()
        val store = ProviderStore(ctx, secrets)
        store.save("pollinations", "", "", "", false)
        store.save("custom", "m", server.url("/v1").toString().trimEnd('/'), "k", true)
        val http = OkHttpClient(); val bus = RunBus()
        val gen = Generator(ProviderEngine(http, store, HealthStore(ctx), bus), http, bus, HistoryStore(ctx), StudioData(ctx), NewsSources(http))
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        runner = JobRunner(gen, db.jobs(), db.articles(), bus, LiveStatus(), BlogIndex(ctx, http))
    }

    @After fun tearDown() { server.shutdown(); db.close() }

    private fun await(target: Int) {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 40_000) {
            val s = runner.state.value
            if (s.jobId > 0 && !s.running && s.done + s.failed >= target) return
            Thread.sleep(100)
        }
        fail("لم تكتمل المهمة: ${runner.state.value}")
    }

    @Test fun jobGeneratesAndPersistsEveryArticle() = runBlocking {
        runner.start(2, PlanMode.Mine, "موضوع أول عن الكلاسيكو\nموضوع ثان عن التسلل")
        await(2)
        assertEquals(2, db.articles().countByStatus("ready"))
        val j = db.jobs().latest()!!
        assertEquals("done", j.state); assertEquals(2, j.next); assertEquals(2, j.done)
        val a = db.articles().ready().first()
        assertTrue(AppJson.decodeFromString(Article.serializer(), a.data).words >= 360)
    }

    @Test fun resumeContinuesFromTheStoredIndexOnly() = runBlocking {
        val topics = listOf(Topic("موضوع أول عن الكلاسيكو"), Topic("موضوع ثان عن التسلل"), Topic("موضوع ثالث عن الدفاع"))
        val id = db.jobs().upsert(JobEntity(total = 3, done = 1, state = "paused", createdAt = 1, topics = AppJson.encodeToString(ListSerializer(Topic.serializer()), topics), next = 1, mode = "Mine"))
        runner.resume()
        await(3)
        assertEquals(2, db.articles().ready().size)
        assertEquals(2, metaCalls)
        assertEquals("done", db.jobs().latest()!!.state)
        assertEquals(id, db.jobs().latest()!!.id)
    }
}
