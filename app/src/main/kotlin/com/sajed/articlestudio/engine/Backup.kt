package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.GenSettings
import com.sajed.articlestudio.data.GitHubSettings
import com.sajed.articlestudio.data.ReviewDao
import com.sajed.articlestudio.data.ReviewEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
class BackupFile(
    val version: Int = 1,
    val savedAt: Long = 0,
    val parallel: Int = 3,
    val ghRepo: String = "",
    val ghBranch: String = "",
    val activeSite: String = "",
    val sites: List<SiteConfig> = emptyList(),
    val reviews: List<ReviewBackup> = emptyList(),
)

@Serializable
class ReviewBackup(
    val siteId: String, val title: String, val remoteUrl: String = "", val publishedAt: Long, val words: Int = 0,
    val provider: String = "", val auto: Int = 0, val metrics: String = "{}", val flags: String = "",
    val rating: Int = 0, val tags: String = "", val notes: String = "", val reviewedAt: Long = 0,
)

/** نسخة احتياطية: المواقع والإعدادات وسجل المراجعات إلى GitHub؛ لا مفاتيح ولا توكن. */
@Singleton
class BackupManager @Inject constructor(
    private val sites: SiteStore, private val gen: GenSettings, private val gh: GitHubSettings,
    private val reviews: ReviewDao, private val client: GitHubClient,
) {
    companion object {
        const val BRANCH = "app-backup"
        const val PATH = "backup/articlestudio-backup.json"
        fun encode(b: BackupFile): String = AppJson.encodeToString(BackupFile.serializer(), b)
        fun decode(s: String): BackupFile {
            val b = AppJson.decodeFromString(BackupFile.serializer(), s)
            require(b.version == 1) { "إصدار نسخة غير مدعوم" }
            return b
        }
    }

    suspend fun snapshot(now: Long = System.currentTimeMillis()) = BackupFile(
        savedAt = now, parallel = gen.parallel.value, ghRepo = gh.repo, ghBranch = gh.branch, activeSite = sites.activeId.value,
        sites = sites.sites.value,
        reviews = reviews.all().map { ReviewBackup(it.siteId, it.title, it.remoteUrl, it.publishedAt, it.words, it.provider, it.auto, it.metrics, it.flags, it.rating, it.tags, it.notes, it.reviewedAt) },
    )

    suspend fun push(): String {
        val b = snapshot()
        client.putText(gh.repo, BRANCH, PATH, encode(b), "backup: ${b.sites.size} sites, ${b.reviews.size} reviews")
        return "حُفظت ${b.sites.size} مواقع و${b.reviews.size} مراجعة في ${gh.repo}"
    }

    /** يدمج النسخة مع الموجود: المواقع تُحدَّث بالمعرّف، والمراجعات المكررة (موقع+عنوان) تُتخطّى. */
    suspend fun restore(): String {
        val txt = client.getText(gh.repo, BRANCH, PATH) ?: return "لا توجد نسخة احتياطية في ${gh.repo}"
        val b = decode(txt)
        b.sites.forEach { sites.upsert(it) }
        gen.setParallel(b.parallel)
        if (b.sites.any { it.id == b.activeSite }) sites.setActive(b.activeSite)
        val have = reviews.all().map { it.siteId to it.title }.toHashSet()
        var added = 0
        b.reviews.filter { (it.siteId to it.title) !in have }.forEach {
            val id = reviews.insert(ReviewEntity(
                articleId = -(System.nanoTime() % 1_000_000_000L) - added, siteId = it.siteId, title = it.title, remoteUrl = it.remoteUrl,
                publishedAt = it.publishedAt, words = it.words, provider = it.provider, auto = it.auto, metrics = it.metrics, flags = it.flags,
                rating = it.rating, tags = it.tags, notes = it.notes, reviewedAt = it.reviewedAt,
            ))
            if (id > 0) added++
        }
        return "استُعيدت ${b.sites.size} مواقع و$added مراجعة"
    }
}
