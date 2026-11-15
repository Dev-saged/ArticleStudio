package com.sajed.articlestudio.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class BackupEntry(val id: String, val key: String, val enabled: Boolean?, val base: String, val model: String)
class BackupGit(val repo: String, val branch: String, val token: String)
class Backup(val entries: List<BackupEntry>, val git: BackupGit?, val unknown: List<String>)
class ImportReport(val keys: List<String>, val custom: Boolean, val git: Boolean, val unknown: List<String>, val error: String = "") {
    val ok get() = error.isEmpty() && (keys.isNotEmpty() || custom || git)
    fun message(): String = when {
        error.isNotEmpty() -> error
        !ok -> "لم أجد مفاتيح صالحة في هذا الملف."
        else -> buildList {
            if (keys.isNotEmpty()) add("استُورد ${keys.size} مفتاحاً: " + keys.joinToString("، ") { ProviderCatalog.all.firstOrNull { s -> s.id == it }?.name ?: it })
            if (custom) add("المزود المخصص")
            if (git) add("إعدادات GitHub")
            if (unknown.isNotEmpty()) add("تجاهلتُ مزودين غير معروفين: " + unknown.take(4).joinToString("، "))
        }.joinToString(" · ")
    }
}

/** قراءة وكتابة ملف نسخ المفاتيح بصيغة موقع Article Studio ({app, v, providers[], github}) وصيغ مبسطة. */
@Singleton
class KeyBackup @Inject constructor(private val store: ProviderStore, private val gh: GitHubSettings) {

