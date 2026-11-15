package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.engine.Article
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.JobRunner
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr
import com.sajed.articlestudio.engine.GenerationService
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArticleItem(val id: Long, val title: String, val status: String, val words: Int, val provider: String, val quality: Int, val error: String, val remote: Boolean = false)

@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val dao: ArticleDao, private val runner: JobRunner, private val sites: com.sajed.articlestudio.engine.SiteStore, @ApplicationContext private val ctx: Context,
) : ViewModel() {
    val items: StateFlow<List<ArticleItem>?> = sites.activeId.flatMapLatest { sid ->
        dao.observeRows().map { l -> l.filter { it.siteId == sid }.map { e -> ArticleItem(e.id, e.title, e.status, e.words, e.provider, e.quality, e.error, e.remoteUrl.isNotEmpty()) } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (id in dao.needQuality()) {
                val e = dao.byId(id) ?: continue
                val q = runCatching { AppJson.decodeFromString(Article.serializer(), e.data).quality }.getOrDefault(0)
                dao.setQuality(id, q.coerceAtLeast(1))
            }
        }
    }

    private val _edit = MutableStateFlow<Pair<Long, Map<String, String>>?>(null)
    val edit: StateFlow<Pair<Long, Map<String, String>>?> = _edit.asStateFlow()

    fun openEdit(id: Long) = viewModelScope.launch {
        val e = dao.byId(id) ?: return@launch
        if (e.status == "failed") return@launch
        val a = withContext(Dispatchers.Default) { AppJson.decodeFromString(Article.serializer(), e.data) }
        _edit.value = id to mapOf("title" to a.title, "desc" to a.desc, "labels" to a.labels.joinToString("، "))
    }

    fun closeEdit() { _edit.value = null }

    fun saveEdit(id: Long, v: Map<String, String>) = viewModelScope.launch {
        val e = dao.byId(id) ?: return@launch
        val a = AppJson.decodeFromString(Article.serializer(), e.data)
        val labels = v["labels"].orEmpty().split('،', ',').map { it.trim() }.filter { it.isNotEmpty() }.take(6)
        val n = a.copy(title = SchemaRules.sanitize(v["title"].orEmpty(), 120), desc = SchemaRules.sanitize(v["desc"].orEmpty(), 200), labels = labels.ifEmpty { a.labels })
        dao.upsert(e.copy(title = n.title, labels = n.labels.joinToString(","), data = AppJson.encodeToString(Article.serializer(), n), updatedAt = System.currentTimeMillis()))
        _edit.value = null
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
    fun clearExported() = viewModelScope.launch { dao.clearExported() }
    fun regenerate(id: Long) { runner.regenerate(id); GenerationService.start(ctx) }
}

private fun statusLabel(s: String) = when (s) { "ready" -> "جاهزة"; "failed" -> "فاشلة"; "exported" -> "مُصدَّرة"; else -> s }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(vm: ArticlesViewModel = hiltViewModel()) {
    val rows by vm.items.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var filter by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    val all = rows.orEmpty()
    val shown = if (filter == null) all else all.filter { it.status == filter }
    val ready = all.count { it.status == "ready" }
    val avg = all.filter { it.words > 0 }.let { l -> if (l.isEmpty()) 0 else l.sumOf { it.words } / l.size }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("t") { Text("المقالات", style = MaterialTheme.typography.headlineLarge, color = c.text) }
        item("k") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel(Modifier.weight(1f)) { Column { Text(ready.toString().ltr(), style = NumberStyle, color = c.goldText); Text("جاهزة", style = MaterialTheme.typography.bodySmall, color = c.muted) } }
                Panel(Modifier.weight(1f)) { Column { Text(if (avg == 0) "—" else avg.toString().ltr(), style = NumberStyle, color = c.goldText); Text("متوسط الكلمات", style = MaterialTheme.typography.bodySmall, color = c.muted) } }
            }
        }
        item("f") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("الكل", filter == null, { filter = null })
                AppChip("جاهزة", filter == "ready", { filter = "ready" })
                AppChip("فاشلة", filter == "failed", { filter = "failed" })
                AppChip("مُصدَّرة", filter == "exported", { filter = "exported" })
            }
        }
        if (rows != null && shown.isEmpty()) item("empty") { EmptyState("لا مقالات هنا", "ابدأ من تبويب التوليد؛ تُحفظ كل مقالة فور اكتمالها.") }
        items(shown, key = { it.id }) { a ->
            Panel(Modifier.fillMaxWidth().clickable(enabled = a.status != "failed") { vm.openEdit(a.id) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tone = when { a.status == "failed" -> c.red; a.quality >= 80 -> c.ok; a.quality >= 60 -> c.goldText; else -> c.muted }
                        Text(
                            if (a.status == "failed") a.error.ifBlank { "فشل" } else (if (a.remote) "منشور على بلوجر · " else "") + "${statusLabel(a.status)} · ${a.words.toString().ltr()} كلمة · ${a.quality.toString().ltr()}/100 · ${a.provider}",
                            style = MaterialTheme.typography.bodySmall, color = tone, modifier = Modifier.weight(1f),
                        )
                        if (a.status != "exported") IconBtn(Icons.Refresh, "إعادة توليد", { vm.regenerate(a.id) })
                        IconBtn(Icons.Trash, "حذف", { confirmDelete = a.id })
                    }
                    if (a.status != "failed" && a.error.isNotBlank()) Text("تنبيه: ${a.error}", style = MaterialTheme.typography.labelMedium, color = c.goldText)
                }
            }
        }
        if (all.any { it.status == "exported" }) item("clr") {
            Text("حذف كل المُصدَّرة", style = MaterialTheme.typography.labelLarge, color = c.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.clearExported() }.padding(vertical = 14.dp))
        }
    }

    confirmDelete?.let { id ->
        ModalBottomSheet(onDismissRequest = { confirmDelete = null }, containerColor = c.bg2) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حذف هذا المقال نهائياً؟", style = MaterialTheme.typography.titleMedium, color = c.text)
                PrimaryButton("حذف", Icons.Trash, { vm.delete(id); confirmDelete = null })
                Text("إلغاء", style = MaterialTheme.typography.labelLarge, color = c.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { confirmDelete = null }.padding(vertical = 12.dp))
            }
        }
    }

    edit?.let { (id, init) ->
        var values by remember(id) { mutableStateOf(init) }
        var errors by remember(id) { mutableStateOf(emptyMap<String, String>()) }
        ModalBottomSheet(onDismissRequest = vm::closeEdit, containerColor = c.bg2) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("تعديل المقال", style = MaterialTheme.typography.titleLarge, color = c.text)
                SchemaForm(Schemas.articleEdit, values, errors) { k, v -> values = values + (k to v) }
                PrimaryButton("حفظ", Icons.Check, {
                    errors = SchemaRules.validate(Schemas.articleEdit, values)
                    if (errors.isEmpty()) vm.saveEdit(id, values)
                })
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(Icons.Document, null, tint = c.muted, modifier = Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
    }
}
