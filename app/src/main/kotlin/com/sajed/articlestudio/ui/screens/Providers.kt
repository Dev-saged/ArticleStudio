package com.sajed.articlestudio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.HealthStore
import com.sajed.articlestudio.data.ProviderCatalog
import com.sajed.articlestudio.data.ProviderSpec
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.SecretException
import com.sajed.articlestudio.engine.ProviderEngine
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderUi(
    val spec: ProviderSpec, val enabled: Boolean, val hasKey: Boolean, val model: String, val base: String,
    val ok: Int, val fail: Int, val last: String, val testing: Boolean = false, val testMsg: String = "",
) { val ready get() = enabled && (!spec.needsKey || hasKey) }

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val store: ProviderStore, private val engine: ProviderEngine, private val health: HealthStore,
) : ViewModel() {
    private val _list = MutableStateFlow(load(emptyMap()))
    val list: StateFlow<List<ProviderUi>> = _list.asStateFlow()
    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private fun load(msgs: Map<String, Pair<Boolean, String>>) = ProviderCatalog.all.map { s ->
        val (ok, fail) = health.totals(s.id)
        val m = msgs[s.id]
        ProviderUi(s, store.enabled(s.id), store.hasKey(s.id), store.model(s.id), store.base(s.id), ok, fail, engine.stat(s.id).last, m?.first ?: false, m?.second.orEmpty())
    }

    private fun reload() = _list.update { old -> load(old.associate { it.spec.id to (it.testing to it.testMsg) }) }

    fun save(id: String, v: Map<String, String>) {
        val custom = id == "custom"
        try {
            store.save(id, if (custom) SchemaRules.sanitize(v["model"].orEmpty(), 120) else "", if (custom) SchemaRules.sanitize(v["base"].orEmpty(), 200).trimEnd('/') else "",
                SchemaRules.sanitize(v["key"].orEmpty(), 300), v["enabled"] == "true")
        } catch (e: SecretException) { _note.value = e.message.orEmpty() }
        engine.resetCooldown(id)
        reload()
    }

    fun clearKey(id: String) { store.clearKey(id); reload() }

    fun test(id: String) {
        val s = ProviderCatalog.spec(id)
        _list.update { l -> l.map { if (it.spec.id == id) it.copy(testing = true, testMsg = "جارٍ الاختبار…") else it } }
        viewModelScope.launch {
            val r = engine.test(s)
            _list.update { l -> l.map { if (it.spec.id == id) it.copy(testing = false, testMsg = r, last = engine.stat(id).last) else it } }
        }
    }

    fun exportKeys() = store.exportKeys()
    fun importKeys(text: String) { val n = try { store.importKeys(text) } catch (e: SecretException) { _note.value = e.message.orEmpty(); reload(); return }; _note.value = if (n > 0) "استُورد $n مفتاحاً" else "لا مفاتيح صالحة في الحافظة"; reload() }
    fun clearNote() { _note.value = "" }
}

private fun open(c: Context, url: String) = runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: ProvidersViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<ProviderUi?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("t") { Text("المزودون", style = MaterialTheme.typography.headlineLarge, color = c.text) }
        item("h") { Text("المفاتيح مشفّرة على الجهاز ولا تغادره إلا نحو المزود نفسه. إن تعثّر مزود ينتقل التوليد تلقائياً إلى التالي.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(list, key = { it.spec.id }) { p ->
            Panel(Modifier.fillMaxWidth().clickable { editing = p }) {
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(10.dp).background(if (!p.enabled) c.line else if (p.ready) c.ok else c.red, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(p.spec.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                            Text(p.spec.hint, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2)
                        }
                        Text(when { !p.enabled -> "متوقف"; !p.spec.needsKey -> "بلا مفتاح"; p.hasKey -> "مفتاح محفوظ"; else -> "يحتاج مفتاحاً" }, style = MaterialTheme.typography.labelSmall, color = c.muted)
                        Icon(Icons.Edit, "تعديل", Modifier.size(20.dp), tint = c.muted)
                    }
                    if (p.ok + p.fail > 0 || p.last.isNotEmpty() || p.testMsg.isNotEmpty()) {
                        Text(
                            buildList {
                                if (p.ok + p.fail > 0) add("نجاح ${p.ok.toString().ltr()} · إخفاق ${p.fail.toString().ltr()}")
                                if (p.testMsg.isNotEmpty()) add(p.testMsg) else if (p.last.isNotEmpty()) add(p.last)
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = if (p.last.isNotEmpty() && p.testMsg.isEmpty()) c.red else c.muted,
                        )
                    }
                }
            }
        }
        item("tools") {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolRow("نسخ المفاتيح إلى الحافظة", Icons.Copy) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("keys", vm.exportKeys()))
                    }
                    ToolRow("استيراد المفاتيح من الحافظة", Icons.Plus) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        vm.importKeys(cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty())
                    }
                    ToolRow("استثناء التطبيق من توفير البطارية", Icons.Settings) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = c.goldText, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    editing?.let { p ->
        val custom = p.spec.id == "custom"
        val schema = if (custom) Schemas.providerCustom else Schemas.provider
        var values by remember(p.spec.id) { mutableStateOf(mapOf("base" to p.base, "model" to p.model, "key" to "", "enabled" to p.enabled.toString())) }
        var errors by remember(p.spec.id) { mutableStateOf(emptyMap<String, String>()) }
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = c.bg2) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(p.spec.name, style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(p.spec.hint, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                if (p.spec.link.isNotEmpty()) Row(Modifier.clickable { open(ctx, p.spec.link) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Link, null, Modifier.size(20.dp), tint = c.goldText)
                    Text("احصل على مفتاح مجاني", style = MaterialTheme.typography.labelLarge, color = c.goldText)
                }
                SchemaForm(schema, values, errors) { k, v -> values = values + (k to v) }
                if (p.hasKey) Text("يوجد مفتاح محفوظ؛ اتركه فارغاً للإبقاء عليه.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                PrimaryButton("حفظ", Icons.Check, {
                    errors = SchemaRules.validate(schema, values)
                    if (errors.isEmpty()) { vm.save(p.spec.id, values); editing = null }
                })
                if (p.spec.needsKey || p.enabled) Text(
                    if (p.testing) "جارٍ الاختبار…" else "اختبار الاتصال", style = MaterialTheme.typography.labelLarge, color = c.goldText, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !p.testing) { vm.test(p.spec.id) }.padding(vertical = 12.dp),
                )
                if (p.hasKey) Text("حذف المفتاح", style = MaterialTheme.typography.labelLarge, color = c.red, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { vm.clearKey(p.spec.id); editing = null }.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().defaultMin().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = c.goldText)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}

private fun Modifier.defaultMin() = this.then(Modifier.padding(vertical = 12.dp))
