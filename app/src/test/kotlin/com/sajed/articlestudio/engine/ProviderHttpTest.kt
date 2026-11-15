package com.sajed.articlestudio.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class ProviderHttpTest {
    private val server = MockWebServer()
    private lateinit var engine: ProviderEngine
    private val long = "نص عربي طويل بما يكفي لتجاوز حد العشرين حرفاً بسهولة تامة"

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mem = HashMap<String, String>()
        val secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        val store = ProviderStore(ctx, secrets)
        server.start()
        store.save("pollinations", "", "", "", false)
        store.save("custom", "test-model", server.url("/v1").toString().trimEnd('/'), "sk-test-key", true)
        engine = ProviderEngine(OkHttpClient(), store, HealthStore(ctx), RunBus())
    }

    @After fun tearDown() = server.shutdown()

    private fun ok(t: String) = MockResponse().setBody("""{"choices":[{"message":{"content":"$t"}}]}""")
    private val msgs = listOf(ChatMessage("user", "اكتب"))
    private fun fails(o: ChatOpts = ChatOpts(noWait = true)) = runBlocking { try { engine.chat(msgs, o); fail("expected failure"); "" } catch (e: IllegalStateException) { e.message.orEmpty() } }

    @Test fun successSendsBearerAndModel() = runBlocking {
        server.enqueue(ok(long))
        val r = engine.chat(msgs, ChatOpts(noWait = true))
        assertEquals(long, r.text); assertEquals("test-model", r.model)
        val rq = server.takeRequest()
        assertEquals("/v1/chat/completions", rq.path)
        assertEquals("Bearer sk-test-key", rq.getHeader("Authorization"))
        assertTrue(rq.body.readUtf8().contains("\"model\":\"test-model\""))
    }

    @Test fun rateLimitCoolsTheModel() {
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(fails().startsWith("فشل كل المزودين"))
        assertTrue(engine.coolSeconds("custom") > 50)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectedKeyBlocksProviderForAnHour() {
        server.enqueue(MockResponse().setResponseCode(401))
        fails()
        assertTrue(engine.coolSeconds("custom") > 3_000)
    }

    @Test fun emptyReplyIsRejected() {
        server.enqueue(ok("قصير"))
        fails()
        assertEquals(1, engine.stat("custom").fail)
    }

    @Test fun validatorRejectionCountsAsFailure() {
        server.enqueue(ok(long))
        fails(ChatOpts(noWait = true, validate = { throw IllegalStateException("غير صالح") }))
        assertEquals("غير صالح", engine.stat("custom").last)
    }
}
