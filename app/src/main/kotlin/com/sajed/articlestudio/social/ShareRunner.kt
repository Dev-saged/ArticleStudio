package com.sajed.articlestudio.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sajed.articlestudio.MainActivity
import com.sajed.articlestudio.R
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ProviderStore
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.data.SocialDao
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.Article
import com.sajed.articlestudio.engine.ChatMessage
import com.sajed.articlestudio.engine.ChatOpts
import com.sajed.articlestudio.engine.ProviderEngine
import com.sajed.articlestudio.engine.RunBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface HookSource { suspend fun hooks(title: String, desc: String, labels: List<String>): List<String> }

/** يولّد صياغات مشاركة بمزودي الذكاء الاصطناعي الحاليين؛ عند أي تعذّر تُستعمل صياغة العنوان الأصلي. */
@Singleton
class ShareWriter @Inject constructor(private val engine: ProviderEngine, private val providers: ProviderStore) : HookSource {
    override suspend fun hooks(title: String, desc: String, labels: List<String>): List<String> {
        if (providers.readySpecs().isEmpty()) return emptyList()
        val sys = "أنت محرر مشاركات اجتماعية عربي. تكتب صياغات قصيرة صادقة تشدّ القارئ دون مبالغة ودون أي معلومة أو رقم غير موجود في العنوان والوصف."
        val usr = "العنوان: $title\nالوصف: $desc\nالأقسام: ${labels.joinToString("، ")}\n" +
            "اكتب 5 صياغات مشاركة مختلفة الأسلوب (سؤال فضول، خطأ شائع، رقم من العنوان، فرق بين أمرين، فائدة مباشرة). كل صياغة بين 30 و90 حرفاً، بلا رموز تعبيرية ولا وسوم ولا روابط. " +
            "أعد مصفوفة JSON من النصوص فقط."
        return try {
            val r = engine.chat(listOf(ChatMessage("system", sys), ChatMessage("user", usr)), ChatOpts(temp = 0.9, max = 700, timeoutMs = 60_000, validate = { t ->
                if (ShareText.parseHooks(t).isEmpty()) throw com.sajed.articlestudio.engine.ValidationFail("لا صياغات")
            }))
            ShareText.parseHooks(r.text)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
}

@Singleton
class ShareNotifier @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun notify(id: Int, title: String, text: String) = runCatching {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "المشاركة الاجتماعية", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
    }
    companion object { const val CHANNEL = "share" }
}

/** ينفّذ المهام المستحقة: يحترم السقف اليومي والفاصل والنافذة، ويصنّف الأخطاء، ولا يكرر منشوراً غير مؤكد. */
class ShareRunner(
    private val dao: SocialDao, private val arts: ArticleDao, private val hooks: HookSource,
    private val publisherFor: (SocialAccountEntity) -> SocialPublisher, private val notifier: ShareNotifier, private val bus: RunBus,
) {
    private val lock = Mutex()
    var zone: ZoneId = ZoneId.systemDefault()
    var pauseBetweenMs: () -> Long = { Random.nextLong(20_000, 60_000) }

    private fun dayStart(now: Long) = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    private fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour

    suspend fun tick(now: Long = System.currentTimeMillis(), maxJobs: Int = 6): Int {
        if (!lock.tryLock()) return 0
        try {
            dao.parkInterrupted()
            var n = 0
            for (job in dao.due(now, maxJobs)) {
                if (n > 0 && Platforms.of(job.platform)?.isWeb == true) delay(pauseBetweenMs())
                process(job, now); n++
            }
            return n
        } finally { lock.unlock() }
    }

    private suspend fun finish(j: ShareJobEntity, status: String, error: String = "", now: Long, postUrl: String = "", runAt: Long = j.runAt, attempts: Int = j.attempts) =
        dao.updateJob(j.copy(status = status, error = error.take(900), doneAt = if (status == "done") now else 0, postUrl = postUrl, runAt = runAt, attempts = attempts))

    private suspend fun process(j: ShareJobEntity, now: Long) {
        val acc = dao.account(j.accountId) ?: return finish(j, "skipped", "الحساب محذوف", now)
        val spec = Platforms.of(j.platform) ?: return finish(j, "skipped", "منصة غير معروفة", now)
        if (!acc.enabled) return finish(j, "skipped", "الحساب معطّل", now)
        if (acc.status == "needs_login") return finish(j, "needs_you", "login: سجّل الدخول من صفحة الحساب", now)

        val allowed = ShareRules.allowedAt(now, dao.doneSince(acc.id, dayStart(now)), acc.dailyCap, dao.lastDone(acc.id), acc.gapMin, acc.winFrom, acc.winTo, ::hourOf, dayStart(now))
        if (allowed > now) return dao.updateJob(j.copy(runAt = allowed + Random.nextLong(0, 4 * 60_000L)))

        val article = arts.byId(j.articleId)?.let { runCatching { AppJson.decodeFromString(Article.serializer(), it.data) }.getOrNull() }
        val text = j.text.ifBlank {
            val hook = ShareText.pick(hooks.hooks(j.title, article?.desc.orEmpty(), article?.labels.orEmpty()), j.title, j.title + " " + article?.desc.orEmpty())
            ShareText.compose(spec, hook, j.url, article?.labels.orEmpty())
        }
        val running = j.copy(status = "running", attempts = j.attempts + 1, text = text, error = "")
        dao.updateJob(running)
        val post = Post(text, text.substringBefore("\n\n" + j.url).substringBefore("\n\n").trim(), j.url)
        try {
            val r = publisherFor(acc).share(acc, post)
            dao.markOk(acc.id, now)
            finish(running, "done", "", now, r.postUrl)
            bus.log("شورك «${j.title.take(40)}» على ${spec.name}")
        } catch (e: CancellationException) {
            dao.updateJob(running.copy(status = "needs_you", error = "أُوقفت المهمة أثناء الإرسال؛ تحقق من الحساب"))
            throw e
        } catch (e: ShareException) {
            when {
                e.code == "login_required" -> {
                    dao.setStatus(acc.id, "needs_login", e.message.orEmpty())
                    finish(running, "needs_you", "login: ${e.message}", now)
                    notifier.notify(2000 + acc.id.hashCode().mod(500), "${acc.label}: يلزم تسجيل الدخول", "افتح التطبيق ← مشاركة وسجّل الدخول ليكمل النشر تلقائياً")
                }
                e.code == "ui_changed" -> {
                    finish(running, "needs_you", "ui: ${e.message} | ${e.diag}", now)
                    notifier.notify(2500 + j.id.toInt().mod(500), "${spec.name}: لم يكتمل النشر الآلي", "«${j.title.take(50)}» جاهز للنشر اليدوي من قائمة المشاركة")
                }
                e.retry && running.attempts < ShareRules.backoffMin.size + 1 ->
                    finish(running, "pending", e.message.orEmpty(), now, runAt = now + ShareRules.jitter(ShareRules.backoffMin[running.attempts - 1] * 60_000L, 20, Random))
                else -> finish(running, "failed", e.message.orEmpty(), now)
            }
        } catch (e: Exception) { finish(running, "failed", e.message.orEmpty(), now) }
    }
}

/** ناشر الويب: يجرّب نسخ وصفة المنصة بالترتيب؛ انتهاء الجلسة يوقف فوراً، وتغيّر الواجهة ينتقل للنسخة التالية. */
@Singleton
class WebSessionPublisher @Inject constructor(private val automator: WebAutomator, private val recipes: RecipeStore) : SocialPublisher {
    private fun spec(acc: SocialAccountEntity) = Platforms.of(acc.platform) ?: throw ShareException("rejected", "منصة غير معروفة")

