package com.sajed.articlestudio.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
        ProviderSpec("sambanova", "SambaNova", "https://api.sambanova.ai/v1", true, "https://cloud.sambanova.ai/apis", "سريع بنماذج Llama وgpt-oss. مفتاح مجاني بلا بطاقة."),
        ProviderSpec("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1", true, "https://build.nvidia.com/settings/api-keys", "عشرات النماذج الكبيرة مجاناً. مفتاح من حساب NVIDIA."),
        ProviderSpec("zai", "Z.ai (GLM)", "https://api.z.ai/api/paas/v4", true, "https://z.ai/manage-apikey/apikey-list", "نماذج GLM Flash مجانية دائماً. جيد بالعربية."),
        ProviderSpec("longcat", "LongCat", "https://api.longcat.chat/openai/v1", true, "https://longcat.chat/platform/api_keys", "حصة يومية مجانية لنموذج LongCat Flash."),
        ProviderSpec("llm7", "LLM7", "https://api.llm7.io/v1", false, "https://token.llm7.io", "يعمل بلا مفتاح بحد منخفض؛ المفتاح المجاني يرفعه.", defaultOn = false),
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
}
