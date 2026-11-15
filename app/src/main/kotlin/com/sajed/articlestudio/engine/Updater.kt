package com.sajed.articlestudio.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sajed.articlestudio.BrandConfig
import com.sajed.articlestudio.BuildConfig
import com.sajed.articlestudio.data.GitHubSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class UpdateInfo(val build: Int, val version: String, val notes: String, val assetApi: String, val size: Long)

enum class UpdatePhase { Idle, Checking, Current, Available, Downloading, Ready, Error }

class UpdateUi(val phase: UpdatePhase = UpdatePhase.Idle, val info: UpdateInfo? = null, val progress: Float = 0f, val message: String = "")

/** تحديث داخل التطبيق من GitHub Releases: فحص، تنزيل بتقدّم، ثم تثبيت عبر مثبّت النظام. */
@Singleton
class Updater @Inject constructor(@ApplicationContext private val ctx: Context, private val http: OkHttpClient, private val cfg: GitHubSettings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _ui = MutableStateFlow(UpdateUi())
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()
    private var job: Job? = null
    private var checkedAt = 0L
    val current: String get() = BuildConfig.VERSION_NAME

    private fun get(url: String, token: String, accept: String = "application/vnd.github+json") =
        http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build().newCall(
            Request.Builder().url(url).header("Accept", accept).apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }.build(),
        )

    /** يفحص الإصدار الأحدث؛ auto=true لا يكرر الفحص قبل 30 دقيقة ولا يعرض أخطاء الشبكة. */
    fun check(auto: Boolean = false) {
        val p = _ui.value.phase
        if (p == UpdatePhase.Checking || p == UpdatePhase.Downloading) return
        if (auto && System.currentTimeMillis() - checkedAt < 30 * 60_000L && p != UpdatePhase.Idle) return
        _ui.value = UpdateUi(UpdatePhase.Checking)
        scope.launch {
            try {
                val info = fetchLatest()
                checkedAt = System.currentTimeMillis()
                _ui.value = if (info == null) UpdateUi(UpdatePhase.Current, message = "أنت على أحدث إصدار ($current)")
                else if (info.build > BuildConfig.VERSION_CODE) UpdateUi(UpdatePhase.Available, info) else UpdateUi(UpdatePhase.Current, message = "أنت على أحدث إصدار ($current)")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _ui.value = if (auto) UpdateUi() else UpdateUi(UpdatePhase.Error, message = e.message.orEmpty())
            }
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? {
        val url = "https://api.github.com/repos/${BrandConfig.UPDATE_REPO}/releases/latest"
        var body: String? = null; var code = 0
        for (tok in listOf("", cfg.token).distinct()) {
            get(url, tok).await().use { r -> code = r.code; if (r.isSuccessful) body = r.body?.string() }
            if (body != null) break
        }
        val txt = body ?: throw IllegalStateException(if (code == 404) "تعذّر الوصول إلى الإصدارات: المستودع خاص. اجعله عاماً أو استخدم توكن بصلاحية قراءة ${BrandConfig.UPDATE_REPO}." else "تعذّر فحص التحديث (HTTP $code)")
        val o = json.parseToJsonElement(txt).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val build = Regex("-b(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val asset = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true } ?: return null
        val ver = Regex("^v?(\\d+\\.\\d+\\.\\d+)").find(tag)?.groupValues?.get(1) ?: tag
        return UpdateInfo(build, ver, (o["body"]?.jsonPrimitive?.contentOrNull.orEmpty()).replace(Regex("(?m)^#+\\s*"), "").trim().take(600),
            asset["url"]?.jsonPrimitive?.contentOrNull.orEmpty(), asset["size"]?.jsonPrimitive?.longOrNull ?: 0L)
    }

    fun download() {
        val info = _ui.value.info ?: return
        if (_ui.value.phase == UpdatePhase.Downloading) return
        _ui.value = UpdateUi(UpdatePhase.Downloading, info)
        job = scope.launch {
            try {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "update.apk")
                var ok = false; var code = 0
                for (tok in listOf("", cfg.token).distinct()) {
                    get(info.assetApi, tok, "application/octet-stream").await().use { r ->
                        code = r.code
                        if (!r.isSuccessful) return@use
                        val total = r.body?.contentLength()?.takeIf { it > 0 } ?: info.size
                        r.body!!.byteStream().use { ins -> out.outputStream().use { os ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var last = 0L
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                os.write(buf, 0, n); done += n
                                if (total > 0 && done - last > 256 * 1024) { last = done; _ui.update { UpdateUi(UpdatePhase.Downloading, info, (done.toFloat() / total).coerceIn(0f, 1f)) } }
                            }
                        } }
                        ok = true
                    }
                    if (ok) break
                }
                if (!ok) throw IllegalStateException("فشل التنزيل (HTTP $code)")
                if (info.size > 0 && out.length() != info.size) throw IllegalStateException("الملف المنزَّل ناقص، أعد المحاولة")
                _ui.value = UpdateUi(UpdatePhase.Ready, info, 1f)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _ui.value = UpdateUi(UpdatePhase.Error, info, message = e.message.orEmpty())
            }
        }
    }

    /** نية التثبيت، أو نية صفحة إذن «تثبيت التطبيقات المجهولة» إن لم يُمنح بعد. */
    fun installIntent(): Intent? {
        val f = File(ctx.cacheDir, "updates/update.apk").takeIf { it.exists() } ?: return null
        if (!ctx.packageManager.canRequestPackageInstalls())
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun dismiss() { _ui.value = UpdateUi(UpdatePhase.Idle) }
}
