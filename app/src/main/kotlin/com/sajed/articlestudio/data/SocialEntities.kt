package com.sajed.articlestudio.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** حساب مشاركة شخصي. الأسرار في SecretStore بمفتاح soc.<id>؛ وجلسات الويب في ملف تعريف WebView معزول باسم الحساب. */
@Entity(tableName = "social_accounts")
data class SocialAccountEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val label: String,
    val enabled: Boolean,
    @ColumnInfo(name = "daily_cap") val dailyCap: Int,
    @ColumnInfo(name = "gap_min") val gapMin: Int,
    @ColumnInfo(name = "win_from") val winFrom: Int,
    @ColumnInfo(name = "win_to") val winTo: Int,
    @ColumnInfo(name = "site_ids") val siteIds: String,
    val status: String,
    @ColumnInfo(name = "status_note") val statusNote: String,
    @ColumnInfo(name = "last_ok_at") val lastOkAt: Long,
    val extra: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** status: pending | running | done | failed | needs_you | skipped */
@Entity(tableName = "share_jobs", indices = [Index(value = ["article_id", "account_id"], unique = true), Index(value = ["status", "run_at"])])
data class ShareJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "article_id") val articleId: Long,
    @ColumnInfo(name = "site_id") val siteId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val platform: String,
    val url: String,
    val title: String,
    val text: String = "",
    val status: String = "pending",
    val attempts: Int = 0,
    @ColumnInfo(name = "run_at") val runAt: Long,
    @ColumnInfo(name = "done_at") val doneAt: Long = 0,
    val error: String = "",
    @ColumnInfo(name = "post_url") val postUrl: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val forced: Boolean = false,
)

@Dao
interface SocialDao {
    @Query("SELECT * FROM social_accounts ORDER BY created_at ASC")
    fun observeAccounts(): Flow<List<SocialAccountEntity>>

    @Query("SELECT * FROM social_accounts ORDER BY created_at ASC")
    suspend fun accounts(): List<SocialAccountEntity>

    @Query("SELECT * FROM social_accounts WHERE id = :id")
    suspend fun account(id: String): SocialAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(a: SocialAccountEntity)

