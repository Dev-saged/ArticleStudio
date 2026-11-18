package com.sajed.articlestudio.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ArticleEntity
import com.sajed.articlestudio.data.ProviderCatalog
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.BrandConfig
import com.sajed.articlestudio.BuildConfig
import com.sajed.articlestudio.engine.GenerationService
import com.sajed.articlestudio.engine.GitHubClient
import com.sajed.articlestudio.engine.JobRunner
import com.sajed.articlestudio.engine.LogLine
import com.sajed.articlestudio.engine.PlanMode
import com.sajed.articlestudio.engine.ProviderEngine
import com.sajed.articlestudio.engine.RunBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NodeState { Idle, Active, Cooling, NoKey }

data class NodeUi(val name: String, val state: NodeState = NodeState.Idle, val coolSec: Int = 0, val progress: Float = 0f)
data class BallPass(val id: Int, val from: Int, val to: Int, val durationMs: Int)
data class RowUi(val n: Int, val title: String, val words: Int, val provider: String)

data class SiteCardUi(
    val id: String, val name: String, val topic: String, val host: String, val running: Boolean, val resumable: Boolean,
    val done: Int, val total: Int, val failed: Int, val phase: String, val ready: Int, val stuck: Int, val published: Int, val active: Boolean,
) { val progress get() = if (total == 0) 0f else (done + failed).toFloat() / total }

