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
    private val mem = java.util.concurrent.ConcurrentHashMap<String, List<BlogPost>>()

    private fun host(url: String) = url.removePrefix("https://").removePrefix("http://").trimEnd('/').lowercase()
    private fun pk(url: String) = if (host(url) == host(BrandConfig.BLOG_SITE)) "posts" else "posts_" + host(url)
    private fun tk(url: String) = if (host(url) == host(BrandConfig.BLOG_SITE)) "t" else "t_" + host(url)
    private fun posts(url: String): List<BlogPost> = mem.getOrPut(pk(url)) { runCatching { json.decodeFromString(ser, sp.getString(pk(url), "[]") ?: "[]") }.getOrDefault(emptyList()) }

    fun count(url: String) = posts(url).size
    fun titles(url: String) = posts(url).map { it.title }
    fun all(url: String): List<BlogPost> = posts(url)
    fun slugs(url: String) = posts(url).map { it.slug }.toSet()
    fun similar(url: String, title: String): BlogPost? = posts(url).firstOrNull { similarTitles(title, it.title) }

    /** يحدّث فهرس موقع (مرة كل 3 ساعات ما لم يُفرض)؛ عند الفشل يبقى آخر نسخة محفوظة. يعيد عدد المقالات. */
    suspend fun refresh(url: String, force: Boolean = false): Int {
        val cur = posts(url)
        if (!force && cur.isNotEmpty() && System.currentTimeMillis() - sp.getLong(tk(url), 0) < 3 * 3_600_000L) return cur.size
        val client = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
        val all = ArrayList<BlogPost>()
        try {
            var start = 1
            while (start < 2000) {
                val rq = "${url.trimEnd('/')}/feeds/posts/default?alt=json&max-results=150&start-index=$start&orderby=published"
                val batch = client.newCall(Request.Builder().url(rq).build()).await().use { r ->
                    if (!r.isSuccessful) return@use null
                    parse(r.body?.string().orEmpty())
                } ?: break
                all += batch
                if (batch.size < 150) break
                start += 150
            }
        } catch (e: Exception) { if (e is CancellationException) throw e }
        if (all.isNotEmpty()) {
            mem[pk(url)] = all
            sp.edit().putString(pk(url), json.encodeToString(ser, all)).putLong(tk(url), System.currentTimeMillis()).apply()
        }
        return posts(url).size
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
