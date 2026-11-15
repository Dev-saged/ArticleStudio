package com.sajed.articlestudio.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class ProviderInfo(val id: String, val name: String, val defaultModel: String, val keyless: Boolean = false)

object ProviderCatalog {
    val all = listOf(
        ProviderInfo("cerebras", "Cerebras", "llama-3.3-70b"),
        ProviderInfo("groq", "Groq", "llama-3.3-70b-versatile"),
        ProviderInfo("gemini", "Gemini", "gemini-2.0-flash"),
        ProviderInfo("mistral", "Mistral", "mistral-small-latest"),
        ProviderInfo("openrouter", "OpenRouter", "meta-llama/llama-3.3-70b-instruct:free"),
        ProviderInfo("pollinations", "Pollinations", "openai", keyless = true),
    )
}

@Singleton
class ProviderStore @Inject constructor(@ApplicationContext c: Context, private val secrets: SecretStore) {
    private val sp = c.getSharedPreferences("providers", Context.MODE_PRIVATE)

    fun enabled(id: String) = sp.getBoolean("$id.on", true)
    fun model(id: String, def: String) = sp.getString("$id.model", null) ?: def
    fun hasKey(id: String) = secrets.has("key.$id")
    fun key(id: String) = secrets.get("key.$id")

    fun save(id: String, model: String, key: String, enabled: Boolean) {
        sp.edit().putString("$id.model", model).putBoolean("$id.on", enabled).apply()
        if (key.isNotEmpty()) secrets.put("key.$id", key)
    }
}
