package com.sajed.articlestudio.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sajed.articlestudio.MainActivity
import com.sajed.articlestudio.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** خدمة أمامية: تبقي العملية حيّة مع إشعار تقدّم وزر إيقاف أثناء التوليد في الخلفية. */
@AndroidEntryPoint
class GenerationService : Service() {
    @Inject lateinit var runner: JobRunner
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { runner.stop(); return START_NOT_STICKY }
        if (!enterForeground(build(JobUi()))) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "articlestudio:gen").apply { acquire(3 * 60 * 60 * 1000L) }
        }
        if (intent == null) runner.resumeIfInterrupted()
        if (watcher?.isActive != true) watcher = scope.launch {
            val t0 = System.currentTimeMillis(); var seen = false
            runner.state.collect { s ->
                if (s.running) seen = true
                notify(build(s))
                if (!s.running && (seen || System.currentTimeMillis() - t0 > 10_000)) { finish(); return@collect }
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { runner.stop(); finish() }

    private fun finish() {
        watcher?.cancel(); watcher = null
        stopForeground(STOP_FOREGROUND_DETACH)
        val s = runner.state.value
        notify(done(s))
        wake?.let { if (it.isHeld) it.release() }; wake = null
        stopSelf()
    }

    override fun onDestroy() { wake?.let { if (it.isHeld) it.release() }; scope.cancel(); super.onDestroy() }

    private fun enterForeground(n: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        true
    } catch (e: Exception) { false }

    private fun notify(n: Notification) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, n)

    private fun open() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun base(): NotificationCompat.Builder {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "التوليد", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentIntent(open()).setOnlyAlertOnce(true)
    }

    private fun build(s: JobUi): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, GenerationService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return base().setContentTitle("استوديو المقالات يولّد الآن").setOngoing(true)
            .setContentText(if (s.total == 0) "تجهيز الخطة…" else "${s.done}/${s.total}" + if (s.phase.isNotEmpty()) " · ${s.phase}" else "")
            .setProgress(s.total, s.done, s.total == 0)
            .addAction(0, "إيقاف", stop).build()
    }

    private fun done(s: JobUi): Notification = base().setAutoCancel(true).setOngoing(false)
        .setContentTitle(if (s.resumable) "توقف التوليد مؤقتاً" else "اكتمل التوليد")
        .setContentText("${s.done} مقالاً جاهزاً" + if (s.failed > 0) " · ${s.failed} فاشلة" else "").build()

    companion object {
        const val ACTION_STOP = "com.sajed.articlestudio.STOP"
        private const val CHANNEL = "generation"
        private const val ID = 7
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, GenerationService::class.java))
    }
}
