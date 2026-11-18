package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ArticleRow
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.ProviderCatalog
import com.sajed.articlestudio.data.ReviewEntity
import com.sajed.articlestudio.engine.BackupManager
import com.sajed.articlestudio.engine.Insights
import com.sajed.articlestudio.engine.ProvRate
import com.sajed.articlestudio.engine.ReviewStore
import com.sajed.articlestudio.engine.ReviewTags
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.engine.Stats
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class InsightsUi(
    val reviews: List<ReviewEntity> = emptyList(), val insights: Insights = Insights.build(emptyList()),
    val weekly: List<com.sajed.articlestudio.engine.SiteWeek> = emptyList(), val secPerArticle: Int = 0,
    val providers: List<ProvRate> = emptyList(), val generatedBy: Map<String, Int> = emptyMap(), val siteNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val store: ReviewStore, arts: ArticleDao, private val sites: SiteStore, private val health: HealthStore, private val backup: BackupManager,
) : ViewModel() {
    val ui: StateFlow<InsightsUi> = combine(store.all, arts.observeRows()) { rv, rows -> build(rv, rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUi())
    private val _msg = MutableStateFlow("")
    val msg: StateFlow<String> = _msg.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { viewModelScope.launch { runCatching { store.backfill() } } }

    private fun build(rv: List<ReviewEntity>, rows: List<ArticleRow>) = InsightsUi(
        rv, Insights.build(rv), Stats.weekly(rows, rv, System.currentTimeMillis()), Stats.secondsPerArticle(rows),
        ProviderCatalog.all.filter { it.kind == "text" }.map { s -> health.totals(s.id).let { ProvRate(s.name, it.first, it.second) } }.filter { it.total > 0 }.sortedByDescending { it.failPct },
        Stats.byProvider(rows), sites.sites.value.associate { it.id to it.name },
    )

    fun rate(id: Long, rating: Int, tags: List<String>, notes: String) = viewModelScope.launch { store.rate(id, rating, tags, notes) }
    fun metrics(r: ReviewEntity) = store.metricsOf(r)
    fun markdown(i: InsightsUi) = i.insights.markdown(i.siteNames)

    private fun run(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true; _msg.value = "جارٍ التنفيذ…"
        viewModelScope.launch {
            _msg.value = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { e.message ?: "تعذّر التنفيذ" }
            _busy.value = false
        }
    }
    fun backupNow() = run { backup.push() }
    fun restoreNow() = run { backup.restore() }
}

@Composable
fun InsightsScreen(onBack: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val s by vm.ui.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<ReviewEntity?>(null) }
    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.Back, "رجوع", { if (open != null) open = null else onBack() })
            Text("الرؤى والمراجعة", style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("المراجعات", tab == 0, { tab = 0; open = null })
            AppChip("الإحصاءات", tab == 1, { tab = 1 })
            AppChip("النسخة الاحتياطية", tab == 2, { tab = 2 })
        }
        val sel = open
        when {
            tab == 0 && sel != null -> ReviewDetail(sel, s.siteNames[sel.siteId] ?: sel.siteId, vm.metrics(sel)) { r, t, n -> vm.rate(sel.id, r, t, n); open = null }
            tab == 0 -> ReviewList(s, { open = it })
            tab == 1 -> StatsTab(s)
            else -> BackupTab(msg, busy, vm::backupNow, vm::restoreNow)
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, mod: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(mod.background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun Head(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun ReviewList(s: InsightsUi, onOpen: (ReviewEntity) -> Unit) {
    val c = AppTheme.colors
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("منشور", "${s.insights.count}", Modifier.weight(1f))
                Kpi("مُقيَّم", "${s.insights.rated}", Modifier.weight(1f))
                Kpi("متوسط آلي", "${s.insights.avgAuto}", Modifier.weight(1f))
            }
        }
        if (s.insights.topFlags.isNotEmpty()) item {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("أكثر نقاط الضعف تكراراً", style = MaterialTheme.typography.titleSmall, color = c.text)
                    s.insights.topFlags.forEach { Text("${it.first} · ${it.second}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                }
            }
        }
        if (s.reviews.isEmpty()) item { Text("تظهر هنا كل مقالة بعد نشرها لتراجعها وتقيّمها.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(s.reviews, key = { it.id }) { r ->
            Row(
                Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable { onOpen(r) }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 2)
                    Text(
                        (s.siteNames[r.siteId] ?: r.siteId) + " · " + r.provider.ifBlank { "—" } + " · " + (if (r.rating > 0) "تقييمك ${r.rating}/5" else "بلا تقييم"),
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                Text("${r.auto}", style = MaterialTheme.typography.titleMedium, color = if (r.auto >= 70) c.text else c.red)
            }
        }
    }
}

@Composable
private fun ReviewDetail(r: ReviewEntity, site: String, metrics: Map<String, String>, onSave: (Int, List<String>, String) -> Unit) {
    val c = AppTheme.colors
    var rating by remember(r.id) { mutableStateOf(r.rating) }
    var tags by remember(r.id) { mutableStateOf(r.tags.split(",").filter { it.isNotBlank() }.toSet()) }
    var notes by remember(r.id) { mutableStateOf(r.notes) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(r.title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("$site · ${r.provider.ifBlank { "—" }} · الدرجة الآلية ${r.auto}", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                metrics.forEach { (k, v) -> Row { Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted); Text(v, style = MaterialTheme.typography.bodySmall, color = c.text) } }
                if (r.flags.isNotBlank()) Text("تنبيهات: " + r.flags.replace(",", "، "), style = MaterialTheme.typography.bodySmall, color = c.red)
            }
        }
        Head("تقييمك")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..5).forEach { n -> AppChip("$n", rating == n, { rating = if (rating == n) 0 else n }) } }
        Head("ما الذي يحتاج تحسيناً؟")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewTags.all.forEach { t -> AppChip(t, t in tags, { tags = if (t in tags) tags - t else tags + t }) }
        }
        Box(Modifier.fillMaxWidth().height(110.dp).background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(10.dp)) {
            BasicTextField(notes, { notes = it.take(600) }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.text),
                decorationBox = { f -> if (notes.isEmpty()) Text("ملاحظات المراجعة (اختياري)", style = MaterialTheme.typography.bodyMedium, color = c.muted); f() })
        }
        PrimaryButton("حفظ المراجعة", Icons.Check, { onSave(rating, tags.toList(), notes) }, Modifier.fillMaxWidth())
        if (r.remoteUrl.startsWith("http")) Text(r.remoteUrl, style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Start)
    }
}

