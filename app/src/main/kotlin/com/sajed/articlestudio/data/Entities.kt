package com.sajed.articlestudio.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** status: ready | failed | exported. data: Article JSON للجاهز، Topic JSON للفاشل. */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "job_id") val jobId: Long,
    val idx: Int,
    val title: String,
    val slug: String,
    val labels: String = "",
    val status: String,
    val words: Int = 0,
    val provider: String = "",
    val html: String = "",
    val error: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val data: String = "",
    @ColumnInfo(name = "site_id", defaultValue = "'default'") val siteId: String = "default",
    @ColumnInfo(defaultValue = "0") val quality: Int = 0,
)

/** state: running | paused | done. topics: خطة المواضيع JSON، next: فهرس الموضوع التالي. */
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val total: Int,
    val done: Int = 0,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val topics: String = "",
    @ColumnInfo(defaultValue = "0") val next: Int = 0,
    @ColumnInfo(defaultValue = "'news'") val mode: String = "news",
    @ColumnInfo(defaultValue = "''") val custom: String = "",
    @ColumnInfo(defaultValue = "0") val failed: Int = 0,
    @ColumnInfo(name = "site_id", defaultValue = "'default'") val siteId: String = "default",
)
