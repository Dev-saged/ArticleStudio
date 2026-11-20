package com.sajed.articlestudio.social

import android.content.Context
import com.sajed.articlestudio.engine.AppJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** وصفات الواجهة بيانات فقط (روابط ومحددات وخطوات من قائمة مغلقة)؛ لا تُنفَّذ نصوص JS قادمة من الخارج. */
object RecipeRules {
    private val OPS = setOf("type", "click", "sleep", "waitGone", "attach")

    /** يعيد سبب الرفض أو null إن كانت الوصفة صالحة. */
    fun validate(raw: String): String? {
        val root = runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return "ليست JSON"
        val pl = (root["platforms"] as? JsonObject) ?: return "platforms ناقصة"
        if (pl.isEmpty()) return "لا منصات"
        for ((id, v) in pl) {
            val vs = (v as? JsonObject)?.get("variants") as? JsonArray ?: return "$id: variants ناقصة"
            if (vs.isEmpty()) return "$id: لا نسخ"
            for (x in vs) {
                val o = x as? JsonObject ?: return "$id: نسخة غير صالحة"
                val url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (!url.startsWith("https://")) return "$id: الرابط يجب أن يكون https"
                val steps = o["steps"] as? JsonArray ?: return "$id: steps ناقصة"
                if (steps.none { ((it as? JsonObject)?.get("final")?.jsonPrimitive?.contentOrNull == "true") }) return "$id: لا نقرة نهائية"
                for (s in steps) {
                    val op = (s as? JsonObject)?.get("op")?.jsonPrimitive?.contentOrNull
                    if (op !in OPS) return "$id: عملية مجهولة $op"
                }
            }
        }
        return null
    }

    fun expand(template: String, v: Map<String, String>): String = Regex("\\{(\\w+)\\}").replace(template) { m ->
        val k = m.groupValues[1]
        when {
            k.endsWith("_enc") -> URLEncoder.encode(v[k.removeSuffix("_enc")].orEmpty(), "UTF-8")
            else -> v[k] ?: m.value
        }
    }
}

@Singleton
class RecipeStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val file get() = File(ctx.filesDir, "social/recipes.json")

    private fun bundled() = ctx.assets.open("social/recipes.json").bufferedReader().use { it.readText() }

    fun raw(): String = runCatching { file.takeIf { it.exists() }?.readText()?.takeIf { RecipeRules.validate(it) == null } }.getOrNull() ?: bundled()

    fun variants(platform: String): List<JsonObject> =
        ((AppJson.parseToJsonElement(raw()).jsonObject["platforms"] as? JsonObject)?.get(platform) as? JsonObject)?.get("variants")?.jsonArray?.map { it.jsonObject }.orEmpty()

    fun install(text: String): String? {
        RecipeRules.validate(text)?.let { return it }
        file.parentFile?.mkdirs(); file.writeText(text)
        return null
    }

    fun reset() { file.delete() }
}
