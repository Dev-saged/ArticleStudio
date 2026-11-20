package com.sajed.articlestudio.social

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sajed.articlestudio.R
import com.sajed.articlestudio.data.SocialDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

object ShareScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** يضمن فحصاً دورياً كل 15 دقيقة (الحد الأدنى في أندرويد) حتى تنفيذ ما استحق. */
    fun ensure(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("share-tick", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ShareWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())
    }

    /** تشغيل مبكر بعد تأخير محدد (يستبدل الأقرب). */
    fun kick(ctx: Context, delayMs: Long) {
        WorkManager.getInstance(ctx).enqueueUniqueWork("share-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ShareWorker>().setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS).setConstraints(net).build())
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareEntry { fun shareRunner(): ShareRunner; fun shareDao(): SocialDao; fun shareNotifier(): ShareNotifier; fun shareStore(): ShareStore }

class ShareWorker(private val ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val e = EntryPointAccessors.fromApplication(ctx, ShareEntry::class.java)
        runCatching { e.shareStore().catchUp() }
        val now = System.currentTimeMillis()
        if (e.shareDao().due(now, 1).isEmpty()) return Result.success()
        runCatching { setForeground(info()) }
        val end = System.currentTimeMillis() + 8 * 60_000L
        runCatching { while (System.currentTimeMillis() < end && e.shareRunner().tick() > 0) Unit }
        e.shareDao().nextRunAt()?.let { next -> runCatching { ShareScheduler.kick(ctx, (next - System.currentTimeMillis()).coerceAtLeast(60_000L)) } }
        return Result.success()
    }

    private fun info(): ForegroundInfo {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (nm.getNotificationChannel("share-run") == null) nm.createNotificationChannel(android.app.NotificationChannel("share-run", "نشر المشاركات", android.app.NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(ctx, "share-run").setSmallIcon(R.drawable.ic_stat).setContentTitle("مشاركة المقالات على حساباتك").setOngoing(true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(3001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(3001, n)
    }
}
