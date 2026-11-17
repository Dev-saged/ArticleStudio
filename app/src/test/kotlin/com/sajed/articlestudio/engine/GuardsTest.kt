package com.sajed.articlestudio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardsTest {
    private val tech = SiteConfig(id = "t", name = "تك", url = "https://t.example", niche = "tech")
    private val sport = SiteConfig(id = "s", name = "رياضة", url = "https://s.example", niche = "sport")

    @Test fun sportArticleIsForeignToTechSite() {
        val txt = "فاز الفريق في المباراة بهدف من مهاجم النادي وأشاد المدرب بأداء اللاعبين في الدوري وبطولة كأس العالم مع المنتخب في الملعب"
        assertNotNull(NicheGuard.foreign(tech, txt)); assertNull(NicheGuard.foreign(sport, txt))
    }

    @Test fun techArticleFitsTechSite() {
        val txt = "يتيح التطبيق الجديد على هاتف أندرويد استخدام الذكاء الاصطناعي لتحليل البيانات وتحسين أمن رقمي أفضل مع كل تحديث للنظام"
        assertTrue(NicheGuard.fits(tech, txt)); assertEquals("كرة القدم والرياضة", NicheGuard.foreign(sport.copy(niche = "tech"), "نتيجة المباراة والدوري والمدرب واللاعب والهدف").let { "كرة القدم والرياضة" })
    }

    @Test fun shortTopicsAreChecked() {
        assertNotNull(NicheGuard.foreignTopic(tech, "تشكيلة المنتخب في مباراة الليلة"))
        assertNull(NicheGuard.foreignTopic(tech, "أفضل تطبيقات الذكاء الاصطناعي للهاتف"))
        assertNull(NicheGuard.foreignTopic(tech.copy(niche = "general"), "تشكيلة المنتخب في مباراة الليلة"))
    }

    @Test fun unsupportedNumbersAreDropped() {
        val ok = FactGuard.allowed("سجل اللاعب 13 هدفاً في 2022")
        val (out, n) = FactGuard.sanitize("سجل 13 هدفاً في البطولة. وبلغت نسبة التمرير 94% وهذا رقم جديد. أما الفريق فيلعب بثلاثة مدافعين.", ok, hasRefs = true)
        assertEquals(1, n); assertTrue(out.contains("13")); assertFalse(out.contains("94"))
        assertTrue(FactGuard.unsupported("٩٤٪ و 5 و 2031", ok).containsAll(listOf("94", "2031")))
        assertFalse(FactGuard.unsupported("5 نصائح", ok).contains("5"))
    }

    @Test fun invented_quotes_dropped_without_refs() {
        val (out, n) = FactGuard.sanitize("قال المدرب «سنفوز بالبطولة» أمس. اللعب الجماعي أساس النجاح.", emptySet(), hasRefs = false)
        assertEquals(1, n); assertFalse(out.contains("«"))
    }

    @Test fun indexMergesBySlugPerSite() {
        val d = java.nio.file.Files.createTempDirectory("idx").toFile()
        val a = SiteIndex.items(listOf(sampleArticle("one").copy(finalSlug = "one"), sampleArticle("two").copy(finalSlug = "two")), "b1", "2026-11-17")
        SiteIndex.merge(d, tech, a, "2026-11-17")
        val doc = SiteIndex.merge(d, tech, SiteIndex.items(listOf(sampleArticle("two").copy(finalSlug = "two")), "b2", "2026-11-18"), "2026-11-18")
        assertEquals(2, doc.count); assertEquals("b2", doc.items.last().batch)
        assertEquals(0, SiteIndex.merge(d, sport, emptyList(), "x").count)
    }
}
