package com.sajed.articlestudio.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.engine.PlanMode
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.stagger
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(isDark: Boolean, onToggleTheme: () -> Unit, onOpenTest: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var logs by remember { mutableStateOf(false) }
    val bar by animateFloatAsState(s.progress, tween(600), label = "progress")
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.go() }
    val go = {
        if (Build.VERSION.SDK_INT >= 33 && !s.running && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.go()
    }

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
                    Text(s.siteName, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                IconBtn(Icons.Document, "سجل العمليات", { logs = true })
                IconBtn(if (isDark) Icons.Sun else Icons.Moon, "تبديل الوضع الفاتح والغامق", onToggleTheme)
            }
        }
        if (s.siteCount > 1) item("sites") {
            val list by vm.sites.sites.collectAsStateWithLifecycle()
            val act by vm.sites.activeId.collectAsStateWithLifecycle()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { x -> AppChip(x.name, x.id == act, { if (!s.running) vm.sites.setActive(x.id) }) }
            }
        }
        item("upd") { com.sajed.articlestudio.ui.UpdateCard(vm.updater) }
        if (s.siteNiche == "sport") item("board") { PitchBoard(s.nodes, s.ballAt, s.pass, Modifier.stagger(1)) }
        item("stats") {
            Row(Modifier.stagger(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("مقالات جاهزة", "${s.done}/${s.shownTotal}".ltr(), Modifier.weight(1f))
                Stat("متوسط الكلمات", if (s.avgWords == 0) "—" else s.avgWords.toString().ltr(), Modifier.weight(1f))
                Stat(if (s.failed > 0) "فاشلة" else "المتبقي", if (s.failed > 0) s.failed.toString().ltr() else "${s.etaMin.toString().ltr()}د", Modifier.weight(1f))
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
        if (s.running && s.phase.isNotEmpty()) item("now") {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("يعمل الآن", style = MaterialTheme.typography.labelMedium, color = c.red)
                    Text(s.phase, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2)
                }
            }
        }
        item("mode") {
            Row(Modifier.stagger(4).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("أخبار حية", s.mode == PlanMode.News, { vm.setMode(PlanMode.News) }, Modifier.weight(1f))
                AppChip("مواضيع دائمة", s.mode == PlanMode.Evergreen, { vm.setMode(PlanMode.Evergreen) }, Modifier.weight(1f))
                AppChip("مواضيعي", s.mode == PlanMode.Mine, { vm.setMode(PlanMode.Mine) }, Modifier.weight(1f))
            }
        }
        item("help") {
            Text(
                when (s.mode) {
                    PlanMode.News -> "يجمع أخباراً حديثة من مصادر موقعك ويدمجها في مقال واحد بصياغة جديدة تناسب قواعده."
                    PlanMode.Evergreen -> "شروحات وأدلة لا تقادم لها في مجال الموقع."
                    PlanMode.Mine -> "اكتب مواضيعك وسيكتب لكل سطر مقالاً كاملاً."
                } + if (s.blogCount > 0) " يتجنّب تكرار ${s.blogCount.toString().ltr()} مقالاً منشوراً في مدونتك." else "",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
        }
        if (s.mode == PlanMode.Mine) item("custom") {
            OutlinedTextField(
                value = s.custom, onValueChange = vm::setCustom, modifier = Modifier.fillMaxWidth().height(140.dp),
                label = { Text("موضوع في كل سطر") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.goldText, unfocusedBorderColor = c.line, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted, cursorColor = c.goldText),
            )
        } else item("count") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { n -> AppChip(n.toString().ltr(), s.total == n, { vm.setTotal(n) }, Modifier.weight(1f)) }
            }
        }
        if (s.notice.isNotEmpty()) item("notice") { Text(s.notice, style = MaterialTheme.typography.bodyMedium, color = c.red) }
        item("go") {
            val label = when {
                s.running -> "إيقاف"
                s.resumable -> "استئناف"
                s.done + s.failed > 0 -> "ولّد من جديد"
                else -> "صافرة البداية"
            }
            PrimaryButton(label, if (s.running) Icons.Stop else Icons.Play, go, Modifier.stagger(5))
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

    if (logs) ModalBottomSheet(onDismissRequest = { logs = false }, containerColor = c.bg2) {
        LazyColumn(Modifier.padding(horizontal = 18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("سجل العمليات", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 8.dp)) }
            if (s.logs.isEmpty()) item { Text("لا أحداث بعد", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            items(s.logs.size) { i ->
                val l = s.logs[i]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(l.t.ltr(), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(l.m, style = MaterialTheme.typography.bodySmall, color = when (l.k) { "e" -> c.red; "o" -> c.ok; "w" -> c.goldText; else -> c.text }, modifier = Modifier.weight(1f))
                }
            }
            item { Box(Modifier.size(12.dp)) }
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
