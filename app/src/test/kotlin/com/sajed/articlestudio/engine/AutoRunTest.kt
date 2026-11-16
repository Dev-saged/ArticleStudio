package com.sajed.articlestudio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRunTest {
    private val s = SiteConfig("s", "موقع", "https://t.example.com", autoEvery = 2)
    private val h = 3_600_000L

    @Test fun windowCoversDayAndWrapsMidnight() {
        assertTrue(AutoPlan.inWindow(0, 24, 13))
        assertTrue(AutoPlan.inWindow(8, 22, 8)); assertFalse(AutoPlan.inWindow(8, 22, 22))
        assertTrue(AutoPlan.inWindow(22, 6, 23)); assertTrue(AutoPlan.inWindow(22, 6, 2)); assertFalse(AutoPlan.inWindow(22, 6, 12))
    }

    @Test fun dueRespectsIntervalOffAndWindow() {
        assertTrue(AutoPlan.due(s, 0, 10 * h, 10))
        assertFalse(AutoPlan.due(s, 9 * h, 10 * h, 10))
        assertTrue(AutoPlan.due(s, 8 * h, 10 * h, 10))
        assertFalse(AutoPlan.due(s.copy(autoEvery = 0), 0, 10 * h, 10))
        assertFalse(AutoPlan.due(s.copy(autoFrom = 8, autoTo = 20), 0, 10 * h, 3))
    }

    @Test fun siteCleanClampsAutoFields() {
        val c = SiteRules.clean(s.copy(autoEvery = 99, autoCount = 0, autoFrom = 30, autoTo = 0))
        assertEquals(24, c.autoEvery); assertEquals(1, c.autoCount); assertEquals(23, c.autoFrom); assertEquals(1, c.autoTo)
    }

    @Test fun summaryDescribesEveryOutcome() {
        val ok = ExportUi(summary = ExportSummary("f.xml", 3, 6, true, emptyList()))
        assertTrue(AutoPipeline.summary(ExportUi(error = "x"), null).startsWith("تعذّر التصدير"))
        assertTrue(AutoPipeline.summary(ok, null).contains("3"))
        assertEquals("تم النشر: 3 مقالاً · 1 منشورة سابقاً", AutoPipeline.summary(ok, PublishReport(ok = 3, skipped = 1)))
        assertTrue(AutoPipeline.summary(ok, PublishReport(fatal = "الصور", retryable = true)).startsWith("توقف النشر"))
        assertEquals("تصدير 3/6 · رفع (2/5)", AutoPipeline.stageText(ExportUi(true, "رفع", 3, 6, 2, 5)))
    }
}
