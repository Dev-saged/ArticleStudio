package com.sajed.articlestudio.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.BrandConfig
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.stagger
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr

@Composable
fun HomeScreen(isDark: Boolean, onToggleTheme: () -> Unit, onOpenTest: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val bar by animateFloatAsState(s.progress, tween(600), label = "progress")

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") {
            Row(Modifier.stagger(0), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "غرفة العمليات", style = MaterialTheme.typography.headlineLarge, color = c.text,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap < 600) taps + 1 else 1
                            lastTap = now
                            if (taps >= 5) { taps = 0; onOpenTest() }
                        },
                    )
                    Text(BrandConfig.TAGLINE, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                IconBtn(if (isDark) Icons.Sun else Icons.Moon, "تبديل الوضع الفاتح والغامق", onToggleTheme)
            }
        }
        item("board") { PitchBoard(s.nodes, s.ballAt, s.pass, Modifier.stagger(1)) }
        item("stats") {
            Row(Modifier.stagger(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("مقالات جاهزة", "${s.done}/${s.total}".ltr(), Modifier.weight(1f))
                Stat("متوسط الكلمات", if (s.done == 0) "—" else s.avgWords.toString().ltr(), Modifier.weight(1f))
                Stat("المتبقي", "${s.etaMin.toString().ltr()}د", Modifier.weight(1f))
            }
        }
        item("bar") {
            Box(Modifier.stagger(3).fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)).background(c.line)) {
                Box(
                    Modifier.fillMaxWidth(bar).height(9.dp).clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient(listOf(c.gold, androidx.compose.ui.graphics.Color(0xFFE8A026)))),
                )
            }
        }
        item("count") {
            Row(Modifier.stagger(4).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { n -> AppChip(n.toString().ltr(), s.total == n, { vm.setTotal(n) }, Modifier.weight(1f)) }
            }
        }
        item("go") {
            val label = when {
                s.running -> "إيقاف مؤقت"
                s.done >= s.total -> "ولّد من جديد"
                s.done > 0 -> "استئناف"
                else -> "صافرة البداية"
            }
            PrimaryButton(label, if (s.running) Icons.Pause else Icons.Play, vm::toggle, Modifier.stagger(5))
        }
        items(s.rows, key = { it.n }) { r ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.n.toString().ltr(), style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = c.red, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyMedium, color = c.text)
                        Text("${r.words.toString().ltr()} كلمة · ${r.provider}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = AppTheme.colors
    Panel(modifier) {
        Column {
            Text(value, style = NumberStyle, color = c.goldText, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal), color = c.muted, maxLines = 1)
        }
    }
}
