package com.sajed.articlestudio.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MotifTest {
    @Test fun allMotifsRender() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val d = StudioData(ctx)
        val bad = d.motifs.filter { (_, v) -> runCatching { SvgDraw.render(v, 100, android.graphics.Typeface.DEFAULT) }.isFailure }.keys
        org.junit.Assert.assertTrue("رموز لا تُرسم: $bad", bad.isEmpty())
    }
}
