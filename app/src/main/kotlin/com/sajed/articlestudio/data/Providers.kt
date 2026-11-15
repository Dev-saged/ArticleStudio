package com.sajed.articlestudio.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

class ProviderSpec(
    val id: String, val name: String, val base: String, val needsKey: Boolean,
    val link: String, val hint: String, val defaultOn: Boolean = true,
)

object ProviderCatalog {
    val all = listOf(
        ProviderSpec("cerebras", "Cerebras", "https://api.cerebras.ai/v1", true, "https://cloud.cerebras.ai", "سريع جداً وحد يومي سخي. مفتاح مجاني."),
        ProviderSpec("groq", "Groq", "https://api.groq.com/openai/v1", true, "https://console.groq.com/keys", "سريع بنماذج مفتوحة. مفتاح مجاني."),
        ProviderSpec("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", true, "https://aistudio.google.com/apikey", "نماذج Flash المجانية من AI Studio."),
        ProviderSpec("mistral", "Mistral", "https://api.mistral.ai/v1", true, "https://console.mistral.ai/api-keys", "خطة تجريبية مجانية، جيد بالعربية."),
        ProviderSpec("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", true, "https://openrouter.ai/keys", "نماذج :free متعددة بحد يومي صغير."),
        ProviderSpec("cohere", "Cohere", "https://api.cohere.ai/compatibility/v1", true, "https://dashboard.cohere.com/api-keys", "مفتاح Trial مجاني بحدود شهرية."),
        ProviderSpec("pollinations", "Pollinations", "https://text.pollinations.ai/openai", false, "https://pollinations.ai", "بلا مفتاح إطلاقاً. أبطأ، شبكة أمان أخيرة."),
        ProviderSpec("zen", "OpenCode Zen", "https://opencode.ai/zen/v1", true, "https://opencode.ai/auth", "تجريبي: نماذج -free وقد يرفضه الخادم.", defaultOn = false),
        ProviderSpec("custom", "مزود مخصص", "", true, "", "أي خدمة متوافقة مع OpenAI: الرابط الأساسي والنموذج والمفتاح.", defaultOn = false),
    )
    fun spec(id: String) = all.first { it.id == id }
}

@Singleton
class ProviderStore @Inject constructor(@ApplicationContext c: Context, private val secrets: SecretStore) {
    private val sp = c.getSharedPreferences("providers", Context.MODE_PRIVATE)

    fun enabled(id: String) = sp.getBoolean("$id.on", ProviderCatalog.spec(id).defaultOn)
    fun model(id: String) = sp.getString("$id.model", "") ?: ""
    fun base(id: String) = if (id == "custom") sp.getString("custom.base", "")?.trim().orEmpty() else ProviderCatalog.spec(id).base
    fun key(id: String) = secrets.get("key.$id").trim()
    fun hasKey(id: String) = secrets.has("key.$id")

    fun ready(s: ProviderSpec) = enabled(s.id) && (!s.needsKey || key(s.id).isNotEmpty()) &&
        (s.id != "custom" || (base(s.id).isNotEmpty() && model(s.id).isNotEmpty()))

    fun readySpecs() = ProviderCatalog.all.filter(::ready)

    fun save(id: String, model: String, base: String, key: String, enabled: Boolean) {
        sp.edit().putBoolean("$id.on", enabled).apply {
            if (id == "custom") { putString("custom.model", model); putString("custom.base", base) }
        }.apply()
        if (key.isNotEmpty()) secrets.put("key.$id", key)
    }

    fun clearKey(id: String) = secrets.put("key.$id", "")

    /** تصدير المفاتيح كنص JSON: مفتاح لكل مزود. */
    fun exportKeys(): String {
        val m = ProviderCatalog.all.mapNotNull { s -> key(s.id).takeIf { it.isNotEmpty() }?.let { s.id to JsonPrimitive(it) } }.toMap()
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(m))
    }

    fun importKeys(text: String): Int {
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return 0
        var n = 0
        for (s in ProviderCatalog.all) {
            val v = o[s.id]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (v.length in 8..300) { secrets.put("key.${s.id}", v); n++ }
        }
        return n
    }
}
