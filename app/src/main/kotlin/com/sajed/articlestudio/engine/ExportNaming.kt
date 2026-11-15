package com.sajed.articlestudio.engine

import com.sajed.articlestudio.BrandConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExportNaming {
    /** عدّاد عربي صحيح: مقال، مقالان، 3-10 مقالات، 11+ مقالاً. */
    fun countPhrase(n: Int): String = when {
        n <= 0 -> "0 مقالات"
        n == 1 -> "مقال واحد"
        n == 2 -> "مقالان"
        n in 3..10 -> "$n مقالات"
        else -> "$n مقالاً"
    }

    /** اسم الملف: «5 مقالات سبورت لايف 24 - 15-11-2026.xml» مع لاحقة (2) عند التكرار. */
    fun fileName(n: Int, date: LocalDate, site: String = BrandConfig.BLOG_NAME, exists: (String) -> Boolean): String {
        val head = when (n) { 1 -> "مقال واحد"; 2 -> "مقالان"; else -> countPhrase(n) }
        val base = "$head $site - ${date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        var name = "$base.xml"
        var i = 2
        while (exists(name)) name = "$base ($i).xml".also { i++ }
        return name
    }
}
