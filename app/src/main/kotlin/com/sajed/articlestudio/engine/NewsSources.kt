package com.sajed.articlestudio.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private class Feed(val name: String, val url: String, val limit: Int = 10)

private val LEAGUES = listOf("eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.champions", "ksa.1", "caf.nations", "afc.champions", "fifa.world")
private fun gnews(q: String) = "https://news.google.com/rss/search?q=" + URLEncoder.encode("$q when:3d", "UTF-8") + "&hl=ar&gl=EG&ceid=EG:ar"
private val FEEDS = listOf(
    Feed("RT عربي", "https://rtarabic.com/rss/sport/"),
    Feed("CNN عربية", "https://arabic.cnn.com/api/v1/rss/sport/rss.xml"),
    Feed("بي بي سي سبورت", "https://feeds.bbci.co.uk/sport/football/rss.xml"),
    Feed("سكاي سبورتس", "https://www.skysports.com/rss/12040"),
    Feed("الغارديان", "https://www.theguardian.com/football/rss"),
    Feed("أخبار جوجل", gnews("كرة القدم"), 14),
    Feed("أخبار جوجل", gnews("الدوري السعودي OR الدوري المصري OR المنتخب المصري OR المنتخب السعودي"), 14),
)

/** يجمع عناوين كرة القدم الحديثة من مصادر متعددة (ESPN وعدة تغذيات RSS عربية وإنجليزية). */
@Singleton
class NewsSources @Inject constructor(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(usedUrls: Set<String> = emptySet(), days: Int = 10): List<Ref> {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val client = http.newBuilder().callTimeout(14, TimeUnit.SECONDS).build()
        val groups = coroutineScope {
            val espn = LEAGUES.map { lg -> async { runCatching { espn(client, lg, cutoff) }.getOrDefault(emptyList()) } }
            val rss = FEEDS.map { f -> async { runCatching { fetchRss(client, f, cutoff) }.getOrDefault(emptyList()) } }
            listOf(espn.awaitAll().flatten().sortedByDescending { it.t }.take(24)) + rss.awaitAll()
        }
        return merge(groups.flatten(), usedUrls).take(64)
    }

    internal fun merge(all: List<Ref>, usedUrls: Set<String>): List<Ref> {
        val seen = HashSet<String>()
        return all.filter { it.url !in usedUrls && seen.add(normAr(it.h)) }.sortedByDescending { it.t }
    }

    private suspend fun espn(client: OkHttpClient, lg: String, cutoff: Long): List<Ref> {
        val rq = Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/soccer/$lg/news?limit=8").build()
        client.newCall(rq).await().use { r ->
            if (!r.isSuccessful) return emptyList()
            val arts = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["articles"] as? JsonArray ?: return emptyList()
            return arts.mapNotNull { e ->
                val a = e as? JsonObject ?: return@mapNotNull null
                val ts = (a["published"] ?: a["lastModified"])?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
                if (ts <= cutoff) return@mapNotNull null
                val url = a["links"]?.jsonObject?.get("web")?.jsonObject?.get("href")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val h = a["headline"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Ref(h, url, a["description"]?.jsonPrimitive?.contentOrNull.orEmpty(), ts, "ESPN")
            }
        }
    }

    private suspend fun fetchRss(client: OkHttpClient, f: Feed, cutoff: Long): List<Ref> {
        val rq = Request.Builder().url(f.url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(rq).await().use { r ->
            if (!r.isSuccessful) return emptyList()
            return parseRss(r.body?.string().orEmpty(), f.name, cutoff).take(f.limit)
        }
    }

    internal fun parseRss(xml: String, source: String, cutoff: Long): List<Ref> = ITEM.findAll(xml).mapNotNull { m ->
        val it = m.value
        var title = field(it, "title") ?: return@mapNotNull null
        val link = field(it, "link") ?: return@mapNotNull null
        val ts = field(it, "pubDate")?.let { d -> runCatching { ZonedDateTime.parse(d.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
        if (ts <= cutoff) return@mapNotNull null
        var src = source
        field(it, "source")?.takeIf { s -> s.isNotBlank() }?.let { s -> src = s; if (title.endsWith(" - $s")) title = title.removeSuffix(" - $s") }
        Ref(title, link, clamp(field(it, "description").orEmpty(), 300), ts, src)
    }.sortedByDescending { it.t }.toList()

    private companion object {
        val ITEM = Regex("<item[ >].*?</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val TAGS = Regex("<[^>]+>")
        fun field(item: String, name: String): String? = Regex("<$name(?: [^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)?.let { clean(it) }?.takeIf { it.isNotEmpty() }
        fun unescape(x: String): String {
            val y = x.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
            return Regex("&#(\\d+);").replace(y) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }.replace("&amp;", "&")
        }
        fun clean(s: String): String {
            val x = unescape(s.trim().removePrefix("<![CDATA[").removeSuffix("]]>")).replace(TAGS, " ")
            return unescape(x).replace(TAGS, " ").replace(Regex("\\s+"), " ").trim()
        }
    }
}
