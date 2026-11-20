package com.sajed.articlestudio.ui.pipe

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ArticleRow
import com.sajed.articlestudio.data.ReviewDao
import com.sajed.articlestudio.data.ReviewEntity
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.engine.ReviewStore
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.social.ShareStore
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.screens.ArticlesViewModel
import com.sajed.articlestudio.ui.screens.ExportViewModel
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** فئات العرض: مفتاح الفلتر ← اسم. */
val ART_FILTERS = listOf("all" to "الكل", "ready" to "جاهزة", "published" to "منشورة", "stuck" to "متوقفة", "failed" to "فشلت", "duplicate" to "مكررة")

fun ArticleRow.matches(f: String) = when (f) {
    "ready" -> status == "ready"; "published" -> remoteUrl.isNotEmpty(); "stuck" -> status == "exported" && remoteUrl.isEmpty()
    "failed" -> status == "failed"; "duplicate" -> status == "duplicate"; else -> true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArticleHubViewModel @Inject constructor(dao: ArticleDao, private val sites: SiteStore, private val share: ShareStore, private val reviewsDao: ReviewDao, private val reviews: ReviewStore) : ViewModel() {
    val rows: StateFlow<List<ArticleRow>?> = combine(dao.observeRows(), sites.activeId) { l, sid -> l.filter { it.siteId == sid }.sortedWith(compareByDescending<ArticleRow> { it.jobId }.thenByDescending { it.idx }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val siteName = MutableStateFlow(sites.active().name).also { f -> viewModelScope.launch { sites.activeId.collect { f.value = sites.get(it).name } } }
    private val sel = MutableStateFlow<Long?>(null)
    val selShares: StateFlow<List<ShareJobEntity>> = sel.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else share.ofArticle(id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _review = MutableStateFlow<ReviewEntity?>(null)
    val review: StateFlow<ReviewEntity?> = _review.asStateFlow()
    private val _msg = MutableStateFlow("")
    val msg: StateFlow<String> = _msg.asStateFlow()

    fun select(id: Long?) { sel.value = id; _review.value = null; _msg.value = ""; if (id != null) viewModelScope.launch { _review.value = reviewsDao.byArticle(id) } }
    fun rate(stars: Int) = viewModelScope.launch {
        val id = sel.value ?: return@launch
        var r = reviewsDao.byArticle(id)
        if (r == null) { reviews.record(id); r = reviewsDao.byArticle(id) }
        r ?: return@launch
        reviews.rate(r.id, stars, r.tags.split(",").filter { it.isNotBlank() }, r.notes)
        _review.value = reviewsDao.byArticle(id)
    }
    fun shareNow() = viewModelScope.launch { val id = sel.value ?: return@launch; _msg.value = if (share.shareNow(id) > 0) "أُضيف للطابور ويُنشر خلال دقائق" else "لا حساب مفعّل أو المقال مُشارك على كل الحسابات" }
}

@Composable
fun ArticlesHub(initialFilter: String, onPublishScreen: () -> Unit, vm: ArticleHubViewModel = hiltViewModel(), base: ArticlesViewModel = hiltViewModel(), exp: ExportViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val siteName by vm.siteName.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var view by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.ifBlank { "all" }) }
    var page by rememberSaveable(filter) { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ArticleRow?>(null) }
    var publishing by remember { mutableStateOf(false) }
    val all = rows.orEmpty()
    val shown = all.filter { it.matches(filter) && (q.isBlank() || it.title.contains(q.trim(), true)) }
    val pageSize = 20
    LaunchedEffect(shown.size) { if (page * pageSize >= shown.size && page > 0) page = (shown.size - 1).coerceAtLeast(0) / pageSize }
    val ready = all.count { it.status == "ready" }

    Column(Modifier.fillMaxSize()) {
        PTop("المقالات", "${siteName.toString().ltr()} · ${(all.size).toString().ltr()} مقالاً", actions = {
            PIcon(if (searching) Icons.Close else Icons.Search, "بحث", { searching = !searching; if (!searching) q = "" })
        })
        if (searching) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(46.dp).clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(q, { q = it; page = 0 }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.text))
            if (q.isEmpty()) Text("ابحث في العناوين", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        PTabs(listOf("قائمة", "لوحة"), view, { view = it }, Modifier.padding(vertical = 4.dp))
        if (view == 0) {
            PChipRow { ART_FILTERS.forEach { (k, l) -> val n = all.count { it.matches(k) }; if (k == "all" || k == filter || n > 0) PChip(l, n, filter == k) { filter = k; page = 0 } } }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                if (rows != null && shown.isEmpty()) item { PEmpty("لا مقالات هنا", if (q.isNotBlank()) "جرّب كلمة أخرى" else "ابدأ من «الخط» بزر التوليد؛ تُحفظ كل مقالة فور اكتمالها.") }
                items(shown.drop(page * pageSize).take(pageSize), key = { it.id }) { a -> ArticleLine(a) { detail = a; vm.select(a.id) } }
                item { PPager(page, shown.size, pageSize) { page = it } }
            }
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("ready" to c.ok, "stuck" to c.goldText, "published" to c.gold, "failed" to c.red).forEach { (k, col) ->
                    val list = all.filter { it.matches(k) }
                    Column(Modifier.width(272.dp).clip(R22).background(c.bg2).border(1.dp, c.line, R22).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(col))
                            Text(ART_FILTERS.first { it.first == k }.second, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp), color = c.text)
                            Text(list.size.toString().ltr(), style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                        if (list.isEmpty()) Text("لا شيء", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        list.take(6).forEach { a ->
                            Column(Modifier.fillMaxWidth().clip(R16).background(c.surface).border(1.dp, c.line, R16).clickable { detail = a; vm.select(a.id) }.padding(11.dp)) {
                                Text(a.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.8.sp), color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { QualityTag(a); Text("${(a.words).toString().ltr()} كلمة", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp), color = c.muted) }
                            }
                        }
                        if (list.size > 6) Text("عرض الكل (${(list.size).toString().ltr()})", Modifier.fillMaxWidth().clip(R13).clickable { filter = k; view = 0; page = 0 }.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = c.goldText)
                    }
                }
            }
        }
        if (ready > 0) PCard(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp)) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ready.toString().ltr()} مقالاً جاهزاً للنشر", Modifier.weight(1f).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
                PPill("انشر الجاهز", Icons.Export, { exp.export(); publishing = true })
            }
        } else VSpace(96)
    }

    detail?.let { a -> ArticleDetail(a, vm, base, { detail = null; vm.select(null) }) }
    if (publishing) PublishProgress(exp, { publishing = false }, onPublishScreen)
}

