package com.sajed.articlestudio.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** صف خفيف للقوائم بلا النص الكامل، فلا يُحمَّل JSON كل مقال عند كل تغيير. */
data class ArticleRow(
    val id: Long, @ColumnInfo(name = "job_id") val jobId: Long, val idx: Int, val title: String, val labels: String,
    val status: String, val words: Int, val provider: String, val error: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long, val quality: Int, @ColumnInfo(name = "site_id") val siteId: String,
    @ColumnInfo(name = "remote_url") val remoteUrl: String,
)

@Dao
interface ArticleDao {
    @Query("SELECT id, job_id, idx, title, labels, status, words, provider, error, updated_at, quality, site_id, remote_url FROM articles ORDER BY job_id DESC, idx ASC")
    fun observeRows(): Flow<List<ArticleRow>>

    @Query("SELECT * FROM articles WHERE status = 'ready' AND site_id = :siteId ORDER BY job_id ASC, idx ASC")
    suspend fun readyFor(siteId: String): List<ArticleEntity>

    @Query("SELECT title FROM articles WHERE status NOT IN ('failed','duplicate') AND site_id = :siteId")
    suspend fun titlesFor(siteId: String): List<String>

    @Query("SELECT id FROM articles WHERE quality = 0 AND status NOT IN ('failed','duplicate') AND data != ''")
    suspend fun needQuality(): List<Long>

    @Query("UPDATE articles SET quality = :q WHERE id = :id")
    suspend fun setQuality(id: Long, q: Int)

    @Query("UPDATE articles SET remote_url = :url WHERE id = :id")
    suspend fun setRemote(id: Long, url: String)

    @Query("SELECT id FROM articles WHERE remote_url != '' AND status != 'failed' AND id NOT IN (SELECT article_id FROM reviews)")
    suspend fun publishedWithoutReview(): List<Long>

    @Query("SELECT * FROM articles WHERE status = 'exported' AND remote_url = '' AND site_id = :siteId ORDER BY job_id ASC, idx ASC")
    suspend fun unpublished(siteId: String): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE site_id = :siteId AND title = :title AND status NOT IN ('failed','duplicate') LIMIT 1")
    suspend fun byTitle(siteId: String, title: String): ArticleEntity?

    @Query("DELETE FROM articles WHERE site_id = :siteId")
    suspend fun deleteSite(siteId: String)

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: Long): ArticleEntity?

    @Query("SELECT COUNT(*) FROM articles WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("UPDATE articles SET status = 'exported', updated_at = :ts WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>, ts: Long)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM articles WHERE status = 'exported'")
    suspend fun clearExported()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: ArticleEntity): Long
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews ORDER BY published_at DESC")
    fun observeAll(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews ORDER BY published_at DESC")
    suspend fun all(): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE article_id = :articleId")
    suspend fun byArticle(articleId: Long): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE site_id = :siteId AND rating BETWEEN 1 AND 3")
    suspend fun lowRated(siteId: String): List<ReviewEntity>

    @Query("UPDATE reviews SET rating = :rating, tags = :tags, notes = :notes, reviewed_at = :ts WHERE id = :id")
    suspend fun rate(id: Long, rating: Int, tags: String, notes: String, ts: Long)

    @Query("DELETE FROM reviews WHERE site_id = :siteId")
    suspend fun deleteSite(siteId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(r: ReviewEntity): Long
}

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(j: JobEntity): Long

    @Query("SELECT * FROM jobs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): JobEntity?

    @Query("SELECT * FROM jobs WHERE site_id = :siteId ORDER BY id DESC LIMIT 1")
    suspend fun latestFor(siteId: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id IN (SELECT MAX(id) FROM jobs GROUP BY site_id)")
    suspend fun latestPerSite(): List<JobEntity>

    @Query("SELECT * FROM jobs ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<JobEntity?>

    @Query("DELETE FROM jobs WHERE site_id = :siteId")
    suspend fun deleteSite(siteId: String)
}

/** ترقيم تراكمي: لا يُعدَّل ترحيل قديم أبداً، تُضاف عناصر جديدة فقط. */
object Migrations {
    val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN data TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN topics TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN next INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE jobs ADD COLUMN mode TEXT NOT NULL DEFAULT 'news'")
            db.execSQL("ALTER TABLE jobs ADD COLUMN custom TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE jobs ADD COLUMN failed INTEGER NOT NULL DEFAULT 0")
        }
    }
    val M2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN site_id TEXT NOT NULL DEFAULT 'default'")
            db.execSQL("ALTER TABLE articles ADD COLUMN quality INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE jobs ADD COLUMN site_id TEXT NOT NULL DEFAULT 'default'")
        }
    }
    val M3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE articles ADD COLUMN remote_url TEXT NOT NULL DEFAULT ''")
        }
    }
    val M4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, article_id INTEGER NOT NULL, site_id TEXT NOT NULL, title TEXT NOT NULL, remote_url TEXT NOT NULL, published_at INTEGER NOT NULL, words INTEGER NOT NULL, provider TEXT NOT NULL, auto INTEGER NOT NULL, metrics TEXT NOT NULL, flags TEXT NOT NULL, rating INTEGER NOT NULL, tags TEXT NOT NULL, notes TEXT NOT NULL, reviewed_at INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reviews_article_id ON reviews (article_id)")
        }
    }
    val M5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS social_accounts (id TEXT NOT NULL, platform TEXT NOT NULL, label TEXT NOT NULL, enabled INTEGER NOT NULL, daily_cap INTEGER NOT NULL, gap_min INTEGER NOT NULL, win_from INTEGER NOT NULL, win_to INTEGER NOT NULL, site_ids TEXT NOT NULL, status TEXT NOT NULL, status_note TEXT NOT NULL, last_ok_at INTEGER NOT NULL, extra TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS share_jobs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, article_id INTEGER NOT NULL, site_id TEXT NOT NULL, account_id TEXT NOT NULL, platform TEXT NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL, run_at INTEGER NOT NULL, done_at INTEGER NOT NULL, error TEXT NOT NULL, post_url TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_share_jobs_article_id_account_id ON share_jobs (article_id, account_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_share_jobs_status_run_at ON share_jobs (status, run_at)")
        }
    }
    val ALL: Array<Migration> = arrayOf(M1_2, M2_3, M3_4, M4_5, M5_6)
}

@Database(entities = [ArticleEntity::class, JobEntity::class, ReviewEntity::class, SocialAccountEntity::class, ShareJobEntity::class], version = 6, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun articles(): ArticleDao
    abstract fun jobs(): JobDao
    abstract fun reviews(): ReviewDao
    abstract fun social(): SocialDao
}
