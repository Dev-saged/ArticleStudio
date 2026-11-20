package com.sajed.articlestudio.ui.pipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ReviewDao
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.engine.UpdatePhase
import com.sajed.articlestudio.engine.Updater
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.social.ShareStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** ملخص منصة: المنتظر، ما يحتاجك، المنشور، الموعد التالي، ونشاط آخر 7 أيام (الأحدث أولاً). */
data class PlatformSum(val id: String, val waiting: Int, val needs: Int, val done: Int, val next: Long?, val week: List<Int>, val accounts: List<SocialAccountEntity>) {
    val loginNeeded get() = accounts.any { it.enabled && it.status == "needs_login" }
    val linked get() = accounts.isNotEmpty()
}

/** عنصر في «يحتاجك». kind: login | share | stuck | update | review */
data class NeedItem(val kind: String, val title: String, val sub: String, val ref: String = "")

data class Overview(
    val ready: Int = 0, val published: Int = 0, val stuck: Int = 0, val failed: Int = 0, val total: Int = 0,
    val platforms: List<PlatformSum> = emptyList(), val waiting: Int = 0, val needsShare: Int = 0, val doneToday: Int = 0,
    val needs: List<NeedItem> = emptyList(),
)

@HiltViewModel
class PipeViewModel @Inject constructor(
    arts: ArticleDao, reviews: ReviewDao, private val share: ShareStore, val sites: SiteStore, val updater: Updater,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val weekStart = LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

    private val rows = arts.observeRows()

    val overview: StateFlow<Overview> = combine(
        combine(rows, sites.activeId) { r, sid -> r.filter { it.siteId == sid } },
        combine(share.stats, share.accounts, share.doneSince(weekStart)) { s, a, d -> Triple(s, a, d) },
        combine(share.needs, updater.ui, reviews.observeAll()) { n, u, rv -> Triple(n, u, rv) },
    ) { mine, (stats, accounts, done), (needJobs, upd, rv) ->
        val today = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val day = { t: Long -> ((t - weekStart) / 86_400_000L).toInt().coerceIn(0, 6) }
        val ids = (Platforms.all.map { it.id }).filter { p -> accounts.any { it.platform == p } || stats.any { it.platform == p } }
        val plats = ids.map { p ->
            val ps = stats.filter { it.platform == p }
            val w = IntArray(7); done.filter { it.platform == p }.forEach { w[6 - day(it.doneAt)]++ }
            PlatformSum(p, ps.filter { it.status == "pending" || it.status == "running" }.sumOf { it.n }, ps.filter { it.status == "needs_you" || it.status == "failed" }.sumOf { it.n },
                ps.filter { it.status == "done" }.sumOf { it.n }, ps.firstOrNull { it.status == "pending" }?.next, w.toList(), accounts.filter { it.platform == p })
        }
        val needs = buildList {
            accounts.filter { it.enabled && it.status == "needs_login" }.forEach { add(NeedItem("login", "${Platforms.of(it.platform)?.name}: يلزم تسجيل الدخول", it.label, it.platform)) }
            accounts.filter { it.enabled && it.status == "attention" }.forEach { add(NeedItem("login", "${it.label}: يحتاج ضبطاً", it.statusNote.take(80), it.platform)) }
            val byPlat = needJobs.groupBy { it.platform }
            byPlat.forEach { (p, l) -> add(NeedItem("share", "${l.size} منشور متوقف على ${Platforms.of(p)?.name}", l.first().error.substringBefore(" | ").take(80), p)) }
            val stuck = mine.count { it.status == "exported" && it.remoteUrl.isEmpty() }
            if (stuck > 0) add(NeedItem("stuck", "$stuck مقال صُدّر ولم يُنشر", "انشره من «النشر والملفات»"))
            if (upd.phase == UpdatePhase.Available) add(NeedItem("update", "إصدار جديد ${upd.info?.version.orEmpty()}", "حدّث التطبيق"))
            val unrated = rv.count { it.rating == 0 }
            if (unrated > 0) add(NeedItem("review", "$unrated مقال بانتظار تقييمك", "من «الرؤى والمراجعة»"))
        }
        Overview(
            ready = mine.count { it.status == "ready" }, published = mine.count { it.remoteUrl.isNotEmpty() }, stuck = mine.count { it.status == "exported" && it.remoteUrl.isEmpty() },
            failed = mine.count { it.status == "failed" }, total = mine.size, platforms = plats, waiting = plats.sumOf { it.waiting }, needsShare = plats.sumOf { it.needs },
            doneToday = done.count { it.doneAt >= today }, needs = needs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Overview())

    fun setAutoExport(v: Boolean) = sites.upsert(sites.active().copy(autoExport = v))
}
