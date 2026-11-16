package com.sajed.articlestudio.engine

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** ما بعد التوليد: يُستدعى من JobRunner ويعيد سطر نتيجة؛ واجهة ليسهل استبداله في الاختبارات. */
fun interface PostRun { suspend fun run(siteId: String, onStage: (String) -> Unit): String }

/** يشغّل التصدير فرفع الصور فتأكيدها فالنشر بالتتابع، ويعيد محاولة النشر تلقائياً حتى تظهر الصور. */
@Singleton
class AutoPipeline @Inject constructor(private val exporter: ExportManager) : PostRun {
    override suspend fun run(siteId: String, onStage: (String) -> Unit): String = coroutineScope {
        exporter.clearPub()
        val watcher = launch { exporter.ui.collect { u -> if (u.running && u.stage.isNotEmpty()) onStage(stageText(u)) } }
        try {
            exporter.export(siteId)
            var tries = 0
            while (exporter.pub.value.report?.retryable == true && tries < RETRIES) {
                tries++
                onStage("بانتظار ظهور الصور على الشبكة… محاولة $tries/$RETRIES")
                delay(RETRY_WAIT)
                exporter.retryPublish()
            }
        } finally { watcher.cancel() }
        summary(exporter.ui.value, exporter.pub.value.report)
    }

    companion object {
        const val RETRIES = 4
        const val RETRY_WAIT = 60_000L

        fun stageText(u: ExportUi): String = "تصدير ${u.stageIdx}/${u.stageCount} · ${u.stage}" + if (u.total > 0) " (${u.done}/${u.total})" else ""

        fun summary(u: ExportUi, r: PublishReport?): String = when {
            u.error != null -> "تعذّر التصدير: ${u.error}"
            r == null -> "صُدّر ${u.summary?.articles ?: 0} مقالاً" + if (u.summary?.pushed == true) " ورُفعت صورها" else ""
            r.consent != null -> "توقف النشر: يلزم إعادة ربط حساب جوجل من «النشر» في المواقع"
            r.fatal != null -> "توقف النشر: ${r.fatal}"
            else -> "تم النشر: ${r.ok} مقالاً" + (if (r.skipped > 0) " · ${r.skipped} منشورة سابقاً" else "") + (if (r.failed.isNotEmpty()) " · ${r.failed.size} فشلت" else "")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PipelineModule { @Binds abstract fun post(p: AutoPipeline): PostRun }
