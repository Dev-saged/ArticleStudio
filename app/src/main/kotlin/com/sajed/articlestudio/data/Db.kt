package com.sajed.articlestudio.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY job_id DESC, idx ASC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE status = 'ready' ORDER BY job_id ASC, idx ASC")
    suspend fun ready(): List<ArticleEntity>

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
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(j: JobEntity): Long

    @Query("SELECT * FROM jobs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): JobEntity?

    @Query("SELECT * FROM jobs ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<JobEntity?>
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
    val ALL: Array<Migration> = arrayOf(M1_2)
}

@Database(entities = [ArticleEntity::class, JobEntity::class], version = 2, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun articles(): ArticleDao
    abstract fun jobs(): JobDao
}
