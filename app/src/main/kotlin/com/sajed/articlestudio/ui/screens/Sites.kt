package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.engine.BlogIndex
import com.sajed.articlestudio.engine.Niches
import com.sajed.articlestudio.engine.SiteConfig
import com.sajed.articlestudio.engine.SiteProfile
import com.sajed.articlestudio.engine.SiteProfiler
import com.sajed.articlestudio.engine.SiteRules
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.schema.Field
import com.sajed.articlestudio.schema.FieldType
import com.sajed.articlestudio.schema.ModuleSchema
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.stagger
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SiteStats(val total: Int = 0, val ready: Int = 0, val exported: Int = 0, val failed: Int = 0, val posts: Int = 0)

/** مخططات خطوات إضافة الموقع: كل خطوة وحدة صغيرة يرسمها المحرك العام. */
object SiteForms {
    private val toneLabels = linkedMapOf("friendly" to "ودّية", "formal" to "رسمية", "analytic" to "تحليلية", "simple" to "مبسّطة")
    private val lengthLabels = linkedMapOf("short" to "قصير", "medium" to "متوسط", "long" to "طويل")
    private val nicheLabels = Niches.all.associate { it.id to it.label }

    val identity = ModuleSchema(
        "site-identity", "الموقع",
        listOf(
            Field("url", "رابط المدونة", required = true, hint = "مثال: https://example.blogspot.com"),
            Field("name", "اسم الموقع", hint = "يُستنتج تلقائياً من المدونة إن تُرك فارغاً"),
        ),
        emptyList(),
    )
    val identityUrl = ModuleSchema("site-url", "الموقع", identity.fields.take(1), emptyList())
    val basics = ModuleSchema(
        "site-basics", "المجال والكاتب",
        listOf(
            Field("niche", "المجال", FieldType.Choice, options = nicheLabels.values.toList()),
            Field("topic", "وصف المجال (اختياري)", hint = "يظهر للمحرك، مثل: أخبار الذكاء الاصطناعي"),
            Field("author", "اسم الكاتب في المدونة"),
        ),
        emptyList(),
    )
    val rules = ModuleSchema(
        "site-rules", "هوية المحتوى وقواعده",
        listOf(
            Field("audience", "الجمهور المستهدف", hint = "مثال: الشباب في الخليج"),
            Field("tone", "نبرة الكتابة", FieldType.Choice, options = toneLabels.values.toList()),
            Field("length", "طول المقال", FieldType.Choice, options = lengthLabels.values.toList()),
            Field("keywords", "الكلمات المفتاحية", lines = 2, hint = "افصل بينها بفاصلة"),
            Field("avoid", "ممنوع ذكره", lines = 2, hint = "مواضيع أو عبارات يتجنبها هذا الموقع"),
            Field("rules", "قواعد خاصة بالموقع", lines = 3, hint = "تُلزَم حرفياً في كل مقال"),
        ),
        emptyList(),
    )
    val publish = ModuleSchema(
        "site-publish", "المصادر والمحتوى",
        listOf(
            Field("feeds", "مصادر RSS إضافية", lines = 3, hint = "رابط في كل سطر"),
            Field("labels", "التصنيفات", hint = "افصل بينها بفاصلة؛ تُملأ من المجال إن تُركت فارغة"),
            Field("count", "عدد المقالات الافتراضي", FieldType.Number, min = 3, max = 30),
            Field("schedule", "ساعات بين المقالات المجدولة", FieldType.Number, min = 0, max = 72, hint = "0 = حسب إعداد التصدير العام"),
            Field("repo", "مستودع الصور (اختياري)", hint = "owner/repo، وإلا يُستخدم المستودع العام"),
            Field("branch", "فرع الصور (اختياري)"),
            Field("faq", "إضافة قسم الأسئلة الشائعة", FieldType.Toggle),
            Field("tables", "السماح بالجداول", FieldType.Toggle),
            Field("sources", "ذكر المصادر في نهاية المقال", FieldType.Toggle),
        ),
        emptyList(),
    )
    val details = listOf(basics, rules, publish)

