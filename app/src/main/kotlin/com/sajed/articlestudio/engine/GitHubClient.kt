package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.GitHubSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class GitHubException(val status: Int, message: String) : Exception(message)
class ImageFile(val name: String, val bytes: ByteArray)

@Singleton
class GitHubClient @Inject constructor(private val http: OkHttpClient, private val cfg: GitHubSettings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { http.newBuilder().callTimeout(60, TimeUnit.SECONDS).build() }

    private suspend fun gh(path: String, method: String = "GET", body: JsonObject? = null, what: String = ""): JsonElement {
        val rq = Request.Builder().url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json").header("Authorization", "Bearer ${cfg.token}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()) ?: if (method == "GET") null else "".toRequestBody()).build()
        client.newCall(rq).await().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val m = runCatching { json.parseToJsonElement(txt).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
                val hint = when (r.code) {
                    403 -> "التوكن بلا صلاحية كتابة على هذا المستودع. في إعدادات التوكن: Repository access يشمل المستودع، وContents ← Read and write، وResource owner هو مالك المستودع."
                    401 -> "التوكن غير صالح أو منتهي"
                    404 -> "المستودع غير موجود أو التوكن لا يراه"
                    else -> m
                }
                throw GitHubException(r.code, "GitHub ${r.code}${if (what.isNotEmpty()) " ($what)" else ""}: $hint")
            }
            return if (txt.isBlank()) JsonNull else json.parseToJsonElement(txt)
        }
    }

    private fun ownerRepo(): Pair<String, String> {
        val p = cfg.repo.trim().split('/')
        if (p.size != 2 || p.any { it.isEmpty() }) throw IllegalArgumentException("صيغة المستودع owner/repo")
        return p[0] to p[1]
    }

    private fun JsonElement.str(vararg k: String): String = k.fold(this) { e, n -> e.jsonObject[n] ?: JsonNull }.jsonPrimitive.contentOrNull.orEmpty()

    /** فحص مسبق: قراءة المستودع ثم صلاحية الكتابة فعلياً. */
    suspend fun check(): List<String> {
        if (cfg.token.isEmpty()) throw IllegalArgumentException("أدخل GitHub Token")
        val (o, r) = ownerRepo()
        val out = ArrayList<String>()
        val info = gh("/repos/$o/$r", what = "قراءة المستودع")
        out += "المستودع ${info.str("full_name")} مقروء"
        val push = info.jsonObject["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.let { runCatching { it.boolean }.getOrNull() }
        if (push == false) throw GitHubException(403, "GitHub 403 (الصلاحيات): التوكن يقرأ المستودع لكنه بلا صلاحية كتابة. فعّل Contents ← Read and write.")
        gh("/repos/$o/$r/git/blobs", "POST", buildJsonObject { put("content", "ok"); put("encoding", "utf-8") }, "اختبار كتابة")
        out += "اختبار الكتابة نجح"
        return out
    }

    /** إيداع واحد لكل الصور على فرع مخصص؛ يعيد الرابط الأساسي المثبّت بمعرّف الإيداع عبر jsDelivr. */
    suspend fun pushImages(files: List<ImageFile>, step: (String) -> Unit): String {
        val (o, r) = ownerRepo()
        val br = cfg.branch.ifBlank { "blog-images" }
        val enc = URLEncoder.encode(br, "UTF-8")
        step("تجهيز الفرع")
        val ref = try { gh("/repos/$o/$r/git/ref/heads/$enc", what = "قراءة الفرع") } catch (e: GitHubException) {
            if (e.status != 404) throw e
            val info = gh("/repos/$o/$r", what = "قراءة المستودع")
            val def = URLEncoder.encode(info.str("default_branch"), "UTF-8")
            val baseRef = gh("/repos/$o/$r/git/ref/heads/$def", what = "قراءة الفرع الأساسي")
            gh("/repos/$o/$r/git/refs", "POST", buildJsonObject { put("ref", "refs/heads/$br"); put("sha", baseRef.str("object", "sha")) }, "إنشاء الفرع")
        }
        val parent = ref.str("object", "sha")
        val pc = gh("/repos/$o/$r/git/commits/$parent", what = "قراءة الإيداع")
        val tree = ArrayList<Pair<String, String>>()
        files.forEachIndexed { i, f ->
            val bl = gh("/repos/$o/$r/git/blobs", "POST", buildJsonObject {
                put("content", Base64.getEncoder().encodeToString(f.bytes)); put("encoding", "base64")
            }, "رفع صورة")
            tree += "images/articles/${f.name}" to bl.str("sha")
            step("رفع الصور ${i + 1}/${files.size}")
        }
        step("إنشاء الإيداع")
        val t = gh("/repos/$o/$r/git/trees", "POST", buildJsonObject {
            put("base_tree", pc.str("tree", "sha"))
            put("tree", buildJsonArray { tree.forEach { (p, s) -> add(buildJsonObject { put("path", p); put("mode", "100644"); put("type", "blob"); put("sha", s) }) } })
        }, "إنشاء الشجرة")
        val c = gh("/repos/$o/$r/git/commits", "POST", buildJsonObject {
            put("message", "images: ${files.size / 2} article covers (Article Studio)")
            put("tree", t.str("sha")); put("parents", buildJsonArray { add(JsonPrimitive(parent)) })
        }, "إنشاء الإيداع")
        gh("/repos/$o/$r/git/refs/heads/$enc", "PATCH", buildJsonObject { put("sha", c.str("sha")) }, "تحديث الفرع")
        return "https://cdn.jsdelivr.net/gh/$o/$r@${c.str("sha")}/images/articles/"
    }

    /** أحدث إصدار منشور: (رقم البناء، رابط الصفحة) أو null. */
    suspend fun latestRelease(repo: String): Pair<Int, String>? = runCatching {
        val rq = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest").header("Accept", "application/vnd.github+json")
            .apply { if (cfg.token.isNotEmpty()) header("Authorization", "Bearer ${cfg.token}") }.build()
        client.newCall(rq).await().use { r ->
            if (!r.isSuccessful) return null
            val j = json.parseToJsonElement(r.body?.string().orEmpty())
            val tag = j.str("tag_name")
            val n = Regex("-b(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            n to j.str("html_url")
        }
    }.getOrNull()
}