@Composable
internal fun QualityTag(a: ArticleRow) = when {
    a.status == "failed" -> PTag("فشل", Tone.Red)
    a.status == "duplicate" -> PTag("مكرر", Tone.Plain)
    a.quality >= 80 -> PTag("جودة ${(a.quality).toString().ltr()}", Tone.Ok)
    a.quality > 0 -> PTag("جودة ${(a.quality).toString().ltr()}", Tone.Gold)
    else -> PTag("جاهز", Tone.Ok)
}

@Composable
internal fun ArticleLine(a: ArticleRow, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(c.red.copy(alpha = .55f + (a.id % 4) * .1f), c.bg2))))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.2.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when { a.remoteUrl.isNotEmpty() -> PTag("منشور", Tone.Ok); a.status == "exported" -> PTag("متوقف", Tone.Gold); else -> QualityTag(a) }
                Text(if (a.status == "failed") a.error.take(40) else "${(a.words).toString().ltr()} كلمة · ${(a.provider).toString().ltr()}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Box(Modifier.padding(horizontal = 16.dp)) { PDivider() }
}

private val DATE = java.text.SimpleDateFormat("EEEE d MMMM · HH:mm", java.util.Locale("ar"))

@Composable
private fun ArticleDetail(a: ArticleRow, vm: ArticleHubViewModel, base: ArticlesViewModel, onClose: () -> Unit) {
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val shares by vm.selShares.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    val edit by base.edit.collectAsStateWithLifecycle()
    var confirmDel by remember { mutableStateOf(false) }
    val url = a.remoteUrl.takeIf { it.startsWith("http") }
    PSheet(onClose, "") {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(c.red.copy(alpha = .85f), c.bg))), contentAlignment = Alignment.BottomStart) {
            Text(a.title, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when { url != null -> PTag("منشور", Tone.Ok); a.status == "exported" -> PTag("صُدّر ولم يُنشر", Tone.Gold); else -> QualityTag(a) }
            if (a.words > 0) PTag("${(a.words).toString().ltr()} كلمة")
            if (a.quality > 0) PTag("جودة ${(a.quality).toString().ltr()}%", Tone.Gold)
            if (a.provider.isNotBlank()) PTag(a.provider)
        }
        if (a.status == "failed" || (a.status == "ready" && a.error.isNotBlank())) Text(a.error, style = MaterialTheme.typography.bodySmall, color = if (a.status == "failed") c.red else c.goldText)
        if (url != null) {
            PLabel("المشاركة على المنصات")
            if (shares.isEmpty()) Text("لم تُجدول بعد على أي حساب", style = MaterialTheme.typography.bodySmall, color = c.muted)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shares.forEach { j ->
                    Row(Modifier.clip(RoundedCornerShape(11.dp)).background(c.surface).border(1.dp, if (j.status == "done") c.ok.copy(alpha = .5f) else c.line, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(platformIcon(j.platform), null, Modifier.size(14.dp), tint = c.text)
                        Text(when (j.status) { "done" -> "تمت"; "pending" -> "بالانتظار"; "running" -> "يُرسل"; "skipped" -> "تُخطّي"; else -> "تحتاجك" }, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = if (j.status == "done") c.ok else c.text)
                    }
                }
            }
            PLabel("تقييمك")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { i -> Icon(Icons.Star, "$i", Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.rate(i) }.padding(5.dp), tint = if ((review?.rating ?: 0) >= i) c.gold else c.line) }
            }
            PCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                PRow("نُشر", DATE.format(java.util.Date(a.updatedAt)), chevron = false)
                PDivider()
                PRow("الرابط", url.removePrefix("https://").ltr(), { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, trail = { Icon(Icons.Link, null, Modifier.size(16.dp), tint = c.muted) }, chevron = false)
            }
        }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = c.goldText)
        VSpace(12)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (url != null) { PGhost("فتح المقال", Icons.Eye, { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, Modifier.weight(1f)); PGhost("شارك الآن", Icons.Share, { vm.shareNow() }, Modifier.weight(1f)) }
            else if (a.status == "failed" || a.status == "duplicate") PGhost(if (a.status == "duplicate") "ولّد رغم التكرار" else "أعد التوليد", Icons.Refresh, { base.regenerate(a.id); onClose() }, Modifier.weight(1f))
        }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PGhost("تعديل", Icons.Edit, { base.openEdit(a.id) }, Modifier.weight(1f), enabled = a.status == "ready" || a.status == "exported")
            PGhost(if (confirmDel) "تأكيد الحذف" else "حذف", Icons.Trash, { if (confirmDel) { base.delete(a.id); onClose() } else confirmDel = true }, Modifier.weight(1f), color = c.red)
        }
    }
    edit?.let { (id, init) ->
        var values by remember(id) { mutableStateOf(init) }
        var errors by remember(id) { mutableStateOf(emptyMap<String, String>()) }
        PSheet(base::closeEdit, "تعديل المقال") {
            VSpace(8)
            SchemaForm(Schemas.articleEdit, values, errors) { k, v -> values = values + (k to v) }
            VSpace(14)
            PCta("حفظ", Icons.Check, { errors = SchemaRules.validate(Schemas.articleEdit, values); if (errors.isEmpty()) base.saveEdit(id, values) })
        }
    }
}

