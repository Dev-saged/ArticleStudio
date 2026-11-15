package com.sajed.articlestudio.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sajed.articlestudio.ui.theme.AppColors
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.BodyFont
import com.sajed.articlestudio.ui.theme.DisplayFont
import com.sajed.articlestudio.ui.theme.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val POS = listOf(
    Offset(72f, 62f), Offset(175f, 44f), Offset(278f, 62f),
    Offset(104f, 152f), Offset(246f, 152f), Offset(175f, 184f),
)

/** لوحة تكتيكية: المزودون لاعبون والكرة تُمرَّر بينهم بقوس مع ظل وارتفاع. */
@Composable
fun PitchBoard(nodes: List<NodeUi>, ballAt: Int, pass: PassEvent?, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val reduce = rememberReduceMotion()
    val tm = rememberTextMeasurer()
    val t = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    var from by remember { mutableIntStateOf(ballAt) }
    var to by remember { mutableIntStateOf(ballAt) }
    var pulseNode by remember { mutableIntStateOf(-1) }

    LaunchedEffect(pass?.id) {
        val p = pass ?: return@LaunchedEffect
        from = p.from; to = p.to
        t.snapTo(0f)
        t.animateTo(1f, tween(if (reduce) 1 else p.durationMs, easing = FastOutSlowInEasing))
        pulseNode = p.to
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(if (reduce) 1 else 700))
    }

    val shape = RoundedCornerShape(26.dp)
    val desc = "لوحة المزودين: " + nodes.joinToString("، ") { it.name }
    Canvas(
        modifier.fillMaxWidth().aspectRatio(350f / 244f).clip(shape).background(c.board)
            .border(2.dp, c.line, shape).semantics { contentDescription = desc },
    ) {
        val s = size.width / 350f
        drawPitch(c, s)
        nodes.forEachIndexed { i, n -> drawNode(i, n, c, s, tm, if (pulseNode == i) pulse.value else 1f) }
        drawBall(from, to, t.value, c, s)
    }
}

private fun DrawScope.drawPitch(c: AppColors, s: Float) {
    val chalk = c.text.copy(alpha = 0.28f)
    val st = Stroke(2f * s)
    drawRoundRect(chalk, Offset(8f * s, 8f * s), Size(334f * s, 228f * s), androidx.compose.ui.geometry.CornerRadius(16f * s), style = st)
    drawLine(chalk, Offset(175f * s, 8f * s), Offset(175f * s, 236f * s), 2f * s)
    drawCircle(chalk, 34f * s, Offset(175f * s, 122f * s), style = st)
    drawRoundRect(chalk, Offset(8f * s, 82f * s), Size(44f * s, 80f * s), androidx.compose.ui.geometry.CornerRadius(6f * s), style = st)
    drawRoundRect(chalk, Offset(298f * s, 82f * s), Size(44f * s, 80f * s), androidx.compose.ui.geometry.CornerRadius(6f * s), style = st)
}

private fun DrawScope.drawNode(i: Int, n: NodeUi, c: AppColors, s: Float, tm: TextMeasurer, pulseK: Float) {
    val p = POS[i] * s
    val active = n.state == NodeState.Active
    val cooling = n.state == NodeState.Cooling
    if (pulseK < 1f) drawCircle(c.gold.copy(alpha = 1f - pulseK), (18f + 16f * pulseK) * s, p, style = Stroke(2f * s))
    drawCircle(if (active) c.gold else if (cooling) c.red.copy(alpha = 0.22f) else c.board, 17f * s, p)
    val ring = if (cooling) c.red else if (active) c.gold else c.text
    drawCircle(
        ring, 17f * s, p,
        style = Stroke(2f * s, pathEffect = if (cooling) PathEffect.dashPathEffect(floatArrayOf(3f * s, 4f * s)) else null),
    )
    if (active) drawArc(c.goldText, -90f, 360f * n.progress, false, p - Offset(22f * s, 22f * s), Size(44f * s, 44f * s), style = Stroke(3f * s, cap = StrokeCap.Round))
    val numStyle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = (12f * s).toSp(), color = if (active) Color(0xFF1B1405) else c.text)
    val num = tm.measure("${i + 1}", numStyle)
    drawText(num, topLeft = p - Offset(num.size.width / 2f, num.size.height / 2f))
    val nameStyle = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = (10.5f * s).toSp(), color = if (cooling) c.muted else c.text)
    val name = tm.measure(n.name, nameStyle)
    drawText(name, topLeft = Offset(p.x - name.size.width / 2f, p.y + 25f * s))
    if (cooling) {
        val cd = tm.measure("${n.coolSec}ث", TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = (10f * s).toSp(), color = c.red))
        drawText(cd, topLeft = Offset(p.x - cd.size.width / 2f, p.y + 25f * s + name.size.height))
    }
}

private fun DrawScope.drawBall(from: Int, to: Int, u: Float, c: AppColors, s: Float) {
    val a = POS[from]; val b = POS[to]
    val cx = (a.x + b.x) / 2f; val cy = min(a.y, b.y) - 46f
    val k = 1f - u
    val x = k * k * a.x + 2f * k * u * cx + u * u * b.x
    val yy = k * k * a.y + 2f * k * u * cy + u * u * b.y
    val gy = a.y + (b.y - a.y) * u
    val resting = u >= 1f
    val h = if (resting) 0f else gy - yy
    val pos = if (resting) b else Offset(x, yy)
    val ground = if (resting) b else Offset(x, gy)
    drawOval(Color.Black.copy(alpha = 0.28f), Offset((ground.x - 7f) * s, (ground.y + 10f - 3f) * s), Size(14f * s, 6f * s))
    val r = 8f * s * (1f + h / 60f)
    val ctr = pos * s
    drawCircle(Color.White, r, ctr)
    drawCircle(c.text, r, ctr, style = Stroke(1.6f * s))
    val pent = Path()
    for (j in 0 until 5) {
        val ang = -PI / 2 + j * 2 * PI / 5
        val px = ctr.x + (r * 0.42f) * cos(ang).toFloat(); val py = ctr.y + (r * 0.42f) * sin(ang).toFloat()
        if (j == 0) pent.moveTo(px, py) else pent.lineTo(px, py)
    }
    pent.close()
    drawPath(pent, Color(0xFF10151F))
}
