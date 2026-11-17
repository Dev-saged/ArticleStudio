package com.sajed.articlestudio.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
class IndexItem(
    val title: String, val slug: String, val labels: List<String>, val words: Int, val provider: String,
    val sources: List<String>, val batch: String, val date: String, val quality: Int = 0,
)

@Serializable
class SiteIndexDoc(val siteId: String, val site: String, val url: String, val niche: String, val updated: String, val count: Int, val items: List<IndexItem>)

/** فهرس مستقل لكل موقع: دفعة التصدير (يُحفظ بجانب XML) وفهرس تراكمي داخلي بلا تداخل بين المواقع. */
object SiteIndex {
    fun items(list: List<Article>, batch: String, date: String) =
        list.map { IndexItem(it.title, it.finalSlug, it.labels, it.words, it.provider, it.sources.map { s -> s.url }.filter { u -> u.isNotEmpty() }, batch, date, it.quality) }

    fun doc(site: SiteConfig, items: List<IndexItem>, date: String) =
        SiteIndexDoc(site.id, site.name, site.url, site.niche, date, items.size, items)

    fun encode(d: SiteIndexDoc) = AppJson.encodeToString(SiteIndexDoc.serializer(), d)

    /** يدمج الدفعة في الفهرس التراكمي للموقع (إحلال بالـslug) ويعيد الوثيقة كاملة. */
    fun merge(dir: File, site: SiteConfig, add: List<IndexItem>, date: String): SiteIndexDoc {
        val f = File(dir, "index-${site.id}.json").also { dir.mkdirs() }
        val old = runCatching { AppJson.decodeFromString(SiteIndexDoc.serializer(), f.readText()).items }.getOrDefault(emptyList())
        val keep = old.filter { o -> add.none { it.slug == o.slug } }
        val doc = doc(site, keep + add, date)
        f.writeText(encode(doc))
        return doc
    }
}
