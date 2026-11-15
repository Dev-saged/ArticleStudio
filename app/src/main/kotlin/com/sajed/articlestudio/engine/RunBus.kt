package com.sajed.articlestudio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class LogLine(val t: String, val m: String, val k: String)
class PassEvent(val id: Int, val from: String, val to: String)

/** قناة الأحداث بين المحرك والواجهة: السجل، المزود العامل، تمريرات الكرة. */
@Singleton
class RunBus @Inject constructor() {
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()
    private val _pass = MutableStateFlow<PassEvent?>(null)
    val pass: StateFlow<PassEvent?> = _pass.asStateFlow()
    private val _since = MutableStateFlow(0L)
    val activeSince: StateFlow<Long> = _since.asStateFlow()
    private var seq = 0
    private var ball: String? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(m: String, k: String = "") {
        _logs.update { (it + LogLine(fmt.format(Date()), m, k)).takeLast(150) }
    }

    @Synchronized fun setActive(id: String?) {
        if (id != null) {
            if (id != ball) _pass.value = PassEvent(++seq, ball ?: id, id)
            ball = id
            _since.value = System.currentTimeMillis()
        }
        _active.value = id
    }

    fun clearLogs() { _logs.value = emptyList() }
}
