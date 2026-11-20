package com.sajed.articlestudio.ui.pipe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.DisplayFont
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr

/** أدوات واجهة «خط الإنتاج»: كل الشاشات تُبنى منها فقط ليبقى الشكل موحداً. */

val R22 = RoundedCornerShape(22.dp)
val R16 = RoundedCornerShape(16.dp)
val R13 = RoundedCornerShape(13.dp)

fun platformIcon(id: String): ImageVector = when (id) {
    "instagram" -> Icons.Instagram; "x" -> Icons.XLogo; "linkedin" -> Icons.LinkedIn; "facebook" -> Icons.Facebook
    "telegram" -> Icons.Telegram; "bluesky" -> Icons.Bluesky; "threads" -> Icons.Threads; else -> Icons.Share
}

enum class Tone { Plain, Gold, Red, Ok }

@Composable
private fun toneColors(t: Tone): Pair<Color, Color> {
    val c = AppTheme.colors
    return when (t) {
        Tone.Plain -> c.bg2 to c.muted
        Tone.Gold -> c.gold.copy(alpha = .16f) to c.goldText
        Tone.Red -> c.red.copy(alpha = .16f) to c.red
        Tone.Ok -> c.ok.copy(alpha = .16f) to c.ok
    }
}

@Composable
fun PCard(modifier: Modifier = Modifier, alert: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier.clip(R22)
            .background(Brush.verticalGradient(listOf(c.surface, c.surface.copy(alpha = .86f))))
            .border(1.dp, if (alert) c.red.copy(alpha = .7f) else c.line, R22)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun PTop(title: String, sub: String = "", onBack: (() -> Unit)? = null, lead: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) PIcon(Icons.Back, "رجوع", onBack)
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 23.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun PIcon(icon: ImageVector, label: String, onClick: () -> Unit, badge: Int = 0, tint: Color? = null) {
    val c = AppTheme.colors
    val h = LocalHapticFeedback.current
    Box(Modifier.size(46.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(15.dp))
                .clickable(role = Role.Button, onClickLabel = label) { h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(21.dp), tint = tint ?: c.text) }
        if (badge > 0) Box(Modifier.align(Alignment.TopStart).defaultMinSize(18.dp, 18.dp).clip(CircleShape).background(c.red).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(badge.coerceAtMost(99).toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
        }
    }
}

@Composable
fun PRing(progress: Float, size: Dp = 46.dp, stroke: Dp = 4.dp, color: Color = AppTheme.colors.gold, center: String = "") {
    val c = AppTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val d = this.size.minDimension - s
            drawArc(c.line, 0f, 360f, false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        if (center.isNotEmpty()) Text(center.ltr(), style = NumberStyle.copy(fontSize = (size.value / 4.2f).sp), color = c.text)
    }
}

@Composable
fun PSpark(values: List<Int>, color: Color, modifier: Modifier = Modifier.width(60.dp).height(22.dp)) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = size.width - i * size.width / (values.size - 1)
            val y = size.height - 2f - (v / mx) * (size.height - 4f)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun PCta(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, gold: Boolean = false, enabled: Boolean = true) {
    val c = AppTheme.colors
    val h = LocalHapticFeedback.current
    val bg = if (gold) Brush.verticalGradient(listOf(c.gold, c.gold.copy(alpha = .85f))) else Brush.verticalGradient(listOf(c.red, c.red.copy(alpha = .86f)))
    val fg = if (gold) Color(0xFF2A2310) else Color.White
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(bg).then(if (enabled) Modifier else Modifier.background(c.bg.copy(alpha = .55f)))
            .clickable(enabled = enabled, role = Role.Button) { h.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg)
    }
}

@Composable
fun PGhost(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null, enabled: Boolean = true) {
    val c = AppTheme.colors
    Row(
        modifier.height(46.dp).clip(R16).background(c.bg2).border(1.dp, c.line, R16).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = color ?: c.text)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = (color ?: c.text).copy(alpha = if (enabled) 1f else .45f), maxLines = 1)
    }
}

@Composable
fun PPill(text: String, icon: ImageVector?, onClick: () -> Unit, primary: Boolean = true, enabled: Boolean = true) {
    val c = AppTheme.colors
    Row(
        Modifier.height(40.dp).clip(R13).background(if (primary) Brush.verticalGradient(listOf(c.red, c.red.copy(alpha = .86f))) else Brush.verticalGradient(listOf(c.bg2, c.bg2)))
            .then(if (primary) Modifier else Modifier.border(1.dp, c.line, R13)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (primary) Color.White else c.text
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = fg.copy(alpha = if (enabled) 1f else .5f))
        Text(text, style = if (primary) MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp) else MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = fg.copy(alpha = if (enabled) 1f else .5f), maxLines = 1)
    }
}

