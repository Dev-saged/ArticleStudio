package com.sajed.articlestudio.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class PubState { Draft, Live, Scheduled;
    companion object { fun of(s: String) = when (s) { "live" -> Live; "scheduled" -> Scheduled; else -> Draft } }
}

class PublishItem(val title: String, val html: String, val labels: List<String>, val at: Instant?)
class Remote(val id: String, val url: String)

/** auth = يحتاج المستخدم لإعادة الربط؛ retry = خطأ مؤقت يستحق إعادة المحاولة. */
class PublishException(message: String, val auth: Boolean = false, val retry: Boolean = false) : Exception(message)

interface BlogPublisher {
    /** يتحقق من الاتصال ويعيد اسم المدونة. */
    suspend fun test(): String
    suspend fun publish(item: PublishItem, state: PubState): Remote
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val lenient = Json { ignoreUnknownKeys = true }

/** يرسل الطلب ويعيد (الرمز، النص) ويحوّل أعطال الشبكة لأخطاء قابلة لإعادة المحاولة. */
private suspend fun exec(http: OkHttpClient, rq: Request): Pair<Int, String> =
    try { http.newCall(rq).await().use { it.code to it.body?.string().orEmpty() } }
    catch (e: CancellationException) { throw e }
    catch (e: IOException) { throw PublishException("تعذّر الاتصال: ${clamp(e.message, 80)}", retry = true) }

/** إعادة المحاولة بتباعد تصاعدي للأخطاء المؤقتة فقط؛ أخطاء الصلاحية والبيانات تفشل فوراً. */
internal suspend fun <T> withRetry(times: Int = 3, base: Long = 1500, block: suspend () -> T): T {
    var last: PublishException? = null
    repeat(times) { i ->
        try { return block() } catch (e: PublishException) {
            if (!e.retry) throw e
            last = e
            if (i < times - 1) delay(base * (i + 1))
        }
    }
    throw last ?: PublishException("فشل النشر")
}

private fun instant(at: Instant?, state: PubState): String? = if (state == PubState.Scheduled && at != null && at.isAfter(Instant.now())) at.toString() else null

/** Blogger API v3 الرسمي برمز وصول OAuth يوفّره تسجيل الدخول بجوجل داخل التطبيق. */
class GooglePublisher(private val http: OkHttpClient, private val token: suspend () -> String, private val blogUrl: String, private val api: String = "https://www.googleapis.com/blogger/v3") : BlogPublisher {
    private var blogId: String? = null
    private var blogName = ""

    private fun check(code: Int, text: String) {
        if (code in 200..299) return
        val msg = runCatching { lenient.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
        throw when (code) {
            401 -> PublishException("انتهت صلاحية الربط، اربط حساب جوجل من جديد", auth = true)
            403 -> PublishException("مرفوض: فعّل Blogger API في مشروعك وتأكد أن الحساب يملك المدونة. ${clamp(msg, 100)}", auth = true)
            404 -> PublishException("لم تُوجد مدونة بهذا الرابط في الحساب المربوط")
            429 -> PublishException("حد الاستخدام، سنعيد المحاولة", retry = true)
            in 500..599 -> PublishException("خطأ مؤقت من بلوجر ($code)", retry = true)
            else -> PublishException("رفض بلوجر ($code): ${clamp(msg, 120)}")
        }
    }

    private suspend fun resolve(): String {
        blogId?.let { return it }
        val u = "$api/blogs/byurl".toHttpUrl().newBuilder().addQueryParameter("url", blogUrl.trimEnd('/') + "/").build()
        val (code, text) = exec(http, Request.Builder().url(u).header("Authorization", "Bearer ${token()}").build())
        check(code, text)
        val o = lenient.parseToJsonElement(text).jsonObject
        blogName = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return (o["id"]?.jsonPrimitive?.contentOrNull ?: throw PublishException("ردّ بلوجر بلا معرّف مدونة")).also { blogId = it }
    }

    override suspend fun test(): String = withRetry { resolve(); blogName.ifBlank { "مدونة" } }

    private fun remote(text: String): Remote {
        val o = lenient.parseToJsonElement(text).jsonObject
        return Remote(o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["url"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    /** الجدولة وفق وثائق Blogger: تُنشأ مسودة ثم تُنشر بـ publishDate مستقبلي. */
    override suspend fun publish(item: PublishItem, state: PubState): Remote {
        val id = resolve()
        val at = instant(item.at, state)
        val draft = state == PubState.Draft || at != null
        val created = withRetry {
            val u = "$api/blogs/$id/posts".toHttpUrl().newBuilder().addQueryParameter("isDraft", draft.toString()).build()
            val body = buildJsonObject {
                put("kind", "blogger#post"); put("title", item.title); put("content", item.html)
                put("labels", buildJsonArray { item.labels.forEach { add(JsonPrimitive(it)) } })
            }
            val (code, text) = exec(http, Request.Builder().url(u).header("Authorization", "Bearer ${token()}").post(body.toString().toRequestBody(JSON_MEDIA)).build())
            check(code, text)
            remote(text)
        }
        if (at == null) return created
        return withRetry {
            val u = "$api/blogs/$id/posts/${created.id}/publish".toHttpUrl().newBuilder().addQueryParameter("publishDate", at).build()
            val (code, text) = exec(http, Request.Builder().url(u).header("Authorization", "Bearer ${token()}").post("".toRequestBody(JSON_MEDIA)).build())
            check(code, text)
            remote(text).let { if (it.id.isEmpty()) created else it }
        }
    }
}
