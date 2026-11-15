package com.sajed.articlestudio.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** بيانات ثابتة من الأصول: الرموز المرسومة (SVG) وعناوين المقالات المنشورة سابقاً. */
@Singleton
class StudioData @Inject constructor(@ApplicationContext ctx: Context) {
    private val root = Json.parseToJsonElement(ctx.assets.open("studio-data.json").bufferedReader().use { it.readText() }).jsonObject
    val motifs: Map<String, String> = root["motifs"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
    val seed: List<String> = root["seed"]!!.jsonArray.map { it.jsonPrimitive.content }
}
