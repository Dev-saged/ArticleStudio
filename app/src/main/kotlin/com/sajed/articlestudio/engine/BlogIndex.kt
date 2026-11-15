package com.sajed.articlestudio.engine

import android.content.Context
import com.sajed.articlestudio.BrandConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
class BlogPost(val title: String, val slug: String, val path: String = "")

/** فهرس مقالات المدونة المنشورة (من تغذية بلوجر العامة) لمنع تكرار العناوين والروابط. */
@Singleton
class BlogIndex @Inject constructor(@ApplicationContext c: Context, private val http: OkHttpClient) {
    private val sp = c.getSharedPreferences("blog", Context.MODE_PRIVATE)
    private val ser = ListSerializer(BlogPost.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var posts: List<BlogPost> = runCatching { json.decodeFromString(ser, sp.getString("posts", "[]") ?: "[]") }.getOrDefault(emptyList())

    val count get() = posts.size
    fun titles() = posts.map { it.title }
    fun all(): List<BlogPost> = posts
    fun slugs() = posts.map { it.slug }.toSet()
    fun similar(title: String): BlogPost? = posts.firstOrNull { similarTitles(title, it.title) }

    /** يحدّث الفهرس (مرة كل 3 ساعات ما لم يُفرض)؛ عند الفشل يبقى آخر نسخة محفوظة. يعيد عدد المقالات. */
    suspend fun refresh(force: Boolean = false): Int {
        if (!force && posts.isNotEmpty() && System.currentTimeMillis() - sp.getLong("t", 0) < 3 * 3_600_000L) return posts.size
        val client = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
        val all = ArrayList<BlogPost>()
        try {
            var start = 1
            while (start < 2000) {
                val url = "${BrandConfig.BLOG_SITE}/feeds/posts/default?alt=json&max-results=150&start-index=$start&orderby=published"
                val batch = client.newCall(Request.Builder().url(url).build()).await().use { r ->
                    if (!r.isSuccessful) return@use null
                    parse(r.body?.string().orEmpty())
                } ?: break
                all += batch
                if (batch.size < 150) break
                start += 150
            }
        } catch (e: Exception) { if (e is CancellationException) throw e }
        if (all.isNotEmpty()) {
            posts = all
            sp.edit().putString("posts", json.encodeToString(ser, all)).putLong("t", System.currentTimeMillis()).apply()
        }
        return posts.size
    }

    internal fun parse(txt: String): List<BlogPost> {
        val feed = json.parseToJsonElement(txt).jsonObject["feed"]?.jsonObject ?: return emptyList()
        return (feed["entry"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = o["title"]?.jsonObject?.get("\$t")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val href = o["link"]?.jsonArray?.firstOrNull { it.jsonObject["rel"]?.jsonPrimitive?.contentOrNull == "alternate" }
                ?.jsonObject?.get("href")?.jsonPrimitive?.contentOrNull.orEmpty()
            BlogPost(title.trim(), href.substringAfterLast('/').removeSuffix(".html"), runCatching { java.net.URI(href).path.orEmpty() }.getOrDefault(""))
        }
    }
}
