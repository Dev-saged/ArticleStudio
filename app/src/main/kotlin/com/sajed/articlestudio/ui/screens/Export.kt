package com.sajed.articlestudio.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.clickable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.GitHubSettings
import com.sajed.articlestudio.data.SecretException
import com.sajed.articlestudio.engine.ExportManager
import com.sajed.articlestudio.engine.ExportUi
import com.sajed.articlestudio.engine.clamp
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(dao: ArticleDao, private val mgr: ExportManager, private val cfg: GitHubSettings) : ViewModel() {
    val ready: StateFlow<Int> = dao.observeAll().map { l -> l.count { it.status == "ready" } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val ui: StateFlow<ExportUi> = mgr.ui

    private val _form = MutableStateFlow(read())
    val form: StateFlow<Map<String, String>> = _form.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val _check = MutableStateFlow<List<Pair<Boolean, String>>>(emptyList())
    val check: StateFlow<List<Pair<Boolean, String>>> = _check.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun read() = mapOf("repo" to cfg.repo, "branch" to cfg.branch, "token" to "", "sched" to cfg.scheduleHours.toString(), "auto" to cfg.autoPush.toString())

    val hasToken get() = cfg.token.isNotEmpty()

    fun change(k: String, v: String) { _form.value = _form.value + (k to v) }

    private fun persist(): Boolean {
        val f = _form.value
        val e = SchemaRules.validate(Schemas.github, f)
        _errors.value = e
        if (e.isNotEmpty()) return false
        cfg.repo = f["repo"].orEmpty(); cfg.branch = f["branch"].orEmpty(); cfg.autoPush = f["auto"] == "true"
        cfg.scheduleHours = f["sched"]?.toIntOrNull() ?: 0
        try { f["token"]?.takeIf { it.isNotBlank() }?.let { cfg.token = it; change("token", "") } }
        catch (e: SecretException) { _check.value = listOf(false to e.message.orEmpty()); return false }
        return true
    }

    fun checkToken() {
        if (!persist() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _check.value = try { mgr.check().map { true to it } } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { listOf(false to clamp(e.message, 200)) }
            _busy.value = false
        }
    }

    fun export() { if (persist() && !_busy.value) viewModelScope.launch { _busy.value = true; mgr.export(); _busy.value = false } }
    fun share(c: Context) { mgr.shareIntent()?.let { runCatching { c.startActivity(it) } } }
}

@Composable
fun ExportScreen(vm: ExportViewModel = hiltViewModel()) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("t") { Text("التصدير", style = MaterialTheme.typography.headlineLarge, color = c.text) }
        item("k") {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text(ready.toString().ltr(), style = NumberStyle, color = c.goldText)
                    Text("مقالات جاهزة للتصدير", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        if (ready == 0 && ui.steps.isEmpty()) item("empty") { EmptyState("لا شيء للتصدير", "عند اكتمال مقالات تظهر هنا حزمة XML جاهزة لاستيراد بلوجر.") }
        item("go") {
            PrimaryButton(if (ui.running) "جارٍ التصدير…" else "صدّر XML الآن", Icons.Export, vm::export, enabled = ready > 0 && !ui.running && !busy)
        }
        if (ui.steps.isNotEmpty()) items(ui.steps.size, key = { "s$it" }) { i ->
            val s = ui.steps[i]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(if (s.state == 'x') Icons.Close else if (s.state == 'd') Icons.Check else Icons.Clock, null, Modifier.size(20.dp), tint = if (s.state == 'x') c.red else if (s.state == 'd') c.ok else c.goldText)
                Text(s.text, color = if (s.state == 'x') c.red else c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        if (ui.lastName != null && !ui.running) item("share") {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("آخر ملف: ${ui.lastName}".ltr(), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("محفوظ أيضاً في التنزيلات ← ArticleStudio. في بلوجر: الإعدادات ← استيراد المحتوى.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Row(Modifier.clickable { vm.share(ctx) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(Icons.Export, null, tint = c.goldText)
                        Text("مشاركة / فتح الملف", style = MaterialTheme.typography.labelLarge, color = c.goldText)
                    }
                }
            }
        }
        item("gh") {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { settings = !settings }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Icon(Icons.Settings, null, tint = c.goldText)
                        Text("إعدادات GitHub والنشر", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                    }
                    if (settings) {
                        SchemaForm(Schemas.github, form, errors) { k, v -> vm.change(k, v) }
                        if (vm.hasToken) Text("يوجد توكن محفوظ؛ اتركه فارغاً للإبقاء عليه.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Text(if (busy) "جارٍ الفحص…" else "فحص التوكن والصلاحيات", style = MaterialTheme.typography.labelLarge, color = c.goldText,
                            modifier = Modifier.clickable(enabled = !busy) { vm.checkToken() }.padding(vertical = 10.dp))
                        check.forEach { (ok, m) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Icon(if (ok) Icons.Check else Icons.Close, null, Modifier.size(18.dp), tint = if (ok) c.ok else c.red)
                                Text(m, style = MaterialTheme.typography.bodySmall, color = if (ok) c.ok else c.red, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
