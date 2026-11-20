package com.sajed.articlestudio.ui.pipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.engine.PlanMode
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.ui.UpdateCard
import com.sajed.articlestudio.ui.home.HomeUiState
import com.sajed.articlestudio.ui.home.HomeViewModel
import com.sajed.articlestudio.ui.home.NodeState
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.stagger
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr

private val TIME = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH)
internal fun hm(t: Long) = TIME.format(java.util.Date(t))
private val HomeUiState.remaining get() = if (running || resumable) (shownTotal - done - failed - dup).coerceAtLeast(0) else 0

/** الرئيسية: رحلة المقال بالأرقام، التوليد، التشغيل الحي، والمنصات. */
@Composable
fun PipeHome(home: HomeViewModel, pipe: PipeViewModel, go: (String) -> Unit, onArticles: (String) -> Unit, onPlatforms: () -> Unit) {
    val s by home.ui.collectAsStateWithLifecycle()
    val o by pipe.overview.collectAsStateWithLifecycle()
    val sitesList by pipe.sites.sites.collectAsStateWithLifecycle()
    val active by pipe.sites.activeId.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var genOpen by remember { mutableStateOf(false) }
    var siteOpen by remember { mutableStateOf(false) }
    val site = sitesList.firstOrNull { it.id == active } ?: sitesList.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item("top") {
            PTop(site?.name.orEmpty(), "${site?.topicLabel.orEmpty()} · تبديل الموقع", lead = {
                Box(Modifier.clip(RoundedCornerShape(13.dp)).clickable(role = Role.Button) { siteOpen = true }) { PAvatar(site?.name?.take(1).orEmpty()) }
            }, actions = { PIcon(Icons.Bell, "يحتاجك", { go("needs") }, badge = o.needs.size) })
        }
        item("pipe") {
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).stagger(0)) {
                Row(Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
                    Stage("قيد التوليد", s.remaining, c.red, s.running, Modifier.weight(1f)) { go("run") }
                    StageSep(); Stage("جاهزة", o.ready, c.text, false, Modifier.weight(1f)) { onArticles("ready") }
                    StageSep(); Stage("منشورة", o.published, c.text, false, Modifier.weight(1f)) { onArticles("published") }
                    StageSep(); Stage("للمشاركة", o.waiting, c.goldText, true, Modifier.weight(1f)) { onPlatforms() }
                }
            }
        }
        item("upd") { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { UpdateCard(pipe.updater) } }
        item("cta") {
            Column(Modifier.padding(horizontal = 16.dp).stagger(1)) {
                when {
                    s.running -> PCta("أوقف التوليد", Icons.Stop, { home.go() })
                    s.resumable -> PCta("استأنف التوليد (${s.remaining} متبقية)", Icons.Play, { home.go() })
                    else -> PCta("ولّد مقالات جديدة", Icons.Generate, { genOpen = true })
                }
                if (s.notice.isNotEmpty()) Text(s.notice, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.red)
            }
        }
        if (s.running || s.resumable) item("live") {
            PCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), onClick = { go("run") }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PRing(s.progress, 48.dp, 4.dp, if (s.running) c.red else c.goldText, "${s.done}/${s.shownTotal}")
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.running) PPulse()
                            Text(if (s.running) "يولّد الآن · ${s.siteName}" else "متوقف مؤقتاً · ${s.siteName}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = c.text)
                        }
                        Text(listOfNotNull(s.rows.firstOrNull()?.title?.let { "«${it.take(30)}…»" }, s.nodes.firstOrNull { it.state == NodeState.Active }?.name, "${(s.parallel).toString().ltr()} بالتوازي").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.Icon(Icons.Next, null, tint = c.muted)
                }
            }
        }
        if (sitesList.size > 1) {
            item("sitesH") { PSection("مواقعك", "إدارة المواقع") { go("sites") } }
            item("sites") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.cards.forEach { k ->
                        PCard(Modifier.width(168.dp), alert = false, onClick = { pipe.sites.setActive(k.id) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PAvatar(k.name.take(1), k.active, 30.dp)
                                    Text(k.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(when { k.running -> "يولّد ${("${k.done}/${k.total}").ltr()}"; k.resumable -> "متوقف · يمكن الاستئناف"; else -> "جاهزة ${k.ready} · منشورة ${k.published}" }, style = MaterialTheme.typography.bodySmall, color = if (k.running) c.red else c.muted, maxLines = 1)
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(c.line)) {
                                    Box(Modifier.fillMaxWidth(if (k.running || k.resumable) k.progress else 0f).height(4.dp).background(c.red))
                                }
                            }
                        }
                    }
                }
            }
        }
        item("platH") { PSection("المنصات", "إدارة الحسابات", onPlatforms) }
        if (o.platforms.isEmpty()) item("platE") {
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { go("add-account") }) {
                PRow("اربط أول حساب", "تيليغرام، X، إنستغرام، لينكدإن، فيسبوك… ليُشارك كل مقال تلقائياً", lead = { PLogo(Icons.Plus, c.goldText) })
            }
        } else o.platforms.chunked(2).forEachIndexed { i, pair ->
            item("plat$i") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p -> PlatformCard(p, Modifier.weight(1f)) { go("plat/${p.id}") } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }

    if (genOpen) GenerateSheet(s, home, pipe, site?.autoExport == true, { genOpen = false })
    if (siteOpen) PSheet({ siteOpen = false }, "اختر الموقع", "لكل موقع مقالاته وقواعده ومجلده المستقل") {
        PCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            s.cards.forEachIndexed { i, k ->
                if (i > 0) PDivider()
                PRow(k.name, "${k.topic} · ${k.host.ltr()}", { pipe.sites.setActive(k.id); siteOpen = false }, lead = { PAvatar(k.name.take(1), k.active, 36.dp) },
                    trail = { if (k.active) PTag("نشط", Tone.Gold) else if (k.running) PTag("يولّد", Tone.Red) }, chevron = false)
            }
        }
        VSpace(12)
        PGhost("إضافة موقع أو تعديله", Icons.Plus, { siteOpen = false; go("sites") }, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StageSep() { Box(Modifier.width(1.dp).height(46.dp).background(AppTheme.colors.line)) }

@Composable
internal fun Stage(label: String, v: Int, color: Color, hot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PNum(v, 25, if (hot && v > 0) color else c.text)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp), color = c.muted, maxLines = 1)
        Box(Modifier.padding(top = 7.dp).width(22.dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(if (hot && v > 0) color else c.line))
    }
}

