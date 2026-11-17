package com.sajed.articlestudio.engine

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sajed.articlestudio.data.JobDao
import com.sajed.articlestudio.data.ProviderStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** قواعد الاستحقاق: الفاصل بالساعات ونافذة اليوم (من/إلى، وتدعم العبور فوق منتصف الليل). */
object AutoPlan {
    fun inWindow(from: Int, to: Int, hour: Int): Boolean = when {
        from == to || (from <= 0 && to >= 24) -> true
        from < to -> hour in from until to
        else -> hour >= from || hour < to
    }

    fun due(s: SiteConfig, lastAt: Long, now: Long, hour: Int): Boolean =
        s.autoEvery > 0 && inWindow(s.autoFrom, s.autoTo, hour) && now - lastAt >= s.autoEvery * 3_600_000L - 120_000L
}

/** ينفّذ دورة التوليد التلقائي للمواقع المستحقة؛ المسار بعد التوليد (تصدير ونشر) يتولاه JobRunner. */
@Singleton
class AutoRunner @Inject constructor(
    @ApplicationContext private val ctx: Context, private val sites: SiteStore, private val runner: JobRunner,
    private val jobs: JobDao, private val providers: ProviderStore, private val bus: RunBus,
) {
    private val sp = ctx.getSharedPreferences("auto", Context.MODE_PRIVATE)
    private val lock = Mutex()

    fun lastRun(siteId: String) = sp.getLong("last-$siteId", 0L)

    /** يعيد عدد المواقع التي بدأ لها توليد. */
    suspend fun tick(now: Long = System.currentTimeMillis(), hour: Int = LocalTime.now().hour, maxWaitMs: Long = 9 * 60_000L): Int {
        if (!lock.tryLock()) return 0
        try {
            var started = 0
            for (s in sites.sites.value) {
                if (runner.isRunning(s.id)) continue
                if (!AutoPlan.due(s, lastRun(s.id), now, hour)) continue
                if (providers.readySpecs().isEmpty()) { bus.log("التوليد التلقائي: لا مزود جاهز", "w"); break }
                sp.edit().putLong("last-${s.id}", now).apply()
                val j = jobs.latestFor(s.id)
                if (j != null && j.state == "running" && j.next < j.total) runner.resume(s.id)
                else runner.start(s.autoCount, runCatching { PlanMode.valueOf(s.mode) }.getOrDefault(PlanMode.News), "", s.id)
                bus.log("توليد تلقائي لـ«${s.name}»: ${s.autoCount} مقالات")
                runCatching { GenerationService.start(ctx) }
                started++
                delay(2_000)
                val t0 = System.currentTimeMillis()
                while (runner.isRunning(s.id) && System.currentTimeMillis() - t0 < maxWaitMs) delay(3_000)
            }
            return started
        } finally { lock.unlock() }
    }
}

object AutoScheduler {
    private const val NAME = "auto-run"

    fun sync(ctx: Context, list: List<SiteConfig>) {
        val wm = WorkManager.getInstance(ctx)
        if (list.none { it.autoEvery > 0 }) { wm.cancelUniqueWork(NAME); return }
        val req = PeriodicWorkRequestBuilder<AutoWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoEntry { fun runner(): AutoRunner }

class AutoWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        runCatching { EntryPointAccessors.fromApplication(applicationContext, AutoEntry::class.java).runner().tick() }
        return Result.success()
    }
}
