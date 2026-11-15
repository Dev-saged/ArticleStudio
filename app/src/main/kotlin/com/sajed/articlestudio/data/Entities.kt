package com.sajed.articlestudio.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val total: Int,
    val done: Int = 0,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
