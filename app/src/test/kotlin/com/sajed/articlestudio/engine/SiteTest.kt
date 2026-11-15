package com.sajed.articlestudio.engine

import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SiteTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun site(niche: String = "tech", name: String = "تقنية اليوم") = SiteConfig("s1", name, "https://tech.example.com", niche = niche)

    @Test fun storeSeedsDefaultSiteAndManagesActive() {
        val st = SiteStore(ctx)
        assertEquals(1, st.sites.value.size)
        assertEquals("default", st.active().id)
        assertTrue(st.active().legacySeed)
        st.upsert(site())
        st.setActive("s1")
        assertEquals("s1", SiteStore(ctx).active().id)
        assertFalse(st.remove("nope"))
        assertTrue(st.remove("s1"))
        assertEquals("default", st.active().id)
        assertFalse("لا يُحذف الموقع الأخير", st.remove("default"))
    }

    @Test fun validationCatchesBadInput() {
        val bad = site().copy(name = "ا", url = "not a url", repo = "x", feeds = listOf("ftp-no"))
        val e = SiteRules.validate(bad)
        assertEquals(setOf("name", "url", "repo", "feeds"), e.keys)
        assertTrue(SiteRules.validate(site()).isEmpty())
    }

    @Test fun cleanNormalisesAndBounds() {
        val c = SiteRules.clean(site().copy(url = " https://a.example.com/ ", keywords = listOf(" ذكاء ", "ذكاء", ""), count = 99, schedule = -3))
        assertEquals("https://a.example.com", c.url)
        assertEquals(listOf("ذكاء"), c.keywords)
        assertEquals(30, c.count)
        assertEquals(0, c.schedule)
    }

    @Test fun nichePresetsAreConsistent() {
        for (n in Niches.all) {
            assertTrue("${n.id} motifs", n.motifs.size >= 5)
            assertTrue("${n.id} labels", n.labels.isNotEmpty())
        }
        assertEquals("general", Niches.of("???").id)
        val data = com.sajed.articlestudio.engine.StudioData(ctx)
        for (n in Niches.all) for (m in n.motifs) assertTrue("motif $m of ${n.id}", data.motifs.containsKey(m))
    }

    @Test fun feedsFollowNicheAndCustomSources() {
        val f = feedsFor(site().copy(feeds = listOf("https://x.example.org/rss")))
        assertTrue(f.any { it.url.contains("aitnews") })
        assertTrue(f.any { it.url.contains("x.example.org") })
        assertTrue(f.any { it.url.contains("news.google.com") })
        assertTrue(feedsFor(site("sport")).none { it.url.contains("aitnews") })
    }

    @Test fun serializationRoundTripKeepsEveryField() {
        val s = site().copy(rules = "لا تذكر الأسعار", avoid = "السياسة", tone = "formal", length = "long", repo = "a/b", faq = false)
        val j = kotlinx.serialization.json.Json.encodeToString(SiteConfig.serializer(), s)
        assertEquals(s, kotlinx.serialization.json.Json.decodeFromString(SiteConfig.serializer(), j))
    }

    @Test fun lengthAndToneDriveGenerationLimits() {
        assertTrue(site().copy(length = "long").minWords > site().copy(length = "short").minWords)
        assertTrue(site().copy(tone = "formal").toneText.contains("رسمية"))
        assertEquals("tech.example.com", site().host)
    }

    @Test fun exportNameUsesSiteName() {
        val n = ExportNaming.fileName(5, LocalDate.of(2026, 11, 15), "تقنية اليوم") { false }
        assertTrue(n, n.contains("تقنية اليوم"))
        assertTrue(n.endsWith(".xml"))
    }

    @Test fun blogIndexKeepsEachSiteSeparate() {
        val bi = BlogIndex(ctx, OkHttpClient())
        assertEquals(0, bi.count("https://tech.example.com"))
        assertNull(bi.similar("https://tech.example.com", "شرح نظام الفار"))
        assertNotNull(bi.all("https://other.example.com"))
    }
}