@Composable
fun PlatformCard(p: PlatformSum, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val spec = Platforms.of(p.id)
    PCard(modifier, alert = p.loginNeeded || p.needs > 0, onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLogo(platformIcon(p.id), size = 30.dp)
                Text(spec?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp), color = c.text, maxLines = 1)
                if (p.loginNeeded || p.needs > 0) Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.red))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                PNum(p.waiting, 27); Box(Modifier.weight(1f))
                PSpark(p.week, if (p.loginNeeded) c.red else c.gold)
            }
            Text(when { !p.linked -> "لا حساب"; p.loginNeeded -> "سجّل الدخول لاستئناف النشر"; p.needs > 0 -> "${p.needs} تحتاجك"; p.next != null -> "منتظرة · التالي ${hm(p.next).ltr()}"; else -> "منشورة ${p.done}" },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = if (p.loginNeeded || p.needs > 0) c.red else c.muted, maxLines = 1)
        }
    }
}

@Composable
private fun GenerateSheet(s: HomeUiState, home: HomeViewModel, pipe: PipeViewModel, auto: Boolean, onClose: () -> Unit) {
    val c = AppTheme.colors
    PSheet(onClose, "توليد دفعة جديدة", "لموقع ${s.siteName} · تُحفظ اختياراتك للمرة القادمة") {
        PLabel("مصدر المواضيع")
        PSeg(listOf("أخبار حية", "مواضيع دائمة", "مواضيعي"), s.mode.ordinal) { home.setMode(PlanMode.entries[it]) }
        if (s.mode == PlanMode.Mine) {
            PLabel("مواضيعك (موضوع في كل سطر)")
            Box(Modifier.fillMaxWidth().height(120.dp).clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(12.dp)) {
                BasicTextField(s.custom, home::setCustom, Modifier.fillMaxSize(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.text))
                if (s.custom.isEmpty()) Text("مثال: أفضل هواتف 2026 تحت 300 دولار", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
        PLabel("عدد المقالات")
        PStepper(s.total, 1..30, home::setTotal)
        PLabel("المزودات بالتوازي")
        PSeg(listOf("1", "2", "3", "4"), (s.parallel - 1).coerceIn(0, 3)) { home.setParallel(it + 1) }
        PLabel("بعد التوليد")
        PCard(Modifier.fillMaxWidth()) {
            PRow("صدّر وانشر تلقائياً", "يُصدّر وينشر على بلوجر ثم يُجدول المشاركة على المنصات", trail = { PToggle(auto, pipe::setAutoExport) }, chevron = false)
        }
        VSpace(16)
        PCta("ابدأ التوليد", Icons.Generate, { home.go(); onClose() })
        if (s.notice.isNotEmpty()) Text(s.notice, Modifier.padding(top = 8.dp).fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = c.red, textAlign = TextAlign.Center)
    }
}

/** التشغيل الحي: التقدم، المزودات، مقالات الدفعة، والسجل. */
@Composable
fun RunScreen(home: HomeViewModel, onBack: () -> Unit) {
    val s by home.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { PTop("التشغيل الحي", "${s.siteName} · ${s.phase.ifBlank { if (s.running) "يعمل" else "متوقف" }}", onBack) {
            if (s.running) PIcon(Icons.Stop, "إيقاف", { home.go() }, tint = c.red) else if (s.resumable) PIcon(Icons.Play, "استئناف", { home.go() }, tint = c.goldText)
        } }
        item {
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PRing(s.progress, 80.dp, 6.dp, c.red, "${(s.progress * 100).toInt()}%")
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) { PNum(s.done, 36); Text("/${s.shownTotal}".ltr(), style = MaterialTheme.typography.titleMedium, color = c.muted) }
                        Text(if (s.running) "مقالات · المتبقي نحو ${s.etaMin} دقيقة" else if (s.resumable) "متوقف · اضغط تشغيل للاستئناف" else "لا تشغيل الآن", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
        item { VSpace(8); PKv(listOf(Triple("${s.parallel}", "بالتوازي", null), Triple("${s.provReady}", "مزودات جاهزة", c.ok), Triple("${s.failed + s.dup}", "إخفاق أو تكرار", if (s.failed > 0) c.red else null))) }
        item { PSection("المزودات") }
        item {
            PChipRow { s.nodes.forEach { n -> PTag(n.name + when (n.state) { NodeState.Active -> " · يكتب"; NodeState.Cooling -> " · يستريح ${n.coolSec}ث"; NodeState.NoKey -> " · بلا مفتاح"; else -> "" },
                when (n.state) { NodeState.Active -> Tone.Red; NodeState.Cooling -> Tone.Gold; NodeState.NoKey -> Tone.Plain; else -> Tone.Ok }) } }
        }
        item { PSection("آخر ما اكتمل", "") }
        if (s.rows.isEmpty()) item { Text("تظهر هنا المقالات فور اكتمالها.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = c.muted) }
        items(s.rows, key = { "r${it.n}" }) { r ->
            PRow(r.title, "${(r.words).toString().ltr()} كلمة · ${(r.provider).toString().ltr()}", lead = { PNum(r.n, 14, c.muted) }, trail = { PTag("جاهز", Tone.Ok) })
        }
        item { PSection("سجل العمليات") }
        item {
            PGroup {
                if (s.logs.isEmpty()) PRow("لا أحداث بعد") else s.logs.take(25).forEachIndexed { i, l ->
                    if (i > 0) PDivider()
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(l.t.ltr(), style = MaterialTheme.typography.labelSmall, color = c.muted)
                        Text(l.m, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = when (l.k) { "e" -> c.red; "w" -> c.goldText; else -> c.text })
                    }
                }
            }
        }
    }
}

/** «يحتاجك»: كل ما يتطلب قراراً، وكل زر ينقلك لمكان حلّه. */
@Composable
fun NeedsScreen(pipe: PipeViewModel, home: HomeViewModel, go: (String) -> Unit, onBack: () -> Unit) {
    val o by pipe.overview.collectAsStateWithLifecycle()
    val s by home.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { PTop("يحتاجك", "كل ما يتطلب قراراً في مكان واحد", onBack) }
        item { Box(Modifier.padding(horizontal = 16.dp)) { UpdateCard(pipe.updater) } }
        if (o.needs.isEmpty()) item { PEmpty("كل شيء على ما يرام", "لا حسابات تحتاج دخولاً، ولا منشورات متوقفة.", Icons.Check) }
        else item {
            PGroup(Modifier.padding(top = 6.dp)) {
                o.needs.forEachIndexed { i, n ->
                    if (i > 0) PDivider()
                    val (icon, tint, label, route) = when (n.kind) {
                        "login" -> Quad(Icons.Key, c.red, "افتح", "plat/${n.ref}/settings")
                        "share" -> Quad(Icons.Warning, c.red, "راجع", "plat/${n.ref}?tab=1")
                        "stuck" -> Quad(Icons.Export, c.red, "انشر", "publish")
                        "update" -> Quad(Icons.Export, c.goldText, "حدّث", "")
                        else -> Quad(Icons.Star, c.goldText, "قيّم", "insights")
                    }
                    PRow(n.title, n.sub, if (route.isEmpty()) null else ({ go(route) }), lead = { PLogo(icon, tint) },
                        trail = { if (route.isNotEmpty()) PPill(label, null, { go(route) }, primary = false) }, chevron = false)
                }
            }
        }
        item { PSection("آخر الأحداث", "التشغيل الحي") { go("run") } }
        item {
            PGroup {
                if (s.logs.isEmpty()) PRow("لا أحداث بعد") else s.logs.take(12).forEachIndexed { i, l ->
                    if (i > 0) PDivider()
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(l.t.ltr(), style = MaterialTheme.typography.labelSmall, color = c.muted)
                        Text(l.m, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (l.k == "e") c.red else c.text)
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
