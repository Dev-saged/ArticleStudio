package com.sajed.articlestudio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteProfilerTest {
    private val feed = """{"feed":{"title":{"${'$'}t":"عالم التقنية"},"subtitle":{"${'$'}t":"أخبار الذكاء الاصطناعي والهواتف"},
        "author":[{"name":{"${'$'}t":"سارة"}}],"category":[{"term":"ذكاء اصطناعي"},{"term":"هواتف"},{"term":"تطبيقات"}],
        "entry":[{"title":{"${'$'}t":"أفضل تطبيقات الذكاء الاصطناعي للهواتف الذكية"}},{"title":{"${'$'}t":"مراجعة هاتف جديد وتطبيقات الكاميرا"}},{"title":{"${'$'}t":"شرح تقنية الذكاء الاصطناعي التوليدي"}}]}}"""

    @Test fun feedIsParsedIntoSnapshot() {
        val s = ProfileLogic.parseFeed(feed)!!
        assertEquals("عالم التقنية", s.title); assertEquals("سارة", s.author); assertEquals(3, s.categories.size); assertEquals(3, s.titles.size)
        assertNull(ProfileLogic.parseFeed("not json")); assertNull(ProfileLogic.parseFeed("""{"feed":{}}"""))
    }

    @Test fun htmlFallbackReadsTitleAndDescription() {
        val s = ProfileLogic.parseHtml("<html><head><title>مطبخ ريما</title><meta name=\"description\" content=\"وصفات عربية سهلة وسريعة\"></head><body><h2><a href=\"x\">طريقة عمل الكبسة بالدجاج الشهية</a></h2></body></html>")!!
        assertEquals("مطبخ ريما", s.title); assertTrue(s.description.contains("وصفات")); assertEquals(1, s.titles.size)
    }

    @Test fun heuristicPicksNicheFromContentAndUsesRealLabels() {
        val h = ProfileLogic.heuristic(ProfileLogic.parseFeed(feed)!!)
        assertEquals("tech", h.niche); assertEquals(listOf("ذكاء اصطناعي", "هواتف", "تطبيقات"), h.labels)
        assertFalse(h.usedAi); assertTrue(h.keywords.size in 3..10)
    }

    @Test fun unknownContentFallsBackToGeneral() {
        assertEquals("general", ProfileLogic.pickNiche(BlogSnapshot("مدونتي", "", "", emptyList(), listOf("يوم عادي"))))
    }

    @Test fun aiJsonIsValidatedAndRepaired() {
        val s = ProfileLogic.parseFeed(feed)!!
        val ok = ProfileLogic.parseAi("<think>x</think>```json\n{\"niche\":\"tech\",\"topic\":\"تقنية وذكاء\",\"audience\":\"الشباب\",\"tone\":\"weird\",\"length\":\"long\",\"keywords\":[\"ai\",\"هواتف\"],\"labels\":[],\"avoid\":\"\",\"rules\":\"\"}\n```", s)!!
        assertEquals("tech", ok.niche); assertEquals("friendly", ok.tone); assertEquals("long", ok.length); assertTrue(ok.usedAi); assertTrue(ok.labels.isNotEmpty())
        assertNotNull(ProfileLogic.parseAi("{\"niche\":\"tech\",\"keywords\":[{\"k\":\"ai\"},[\"x\"],\"هواتف\"]}", s))
        assertNotNull(ProfileLogic.parseAi("{\"niche\":\"nope\",\"keywords\":[\"a\"]}", s)); assertNull(ProfileLogic.parseAi("لا json", s)); assertNull(ProfileLogic.parseAi("{\"niche\":\"tech\"}", s))
    }

    @Test fun applyKeepsExistingNameAndFillsIdentity() {
        val prof = ProfileLogic.heuristic(ProfileLogic.parseFeed(feed)!!)
        val site = prof.applyTo(SiteConfig("x", "اسمي", "https://a.example.com"))
        assertEquals("اسمي", site.name); assertEquals("tech", site.niche); assertEquals(prof.labels, site.labels)
        assertEquals("عالم التقنية", prof.applyTo(SiteConfig("x", "", "https://a.example.com")).name)
    }
}
