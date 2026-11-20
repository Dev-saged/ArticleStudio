package com.sajed.articlestudio.social

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.sajed.articlestudio.engine.AppJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** نتيجة محاولة أتمتة: ok، أو فشل برمز وتشخيص مختصر للأزرار الظاهرة (لإصلاح الوصفة). */
class AutoOutcome(val ok: Boolean, val code: String = "", val msg: String = "", val diag: String = "")

/** يشغّل صفحات المنصات في WebView مخفي بملف تعريف دائم لكل حساب، فتبقى الجلسة (كوكيز وتخزين) بين التشغيلات. */
@Singleton
class WebAutomator @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())

    fun multiProfile() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private val lib by lazy { ctx.assets.open("social/automation.js").bufferedReader().use { it.readText() } }

    /** اسم ملف التعريف: حروف وأرقام وشرطات فقط. */
    fun profileName(accountId: String) = "acc_" + accountId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    @SuppressLint("SetJavaScriptEnabled")
    fun newWebView(context: Context, profile: String): WebView {
        val wv = WebView(context)
        if (multiProfile()) runCatching {
            ProfileStore.getInstance().getOrCreateProfile(profile)
            WebViewCompat.setProfile(wv, profile)
        }
        wv.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        return wv
    }

    private fun flush(profile: String) {
        runCatching { CookieManager.getInstance().flush() }
        if (multiProfile()) runCatching { ProfileStore.getInstance().getProfile(profile)?.cookieManager?.flush() }
    }

    private class Bridge(val done: CompletableDeferred<AutoOutcome>, val clicked: () -> Unit) {
        @JavascriptInterface fun report(json: String) {
            val o = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
            when (o["s"]?.jsonPrimitive?.contentOrNull) {
                "clicked_final" -> clicked()
                "ok" -> done.complete(AutoOutcome(true))
                "fail" -> done.complete(AutoOutcome(false, o.s("code").ifEmpty { "ui_changed" }, o.s("msg"), o.s("diag")))
            }
        }
        private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /** ينفّذ نسخة وصفة واحدة. التسلسل مضمون: WebView واحد في كل مرة. */
    suspend fun run(profile: String, variant: JsonObject, vars: Map<String, String>, timeoutMs: Long = 90_000): AutoOutcome = mutex.withLock {
        val done = CompletableDeferred<AutoOutcome>()
        val clickedFinal = java.util.concurrent.atomic.AtomicBoolean(false)
        var wv: WebView? = null
        try {
            val url = RecipeRules.expand(variant["url"]!!.jsonPrimitive.content, vars)
            val loginRe = ((variant["login"] as? JsonObject)?.get("urlRegex")?.jsonPrimitive?.contentOrNull).orEmpty().takeIf { it.isNotEmpty() }?.let { Regex(it, RegexOption.IGNORE_CASE) }
            val recipe = variant.toString()
            val varsJson = AppJson.encodeToString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()), vars)
            withContext(Dispatchers.Main) {
                val v = newWebView(ctx, profile).also { wv = it }
                v.addJavascriptInterface(Bridge(done) { clickedFinal.set(true) }, "AS")
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(412, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(892, android.view.View.MeasureSpec.EXACTLY))
                v.layout(0, 0, 412, 892)
                var started = false
                v.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, u: String) {
                        if (done.isCompleted) return
                        if (loginRe != null && loginRe.containsMatchIn(u) && !clickedFinal.get()) { done.complete(AutoOutcome(false, "login_required", "الجلسة منتهية")); return }
                        if (clickedFinal.get()) { main.postDelayed({ if (!done.isCompleted) done.complete(AutoOutcome(true)) }, 3_000); return }
                        if (started && !u.startsWith(url.substringBefore('?'))) return
                        started = true
                        view.evaluateJavascript(lib, null)
                        view.evaluateJavascript("window.__as.run($recipe, $varsJson);", null)
                    }
                }
                v.loadUrl(url)
            }
            val r = withTimeoutOrNull(timeoutMs) { done.await() }
            r ?: AutoOutcome(false, "timeout", "انتهت المهلة", diagOf(wv))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            AutoOutcome(false, "ui_changed", e.message.orEmpty().take(100))
        } finally {
            withContext(Dispatchers.Main) { runCatching { wv?.stopLoading(); wv?.destroy() } }
            flush(profile)
        }
    }

    private suspend fun diagOf(wv: WebView?): String = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String>()
        runCatching { wv?.evaluateJavascript("(window.__as&&window.__as.diag())||''") { d.complete(it.orEmpty()) } } .onFailure { d.complete("") }
        withTimeoutOrNull(2_000) { d.await() }.orEmpty().trim('"').replace("\\\"", "\"").take(600)
    }

    /** هل توجد كوكيز جلسة الدخول لهذه المنصة في ملف تعريف الحساب؟ (يقرأ المخزن مباشرة بلا تحميل صفحة). */
    fun hasSession(profile: String, spec: PlatformSpec): Boolean {
        if (spec.cookies.isEmpty()) return true
        flush(profile)
        val cm = (if (multiProfile()) runCatching { ProfileStore.getInstance().getProfile(profile)?.cookieManager }.getOrNull() else null) ?: CookieManager.getInstance()
        val parts = cm.getCookie(spec.cookieUrl).orEmpty().split(";").map { it.trim() }
        return spec.cookies.all { n -> parts.any { it.startsWith("$n=") && it.length > n.length + 1 } }
    }

    suspend fun pause(ms: Long) = delay(ms)
}