@Composable
private fun StatsTab(s: InsightsUi) {
    val c = AppTheme.colors
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("نُشر هذا الأسبوع", "${s.weekly.sumOf { it.published }}", Modifier.weight(1f))
                Kpi("وُلّد هذا الأسبوع", "${s.weekly.sumOf { it.generated }}", Modifier.weight(1f))
                Kpi("ثانية/مقال", if (s.secPerArticle == 0) "—" else "${s.secPerArticle}", Modifier.weight(1f))
            }
        }
        item { Head("هذا الأسبوع لكل موقع") }
        if (s.weekly.isEmpty()) item { Text("لا نشاط في آخر سبعة أيام.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(s.weekly, key = { it.siteId }) { w ->
            val max = (s.weekly.maxOfOrNull { it.published + it.generated } ?: 1).coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(s.siteNames[w.siteId] ?: w.siteId, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text); Text("نُشر ${w.published} · وُلّد ${w.generated}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.line, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((w.published + w.generated).toFloat() / max).height(6.dp).background(c.text, RoundedCornerShape(3.dp)))
                }
            }
        }
        item { Head("إخفاق المزودين (تراكمي)") }
        if (s.providers.isEmpty()) item { Text("لا بيانات بعد.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(s.providers, key = { it.id }) { p ->
            Row { Text(p.id, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                Text("${p.fail} من ${p.total} · ${p.failPct}%", style = MaterialTheme.typography.bodySmall, color = if (p.failPct >= 30) c.red else c.muted) }
        }
        item { Head("المقالات الجاهزة لكل مزود") }
        items(s.generatedBy.entries.sortedByDescending { it.value }, key = { it.key }) { e ->
            Row { Text(e.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text); Text("${e.value}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
        }
        item { Text("التكلفة المالية غير مقيسة: المزودون على الطبقات المجانية. الزمن هو متوسط الفارق بين مقالين متتاليين.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
    }
}

@Composable
private fun BackupTab(msg: String, busy: Boolean, onPush: () -> Unit, onPull: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تُحفظ المواقع وإعداداتها والمراجعات في فرع app-backup بمستودع GitHub المضبوط في تبويب السجل. لا تُحفظ المفاتيح ولا التوكن.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        PrimaryButton("حفظ نسخة احتياطية الآن", Icons.Export, onPush, Modifier.fillMaxWidth(), enabled = !busy)
        AppChip("استعادة آخر نسخة (دمج)", false, { if (!busy) onPull() })
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}
