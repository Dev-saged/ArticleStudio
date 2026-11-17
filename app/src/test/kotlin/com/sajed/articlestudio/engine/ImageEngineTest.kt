package com.sajed.articlestudio.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
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
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageEngineTest {
    private val server = MockWebServer()
    private lateinit var store: ProviderStore
    private lateinit var eng: ImageEngine
    private val hits = ArrayList<String>()
    private val png = ByteArray(20_000) { (it % 251).toByte() }
    private val handlers = HashMap<String, (RecordedRequest) -> MockResponse>()

    @Before fun setup() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val mem = HashMap<String, String>()
        val secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        store = ProviderStore(ctx, secrets)
        eng = ImageEngine(OkHttpClient(), store)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val seg = request.path!!.trim('/').substringBefore('/').substringBefore('?')
                hits += seg
                return handlers[seg]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val u = server.url("/").toString()
        eng.bases["together"] = u + "together"
        eng.bases["img-pollinations"] = u + "poll/"
        eng.bases["img-pexels"] = u + "pexels"
        eng.bases["img-cloudflare"] = u + "cf/"
        eng.bases["gemini"] = u + "gem/"
        handlers["poll"] = { MockResponse().setBody(Buffer().write(png)) }
    }

    @After fun down() { server.shutdown() }

    private fun req() = ImgReq("a stadium at night", "stadium night", 3)
    private fun key(id: String, v: String) = store.save(id, "", "", v, true)

    @Test fun togetherFailureFallsBackToPollinationsThenIsSkipped() = runBlocking {
        key("together", "tk")
        handlers["together"] = { MockResponse().setResponseCode(500) }
        val first = eng.generate(req()) { it.size > 1000 }
        assertEquals("img-pollinations", first!!.source)
        assertEquals(1, eng.stat("together").fail)
        hits.clear()
        eng.generate(req()) { true }
        assertFalse(hits.contains("together"))
    }

    @Test fun togetherSuccessDecodesBase64() = runBlocking {
        key("together", "tk")
        handlers["together"] = { MockResponse().setBody("""{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(png)}"}]}""") }
        val h = eng.generate(req()) { true }
        assertEquals("together", h!!.source); assertEquals(png.size, h.bytes.size)
        assertEquals(1, eng.stat("together").ok)
    }

    @Test fun rejectedImageCountsAsFailureAndMovesOn() = runBlocking {
        key("together", "tk")
        handlers["together"] = { MockResponse().setBody("""{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(png)}"}]}""") }
        var n = 0
        val h = eng.generate(req()) { n++ > 0 }
        assertEquals("img-pollinations", h!!.source); assertEquals(1, eng.stat("together").fail)
    }

    @Test fun deniedKeyIsMarkedHard() = runBlocking {
        key("gemini", "gk")
        handlers["gem"] = { MockResponse().setResponseCode(403) }
        eng.generate(req()) { true }
        assertTrue(eng.stat("gemini").hard)
        assertTrue(eng.stat("gemini").last.contains("المفتاح"))
    }

    @Test fun pexelsSearchesThenDownloads() = runBlocking {
        key("img-pexels", "pk")
        handlers["poll"] = { MockResponse().setResponseCode(500) }
        handlers["pexels"] = { r ->
            if (r.getHeader("Authorization") != "pk") MockResponse().setResponseCode(401)
            else MockResponse().setBody("""{"photos":[{"src":{"large2x":"${server.url("/dl/a.jpg")}"}}]}""")
        }
        handlers["dl"] = { MockResponse().setBody(Buffer().write(png)) }
        val h = eng.generate(req()) { true }
        assertEquals("img-pexels", h!!.source)
    }

    @Test fun cloudflareRejectsMalformedKeyWithoutNetwork() = runBlocking {
        key("img-cloudflare", "no-colon-here")
        assertTrue(eng.test("img-cloudflare").contains("ACCOUNT_ID"))
        assertFalse(hits.contains("cf"))
    }

    @Test fun cloudflareParsesResultImage() = runBlocking {
        key("img-cloudflare", "0123456789abcdef0123456789abcdef:tok")
        handlers["cf"] = { r ->
            assertTrue(r.path!!.contains("0123456789abcdef0123456789abcdef/ai/run/@cf/black-forest-labs/flux-1-schnell"))
            MockResponse().setBody("""{"result":{"image":"${Base64.getEncoder().encodeToString(png)}"},"success":true}""")
        }
        val h = eng.generate(req()) { true }
        assertEquals("img-cloudflare", h!!.source)
    }

    @Test fun allSourcesFailingReturnsNull() = runBlocking {
        handlers["poll"] = { MockResponse().setResponseCode(500) }
        assertNull(eng.generate(req()) { true })
        assertNotNull(eng.stat("img-pollinations").last)
    }
}
