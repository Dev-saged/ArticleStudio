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
}
