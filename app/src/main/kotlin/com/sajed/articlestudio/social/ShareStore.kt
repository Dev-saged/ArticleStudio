package com.sajed.articlestudio.social

import android.content.Context
import com.sajed.articlestudio.data.SecretStore
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.data.SocialDao
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.PubState
import com.sajed.articlestudio.engine.SiteStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** الحسابات وطابور المشاركة: التسجيل بعد نشر كل مقال، دون تكرار (مقال × حساب). */
@Singleton
class ShareStore @Inject constructor(
    @ApplicationContext private val ctx: Context, private val dao: SocialDao, private val sites: SiteStore, private val secrets: SecretStore,
) {
    val accounts = dao.observeAccounts()
    val jobs = dao.observeJobs()

    /** يضيف مهام لكل حساب مؤهل. المسودة لا تُشارك (رابطها غير عام)، والمجدول يُشارك بعد موعد نشره. */
    suspend fun enqueue(articleId: Long, siteId: String, url: String, title: String, publishAt: Instant?, state: PubState, now: Long = System.currentTimeMillis(), r: Random = Random): Int {
        if (state == PubState.Draft || !url.startsWith("http") || !sites.get(siteId).share) return 0
        var n = 0
        for (a in dao.accounts()) {
            if (!a.enabled || (a.siteIds.isNotBlank() && siteId !in a.siteIds.split(","))) continue
            val base = if (state == PubState.Scheduled) (publishAt?.toEpochMilli() ?: now) + 5 * 60_000L else now
            val at = base + r.nextLong(2 * 60_000L, 8 * 60_000L)
            if (dao.insertJob(ShareJobEntity(articleId = articleId, siteId = siteId, accountId = a.id, platform = a.platform, url = url, title = title, runAt = at, createdAt = now)) > 0) n++
        }
        if (n > 0) runCatching { ShareScheduler.kick(ctx, 2 * 60_000L) }
        return n
    }

    suspend fun addAccount(platform: String, label: String, extra: Map<String, String>, secret: String, now: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString().take(8)
        val spec = Platforms.of(platform) ?: throw IllegalArgumentException("منصة غير معروفة")
        dao.upsertAccount(SocialAccountEntity(
            id = id, platform = platform, label = label.trim().ifEmpty { spec.name }.take(40), enabled = true, dailyCap = 3, gapMin = 20, winFrom = 8, winTo = 23,
            siteIds = "", status = if (spec.isWeb) "needs_login" else "new", statusNote = if (spec.isWeb) "سجّل الدخول لأول مرة" else "", lastOkAt = 0,
            extra = AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { extra.forEach { (k, v) -> put(k, v.trim()) } }), createdAt = now,
        ))
        if (secret.isNotBlank()) secrets.put("soc.$id", secret.trim())
        return id
    }

    suspend fun update(a: SocialAccountEntity) = dao.upsertAccount(a.copy(
        label = a.label.trim().take(40), dailyCap = a.dailyCap.coerceIn(1, 20), gapMin = a.gapMin.coerceIn(1, 720), winFrom = a.winFrom.coerceIn(0, 24), winTo = a.winTo.coerceIn(0, 24)))

    suspend fun remove(id: String) { dao.deleteJobsOf(id); dao.deleteAccount(id); secrets.put("soc.$id", "") }

    suspend fun markLoggedIn(id: String, now: Long = System.currentTimeMillis()) { dao.markOk(id, now); dao.requeueLogin(id, now) }

    suspend fun retry(jobId: Long, now: Long = System.currentTimeMillis()) {
        val j = dao.job(jobId) ?: return
        dao.updateJob(j.copy(status = "pending", runAt = now, error = "", attempts = 0))
        runCatching { ShareScheduler.kick(ctx, 0) }
    }

    suspend fun skip(jobId: Long) { dao.job(jobId)?.let { dao.updateJob(it.copy(status = "skipped")) } }
}
