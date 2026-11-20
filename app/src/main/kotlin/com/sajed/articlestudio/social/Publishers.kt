package com.sajed.articlestudio.social

import com.sajed.articlestudio.data.SecretStore
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/** code: login_required | network | rate | rejected | ui_changed | timeout. retry = يستحق إعادة المحاولة. */
class ShareException(val code: String, message: String, val retry: Boolean = false, val diag: String = "") : Exception(message)

class ShareResult(val postUrl: String = "")

/** text = النص الكامل مع الرابط؛ hook = الصياغة وحدها (لنوافذ المشاركة التي تضيف الرابط بنفسها). */
class Post(val text: String, val hook: String, val url: String, val image: java.io.File? = null)

interface SocialPublisher {
    /** يتحقق من صلاحية الحساب دون نشر ويعيد وصفاً قصيراً. */
    suspend fun check(acc: SocialAccountEntity): String
    suspend fun share(acc: SocialAccountEntity, post: Post): ShareResult
}

internal fun extraOf(acc: SocialAccountEntity): JsonObject = runCatching { AppJson.parseToJsonElement(acc.extra).jsonObject }.getOrDefault(JsonObject(emptyMap()))
internal fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()

private val JSON = "application/json; charset=utf-8".toMediaType()

private suspend fun OkHttpClient.call(rq: Request): Pair<Int, JsonObject> {
    val rs = try { newCall(rq).await() } catch (e: CancellationException) { throw e } catch (e: IOException) { throw ShareException("network", "تعذّر الاتصال: ${e.message.orEmpty().take(60)}", retry = true) }
    return rs.use { r ->
        val body = runCatching { AppJson.parseToJsonElement(r.body?.string().orEmpty()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        r.code to body
    }
}

private fun tgFail(c: Int, b: JsonObject): Nothing {
    val d = b.s("description")
    when {
        c == 401 -> throw ShareException("login_required", "تيليغرام: التوكن غير صحيح، انسخه من BotFather مرة أخرى")
        c == 403 -> throw ShareException("rejected", "تيليغرام: البوت لا يملك صلاحية النشر هنا؛ أضفه مشرفاً في القناة (أو افتح محادثته أولاً إن كانت خاصة)")
        c == 400 && d.contains("chat not found", true) -> throw ShareException("rejected", "تيليغرام: المعرّف غير صحيح أو البوت ليس في القناة")
        c == 400 -> throw ShareException("rejected", "تيليغرام: ${d.take(90)}")
    }
    fail(c, "تيليغرام")
}

private fun fail(code: Int, what: String): Nothing = when {
    code == 401 || code == 403 -> throw ShareException("login_required", "$what: بيانات الدخول غير صالحة")
    code == 429 -> throw ShareException("rate", "$what: حد الطلبات، سيُعاد لاحقاً", retry = true)
    code >= 500 -> throw ShareException("network", "$what: خادم المنصة لا يستجيب", retry = true)
    else -> throw ShareException("rejected", "$what: رفضت المنصة الطلب ($code)")
}

class TelegramPublisher(private val http: OkHttpClient, private val secrets: SecretStore, private val base: String = "https://api.telegram.org") : SocialPublisher {
    private fun token(acc: SocialAccountEntity) = secrets.get("soc.${acc.id}").ifBlank { throw ShareException("login_required", "التوكن غير محفوظ") }

    override suspend fun check(acc: SocialAccountEntity): String {
        val t = token(acc)
        val (c, b) = http.call(Request.Builder().url("$base/bot$t/getMe").build())
        if (c != 200) tgFail(c, b)
        val me = b["result"] as? JsonObject
        val chat = extraOf(acc).s("chat").ifBlank { throw ShareException("rejected", "معرّف المحادثة أو القناة ناقص") }
        val (cc, cb) = http.call(Request.Builder().url("$base/bot$t/getChat".toHttpUrl().newBuilder().addQueryParameter("chat_id", chat).build()).build())
        if (cc != 200) tgFail(cc, cb)
        val type = (cb["result"] as? JsonObject)?.s("type").orEmpty()
        if (type == "channel") {
            val uid = me?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
            val (mc, mb) = http.call(Request.Builder().url("$base/bot$t/getChatMember".toHttpUrl().newBuilder().addQueryParameter("chat_id", chat).addQueryParameter("user_id", uid).build()).build())
            if (mc != 200) tgFail(mc, mb)
            val st = (mb["result"] as? JsonObject)?.s("status").orEmpty()
            if (st != "administrator" && st != "creator") throw ShareException("rejected", "تيليغرام: البوت في القناة لكنه ليس مشرفاً؛ امنحه صلاحية نشر الرسائل")
        }
        return "البوت @" + me?.s("username").orEmpty() + " جاهز للنشر في " + chat
    }

    override suspend fun share(acc: SocialAccountEntity, post: Post): ShareResult {
        val text = post.text
        val chat = extraOf(acc).s("chat").ifBlank { throw ShareException("rejected", "معرّف المحادثة أو القناة ناقص") }
        val body = buildJsonObject { put("chat_id", chat); put("text", text); put("disable_web_page_preview", false) }
        val (c, b) = http.call(Request.Builder().url("$base/bot${token(acc)}/sendMessage").post(body.toString().toRequestBody(JSON)).build())
        if (c != 200) tgFail(c, b)
        val chatUser = ((b["result"] as? JsonObject)?.get("chat") as? JsonObject)?.s("username").orEmpty()
        val id = (b["result"] as? JsonObject)?.get("message_id")?.jsonPrimitive?.contentOrNull.orEmpty()
        return ShareResult(if (chatUser.isNotEmpty() && id.isNotEmpty()) "https://t.me/$chatUser/$id" else "")
    }
}

class BlueskyPublisher(private val http: OkHttpClient, private val secrets: SecretStore, private val base: String = "https://bsky.social") : SocialPublisher {
    private class Session(val jwt: String, val did: String)

    private suspend fun session(acc: SocialAccountEntity): Session {
        val handle = extraOf(acc).s("handle").ifBlank { throw ShareException("login_required", "المعرّف ناقص") }
        val pass = secrets.get("soc.${acc.id}").ifBlank { throw ShareException("login_required", "كلمة مرور التطبيق غير محفوظة") }
        val body = buildJsonObject { put("identifier", handle); put("password", pass) }
        val (c, b) = http.call(Request.Builder().url("$base/xrpc/com.atproto.server.createSession").post(body.toString().toRequestBody(JSON)).build())
        if (c != 200) fail(c, "بلوسكي")
        return Session(b.s("accessJwt"), b.s("did"))
    }

    override suspend fun check(acc: SocialAccountEntity): String = "الحساب ${extraOf(acc).s("handle")}".also { session(acc) }

    override suspend fun share(acc: SocialAccountEntity, post: Post): ShareResult {
        val text = post.text; val url = post.url
        val s = session(acc)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val u = url.toByteArray(Charsets.UTF_8)
        val at = indexOf(bytes, u)
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.post"); put("text", text); put("createdAt", Instant.now().toString())
            put("langs", buildJsonArray { add(JsonPrimitive("ar")) })
            if (at >= 0) put("facets", buildJsonArray {
                add(buildJsonObject {
                    put("index", buildJsonObject { put("byteStart", at); put("byteEnd", at + u.size) })
                    put("features", buildJsonArray { add(buildJsonObject { put("\$type", "app.bsky.richtext.facet#link"); put("uri", url) }) })
                })
            })
        }
        val body = buildJsonObject { put("repo", s.did); put("collection", "app.bsky.feed.post"); put("record", record) }
        val (c, b) = http.call(Request.Builder().url("$base/xrpc/com.atproto.repo.createRecord").header("Authorization", "Bearer ${s.jwt}").post(body.toString().toRequestBody(JSON)).build())
        if (c != 200) fail(c, "بلوسكي")
        val rkey = b.s("uri").substringAfterLast('/')
        return ShareResult(if (rkey.isNotEmpty()) "https://bsky.app/profile/${extraOf(acc).s("handle")}/post/$rkey" else "")
    }

    private fun indexOf(h: ByteArray, n: ByteArray): Int {
        if (n.isEmpty()) return -1
        outer@ for (i in 0..h.size - n.size) { for (j in n.indices) if (h[i + j] != n[j]) continue@outer; return i }
        return -1
    }
}
