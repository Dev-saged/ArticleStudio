package com.sajed.articlestudio.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.data.ProviderCatalog
import com.sajed.articlestudio.data.ProviderInfo
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.schema.SchemaForm
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class ProviderUi(val info: ProviderInfo, val model: String, val hasKey: Boolean, val enabled: Boolean)

@HiltViewModel
class ProvidersViewModel @Inject constructor(private val store: ProviderStore) : ViewModel() {
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<ProviderUi>> = _list.asStateFlow()

    private fun load() = ProviderCatalog.all.map { ProviderUi(it, store.model(it.id, it.defaultModel), store.hasKey(it.id), store.enabled(it.id)) }

    fun save(id: String, model: String, key: String, enabled: Boolean) {
        val info = ProviderCatalog.all.first { it.id == id }
        store.save(id, SchemaRules.sanitize(model).ifEmpty { info.defaultModel }, SchemaRules.sanitize(key, 300), enabled)
        _list.value = load()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: ProvidersViewModel = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var editing by remember { mutableStateOf<ProviderUi?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("t") { Text("المزودون", style = MaterialTheme.typography.headlineLarge, color = c.text) }
        item("h") { Text("المفاتيح مشفّرة على الجهاز ولا تغادره إلا نحو المزود نفسه.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(list, key = { it.info.id }) { p ->
            Panel(Modifier.fillMaxWidth().clickable { editing = p }) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(10.dp).background(if (!p.enabled) c.line else if (p.hasKey || p.info.keyless) c.ok else c.red, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(p.info.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(p.model, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                    }
                    Text(
                        when { !p.enabled -> "متوقف"; p.info.keyless -> "بلا مفتاح"; p.hasKey -> "مفتاح محفوظ"; else -> "يحتاج مفتاحاً" },
                        style = MaterialTheme.typography.labelSmall, color = c.muted,
                    )
                    Icon(Icons.Edit, "تعديل", Modifier.size(20.dp), tint = c.muted)
                }
            }
        }
    }

    editing?.let { p ->
        var values by remember(p.info.id) { mutableStateOf(mapOf("name" to p.info.name, "model" to p.model, "key" to "", "enabled" to p.enabled.toString())) }
        var errors by remember(p.info.id) { mutableStateOf(emptyMap<String, String>()) }
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = c.bg2) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(p.info.name, style = MaterialTheme.typography.titleLarge, color = c.text)
                SchemaForm(Schemas.provider, values, errors) { k, v -> if (k != "name") values = values + (k to v) }
                if (p.hasKey) Text("يوجد مفتاح محفوظ؛ اتركه فارغاً للإبقاء عليه.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                PrimaryButton("حفظ", Icons.Check, {
                    errors = SchemaRules.validate(Schemas.provider, values)
                    if (errors.isEmpty()) { vm.save(p.info.id, values["model"].orEmpty(), values["key"].orEmpty(), values["enabled"] == "true"); editing = null }
                })
            }
        }
    }
}