    override suspend fun check(acc: SocialAccountEntity): String {
        if (!automator.hasSession(automator.profileName(acc.id), spec(acc))) throw ShareException("login_required", "لم أجد جلسة دخول: سجّل الدخول في الصفحة ثم اضغط «تم تسجيل الدخول»")
        return "الجلسة نشطة"
    }

    override suspend fun share(acc: SocialAccountEntity, post: Post): ShareResult {
        if (!automator.hasSession(automator.profileName(acc.id), spec(acc))) throw ShareException("login_required", "الجلسة منتهية")
        val vs = recipes.variants(acc.platform)
        if (vs.isEmpty()) throw ShareException("rejected", "لا وصفة لهذه المنصة")
        val vars = mapOf("text" to post.text, "hook" to post.hook, "url" to post.url)
        var last = AutoOutcome(false, "ui_changed", "لا نسخة ناجحة")
        for (v in vs) {
            val o = automator.run(automator.profileName(acc.id), v, vars)
            if (o.ok) return ShareResult()
            if (o.code == "login_required") throw ShareException("login_required", "الجلسة منتهية")
            last = o
        }
        if (last.code == "timeout") throw ShareException("network", "انتهت المهلة قبل النشر", retry = true, diag = last.diag)
        throw ShareException("ui_changed", last.msg, diag = last.diag)
    }
}
