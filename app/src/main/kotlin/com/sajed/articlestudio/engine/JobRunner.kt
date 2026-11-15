package com.sajed.articlestudio.engine

import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.ArticleEntity
import com.sajed.articlestudio.data.JobDao
import com.sajed.articlestudio.data.JobEntity
import com.sajed.articlestudio.ui.LiveStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

data class JobUi(
    val running: Boolean = false, val jobId: Long = 0, val total: Int = 0, val done: Int = 0,
    val failed: Int = 0, val resumable: Boolean = false, val phase: String = "",
)

/** يشغّل المهمة في نطاق التطبيق ويحفظ كل مقال فور اكتماله؛ يستأنف من الفهرس المخزّن بعد أي توقف. */
@Singleton
class JobRunner @Inject constructor(
    private val gen: Generator, private val jobs: JobDao, private val arts: ArticleDao,
    private val bus: RunBus, private val live: LiveStatus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(JobUi())
    val state: StateFlow<JobUi> = _state.asStateFlow()
    private var run: Job? = null
    private val topicsSer = ListSerializer(Topic.serializer())

    init { scope.launch { restore() } }

    private suspend fun restore() {
        val j = jobs.latest() ?: return
        if (_state.value.running) return
        _state.value = JobUi(false, j.id, j.total, j.done, j.failed, resumable = j.state != "done" && j.next < j.total, phase = "")
    }

    fun isRunning() = run?.isActive == true

    /** يُستدعى من الخدمة بعد إعادة تشغيل العملية: يستأنف إن كانت المهمة قيد التشغيل لحظة التوقف. */
    fun resumeIfInterrupted() {
        scope.launch { if (jobs.latest()?.state == "running" && !isRunning()) resume() }
    }

    fun start(n: Int, mode: PlanMode, custom: String) {
        if (isRunning()) return
        run = scope.launch {
            val id = jobs.upsert(JobEntity(total = n, state = "running", createdAt = System.currentTimeMillis(), mode = mode.name, custom = custom))
            bus.log("بدء مهمة جديدة")
            execute(jobs.latest()!!.copy(id = id))
        }
    }

    fun resume() {
        if (isRunning()) return
        run = scope.launch {
            val j = jobs.latest() ?: return@launch
            if (j.next >= j.total) return@launch
            bus.log("استئناف المهمة من المقال ${j.next + 1}")
            execute(j.copy(state = "running"))
        }
    }

    fun stop() { run?.cancel() }

    /** إعادة توليد مقال واحد (جاهز أو فاشل) دون المساس بباقي المهمة. */
    fun regenerate(id: Long) {
        if (isRunning()) return
        run = scope.launch {
            val e = arts.byId(id) ?: return@launch
            val topic = if (e.status == "failed") AppJson.decodeFromString(Topic.serializer(), e.data)
            else AppJson.decodeFromString(Article.serializer(), e.data).let { a -> Topic(a.topic, labels = a.labels, ref = a.ref) }
            _state.update { it.copy(running = true, phase = "إعادة توليد: ${clamp(topic.topic, 40)}") }
            live.set("إعادة توليد: ${clamp(topic.topic, 50)}")
            try {
                val a = gen.article(topic)
                arts.upsert(e.copy(title = a.title, slug = a.slug, labels = a.labels.joinToString(","), status = "ready", words = a.words, provider = a.provider, error = "", data = AppJson.encodeToString(Article.serializer(), a), updatedAt = System.currentTimeMillis()))
                bus.log("أُعيد توليد المقال: ${a.words} كلمة", "o")
                live.set("أُعيد توليد المقال")
            } catch (ex: CancellationException) { throw ex } catch (ex: Exception) {
                bus.log("فشل: " + clamp(ex.message, 70), "e"); live.set("فشلت إعادة التوليد")
            } finally { _state.update { it.copy(running = false, phase = "") } }
        }
    }

    private suspend fun execute(job0: JobEntity) {
        var job = job0
        _state.value = JobUi(true, job.id, job.total, job.done, job.failed, phase = "تخطيط المواضيع")
        try {
            var topics = if (job.topics.isEmpty()) emptyList() else AppJson.decodeFromString(topicsSer, job.topics)
            if (topics.isEmpty()) {
                bus.log("تخطيط المواضيع…"); live.set("تخطيط المواضيع…")
                topics = gen.plan(job.total, PlanMode.valueOf(job.mode), job.custom)
                job = job.copy(topics = AppJson.encodeToString(topicsSer, topics), total = topics.size, next = 0)
                jobs.upsert(job)
                bus.log("الخطة جاهزة: ${topics.size} مواضيع", "o")
            }
            while (job.next < topics.size) {
                val t = topics[job.next]
                bus.log("(${job.next + 1}/${topics.size}) ${clamp(t.topic, 50)}")
                live.set("يكتب (${job.next + 1}/${topics.size}): ${clamp(t.topic, 44)}")
                _state.value = JobUi(true, job.id, topics.size, job.done, job.failed, phase = clamp(t.topic, 50))
                var ok = false
                try {
                    val a = gen.article(t)
                    arts.upsert(ArticleEntity(jobId = job.id, idx = job.next, title = a.title, slug = a.slug, labels = a.labels.joinToString(","), status = "ready", words = a.words, provider = a.provider, updatedAt = System.currentTimeMillis(), data = AppJson.encodeToString(Article.serializer(), a)))
                    bus.log("تم: ${a.words} كلمة عبر ${a.provider}", "o"); ok = true
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    bus.log("فشل الموضوع: " + clamp(e.message, 70), "e")
                    arts.upsert(ArticleEntity(jobId = job.id, idx = job.next, title = t.topic, slug = "", status = "failed", error = clamp(e.message, 120), updatedAt = System.currentTimeMillis(), data = AppJson.encodeToString(Topic.serializer(), t)))
                }
                job = job.copy(next = job.next + 1, done = job.done + if (ok) 1 else 0, failed = job.failed + if (ok) 0 else 1)
                jobs.upsert(job)
                _state.value = JobUi(true, job.id, topics.size, job.done, job.failed, phase = "")
                delay(1200)
            }
            jobs.upsert(job.copy(state = "done"))
            bus.log("اكتمل التوليد", "o"); live.set("اكتمل التوليد: ${job.done} مقالاً جاهزاً للتصدير")
            _state.value = JobUi(false, job.id, topics.size, job.done, job.failed)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { jobs.upsert(job.copy(state = "paused")) }
            bus.log("أُوقفت المهمة", "w"); live.set("متوقف مؤقتاً: ${job.done}/${job.total}")
            _state.value = JobUi(false, job.id, job.total, job.done, job.failed, resumable = job.next < job.total)
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { jobs.upsert(job.copy(state = "paused")) }
            bus.log("توقف: " + clamp(e.message, 80), "e"); live.set("توقف: " + clamp(e.message, 50))
            _state.value = JobUi(false, job.id, job.total, job.done, job.failed, resumable = job.next < job.total)
        }
    }
}
