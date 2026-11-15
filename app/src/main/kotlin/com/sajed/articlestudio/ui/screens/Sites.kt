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

    val basics = ModuleSchema(
        "site-basics", "الأساسيات",
        listOf(
            Field("name", "اسم الموقع", required = true),
            Field("url", "رابط الموقع", required = true, hint = "مثال: https://example.blogspot.com"),
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
        "site-publish", "المصادر والنشر",
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
    val steps = listOf(basics, rules, publish)

    fun values(s: SiteConfig): Map<String, String> = mapOf(
        "name" to s.name, "url" to s.url, "niche" to nicheLabels.getValue(s.niche.takeIf { it in nicheLabels } ?: "general"),
        "topic" to s.nicheLabel, "author" to s.author, "audience" to s.audience,
        "tone" to toneLabels.getValue(s.tone.takeIf { it in toneLabels } ?: "friendly"),
        "length" to lengthLabels.getValue(s.length.takeIf { it in lengthLabels } ?: "medium"),
        "keywords" to s.keywords.joinToString("، "), "avoid" to s.avoid, "rules" to s.rules,
        "feeds" to s.feeds.joinToString("\n"), "labels" to s.labels.joinToString("، "),
        "count" to s.count.toString(), "schedule" to s.schedule.toString(), "repo" to s.repo, "branch" to s.branch,
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
    val store: SiteStore, private val db: AppDb, private val blog: BlogIndex,
) : ViewModel() {
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

private fun nicheColor(hue: Int) = Color.hsv(hue.toFloat(), 0.55f, 0.88f)

@Composable
fun SitesScreen(onGenerate: () -> Unit, vm: SitesViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val sites by vm.store.sites.collectAsStateWithLifecycle()
    val active by vm.store.activeId.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SiteConfig?>(null) }
    var deleting by remember { mutableStateOf<SiteConfig?>(null) }

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
                onEdit = { editing = s }, onDelete = { deleting = s },
            )
        }
    }

    editing?.let { s -> SiteEditor(s, isNew = s.id.isEmpty(), onClose = { editing = null }, onSave = { built -> vm.save(built, s.id.isEmpty()); editing = null }) }
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
    onActivate: () -> Unit, onGenerate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("منشور", st.posts, Modifier.weight(1f)); Mini("مولّد", st.total, Modifier.weight(1f))
            Mini("جاهز", st.ready, Modifier.weight(1f)); Mini("مُصدَّر", st.exported, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onGenerate) { Text("ولّد لهذا الموقع", color = c.goldText) }
            Box(Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteEditor(site: SiteConfig, isNew: Boolean, onClose: () -> Unit, onSave: (SiteConfig) -> Unit) {
    val c = AppTheme.colors
    var step by remember { mutableStateOf(0) }
    var values by remember { mutableStateOf(SiteForms.values(site)) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val schema = SiteForms.steps[step]

    fun check(): Boolean {
        val e = LinkedHashMap(SchemaRules.validate(schema, values))
        if (step == 0) {
            val v = SiteRules.validate(SiteForms.build(site, values))
            v["name"]?.let { e["name"] = it }; v["url"]?.let { e["url"] = it }
        }
        if (step == 2) {
            val v = SiteRules.validate(SiteForms.build(site, values))
            v["repo"]?.let { e["repo"] = it }; v["feeds"]?.let { e["feeds"] = it }
        }
        errors = e
        return e.isEmpty()
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (isNew) "موقع جديد" else "تعديل «${site.name}»", style = MaterialTheme.typography.titleLarge, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteForms.steps.forEachIndexed { i, st ->
                    Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) c.gold else c.line, RoundedCornerShape(3.dp)))
                }
            }
            Text("${(step + 1).toString().ltr()} / ${SiteForms.steps.size.toString().ltr()}  ${schema.title}", style = MaterialTheme.typography.labelLarge, color = c.goldText)
            SchemaForm(schema, values, errors) { k, v ->
                values = if (k == "niche" && isNew) SiteForms.onNicheChanged(values, v) else values + (k to v)
                if (errors.containsKey(k)) errors = errors - k
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) AppChip("السابق", false, { step -= 1; errors = emptyMap() })
                Box(Modifier.weight(1f))
                if (step < SiteForms.steps.lastIndex) PrimaryButton("التالي", Icons.Next, { if (check()) step += 1 }, Modifier.weight(2f))
                else PrimaryButton(if (isNew) "إضافة الموقع" else "حفظ", Icons.Check, { if (check()) onSave(SiteForms.build(site, values)) }, Modifier.weight(2f))
            }
            Box(Modifier.height(12.dp))
        }
    }
}
