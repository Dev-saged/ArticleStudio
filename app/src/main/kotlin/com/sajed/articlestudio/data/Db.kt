package com.sajed.articlestudio.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY job_id DESC, idx ASC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM articles WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: ArticleEntity): Long
}

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(j: JobEntity): Long

    @Query("SELECT * FROM jobs ORDER BY id DESC LIMIT 1")
    suspend fun latest(): JobEntity?
}

/** ترقيم تراكمي: لا يُعدَّل ترحيل قديم أبداً، تُضاف عناصر جديدة فقط. */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}

@Database(entities = [ArticleEntity::class, JobEntity::class], version = 1, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun articles(): ArticleDao
    abstract fun jobs(): JobDao
}
