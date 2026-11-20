package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.engine.Article
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.JobRunner
import com.sajed.articlestudio.schema.SchemaRules
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
