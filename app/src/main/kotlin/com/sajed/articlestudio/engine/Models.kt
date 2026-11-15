package com.sajed.articlestudio.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Ref(val h: String, val url: String, val d: String = "", val t: Long = 0, val s: String = "")
@Serializable data class Topic(val topic: String, val angle: String = "", val labels: List<String> = emptyList(), val ref: Ref? = null, val refs: List<Ref> = emptyList()) {
    val all: List<Ref> get() = refs.ifEmpty { listOfNotNull(ref) }
}
@Serializable data class Fact(val v: String, val c: String)
@Serializable data class Section(val h: String, val html: String)
@Serializable data class Faq(val q: String, val a: String)

@Serializable
data class Article(
    val title: String, val desc: String, val slug: String, val labels: List<String>,
    val kicker: String, val motif: String, val hue: Int, val infotitle: String,
    val facts: List<Fact>, val tldr: String, val intro: String, val sections: List<Section>,
    val faq: List<Faq>, val outro: String, val ref: Ref?, val words: Int,
    val provider: String, val model: String, val topic: String, val finalSlug: String = "", val refs: List<Ref> = emptyList(),
) {
    val sources: List<Ref> get() = refs.ifEmpty { listOfNotNull(ref) }
    /** درجة الجودة 0..100: الطول، الأقسام، الحقائق، الأسئلة، المصدر. */
    val quality: Int get() {
        var q = 0
        q += (words.coerceAtMost(800) * 40 / 800)
        q += (sections.size.coerceAtMost(5) * 4)
        q += (facts.size.coerceAtMost(4) * 5)
        q += (faq.size.coerceAtMost(3) * 4)
        if (desc.length in 110..160) q += 4
        if (title.length in 35..75) q += 4
        return q.coerceAtMost(100)
    }
}

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
