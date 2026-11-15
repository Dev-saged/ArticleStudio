package com.sajed.articlestudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.sajed.articlestudio.ui.theme.AppTheme

@Composable
fun Modifier.focusRing(src: MutableInteractionSource, shape: RoundedCornerShape): Modifier {
    val focused by src.collectIsFocusedAsState()
    return if (focused) border(2.dp, AppTheme.colors.goldText, shape) else this
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.size(44.dp).background(c.surface, shape).border(BorderStroke(1.5.dp, c.line), shape)
            .focusRing(src, shape)
            .clickable(src, androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = label) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.text, modifier = Modifier.size(22.dp)) }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    val src = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val ink = Color(0xFF1B1405)
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).alpha(if (enabled) 1f else 0.5f)
            .background(Brush.linearGradient(listOf(Color(0xFFFFD35C), Color(0xFFE8A026))), shape)
            .focusRing(src, shape)
            .clickable(src, androidx.compose.material3.ripple(color = ink), enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onClick()
            }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.defaultMinSize(minHeight = 44.dp)
            .background(if (selected) c.gold.copy(alpha = 0.14f) else Color.Transparent, shape)
            .border(1.5.dp, if (selected) c.goldText else c.line, shape)
            .focusRing(src, shape)
            .clickable(src, androidx.compose.material3.ripple(), role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = if (selected) c.goldText else c.text) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.background(c.surface, shape).border(1.5.dp, c.line, shape).padding(horizontal = 12.dp, vertical = 9.dp)) { content() }
}

@Composable
fun LiveBar(text: String) {
    val red = remember { com.sajed.articlestudio.BrandConfig.light.red.toColor() }
    Row(
        Modifier.fillMaxWidth().background(red).padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Color.White, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 1.dp)) {
            Text("مباشر", style = MaterialTheme.typography.labelSmall, color = red)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