/** تقدم «انشر الجاهز»: مراحل التصدير ثم النشر على بلوجر. */
@Composable
private fun PublishProgress(exp: ExportViewModel, onClose: () -> Unit, onPublishScreen: () -> Unit) {
    val c = AppTheme.colors
    val ui by exp.ui.collectAsStateWithLifecycle()
    val pub by exp.pub.collectAsStateWithLifecycle()
    PSheet(onClose, "نشر الجاهز", "يكمل في الخلفية مع إشعار؛ يمكنك إغلاق هذه النافذة") {
        val p = when { pub.running && pub.total > 0 -> pub.done.toFloat() / pub.total; ui.stageCount > 0 -> ui.stageIdx.toFloat() / ui.stageCount; else -> 0f }
        PCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PRing(p, 72.dp, 6.dp, c.gold, if (pub.running) "${pub.done}/${pub.total}" else "${(p * 100).toInt()}%")
                Column {
                    Text(when { pub.running -> "النشر على بلوجر"; ui.running -> ui.stage.ifBlank { "يحضّر" }; ui.error != null -> "تعذّر"; pub.report != null || ui.summary != null -> "اكتمل"; else -> "يبدأ…" }, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(ui.error ?: pub.report?.fatal ?: ui.summary?.let { "${(it.articles).toString().ltr()} مقال · ${(it.images).toString().ltr()} صورة" } ?: "", style = MaterialTheme.typography.bodySmall, color = if (ui.error != null || pub.report?.fatal != null) c.red else c.muted)
                }
            }
        }
        val stages = listOf("رسم الصور", "رفع الصور", "بناء XML", "النشر على بلوجر", "جدولة المشاركة")
        PCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            stages.forEachIndexed { i, st ->
                if (i > 0) PDivider()
                val doneStage = i < 3 && (ui.stageIdx > i + 1 || ui.summary != null) || i == 3 && pub.report != null && !pub.running || i == 4 && pub.report?.fatal == null && pub.report != null && !pub.running
                val now = !doneStage && (i < 3 && ui.running && ui.stageIdx == i + 1 || i == 3 && pub.running)
                PRow(st, "", trail = { PRing(if (doneStage) 1f else if (now) .5f else 0f, 28.dp, 3.dp, if (doneStage) c.ok else c.gold) }, chevron = false)
            }
        }
        if (ui.error != null || pub.report?.fatal != null) { VSpace(12); PGhost("افتح النشر والملفات", Icons.Export, { onClose(); onPublishScreen() }, Modifier.fillMaxWidth()) }
    }
}
