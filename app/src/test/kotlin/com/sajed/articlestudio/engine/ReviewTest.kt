package com.sajed.articlestudio.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.ArticleEntity
import com.sajed.articlestudio.data.ArticleRow
import com.sajed.articlestudio.data.ReviewEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewTest {
    private lateinit var db: AppDb
    private lateinit var store: ReviewStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java).allowMainThreadQueries().build()
        store = ReviewStore(db.reviews(), db.articles())
    }
    @After fun tearDown() = db.close()

    private fun seed(a: Article = sampleArticle(), site: String = "s1", url: String = "https://b.example/p/1.html"): Long = runBlocking {
        val id = db.articles().upsert(ArticleEntity(jobId = 1, idx = 0, title = a.title, slug = a.slug, status = "exported", words = a.words, provider = a.provider,
            updatedAt = 1, data = AppJson.encodeToString(Article.serializer(), a), siteId = site, remoteUrl = url))
        id
    }

    @Test fun recordStoresMetricsOnceAndKeepsUrl() = runBlocking {
        val id = seed()
        store.record(id, 1000); store.record(id, 2000)
        val all = db.reviews().all()
        assertEquals(1, all.size)
        assertEquals(1000L, all[0].publishedAt)
        assertEquals("https://b.example/p/1.html", all[0].remoteUrl)
        assertEquals("groq", all[0].provider)
        assertEquals("4", store.metricsOf(all[0])["الأقسام"])
    }

    @Test fun nonHttpRemoteMarkerIsNotStoredAsUrl() = runBlocking {
        val id = seed(url = "published")
        store.record(id)
        assertEquals("", db.reviews().all()[0].remoteUrl)
    }

    @Test fun backfillRecordsOnlyPublishedArticlesWithoutReview() = runBlocking {
        val a = seed(); seed(sampleArticle(slug = "b", title = "عنوان ثانٍ"), url = "")
        assertEquals(1, store.backfill())
        assertEquals(0, store.backfill())
        assertEquals(a, db.reviews().all()[0].articleId)
    }

    @Test fun analyzerFlagsWeakArticle() {
        val weak = sampleArticle().copy(words = 200, sections = emptyList(), faq = emptyList(), facts = emptyList(), ref = null, refs = emptyList())
        val r = ReviewAnalyzer.analyze(weak)
        assertTrue("المقال قصير" in r.flags); assertTrue("أقسام قليلة" in r.flags); assertTrue("أسئلة شائعة ناقصة" in r.flags)
        assertTrue(r.score < 40)
    }

    @Test fun analyzerDetectsRepeatedSentences() {
        val s = "هذه جملة طويلة بما يكفي لتُحتسب في فحص التكرار داخل المقال"
        val a = sampleArticle().copy(sections = List(4) { Section("ق$it", "<p>$s. $s.</p>") })
        assertTrue("تكرار جمل" in ReviewAnalyzer.analyze(a).flags)
    }

    @Test fun rateClampsAndRejectsUnknownTags() = runBlocking {
        val id = seed(); store.record(id)
        val rid = db.reviews().all()[0].id
        store.rate(rid, 9, listOf("تكرار", "وسم مجهول"), "ملاحظة")
        val r = db.reviews().byArticle(id)!!
        assertEquals(5, r.rating); assertEquals("تكرار", r.tags); assertEquals("ملاحظة", r.notes)
        assertTrue(r.reviewedAt > 0)
    }

    @Test fun guidanceAppearsOnlyAfterTagRepeatsInLowRatedReviews() = runBlocking {
        val a = seed(sampleArticle(slug = "a1", title = "أ")); val b = seed(sampleArticle(slug = "b1", title = "ب"))
        store.record(a); store.record(b)
        val ids = db.reviews().all().map { it.id }
        store.rate(ids[0], 2, listOf("تكرار"), "")
        assertEquals("", store.guidance("s1"))
        store.rate(ids[1], 3, listOf("تكرار"), "")
        assertTrue(store.guidance("s1").contains("تجنّب تكرار"))
        assertEquals("", store.guidance("other"))
    }

    @Test fun insightsAggregateByProviderAndSite() {
        fun r(site: String, prov: String, auto: Int, rating: Int, flags: String = "") = ReviewEntity(articleId = 0, siteId = site, title = "t", publishedAt = 1, provider = prov, auto = auto, rating = rating, flags = flags)
        val i = Insights.build(listOf(r("a", "groq", 80, 4, "تكرار جمل"), r("a", "groq", 60, 0, "تكرار جمل"), r("b", "gemini", 90, 5)))
        assertEquals(3, i.count); assertEquals(2, i.rated); assertEquals(76, i.avgAuto)
        assertEquals(4.5f, i.avgRating, 0.01f)
        assertEquals("groq", i.byProvider[0].name); assertEquals("تكرار جمل" to 2, i.topFlags[0])
        assertTrue(i.markdown(mapOf("a" to "موقعي")).contains("موقعي"))
    }

    @Test fun deletingSiteRemovesItsReviews() = runBlocking {
        store.record(seed(site = "gone")); db.reviews().deleteSite("gone")
        assertNull(db.reviews().all().firstOrNull())
    }

    private fun row(job: Long, at: Long, site: String = "s", st: String = "ready", prov: String = "groq") =
        ArticleRow(0, job, 0, "t", "", st, 500, prov, "", at, 60, site, "")

    @Test fun secondsPerArticleIgnoresLongPausesAndFailures() {
        val rows = listOf(row(1, 0), row(1, 40_000), row(1, 100_000), row(1, 100_000 + 3_600_000), row(1, 5, st = "failed"))
        assertEquals(50, Stats.secondsPerArticle(rows))
        assertEquals(0, Stats.secondsPerArticle(emptyList()))
    }

    @Test fun weeklyCountsOnlyLastSevenDays() {
        val now = 10 * Stats.WEEK
        val rv = listOf(ReviewEntity(articleId = 1, siteId = "s", title = "a", publishedAt = now - 1000), ReviewEntity(articleId = 2, siteId = "s", title = "b", publishedAt = now - 2 * Stats.WEEK))
        val w = Stats.weekly(listOf(row(1, now - 500), row(1, now - 3 * Stats.WEEK)), rv, now)
        assertEquals(1, w.single().published); assertEquals(1, w.single().generated)
    }

    @Test fun backupRoundTripKeepsSitesAndExcludesSecrets() {
        val b = BackupFile(savedAt = 5, parallel = 2, ghRepo = "u/r", sites = listOf(SiteConfig(id = "s1", name = "موقعي", url = "https://x.example", perSlot = 3)),
            reviews = listOf(ReviewBackup("s1", "عنوان", publishedAt = 9, rating = 4, tags = "تكرار")))
        val txt = BackupManager.encode(b)
        assertTrue(!txt.contains("token", ignoreCase = true) && !txt.contains("apikey", ignoreCase = true))
        val back = BackupManager.decode(txt)
        assertEquals(3, back.sites[0].perSlot); assertEquals(4, back.reviews[0].rating); assertEquals(2, back.parallel)
    }
}