    @Query("DELETE FROM social_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("UPDATE social_accounts SET status = :status, status_note = :note WHERE id = :id")
    suspend fun setStatus(id: String, status: String, note: String)

    @Query("UPDATE social_accounts SET status = 'ok', status_note = '', last_ok_at = :ts WHERE id = :id")
    suspend fun markOk(id: String, ts: Long)

    @Query("SELECT * FROM share_jobs ORDER BY CASE status WHEN 'running' THEN 0 WHEN 'pending' THEN 1 WHEN 'needs_you' THEN 2 ELSE 3 END, run_at DESC LIMIT 200")
    fun observeJobs(): Flow<List<ShareJobEntity>>

    @Query("SELECT * FROM share_jobs WHERE id = :id")
    suspend fun job(id: Long): ShareJobEntity?

    @Query("SELECT * FROM share_jobs WHERE status = 'pending' AND run_at <= :now ORDER BY run_at ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<ShareJobEntity>

    @Query("SELECT COUNT(*) FROM share_jobs WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT MIN(run_at) FROM share_jobs WHERE status = 'pending'")
    suspend fun nextRunAt(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJob(j: ShareJobEntity): Long

    @Update
    suspend fun updateJob(j: ShareJobEntity)

    @Query("SELECT COUNT(*) FROM share_jobs WHERE account_id = :accountId AND status = 'done' AND done_at >= :since")
    suspend fun doneSince(accountId: String, since: Long): Int

    @Query("SELECT COALESCE(MAX(done_at), 0) FROM share_jobs WHERE account_id = :accountId AND status = 'done'")
    suspend fun lastDone(accountId: String): Long

    /** مهمة كانت قيد الإرسال عند توقف التطبيق: النتيجة مجهولة، فلا تُعاد تلقائياً كي لا يتكرر المنشور. */
    @Query("UPDATE share_jobs SET status = 'needs_you', error = 'توقف التطبيق أثناء الإرسال؛ تحقق من الحساب ثم أعد المحاولة أو تخطَّ' WHERE status = 'running'")
    suspend fun parkInterrupted()

    @Query("UPDATE share_jobs SET status = 'pending', run_at = :now, error = '' WHERE account_id = :accountId AND status = 'needs_you' AND error LIKE 'login:%'")
    suspend fun requeueLogin(accountId: String, now: Long)

    @Query("DELETE FROM share_jobs WHERE account_id = :accountId")
    suspend fun deleteJobsOf(accountId: String)

    @Query("SELECT COUNT(*) FROM share_jobs WHERE account_id = :accountId AND status IN ('pending','running')")
    suspend fun pendingOf(accountId: String): Int

    @Query("SELECT article_id FROM share_jobs WHERE account_id = :accountId")
    suspend fun articleIdsOf(accountId: String): List<Long>

    @Query("SELECT article_id AS articleId, account_id AS accountId, status FROM share_jobs")
    fun observeMarks(): Flow<List<JobMark>>

    @Query("SELECT * FROM share_jobs WHERE platform = :platform AND status IN ('pending','needs_you','failed') ORDER BY run_at ASC, id ASC")
    suspend fun toPublish(platform: String): List<ShareJobEntity>

    @Query("UPDATE share_jobs SET forced = 0 WHERE platform = :platform AND status = 'pending' AND forced = 1")
    suspend fun unforce(platform: String)

    @Query("SELECT platform, status, COUNT(*) AS n, MIN(run_at) AS next FROM share_jobs GROUP BY platform, status")
    fun observeStats(): Flow<List<PlatformStat>>

    @Query("SELECT platform, done_at AS doneAt FROM share_jobs WHERE status = 'done' AND done_at >= :since")
    fun observeDoneSince(since: Long): Flow<List<DoneMark>>

    @Query("SELECT * FROM share_jobs WHERE platform = :platform AND status IN ('pending','running') ORDER BY forced DESC, run_at ASC, id ASC LIMIT :limit OFFSET :offset")
    fun observeWaiting(platform: String, limit: Int, offset: Int): Flow<List<ShareJobEntity>>

    @Query("SELECT * FROM share_jobs WHERE platform = :platform AND status IN ('needs_you','failed') ORDER BY run_at DESC LIMIT :limit OFFSET :offset")
    fun observeNeeds(platform: String, limit: Int, offset: Int): Flow<List<ShareJobEntity>>

    @Query("SELECT * FROM share_jobs WHERE platform = :platform AND status IN ('done','skipped') ORDER BY done_at DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observeHistory(platform: String, limit: Int, offset: Int): Flow<List<ShareJobEntity>>

    @Query("SELECT * FROM share_jobs WHERE status IN ('needs_you','failed') ORDER BY run_at DESC LIMIT 50")
    fun observeAllNeeds(): Flow<List<ShareJobEntity>>

    @Query("SELECT * FROM share_jobs WHERE article_id = :articleId")
    fun observeOfArticle(articleId: Long): Flow<List<ShareJobEntity>>

    @Query("UPDATE share_jobs SET text = :text WHERE id = :id AND status != 'done'")
    suspend fun setText(id: Long, text: String)

    @Query("SELECT * FROM share_jobs WHERE article_id = :articleId")
    suspend fun jobsOfArticle(articleId: Long): List<ShareJobEntity>
}

/** حالة مشاركة مقال على حساب (للسجل المرئي). */
data class JobMark(val articleId: Long, val accountId: String, val status: String)

/** مقال منشور برابط عام، مرشح للمشاركة. */
data class ShareCandidate(val id: Long, val title: String, @ColumnInfo(name = "site_id") val siteId: String, @ColumnInfo(name = "remote_url") val remoteUrl: String)

/** عدد مهام منصة في حالة، وأقرب موعد. */
data class PlatformStat(val platform: String, val status: String, val n: Int, val next: Long?)

/** منشور تمّ (للمنحنى الأسبوعي). */
data class DoneMark(val platform: String, val doneAt: Long)
