package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.schema.KpiRow
import com.sajed.articlestudio.schema.Row
import com.sajed.articlestudio.schema.SchemaRow
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArticlesViewModel @Inject constructor(dao: ArticleDao) : ViewModel() {
    val rows: StateFlow<List<Row>?> = dao.observeAll()
        .map { l -> l.map { mapOf("id" to it.id.toString(), "title" to it.title, "words" to it.words.toString(), "provider" to it.provider, "status" to it.status) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ArticlesScreen(vm: ArticlesViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val schema = Schemas.article
    var filter by remember { mutableStateOf<String?>(null) }
    val all = rows.orEmpty()
    val shown = if (filter == null) all else all.filter { it[schema.filterKey!!] == filter }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("t") { Text("المقالات", style = MaterialTheme.typography.headlineLarge, color = c.text) }
        item("k") { KpiRow(schema, all) }
        item("f") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("الكل", filter == null, { filter = null })
                AppChip("جاهزة", filter == "ready", { filter = "ready" })
                AppChip("فاشلة", filter == "failed", { filter = "failed" })
            }
        }
        if (rows != null && shown.isEmpty()) item("empty") { EmptyState("لا مقالات بعد", "ابدأ من تبويب التوليد وستُحفظ كل مقالة فور اكتمالها.") }
        items(shown, key = { it["id"]!! }) { r ->
            Column { SchemaRow(schema, r); HorizontalDivider(color = c.line) }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(Icons.Document, null, tint = c.muted, modifier = Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
