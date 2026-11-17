package com.sajed.articlestudio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoServiceTest {
    private val server = MockWebServer()
    private lateinit var svc: PhotoService
    private lateinit var ctx: Context

    @Before fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        val mem = HashMap<String, String>()
        val secrets = object : SecretStore(ctx) {
            override fun put(name: String, value: String) { if (value.isEmpty()) mem.remove(name) else mem[name] = value }
            override fun get(name: String) = mem[name].orEmpty()
            override fun has(name: String) = mem.containsKey(name)
        }
        val store = ProviderStore(ctx, secrets)
        server.start()
        val http = OkHttpClient()
        val images = ImageEngine(http, store)
        images.bases["img-pollinations"] = server.url("/prompt/").toString()
        svc = PhotoService(ctx, images, ProviderEngine(http, store, HealthStore(ctx), RunBus())); svc.promptWriter = false
        File(ctx.cacheDir, "photos").deleteRecursively()
    }

    @After fun down() { server.shutdown() }

    private fun jpeg(noise: Boolean): ByteArray {
        val b = Bitmap.createBitmap(1344, 768, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        if (noise) { c.drawRect(0f, 0f, 1344f, 768f, Paint().apply { shader = LinearGradient(0f, 0f, 1344f, 768f, intArrayOf(Color.rgb(20, 90, 40), Color.rgb(230, 220, 120), Color.rgb(10, 20, 60)), null, Shader.TileMode.CLAMP) })
            val p = Paint(); for (i in 0 until 3000) { p.color = Color.rgb((i * 7) % 255, (i * 13) % 255, (i * 29) % 255); c.drawCircle((i * 37 % 1344).toFloat(), (i * 53 % 768).toFloat(), 14f, p) } }
        else c.drawColor(Color.rgb(30, 30, 30))
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    }

    @Test fun goodPhotoIsAcceptedAndCached() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(jpeg(true))))
        val a = sampleArticle()
        val p = svc.fetch(null, a)
        assertNotNull(p); assertEquals(1, server.requestCount)
        assertNotNull(svc.fetch(null, a)); assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().path!!.contains("width=1344"))
    }

    @Test fun blankOrBrokenPhotoFallsBackToNull() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(jpeg(false)))); server.enqueue(MockResponse().setResponseCode(500))
        assertNull(svc.fetch(null, sampleArticle("x2", "عنوان آخر لمقال ثان للاختبار فقط")))
    }

    @Test fun promptNeverContainsRealNamesOrText() {
        val p = PhotoPrompt.fallback(SiteConfig(id = "a", name = "x", url = "https://x.y", niche = "food"), sampleArticle())
        assertTrue(p.contains("no text") && p.contains("photorealistic")); assertTrue(!p.contains("ميسي"))
    }

    @Test fun photoCoverRendersAndDumps() {
        val data = StudioData(ctx)
        val r = ImageRenderer(ctx, data)
        val src = System.getenv("PHOTO")?.let { File(it).takeIf(File::exists)?.readBytes() } ?: jpeg(true)
        val photo = android.graphics.BitmapFactory.decodeByteArray(src, 0, src.size)
        val site = SiteConfig(id = "s", name = "سبورت لايف 24", url = "https://sport1live24.blogspot.com", niche = "sport")
        val out = File(System.getenv("RENDER_OUT") ?: "build/render").apply { mkdirs() }
        File(out, "photo-cover.png").outputStream().use { r.coverBitmap(sampleArticle(), site, photo).compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(out, "photo-card.png").outputStream().use { r.cardBitmap(sampleArticle(), site, photo).compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(out, "photo-cover.png").length() > 10_000)
    }
}
