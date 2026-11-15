package com.sajed.articlestudio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** دخول متدرّج: شفافية وإزاحة فقط، ويحترم إيقاف الحركات في النظام. */
@Composable
fun Modifier.stagger(index: Int): Modifier {
    val reduce = rememberReduceMotion()
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) { delay(index * 70L); a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 200f)) }
    }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }
}

/** خلفية حيّة: توهج أحمر يطوف ببطء خلف المحتوى. */
@Composable
fun AmbientBackground(content: @Composable () -> Unit) {
    val c = AppTheme.colors
    val reduce = rememberReduceMotion()
    val k = if (reduce) 0.5f else {
        val tr = rememberInfiniteTransition(label = "ambient")
        val v = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "k")
        v.value
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(c.bg)
            val center = Offset(size.width * (0.55f + 0.45f * k), size.height * (0.02f + 0.1f * k))
            drawRect(Brush.radialGradient(listOf(c.glow, androidx.compose.ui.graphics.Color.Transparent), center, size.width * 0.9f))
        }
        content()
    }
}
