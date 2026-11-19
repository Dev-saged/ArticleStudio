package com.sajed.articlestudio

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.Migrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate1To2KeepsRowsAndAddsColumns() {
        helper.createDatabase("t", 1).apply {
            execSQL("INSERT INTO jobs (total, done, state, created_at) VALUES (5, 2, 'done', 1)")
            execSQL("INSERT INTO articles (job_id, idx, title, slug, labels, status, words, provider, html, error, updated_at) VALUES (1, 0, 'عنوان', 's', '', 'ready', 700, 'groq', '', '', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("t", 2, true, *Migrations.ALL)
        db.query("SELECT title, data FROM articles").use { assertEquals(true, it.moveToFirst()); assertEquals("عنوان", it.getString(0)); assertEquals("", it.getString(1)) }
        db.query("SELECT total, next, mode, failed FROM jobs").use { it.moveToFirst(); assertEquals(5, it.getInt(0)); assertEquals(0, it.getInt(1)); assertEquals("news", it.getString(2)); assertEquals(0, it.getInt(3)) }
    }

    @Test fun migrate2To3AddsSiteAndQualityWithDefaults() {
        helper.createDatabase("t3", 2).apply {
            execSQL("INSERT INTO jobs (total, done, state, created_at, next, mode, custom, failed) VALUES (3, 1, 'done', 1, 0, 'news', '', 0)")
            execSQL("INSERT INTO articles (job_id, idx, title, slug, labels, status, words, provider, html, error, updated_at, data) VALUES (1, 0, 'عنوان', 's', '', 'ready', 700, 'groq', '', '', 1, '{}')")
            close()
        }
        val db = helper.runMigrationsAndValidate("t3", 3, true, *Migrations.ALL)
        db.query("SELECT site_id, quality FROM articles").use { assertEquals(true, it.moveToFirst()); assertEquals("default", it.getString(0)); assertEquals(0, it.getInt(1)) }
        db.query("SELECT site_id FROM jobs").use { it.moveToFirst(); assertEquals("default", it.getString(0)) }
    }

    @Test fun migrate3To4AddsRemoteUrl() {
        helper.createDatabase("t4", 3).apply {
            execSQL("INSERT INTO articles (job_id, idx, title, slug, labels, status, words, provider, html, error, updated_at, data, site_id, quality) VALUES (1, 0, 'عنوان', 's', '', 'exported', 700, 'g', '', '', 1, '{}', 'default', 50)")
            close()
        }
        val db = helper.runMigrationsAndValidate("t4", 4, true, *Migrations.ALL)
        db.query("SELECT remote_url, quality FROM articles").use { assertEquals(true, it.moveToFirst()); assertEquals("", it.getString(0)); assertEquals(50, it.getInt(1)) }
    }

    @Test fun migrate4To5CreatesReviewsAndKeepsArticles() {
        helper.createDatabase("t5", 4).apply {
            execSQL("INSERT INTO articles (job_id, idx, title, slug, labels, status, words, provider, html, error, updated_at, data, site_id, quality, remote_url) VALUES (1, 0, 'عنوان', 's', '', 'exported', 700, 'g', '', '', 1, '{}', 'default', 50, 'https://x/y')")
            close()
        }
        val db = helper.runMigrationsAndValidate("t5", 5, true, *Migrations.ALL)
        db.query("SELECT remote_url FROM articles").use { assertEquals(true, it.moveToFirst()); assertEquals("https://x/y", it.getString(0)) }
        db.execSQL("INSERT INTO reviews (article_id, site_id, title, remote_url, published_at, words, provider, auto, metrics, flags, rating, tags, notes, reviewed_at) VALUES (1, 'default', 't', '', 1, 1, 'g', 60, '{}', '', 0, '', '', 0)")
        db.query("SELECT COUNT(*) FROM reviews").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test fun migrate5To6CreatesSocialTables() {
        helper.createDatabase("t6", 5).close()
        val db = helper.runMigrationsAndValidate("t6", 6, true, *Migrations.ALL)
        db.execSQL("INSERT INTO social_accounts (id, platform, label, enabled, daily_cap, gap_min, win_from, win_to, site_ids, status, status_note, last_ok_at, extra, created_at) VALUES ('a','x','x',1,3,20,8,23,'','ok','',0,'{}',1)")
        db.execSQL("INSERT INTO share_jobs (article_id, site_id, account_id, platform, url, title, text, status, attempts, run_at, done_at, error, post_url, created_at) VALUES (1,'d','a','x','u','t','','pending',0,1,0,'','',1)")
        db.query("SELECT COUNT(*) FROM share_jobs").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
