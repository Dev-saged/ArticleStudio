package com.sajed.articlestudio.ui

import android.content.Context
import com.sajed.articlestudio.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPrefs @Inject constructor(@ApplicationContext c: Context) {
    private val sp = c.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.System),
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(m: ThemeMode) {
        sp.edit().putString("theme", m.name).apply()
        _mode.value = m
    }
}

/** حالة الشريط الحي العام في أعلى كل الشاشات؛ ستغذّيه الخدمة الأمامية لاحقاً. */
@Singleton
class LiveStatus @Inject constructor() {
    private val _text = MutableStateFlow("جاهز للبدء")
    val text: StateFlow<String> = _text.asStateFlow()
    fun set(t: String) { _text.value = t }
}