@Composable
fun PTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(4.dp)) {
        labels.forEachIndexed { i, l ->
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (i == selected) c.bg2 else Color.Transparent).clickable(role = Role.Tab) { onSelect(i) }, contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal), color = if (i == selected) c.text else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun PChip(text: String, count: Int?, on: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.height(36.dp).clip(R13).background(if (on) c.text else Color.Transparent).border(1.dp, if (on) c.text else c.line, R13).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp), color = if (on) c.bg else c.muted)
        if (count != null) Text(count.toString().ltr(), style = MaterialTheme.typography.labelSmall, color = if (on) c.bg else c.muted)
    }
}

@Composable
fun PChipRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), content = content)

@Composable
fun PTag(text: String, tone: Tone = Tone.Plain) {
    val (bg, fg) = toneColors(tone)
    Text(text, Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 7.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = fg, maxLines = 1)
}

@Composable
fun PSection(title: String, action: String = "", onAction: () -> Unit = {}) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = c.muted)
        if (action.isNotEmpty()) Text(action, Modifier.clip(R13).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.goldText)
    }
}

@Composable
fun PKv(items: List<Triple<String, String, Color?>>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (v, l, col) ->
            Column(Modifier.weight(1f).clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(10.dp)) {
                Text(v.ltr(), style = NumberStyle.copy(fontSize = 18.sp), color = col ?: c.text, maxLines = 1)
                Text(l, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp), color = c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun PRow(title: String, sub: String = "", onClick: (() -> Unit)? = null, lead: (@Composable () -> Unit)? = null, trail: (@Composable () -> Unit)? = null, chevron: Boolean = onClick != null, titleColor: Color? = null) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 58.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = titleColor ?: c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trail?.invoke()
        if (chevron) Icon(Icons.Next, null, Modifier.size(16.dp), tint = c.muted.copy(alpha = .7f))
    }
}

@Composable
fun PDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line)) }

@Composable
fun PGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    PCard(modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)

@Composable
fun PLogo(icon: ImageVector, tint: Color? = null, size: Dp = 34.dp) {
    val c = AppTheme.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(c.bg2).border(1.dp, c.line, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .5f), tint = tint ?: c.text)
    }
}

@Composable
fun PAvatar(letter: String, active: Boolean = true, size: Dp = 40.dp) {
    val c = AppTheme.colors
    Box(
        Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(if (active) Brush.linearGradient(listOf(c.red, c.red.copy(alpha = .7f))) else Brush.linearGradient(listOf(c.bg2, c.bg2))),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = MaterialTheme.typography.titleMedium, color = if (active) Color.White else c.text) }
}

@Composable
fun PToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Box(Modifier.width(44.dp).height(26.dp).clip(CircleShape).background(if (on) c.red else c.line).clickable(role = Role.Switch) { onChange(!on) }.padding(3.dp), contentAlignment = if (on) Alignment.CenterStart else Alignment.CenterEnd) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Color.White else c.muted))
    }
}

@Composable
fun PSeg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (i == selected) c.text else c.surface).border(1.dp, if (i == selected) c.text else c.line, RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal), color = if (i == selected) c.bg else c.text, textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
fun PStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(Icons.Minus, "إنقاص", { onChange((value - 1).coerceIn(range)) })
        Text(value.toString().ltr(), Modifier.weight(1f), style = NumberStyle, color = c.text, textAlign = TextAlign.Center)
        PIcon(Icons.Plus, "زيادة", { onChange((value + 1).coerceIn(range)) })
    }
}

@Composable
fun PLabel(text: String) = Text(text, Modifier.padding(top = 12.dp, bottom = 7.dp), style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp), color = AppTheme.colors.muted)

@Composable
fun PPager(page: Int, total: Int, size: Int, onPage: (Int) -> Unit) {
    if (total <= size) return
    val c = AppTheme.colors
    val last = (total - 1) / size
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PPill("السابق", null, { onPage(page - 1) }, primary = false, enabled = page > 0)
        Text("${("${page * size + 1}–${minOf(total, (page + 1) * size)}").ltr()} من ${total.toString().ltr()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center)
        PPill("التالي", null, { onPage(page + 1) }, primary = false, enabled = page < last)
    }
}

@Composable
fun PEmpty(title: String, hint: String, icon: ImageVector = Icons.Document) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PLogo(icon, c.goldText, 52.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PSheet(onDismiss: () -> Unit, title: String, sub: String = "", content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg2, dragHandle = {
        Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(42.dp).height(4.dp).clip(CircleShape).background(c.line))
    }) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
            if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
            content()
        }
    }
}

@Composable
fun PNum(v: Any, size: Int = 25, color: Color = AppTheme.colors.text) = Text(v.toString().ltr(), style = NumberStyle.copy(fontSize = size.sp, fontFamily = DisplayFont), color = color, maxLines = 1)

@Composable
fun PPulse() { val c = AppTheme.colors; Box(Modifier.size(14.dp).clip(CircleShape).background(c.red.copy(alpha = .25f)), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).clip(CircleShape).background(c.red)) } }

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
