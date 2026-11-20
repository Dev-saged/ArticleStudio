package com.sajed.articlestudio.social

import android.content.Context
import com.sajed.articlestudio.data.ArticleDao
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** الحسابات وطابور المشاركة: التسجيل بعد نشر كل مقال، دون تكرار (مقال × حساب). */
@Singleton
class ShareStore @Inject constructor(
    @ApplicationContext private val ctx: Context, private val dao: SocialDao, private val sites: SiteStore, private val secrets: SecretStore, private val arts: ArticleDao,
) {
    val accounts = dao.observeAccounts()
    val jobs = dao.observeJobs()
    val marks = dao.observeMarks()
    val shareable = arts.observeShareable()

    private val prefs = ctx.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val _backfill = MutableStateFlow(prefs.getBoolean("backfill", true))
    val backfill: StateFlow<Boolean> = _backfill.asStateFlow()
    fun setBackfill(v: Boolean) { prefs.edit().putBoolean("backfill", v).apply(); _backfill.value = v }

    /** يضيف للطابور المقالات المنشورة سابقاً ولم تُسجَّل لحساب، بترتيب النشر الأقدم أولاً وبقدر ما تبقى من السقف اليومي. */
    suspend fun catchUp(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), r: Random = Random): Int {
        if (!_backfill.value) return 0
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val cands = arts.shareCandidates()
        var n = 0
        for (a in dao.accounts()) {
            if (!a.enabled || a.status == "needs_login") continue
            var left = a.dailyCap - dao.doneSince(a.id, today) - dao.pendingOf(a.id)
            if (left <= 0) continue
            val seen = dao.articleIdsOf(a.id).toHashSet()
            for (c in cands) {
                if (left <= 0) break
                if (c.id in seen) continue
                val site = sites.get(c.siteId)
                if (!site.share || PubState.of(site.pubState) == PubState.Draft) continue
                if (a.siteIds.isNotBlank() && c.siteId !in a.siteIds.split(",")) continue
                if (dao.insertJob(ShareJobEntity(articleId = c.id, siteId = c.siteId, accountId = a.id, platform = a.platform, url = c.remoteUrl, title = c.title, runAt = now + r.nextLong(60_000L, 4 * 60_000L), createdAt = now)) > 0) { n++; left-- }
            }
        }
        if (n > 0) runCatching { ShareScheduler.kick(ctx, 60_000L) }
        return n
    }

    /** مشاركة مقال منشور الآن على الحسابات المفعّلة (يُعيد جدولة ما فشل أو تُخطّي). يعيد عدد المهام الجديدة أو المعاد جدولتها. */
    suspend fun shareNow(articleId: Long, now: Long = System.currentTimeMillis()): Int {
        val c = arts.shareCandidates().firstOrNull { it.id == articleId } ?: return 0
        val existing = dao.jobsOfArticle(articleId).associateBy { it.accountId }
        var n = 0
        for (a in dao.accounts()) {
            if (!a.enabled || (a.siteIds.isNotBlank() && c.siteId !in a.siteIds.split(","))) continue
            val j = existing[a.id]
            if (j == null) { if (dao.insertJob(ShareJobEntity(articleId = c.id, siteId = c.siteId, accountId = a.id, platform = a.platform, url = c.remoteUrl, title = c.title, runAt = now, createdAt = now)) > 0) n++ }
            else if (j.status == "failed" || j.status == "skipped" || j.status == "needs_you") { dao.updateJob(j.copy(status = "pending", runAt = now, error = "", attempts = 0)); n++ }
        }
        if (n > 0) runCatching { ShareScheduler.kick(ctx, 0) }
        return n
    }

    /** يحدّث بيانات حساب api (المعرّف/الهدف، والسر إن لم يكن فارغاً) ويعيده إلى حالة «لم يُفحص». */
    suspend fun setCredentials(id: String, extra: Map<String, String>, secret: String) {
        val a = dao.account(id) ?: return
        val merged = extraOf(a).mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() } + extra.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }
        dao.upsertAccount(a.copy(extra = AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { merged.forEach { (k, v) -> put(k, v) } }), status = "new", statusNote = ""))
        if (secret.isNotBlank()) secrets.put("soc.$id", secret.trim())
    }

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
