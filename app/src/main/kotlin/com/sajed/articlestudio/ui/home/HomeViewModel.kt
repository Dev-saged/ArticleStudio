package com.sajed.articlestudio.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.ui.LiveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class NodeState { Idle, Active, Cooling }

data class NodeUi(val name: String, val state: NodeState = NodeState.Idle, val coolSec: Int = 0, val progress: Float = 0f)
data class PassEvent(val id: Int, val from: Int, val to: Int, val durationMs: Int)
data class RowUi(val n: Int, val title: String, val words: Int, val provider: String)

data class HomeUiState(
    val total: Int = 20,
    val done: Int = 0,
    val running: Boolean = false,
    val nodes: List<NodeUi> = NAMES.map { NodeUi(it) },
    val ballAt: Int = 0,
    val pass: PassEvent? = null,
    val rows: List<RowUi> = emptyList(),
    val wordsSum: Int = 0,
) {
    val avgWords get() = if (done == 0) 0 else wordsSum / done
    val etaMin get() = ((total - done) * 3 + 1) / 2
    val progress get() = if (total == 0) 0f else done.toFloat() / total
}

private val NAMES = listOf("Cerebras", "Groq", "Gemini", "Mistral", "OpenRouter", "Pollinations")
private val SAMPLE_TITLES = listOf(
    "سكالوني يجدد عقده مع الأرجنتين حتى مونديال 2030", "فنسنت كومباني يعود لتدريبات بايرن ميونخ",
    "كيف تقرأ جدول الترتيب في الدوريات الكبرى", "قاعدة التسلل وكيف تُحتسب",
    "الضغط العالي في كرة القدم", "تقنية الفار: كيف تعمل ومتى تتدخل",
    "دليل المشجع لمتابعة الدوريات الأوروبية", "كيف تُختار مدن استضافة كأس العالم؟",
)

/** وضع المعاينة: يحاكي توزيع العمل وتبديل المزودين ببيانات تجريبية إلى أن يُربط المحرك الحقيقي. */
@HiltViewModel
class HomeViewModel @Inject constructor(private val live: LiveStatus) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private var run: Job? = null
    private var passId = 0
    private val coolUntil = LongArray(NAMES.size)

    init {
        viewModelScope.launch {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                _ui.update { s ->
                    s.copy(nodes = s.nodes.mapIndexed { i, n ->
                        if (n.state != NodeState.Cooling) n
                        else if (coolUntil[i] <= now) n.copy(state = NodeState.Idle, coolSec = 0)
                        else n.copy(coolSec = ((coolUntil[i] - now + 999) / 1000).toInt())
                    })
                }
            }
        }
    }

    fun setTotal(n: Int) = _ui.update { it.copy(total = n, done = minOf(it.done, n)) }

    fun toggle() {
        if (run?.isActive == true) { run?.cancel(); stopped("متوقف مؤقتاً"); return }
        if (_ui.value.done >= _ui.value.total) _ui.update { it.copy(done = 0, wordsSum = 0, rows = emptyList()) }
        _ui.update { it.copy(running = true) }
        run = viewModelScope.launch { loop() }
    }

    private fun stopped(msg: String) {
        _ui.update { s -> s.copy(running = false, nodes = s.nodes.map { if (it.state == NodeState.Active) it.copy(state = NodeState.Idle, progress = 0f) else it }) }
        live.set(msg)
    }

    private fun nextFree(from: Int): Int {
        val now = System.currentTimeMillis()
        for (k in 1..NAMES.size) { val j = (from + k) % NAMES.size; if (coolUntil[j] <= now) return j }
        return (from + 1) % NAMES.size
    }

    private suspend fun pass(to: Int, ms: Int) {
        val from = _ui.value.ballAt
        _ui.update { it.copy(pass = PassEvent(++passId, from, to, ms)) }
        delay(ms + 700L)
        _ui.update { it.copy(ballAt = to) }
    }

    private suspend fun loop() {
        while (_ui.value.done < _ui.value.total) {
            val cur = _ui.value.ballAt
            if (_ui.value.done > 8 && Random.nextFloat() < 0.22f) {
                coolUntil[cur] = System.currentTimeMillis() + 14_000
                _ui.update { s -> s.copy(nodes = s.nodes.mapIndexed { i, n -> if (i == cur) n.copy(state = NodeState.Cooling, coolSec = 14) else n }) }
                live.set("${NAMES[cur]}: الحد مستهلك، تُمرَّر الكرة تلقائياً")
                pass(nextFree(cur), 650)
                continue
            }
            val title = SAMPLE_TITLES[_ui.value.done % SAMPLE_TITLES.size]
            live.set("${NAMES[cur]} يكتب الآن: $title")
            val steps = Random.nextInt(35, 50)
            for (k in 1..steps) {
                _ui.update { s -> s.copy(nodes = s.nodes.mapIndexed { i, n -> if (i == cur) n.copy(state = NodeState.Active, progress = k / steps.toFloat()) else n }) }
                delay(60)
            }
            val words = Random.nextInt(690, 810)
            _ui.update { s ->
                val d = s.done + 1
                s.copy(
                    done = d, wordsSum = s.wordsSum + words,
                    nodes = s.nodes.mapIndexed { i, n -> if (i == cur) n.copy(state = NodeState.Idle, progress = 0f) else n },
                    rows = (listOf(RowUi(d, title, words, NAMES[cur])) + s.rows).take(4),
                )
            }
            if (_ui.value.done >= _ui.value.total) break
            val nx = nextFree(cur)
            live.set("تمريرة إلى ${NAMES[nx]}")
            pass(nx, 950)
        }
        _ui.update { it.copy(running = false) }
        live.set("اكتمل التوليد: ${_ui.value.total} مقالاً جاهزاً للتصدير")
    }

    override fun onCleared() { run?.cancel() }
}