    fun values(s: SiteConfig): Map<String, String> = mapOf(
        "name" to s.name, "url" to s.url, "niche" to nicheLabels.getValue(s.niche.takeIf { it in nicheLabels } ?: "general"),
        "topic" to s.nicheLabel, "author" to s.author, "audience" to s.audience,
        "tone" to toneLabels.getValue(s.tone.takeIf { it in toneLabels } ?: "friendly"),
        "length" to lengthLabels.getValue(s.length.takeIf { it in lengthLabels } ?: "medium"),
        "keywords" to s.keywords.joinToString("، "), "avoid" to s.avoid, "rules" to s.rules,
        "feeds" to s.feeds.joinToString("\n"), "labels" to s.labels.joinToString("، "),
        "count" to s.count.toString(), "schedule" to s.schedule.toString(), "repo" to s.repo, "branch" to s.branch,
        "autoExport" to s.autoExport.toString(), "autoEvery" to s.autoEvery.toString(), "autoCount" to s.autoCount.toString(),
        "autoFrom" to s.autoFrom.toString(), "autoTo" to s.autoTo.toString(),
        "faq" to s.faq.toString(), "tables" to s.tables.toString(), "sources" to s.sources.toString(),
    )

    private fun list(v: String) = v.split(',', '،', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun build(base: SiteConfig, v: Map<String, String>): SiteConfig {
        val niche = nicheLabels.entries.firstOrNull { it.value == v["niche"] }?.key ?: base.niche
        return base.copy(
            name = v["name"].orEmpty(), url = v["url"].orEmpty(), niche = niche, nicheLabel = v["topic"].orEmpty().trim(), author = v["author"].orEmpty(),
            audience = v["audience"].orEmpty(), tone = toneLabels.entries.firstOrNull { it.value == v["tone"] }?.key ?: base.tone,
            length = lengthLabels.entries.firstOrNull { it.value == v["length"] }?.key ?: base.length,
            keywords = list(v["keywords"].orEmpty()), avoid = v["avoid"].orEmpty(), rules = v["rules"].orEmpty(),
            feeds = v["feeds"].orEmpty().split('\n', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() },
            labels = list(v["labels"].orEmpty()), count = v["count"]?.toIntOrNull() ?: base.count, schedule = v["schedule"]?.toIntOrNull() ?: base.schedule,
            repo = v["repo"].orEmpty().trim(), branch = v["branch"].orEmpty().trim(),
            autoExport = v["autoExport"] == "true", autoEvery = v["autoEvery"]?.toIntOrNull() ?: base.autoEvery, autoCount = v["autoCount"]?.toIntOrNull() ?: base.autoCount,
            autoFrom = v["autoFrom"]?.toIntOrNull() ?: base.autoFrom, autoTo = v["autoTo"]?.toIntOrNull() ?: base.autoTo,
            faq = v["faq"] == "true", tables = v["tables"] == "true", sources = v["sources"] == "true",
        )
    }

    /** عند تغيير المجال لموقع جديد نملأ الكلمات والتصنيفات المقترحة إن كانت فارغة. */
    fun onNicheChanged(v: Map<String, String>, label: String): Map<String, String> {
        val n = Niches.all.firstOrNull { it.label == label } ?: return v
        val m = v.toMutableMap(); m["niche"] = label
        if (v["keywords"].isNullOrBlank()) m["keywords"] = n.keywords.joinToString("، ")
        if (v["labels"].isNullOrBlank()) m["labels"] = n.labels.joinToString("، ")
        return m
    }

    fun newSite() = SiteConfig(id = "", name = "", url = "https://", niche = "general")
}

@HiltViewModel
class SitesViewModel @Inject constructor(
    val store: SiteStore, private val db: AppDb, private val blog: BlogIndex, private val profiler: SiteProfiler,
) : ViewModel() {
    sealed interface Analysis { object Idle : Analysis; object Running : Analysis; class Done(val profile: SiteProfile) : Analysis; class Failed(val msg: String) : Analysis }
    private val _analysis = kotlinx.coroutines.flow.MutableStateFlow<Analysis>(Analysis.Idle)
    val analysis: StateFlow<Analysis> = _analysis
    fun resetAnalysis() { _analysis.value = Analysis.Idle }

    fun analyze(url: String) {
        if (_analysis.value is Analysis.Running) return
        _analysis.value = Analysis.Running
        viewModelScope.launch {
            _analysis.value = try { Analysis.Done(profiler.analyze(url)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Analysis.Failed(e.message ?: "تعذّر التحليل") }
        }
    }

    val stats: StateFlow<Map<String, SiteStats>> = combine(store.sites, db.articles().observeRows()) { sites, rows ->
        sites.associate { s ->
            val mine = rows.filter { it.siteId == s.id }
            s.id to SiteStats(mine.size, mine.count { it.status == "ready" }, mine.count { it.status == "exported" }, mine.count { it.status == "failed" }, blog.count(s.url))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun activate(id: String) = store.setActive(id)

    fun save(s: SiteConfig, isNew: Boolean): SiteConfig {
        val c = SiteRules.clean(if (isNew) s.copy(id = SiteRules.newId(s.name)) else s)
        store.upsert(c)
        if (isNew) store.setActive(c.id)
        viewModelScope.launch { runCatching { blog.refresh(c.url, force = true) } }
        return c
    }

    fun remove(id: String) = viewModelScope.launch {
        if (store.remove(id)) { db.articles().deleteSite(id); db.jobs().deleteSite(id) }
    }
}

private fun pubSummary(s: SiteConfig) = when (s.pubMode) {
    "google" -> "النشر: ${pubModeLabel(s.pubMode)} · " + when (s.pubState) { "live" -> "مباشر"; "scheduled" -> "مجدول"; else -> "مسودة" }
    else -> "النشر المباشر متوقف"
}

private fun nicheColor(hue: Int) = Color.hsv(hue.toFloat(), 0.55f, 0.88f)

@Composable
fun SitesScreen(onGenerate: () -> Unit, vm: SitesViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val sites by vm.store.sites.collectAsStateWithLifecycle()
    val active by vm.store.activeId.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SiteConfig?>(null) }
    var deleting by remember { mutableStateOf<SiteConfig?>(null) }
    var publishing by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") {
            Column(Modifier.stagger(0)) {
                Text("لوحة المواقع", style = MaterialTheme.typography.headlineLarge, color = c.text)
                Text("كل موقع بقواعده ومصادره وملفاته المستقلة", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
        item("kpi") {
            val all = stats.values
            Row(Modifier.stagger(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("المواقع", sites.size.toString().ltr(), Modifier.weight(1f))
                Kpi("المقالات", all.sumOf { it.total }.toString().ltr(), Modifier.weight(1f))
                Kpi("جاهزة للتصدير", all.sumOf { it.ready }.toString().ltr(), Modifier.weight(1f))
            }
        }
        item("add") { PrimaryButton("إضافة موقع جديد", Icons.Plus, { editing = SiteForms.newSite() }, Modifier.stagger(2)) }
        items(sites, key = { it.id }) { s ->
            SiteCard(
                s, stats[s.id] ?: SiteStats(), s.id == active, canDelete = sites.size > 1,
                onActivate = { vm.activate(s.id) }, onGenerate = { vm.activate(s.id); onGenerate() },
                onEdit = { editing = s }, onDelete = { deleting = s }, onPublish = { publishing = s.id },
            )
        }
    }

    publishing?.let { PublishSheet(it, onClose = { publishing = null }) }
    editing?.let { s -> SiteEditor(s, isNew = s.id.isEmpty(), vm = vm, onClose = { editing = null }, onSave = { built -> val isNew = s.id.isEmpty(); val saved = vm.save(built, isNew); editing = null; if (isNew) publishing = saved.id }) }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف «${s.name}»؟") },
            text = { Text("تُحذف مقالات هذا الموقع المحفوظة داخل التطبيق وسجل مهامه. الملفات المصدّرة في مجلد التنزيلات لا تتأثر.") },
            confirmButton = { TextButton({ vm.remove(s.id); deleting = null }) { Text("حذف", color = c.red) } },
            dismissButton = { TextButton({ deleting = null }) { Text("إلغاء", color = c.muted) } },
        )
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier) {
    val c = AppTheme.colors
    Panel(modifier) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = com.sajed.articlestudio.ui.theme.NumberStyle, color = c.text)
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SiteCard(
    s: SiteConfig, st: SiteStats, active: Boolean, canDelete: Boolean,
    onActivate: () -> Unit, onGenerate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onPublish: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val accent = nicheColor(s.nicheDef.hue)
    Column(
        Modifier.fillMaxWidth().background(c.surface, shape)
            .then(Modifier.background(if (active) c.gold.copy(alpha = 0.08f) else Color.Transparent, shape))
            .clickable(onClick = onActivate)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).background(accent, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.host.ltr(), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (active) Text("النشط", style = MaterialTheme.typography.labelMedium, color = c.goldText, modifier = Modifier.background(c.gold.copy(alpha = 0.16f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Text(s.topicLabel, style = MaterialTheme.typography.bodyMedium, color = c.text)
        Text(pubSummary(s), style = MaterialTheme.typography.labelMedium, color = if (s.pubMode == "off") c.muted else c.goldText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("منشور", st.posts, Modifier.weight(1f)); Mini("مولّد", st.total, Modifier.weight(1f))
            Mini("جاهز", st.ready, Modifier.weight(1f)); Mini("مُصدَّر", st.exported, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onGenerate) { Text("ولّد", color = c.goldText) }
            Box(Modifier.weight(1f))
            TextButton(onPublish) { Text("النشر", color = c.text) }
            TextButton(onEdit) { Text("تعديل", color = c.text) }
            if (canDelete) TextButton(onDelete) { Text("حذف", color = c.red) }
        }
    }
}

@Composable
private fun Mini(label: String, v: Int, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier.background(c.bg2, RoundedCornerShape(10.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.toString().ltr(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}

private val EVERY = listOf(0 to "متوقف", 1 to "كل ساعة", 3 to "كل 3 ساعات", 6 to "كل 6 ساعات", 12 to "كل 12 ساعة", 24 to "يومياً")
private val PER_RUN = listOf(1, 2, 3, 5)
private val WINDOWS = listOf((0 to 24) to "طوال اليوم", (8 to 22) to "النهار", (16 to 24) to "المساء", (22 to 6) to "الليل")

@Composable
private fun SectionTitle(text: String, hint: String = "") {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SiteEditor(site: SiteConfig, isNew: Boolean, vm: SitesViewModel, onClose: () -> Unit, onSave: (SiteConfig) -> Unit) {
    val c = AppTheme.colors
    var values by remember { mutableStateOf(SiteForms.values(site)) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var analyzed by remember { mutableStateOf(!isNew) }
    var details by remember { mutableStateOf(false) }
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    androidx.compose.runtime.DisposableEffect(Unit) { vm.resetAnalysis(); onDispose { vm.resetAnalysis() } }
    androidx.compose.runtime.LaunchedEffect(analysis) {
        (analysis as? SitesViewModel.Analysis.Done)?.let { d ->
            val merged = d.profile.applyTo(SiteForms.build(site, values))
            val nv = SiteForms.values(merged)
            values = nv + mapOf("url" to values["url"].orEmpty(), "autoExport" to values["autoExport"].orEmpty(), "autoEvery" to values["autoEvery"].orEmpty(),
                "autoCount" to values["autoCount"].orEmpty(), "autoFrom" to values["autoFrom"].orEmpty(), "autoTo" to values["autoTo"].orEmpty(), "repo" to values["repo"].orEmpty(), "branch" to values["branch"].orEmpty())
            analyzed = true; errors = emptyMap()
        }
    }
    val built = SiteForms.build(site, values)

    fun set(k: String, v: String) { values = values + (k to v); if (errors.containsKey(k)) errors = errors - k }

    fun validate(): Boolean {
        val e = LinkedHashMap<String, String>()
        SiteRules.validate(built.let { if (it.name.isBlank()) it.copy(name = "موقع") else it }).forEach { (k, v) -> if (k in setOf("url", "repo", "feeds")) e[k] = v }
        if (analyzed && built.name.isBlank()) e["name"] = "اسم الموقع قصير"
        errors = e
        return e.isEmpty()
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (isNew) "موقع جديد" else "إعدادات «${site.name}»", style = MaterialTheme.typography.titleLarge, color = c.text)

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("1 · المدونة", if (analyzed) "تم استنتاج الهوية من المدونة. أعد التحليل إن تغيّر محتواها." else "أدخل الرابط فقط، والباقي يُستنتج تلقائياً من محتوى مدونتك.")
                    SchemaForm(if (isNew && !analyzed) SiteForms.identityUrl else SiteForms.identity, values, errors) { k, v -> set(k, v) }
                    when (val a = analysis) {
                        is SitesViewModel.Analysis.Running -> Text("جارٍ قراءة المدونة وتحليلها…", style = MaterialTheme.typography.bodyMedium, color = c.goldText)
                        is SitesViewModel.Analysis.Failed -> Text(a.msg, style = MaterialTheme.typography.bodyMedium, color = c.red)
                        is SitesViewModel.Analysis.Done -> Text(
                            (if (a.profile.usedAi) "تم التحليل بالذكاء الاصطناعي" else "تم التحليل من بيانات المدونة (فعّل مزوداً لدقة أعلى)") + " · ${a.profile.posts.toString().ltr()} مقالاً قُرئ",
                            style = MaterialTheme.typography.bodyMedium, color = c.goldText,
                        )
                        else -> {}
                    }
                    PrimaryButton(if (analyzed) "إعادة التحليل والتعبئة التلقائية" else "حلّل مدونتي وعبّئ كل شيء تلقائياً", Icons.Refresh, {
                        val u = built.url.trim()
                        if (SiteRules.validate(built.copy(name = "موقع"))["url"] != null) errors = mapOf("url" to "رابط غير صالح، مثال: https://example.blogspot.com") else vm.analyze(u)
                    }, enabled = analysis !is SitesViewModel.Analysis.Running)
                    if (isNew && !analyzed) TextButton({ analyzed = true; if (values["name"].isNullOrBlank()) set("name", "") }) { Text("إضافة يدوية بدون تحليل", color = c.muted) }
                }
            }

            if (analyzed) {
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("2 · هوية المحتوى", "تُستخدم تلقائياً في كل توليد")
                        Summary("المجال", built.topicLabel); Summary("النبرة", values["tone"].orEmpty()); Summary("طول المقال", values["length"].orEmpty())
                        Summary("الجمهور", built.audience.ifBlank { "—" }); Summary("الكلمات المفتاحية", built.keywords.size.toString().ltr()); Summary("التصنيفات", built.labels.size.toString().ltr())
                        TextButton({ details = !details }) { Text(if (details) "إخفاء التفاصيل" else "تعديل التفاصيل يدوياً", color = c.goldText) }
                        if (details) SiteForms.details.forEach { sc ->
                            Text(sc.title, style = MaterialTheme.typography.labelLarge, color = c.goldText)
                            SchemaForm(sc, values, errors) { k, v -> set(k, if (k == "niche" && isNew) { values = SiteForms.onNicheChanged(values, v); v } else v) }
                        }
                    }
                }

                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("3 · التوليد والنشر التلقائي", "المؤقت يولّد في الخلفية ثم يصدّر ويرفع الصور وينشر بلا تدخل")
                        Text("التكرار", style = MaterialTheme.typography.labelLarge, color = c.muted)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EVERY.forEach { (h, l) -> AppChip(l, built.autoEvery == h, { set("autoEvery", h.toString()) }) }
                        }
                        if (built.autoEvery > 0) {
                            Text("مقالات كل دورة", style = MaterialTheme.typography.labelLarge, color = c.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PER_RUN.forEach { n -> AppChip(n.toString().ltr(), built.autoCount == n, { set("autoCount", n.toString()) }) } }
                            Text("ساعات العمل", style = MaterialTheme.typography.labelLarge, color = c.muted)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WINDOWS.forEach { (w, l) -> AppChip(l, built.autoFrom == w.first && built.autoTo == w.second, { set("autoFrom", w.first.toString()); set("autoTo", w.second.toString()) }) }
                            }
                        }
                        SchemaForm(ModuleSchema("auto", "", listOf(Field("autoExport", "بعد كل توليد: تصدير ورفع صور ونشر تلقائي", FieldType.Toggle)), emptyList()), values, errors) { k, v -> set(k, v) }
                    }
                }
            }

            if (analyzed) PrimaryButton(if (isNew) "إضافة الموقع" else "حفظ", Icons.Check, { if (validate()) onSave(built) })
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = c.text, modifier = Modifier.weight(0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
