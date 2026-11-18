package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.ArticleRow
import com.sajed.articlestudio.data.ReviewEntity

class ProvRate(val id: String, val ok: Int, val fail: Int) {
    val total get() = ok + fail
    val failPct get() = if (total == 0) 0 else fail * 100 / total
}

class SiteWeek(val siteId: String, val published: Int, val generated: Int)

/** حسابات لوحة الإحصاءات، نقية وقابلة للاختبار. */
object Stats {
    const val WEEK = 7L * 24 * 3600 * 1000
    private const val MAX_GAP = 10L * 60 * 1000

    fun weekly(rows: List<ArticleRow>, reviews: List<ReviewEntity>, now: Long): List<SiteWeek> {
        val from = now - WEEK
        val pub = reviews.filter { it.publishedAt >= from }.groupingBy { it.siteId }.eachCount()
        val gen = rows.filter { it.updatedAt >= from && it.status in setOf("ready", "exported") }.groupingBy { it.siteId }.eachCount()
        return (pub.keys + gen.keys).map { SiteWeek(it, pub[it] ?: 0, gen[it] ?: 0) }.sortedByDescending { it.published + it.generated }
    }

    /** متوسط ثواني المقال الواحد: الفارق بين مقالين متتاليين من نفس المهمة، مع تجاهل فجوات التوقف الطويلة. */
    fun secondsPerArticle(rows: List<ArticleRow>): Int {
        val gaps = rows.filter { it.status != "failed" }.groupBy { it.jobId }.values.flatMap { l ->
            l.map { it.updatedAt }.sorted().zipWithNext { a, b -> b - a }.filter { it in 1_000..MAX_GAP }
        }
        return if (gaps.isEmpty()) 0 else (gaps.sum() / gaps.size / 1000).toInt()
    }

    fun byProvider(rows: List<ArticleRow>): Map<String, Int> =
        rows.filter { it.status in setOf("ready", "exported") && it.provider.isNotBlank() }.groupingBy { it.provider }.eachCount()
}
