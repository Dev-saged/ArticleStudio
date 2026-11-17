package com.sajed.articlestudio.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.HistoryStore
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeneratorTest {
    private val server = MockWebServer()
    private lateinit var gen: Generator
    private var sectionCalls = 0

    private fun words(n: Int, w: String = "كلمة") = List(n) { w }.joinToString(" ")

    private val meta = """
[[title]] كيف يتفوق ريال مدريد على برشلونة في الكلاسيكو القادم
[[desc]] قراءة هادئة لمواجهة الكلاسيكو المرتقبة وما يحتاجه كل فريق لحسمها.
[[slug]] real-madrid-vs-barcelona-clasico-preview
[[labels]] الدوري الإسباني | تحليل
[[kicker]] تحليل فني
[[motif]] duel
[[hue]] 200
[[infotitle]] أرقام الكلاسيكو
[[fact]] 4|كلاسيكو متتالي
[[fact]] 12|هدفاً للفريقين
[[fact]] 3|بطاقات حمراء
[[fact]] 2024|آخر مواجهة
[[tldr]] مواجهة متقاربة يحسمها الوسط والتفاصيل الصغيرة.
[[intro]]
${words(45)}

${words(30)}
[[outline]]
- كيف تبدو المواجهة هذا الموسم؟
- ما أبرز نقاط القوة عند الفريقين؟
- من يملك أفضلية خط الوسط؟
- كيف يؤثر الغياب على الخطة؟
[[faq]] من الأقرب للفوز؟ | الأمر متقارب والتفاصيل ستحسم النتيجة النهائية.
[[faq]] متى تقام المباراة؟ | يحددها جدول الدوري الرسمي المعلن للجميع.
[[outro]]
${words(40)}
""".trimIndent()

    private fun sections() = listOf("كيف تبدو المواجهة هذا الموسم؟", "ما أبرز نقاط القوة عند الفريقين؟", "من يملك أفضلية خط الوسط؟", "كيف يؤثر الغياب على الخطة؟")
        .joinToString("\n") { "[[section]] $it\n${words(80)}\n\n${words(60)}" }

    private fun reply(t: String) = MockResponse().setBody("""{"choices":[{"message":{"content":${kotlinx.serialization.json.JsonPrimitive(t)}}}]}""")

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mem = HashMap<String, String>()
        val secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val b = request.body.readUtf8()
                return if ("اكتب هذه الأقسام" in b) { sectionCalls++; reply(sections()) } else reply(meta)
            }
        }
        server.start()
        val store = ProviderStore(ctx, secrets)
        store.save("pollinations", "", "", "", false)
        store.save("custom", "m", server.url("/v1").toString().trimEnd('/'), "k", true)
        val http = OkHttpClient()
        val bus = RunBus()
        gen = Generator(ProviderEngine(http, store, HealthStore(ctx), bus), http, bus, HistoryStore(ctx), StudioData(ctx), NewsSources(http))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun twoStagePipelineAssemblesAValidArticle() = runBlocking {
        val a = gen.article(SiteConfig("default", "سبورت لايف 24", "https://sport1live24.blogspot.com", niche = "sport"), Topic("الكلاسيكو", labels = listOf("أخبار"), ref = Ref("خبر", "https://espn.com/x", "انتهت المباراة بـ12 هدفاً في موسم 2024", 1_700_000_000_000)))
        assertEquals("duel", a.motif)
        assertEquals(200, a.hue)
        assertEquals(4, a.facts.size)
        assertEquals(4, a.sections.size)
        assertEquals(2, a.faq.size)
        assertTrue("الكلمات ${a.words}", a.words >= 360)
        assertEquals("real-madrid-vs-barcelona-clasico-preview", a.slug)
        assertEquals("https://espn.com/x", a.ref?.url)
        assertEquals(1, sectionCalls)
        assertTrue(a.sections.all { it.html.startsWith("<p>") })
    }
}