data class HomeUiState(
    val room: Boolean = false,
    val cards: List<SiteCardUi> = emptyList(),
    val total: Int = 10,
    val mode: PlanMode = PlanMode.News,
    val custom: String = "",
    val running: Boolean = false,
    val resumable: Boolean = false,
    val done: Int = 0,
    val failed: Int = 0,
    val dup: Int = 0,
    val jobTotal: Int = 0,
    val nodes: List<NodeUi> = emptyList(),
    val parallel: Int = 3,
    val provReady: Int = 0,
    val provCooling: Int = 0,
    val ballAt: Int = 0,
    val pass: BallPass? = null,
    val rows: List<RowUi> = emptyList(),
    val avgWords: Int = 0,
    val notice: String = "",
    val logs: List<LogLine> = emptyList(),
    val phase: String = "",
    val blogCount: Int = 0,
    val siteName: String = "",
    val siteNiche: String = "sport",
    val siteCount: Int = 1,
) {
    val shownTotal get() = if (jobTotal > 0) jobTotal else total
    val etaMin get() = ((shownTotal - done - failed - dup).coerceAtLeast(0) * 2)
    val progress get() = if (shownTotal == 0) 0f else (done + failed + dup).toFloat() / shownTotal
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runner: JobRunner, private val bus: RunBus, private val engine: ProviderEngine,
    private val store: ProviderStore, private val gh: GitHubClient, private val blog: com.sajed.articlestudio.engine.BlogIndex, val updater: com.sajed.articlestudio.engine.Updater, private val gen: com.sajed.articlestudio.data.GenSettings, val sites: com.sajed.articlestudio.engine.SiteStore, arts: ArticleDao, @ApplicationContext private val ctx: Context,
) : ViewModel() {
    private val customBy = HashMap<String, String>()
    private val inputs = MutableStateFlow(Triple(10, PlanMode.News, "") to "")
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    @Volatile private var articles: List<com.sajed.articlestudio.data.ArticleRow> = emptyList()
    private var ballAt = 0
    private var lastPassId = 0
    private var currentPass: BallPass? = null
    private val _room = MutableStateFlow(false)

    init {
        updater.check(auto = true)
        viewModelScope.launch { arts.observeRows().collect { articles = it } }
        viewModelScope.launch { sites.activeId.collect { id -> inputs.update { (a, b) -> a.copy(third = customBy[id].orEmpty()) to b }; runCatching { blog.refresh(sites.active().url) } } }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (true) {
                // لا نحدّث بسرعة إلا والشاشة ظاهرة، لتبقى بقية التطبيق سلسة
                refresh()
                delay(if (_ui.subscriptionCount.value > 0) 250 else 1500)
            }
        }
    }

    private fun nodeSpecs() = store.readySpecs().take(6).ifEmpty { ProviderCatalog.all.take(6) }

    private fun refresh() {
        val specs = nodeSpecs()
        val job = runner.stateOf(sites.activeId.value)
        val active = bus.active.value
        val since = bus.activeSince.value
        val nodes = specs.map { s ->
            val cool = engine.coolSeconds(s.id)
            when {
                !store.ready(s) -> NodeUi(s.name, NodeState.NoKey)
                s.id == active -> NodeUi(s.name, NodeState.Active, progress = ((System.currentTimeMillis() - since) / 40_000f).coerceIn(0.05f, 0.95f))
                cool > 0 -> NodeUi(s.name, NodeState.Cooling, cool)
                else -> NodeUi(s.name)
            }
        }
        bus.pass.value?.let { p ->
            if (p.id != lastPassId) {
                lastPassId = p.id
                val from = specs.indexOfFirst { it.id == p.from }; val to = specs.indexOfFirst { it.id == p.to }
                if (to >= 0) { currentPass = BallPass(p.id, if (from < 0) to else from, to, 900); ballAt = to }
            }
        }
        val mine = articles.filter { it.jobId == job.jobId && (it.status == "ready" || it.status == "exported") }
        val words = mine.filter { it.words > 0 }
        val (inp, notice) = inputs.value
        val cards = sites.sites.value.map { x ->
            val st = runner.stateOf(x.id); val rs = articles.filter { it.siteId == x.id }
            SiteCardUi(x.id, x.name, x.topicLabel, x.host, st.running, st.resumable && !st.running, st.done, st.total, st.failed, st.phase,
                rs.count { it.status == "ready" }, rs.count { it.status == "exported" && it.remoteUrl.isEmpty() }, rs.count { it.remoteUrl.isNotEmpty() }, x.id == sites.activeId.value)
        }
        _ui.value = HomeUiState(
            room = _room.value, cards = cards,
            total = inp.first, mode = inp.second, custom = inp.third, running = job.running, resumable = job.resumable && !job.running,
            done = job.done, failed = job.failed, dup = job.dup, jobTotal = job.total, nodes = nodes, parallel = gen.parallel.value, provReady = nodes.count { it.state != NodeState.NoKey && it.state != NodeState.Cooling }, provCooling = nodes.count { it.state == NodeState.Cooling }, ballAt = ballAt.coerceIn(0, (nodes.size - 1).coerceAtLeast(0)),
            pass = currentPass, rows = mine.sortedByDescending { it.idx }.take(4).map { RowUi(it.idx + 1, it.title, it.words, it.provider) },
            avgWords = if (words.isEmpty()) 0 else words.sumOf { it.words } / words.size, notice = notice, logs = bus.logs.value.asReversed().take(60), phase = job.phase, blogCount = blog.count(sites.active().url), siteName = sites.active().name, siteNiche = sites.active().niche, siteCount = sites.sites.value.size,
        )
    }

    fun setParallel(n: Int) = gen.setParallel(n)

    fun openRoom(id: String) { sites.setActive(id); _room.value = true }
    fun closeRoom() { _room.value = false }

    /** إجراء سريع من البطاقة: إيقاف الجاري أو استئناف المتوقف. */
    fun quick(id: String) {
        val st = runner.stateOf(id)
        if (st.running) runner.stop(id) else if (st.resumable) { runner.resume(id); GenerationService.start(ctx) }
    }

    fun setTotal(n: Int) = inputs.update { (a, b) -> a.copy(first = n) to b }
    fun setMode(m: PlanMode) = inputs.update { (a, b) -> a.copy(second = m) to b }
    fun setCustom(t: String) { customBy[sites.activeId.value] = t.take(4000); inputs.update { (a, b) -> a.copy(third = t.take(4000)) to b } }

    /** يبدأ أو يستأنف أو يوقف؛ يعيد true إذا بدأ عملاً يحتاج الخدمة الأمامية. */
    fun go() {
        val s = _ui.value
        val sid = sites.activeId.value
        if (s.running) { runner.stop(sid); return }
        if (store.readySpecs().isEmpty()) { inputs.update { it.first to "فعّل مزوداً واحداً على الأقل: أضف مفتاحاً أو فعّل Pollinations" }; return }
        if (s.mode == PlanMode.Mine && s.custom.lines().none { it.trim().length > 5 }) { inputs.update { it.first to "اكتب موضوعاً واحداً على الأقل في كل سطر" }; return }
        inputs.update { it.first to "" }
        if (s.resumable) runner.resume(sid) else runner.start(s.total, s.mode, s.custom, sid)
        GenerationService.start(ctx)
    }
}
