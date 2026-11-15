package com.sajed.articlestudio

import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test fun oklchWhiteAndBlack() {
        val w = Oklch(1f, 0f, 0f).toColor()
        assertTrue(w.red > 0.99f && w.green > 0.99f && w.blue > 0.99f)
        val b = Oklch(0f, 0f, 0f).toColor()
        assertTrue(b.red < 0.01f && b.blue < 0.01f)
    }

    @Test fun schemaRejectsBadValues() {
        val e = SchemaRules.validate(Schemas.article, mapOf("title" to "", "words" to "-3", "status" to "x"))
        assertEquals(setOf("title", "words", "status"), e.keys)
    }

    @Test fun schemaAcceptsGoodValues() {
        val e = SchemaRules.validate(Schemas.article, mapOf("title" to "عنوان", "words" to "700", "status" to "ready"))
        assertTrue(e.isEmpty())
    }

    @Test fun sanitizeStripsControlChars() {
        assertEquals("أب", SchemaRules.sanitize("  أ\u0000ب\u0007 "))
        assertEquals(10, SchemaRules.sanitize("x".repeat(50), 10).length)
    }

    @Test fun kpiAverageIgnoresZero() {
        val rows = listOf(mapOf("words" to "700", "status" to "ready"), mapOf("words" to "0", "status" to "failed"), mapOf("words" to "800", "status" to "ready"))
        assertEquals("750", Schemas.article.kpis[1].value(rows))
        assertEquals("2", Schemas.article.kpis[0].value(rows))
    }
}
