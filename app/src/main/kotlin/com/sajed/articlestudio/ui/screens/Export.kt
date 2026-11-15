package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(dao: ArticleDao) : ViewModel() {
    val ready: StateFlow<Int> = dao.observeAll().map { l -> l.count { it.status == "ready" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun ExportScreen(vm: ExportViewModel = hiltViewModel()) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("التصدير", style = MaterialTheme.typography.headlineLarge, color = c.text)
        Panel {
            Column {
                Text(ready.toString().ltr(), style = NumberStyle, color = c.goldText)
                Text("مقالات جاهزة للتصدير", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        if (ready == 0) EmptyState("لا شيء للتصدير", "عند اكتمال مقالات ستظهر هنا حزمة XML جاهزة لاستيراد بلوجر.")
    }
}