    fun export(withGit: Boolean = true): String {
        val o = buildJsonObject {
            put("app", "article-studio"); put("v", 1); put("exported", Instant.now().toString())
            put("providers", buildJsonArray {
                ProviderCatalog.all.forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id); put("name", s.name); put("enabled", store.enabled(s.id)); put("key", store.key(s.id))
                        if (s.id == "custom") { put("base", store.base("custom")); put("model", store.model("custom")) }
                    })
                }
            })
            if (withGit && gh.token.isNotEmpty()) put("github", buildJsonObject { put("repo", gh.repo); put("branch", gh.branch); put("token", gh.token) })
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), o)
    }

    fun import(text: String): ImportReport {
        val b = try { parse(text) } catch (e: IllegalArgumentException) { return ImportReport(emptyList(), false, false, emptyList(), e.message.orEmpty()) }
        val keys = ArrayList<String>(); var custom = false; var git = false
        try {
            for (e in b.entries) {
                val on = e.enabled ?: store.enabled(e.id)
                if (e.id == "custom") {
                    val base = e.base.ifEmpty { store.base("custom") }; val model = e.model.ifEmpty { store.model("custom") }
                    store.save("custom", model, base, e.key, on); if (base.isNotEmpty() || model.isNotEmpty()) custom = true
                } else store.save(e.id, "", "", e.key, on)
                if (e.key.isNotEmpty()) keys += e.id
            }
            b.git?.let { g ->
                if (g.repo.isNotEmpty()) gh.repo = g.repo
                if (g.branch.isNotEmpty()) gh.branch = g.branch
                if (g.token.isNotEmpty()) gh.token = g.token
                git = true
            }
        } catch (e: SecretException) { return ImportReport(keys, custom, git, b.unknown, e.message.orEmpty()) }
        return ImportReport(keys.distinct(), custom, git, b.unknown)
    }

    companion object {
        private val ALIAS = mapOf(
            "google" to "gemini", "googlegemini" to "gemini", "geminiapi" to "gemini", "aistudio" to "gemini",
            "opencode" to "zen", "opencodezen" to "zen", "samba" to "sambanova", "nvidianim" to "nvidia", "nim" to "nvidia",
            "zhipu" to "zai", "zai" to "zai", "glm" to "zai", "llm7io" to "llm7", "huggingface" to "hf",
        )
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        private fun resolve(raw: String, name: String = ""): String? {
            val ids = ProviderCatalog.all.map { it.id }.toSet()
            for (c in listOf(raw, name)) {
                val n = c.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (n.isEmpty()) continue
                if (n in ids) return n
                ALIAS[n]?.let { if (it in ids) return it }
                ids.firstOrNull { n.startsWith(it) && it.length >= 4 }?.let { return it }
            }
            return null
        }

        private fun clean(k: String) = k.trim().trim('"', '\'', '`', ',').replace(Regex("\\s+"), "")
        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        /** يستخرج كتلة JSON من نص قد يحوي BOM أو أسوار ``` أو نصاً قبلها/بعدها. */
        internal fun extractJson(text: String): JsonElement? {
            val t = text.removePrefix("﻿").replace("```json", "").replace("```", "").trim()
            runCatching { return json.parseToJsonElement(t) }
            val a = t.indexOf('{'); val z = t.lastIndexOf('}')
            if (a in 0 until z) runCatching { return json.parseToJsonElement(t.substring(a, z + 1)) }
            return null
        }

        fun parse(text: String): Backup {
            val src = text.trim()
            if (src.isEmpty()) throw IllegalArgumentException("الملف فارغ.")
            val root = extractJson(src)
            val entries = LinkedHashMap<String, BackupEntry>(); val unknown = ArrayList<String>(); var git: BackupGit? = null
            fun put(id: String?, label: String, key: String, en: Boolean?, base: String, model: String) {
                if (id == null) { if (key.isNotEmpty() || label.isNotEmpty()) unknown += label.ifEmpty { "?" }; return }
                val k = clean(key)
                if (k.isNotEmpty() && k.length < 8) return
                val old = entries[id]
                entries[id] = BackupEntry(id, k.ifEmpty { old?.key.orEmpty() }, en ?: old?.enabled, base.ifEmpty { old?.base.orEmpty() }, model.ifEmpty { old?.model.orEmpty() })
            }
            if (root is JsonObject) {
                val list = root["providers"]
                when (list) {
                    is JsonArray -> list.forEach { e -> (e as? JsonObject)?.let { o ->
                        val enabled = (o["enabled"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } ?: (o["en"] as? JsonPrimitive)?.booleanOrNull
                        val key = o.s("key").ifEmpty { o.s("apiKey") }.ifEmpty { o.s("token") }
                        put(resolve(o.s("id"), o.s("name")), o.s("id").ifEmpty { o.s("name") }, key, enabled, o.s("base").ifEmpty { o.s("baseUrl") }, o.s("model"))
                    } }
                    is JsonObject -> list.forEach { (id, v) ->
                        if (v is JsonObject) put(resolve(id, v.s("name")), id, v.s("key"), (v["enabled"] as? JsonPrimitive)?.booleanOrNull, v.s("base"), v.s("model"))
                        else if (v is JsonPrimitive) put(resolve(id), id, v.contentOrNull.orEmpty(), null, "", "")
                    }
                    else -> root.forEach { (id, v) ->
                        if (id in setOf("app", "v", "exported", "github", "version")) return@forEach
                        if (v is JsonPrimitive && v.isString) put(resolve(id), id, v.content, null, "", "")
                        else if (v is JsonObject && v.s("key").isNotEmpty()) put(resolve(id, v.s("name")), id, v.s("key"), null, v.s("base"), v.s("model"))
                    }
                }
                (root["github"] as? JsonObject)?.let { g -> git = BackupGit(g.s("repo"), g.s("branch"), g.s("token")) }
            } else if (root is JsonArray) {
                root.forEach { e -> (e as? JsonObject)?.let { o -> put(resolve(o.s("id"), o.s("name")), o.s("id").ifEmpty { o.s("name") }, o.s("key"), null, o.s("base"), o.s("model")) } }
            } else {
                src.lines().forEach { ln ->
                    val m = Regex("^\\s*([A-Za-z0-9_ .-]+?)\\s*[:=]\\s*(.+)$").find(ln) ?: return@forEach
                    val id = resolve(m.groupValues[1].replace(Regex("(?i)_?api_?key|_?key|_?token"), ""))
                    if (id != null) put(id, m.groupValues[1], m.groupValues[2], null, "", "")
                }
            }
            if (entries.isEmpty() && git == null) throw IllegalArgumentException(if (root == null) "لم أتعرّف على صيغة الملف." else "لا مزودين في الملف.")
            return Backup(entries.values.toList(), git, unknown.distinct())
        }
    }
}
