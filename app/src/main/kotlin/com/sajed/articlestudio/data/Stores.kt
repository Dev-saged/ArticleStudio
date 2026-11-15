package com.sajed.articlestudio.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
class Health(var ok: Int = 0, var fail: Int = 0, var streak: Int = 0, var until: Long = 0)

/** ذاكرة صحة النماذج: تُبقي الأفضل أولاً وتحجب المتعثر ساعة بعد ثلاثة إخفاقات متتالية. */
@Singleton
class HealthStore @Inject constructor(@ApplicationContext c: Context) {
    private val sp = c.getSharedPreferences("health", Context.MODE_PRIVATE)
    private val ser = MapSerializer(String.serializer(), Health.serializer())
    private val map: MutableMap<String, Health> = runCatching {
        Json.decodeFromString(ser, sp.getString("h", "{}") ?: "{}").toMutableMap()
    }.getOrDefault(mutableMapOf())

    @Synchronized private fun get(k: String) = map.getOrPut(k) { Health() }
    @Synchronized private fun persist() { sp.edit().putString("h", Json.encodeToString(ser, map)).apply() }

    @Synchronized fun blockedUntil(k: String) = map[k]?.until ?: 0L
    @Synchronized fun score(k: String) = map[k]?.let { it.ok - it.fail * 2 } ?: 0
    @Synchronized fun reward(k: String) { get(k).apply { ok++; streak = 0 }; persist() }
    @Synchronized fun penal(k: String, now: Long) {
        get(k).apply { fail++; streak++; if (streak >= 3) { until = now + 3_600_000; streak = 0 } }
        persist()
    }
    @Synchronized fun totals(prefix: String): Pair<Int, Int> =
        map.filterKeys { it.startsWith("$prefix|") }.values.let { v -> v.sumOf { it.ok } to v.sumOf { it.fail } }
}

@Serializable
class History(val slugs: List<String> = emptyList(), val topics: List<String> = emptyList(), val batches: Int = 0, val urls: List<String> = emptyList())

@Singleton
class HistoryStore @Inject constructor(@ApplicationContext c: Context) {
    private val sp = c.getSharedPreferences("history", Context.MODE_PRIVATE)
    private fun key(siteId: String) = if (siteId == "default") "h" else "h_$siteId"
    fun load(siteId: String = "default"): History = runCatching { Json.decodeFromString(History.serializer(), sp.getString(key(siteId), "") ?: "") }.getOrDefault(History())
    fun add(slugs: List<String>, topics: List<String>, urls: List<String> = emptyList(), siteId: String = "default") {
        val h = load(siteId)
        val n = History((h.slugs + slugs).takeLast(400), (h.topics + topics).takeLast(200), h.batches + 1, (h.urls + urls).takeLast(600))
        sp.edit().putString(key(siteId), Json.encodeToString(History.serializer(), n)).apply()
    }
}

@Singleton
class GitHubSettings @Inject constructor(@ApplicationContext c: Context, private val secrets: SecretStore) {
    private val sp = c.getSharedPreferences("github", Context.MODE_PRIVATE)
    var repo: String
        get() = sp.getString("repo", null) ?: com.sajed.articlestudio.BrandConfig.DEFAULT_IMAGES_REPO
        set(v) { sp.edit().putString("repo", v.trim()).apply() }
    var branch: String
        get() = sp.getString("branch", null) ?: "blog-images"
        set(v) { sp.edit().putString("branch", v.trim()).apply() }
    var token: String
        get() = secrets.get("gh.token").trim()
        set(v) = secrets.put("gh.token", v.trim())
    var autoPush: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) { sp.edit().putBoolean("auto", v).apply() }
    var scheduleHours: Int
        get() = sp.getInt("sched", 0)
        set(v) { sp.edit().putInt("sched", v.coerceIn(0, 72)).apply() }
}
