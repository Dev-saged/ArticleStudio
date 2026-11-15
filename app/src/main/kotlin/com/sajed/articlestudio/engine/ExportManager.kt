package com.sajed.articlestudio.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.GitHubSettings
import com.sajed.articlestudio.data.HistoryStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

class ExportStep(val text: String, val state: Char)
class ExportUi(val running: Boolean = false, val steps: List<ExportStep> = emptyList(), val lastName: String? = null)

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val arts: ArticleDao, private val renderer: ImageRenderer,
    private val gh: GitHubClient, private val cfg: GitHubSettings, private val history: HistoryStore,
) {
    private val dir get() = File(ctx.filesDir, "exports").apply { mkdirs() }
    private val _ui = MutableStateFlow(ExportUi(lastName = dir.listFiles()?.filter { it.extension == "xml" }?.maxByOrNull { it.lastModified() }?.name))
    val ui: StateFlow<ExportUi> = _ui.asStateFlow()

    private fun step(t: String, s: Char = 'a') = _ui.update { u ->
        val l = u.steps.toMutableList()
        if (l.isNotEmpty() && l.last().state == 'a') l[l.lastIndex] = ExportStep(l.last().text, 'd')
        l += ExportStep(t, s); ExportUi(u.running, l, u.lastName)
    }

    private fun finishSteps(last: ExportStep?) = _ui.update { u -> ExportUi(false, u.steps.map { if (it.state == 'a') ExportStep(it.text, 'd') else it } + listOfNotNull(last), u.lastName) }

    suspend fun check(): List<String> = gh.check()

    suspend fun export() = withContext(Dispatchers.Default) {
        _ui.update { ExportUi(true, emptyList(), it.lastName) }
        try {
            val rows = arts.ready()
            if (rows.isEmpty()) throw IllegalStateException("لا مقالات جاهزة للتصدير")
            val auto = cfg.autoPush
            if (auto) { if (cfg.token.isEmpty()) throw IllegalStateException("أدخل GitHub Token أو عطّل الرفع التلقائي"); step("فحص صلاحيات GitHub"); gh.check() }
            val parsed = rows.map { it to AppJson.decodeFromString(Article.serializer(), it.data) }
            val list = XmlBuilder.assignSlugs(parsed.map { it.second }, history.load().slugs.toSet())
            step("رسم الصور")
            val files = ArrayList<ImageFile>()
            list.forEach { a -> val im = renderer.render(a); files += ImageFile("${a.finalSlug}-cover.jpg", im.cover); files += ImageFile("${a.finalSlug}-info.jpg", im.info) }
            val base: String
            if (auto) base = gh.pushImages(files) { step(it) }
            else {
                step("تجميع ZIP")
                base = "https://cdn.jsdelivr.net/gh/${cfg.repo}@${cfg.branch.ifBlank { "main" }}/images/articles/"
                val zip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z -> files.forEach { f -> z.putNextEntry(ZipEntry(f.name)); z.write(f.bytes); z.closeEntry() } } }.toByteArray()
                saveDownload("article-images.zip", "application/zip", zip)
            }
            step("بناء XML")
            val xml = XmlBuilder.build(list, base, cfg.scheduleHours)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            val name = "articles-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xml"
            File(dir, name).writeText(xml)
            saveDownload(name, "text/xml", xml.toByteArray())
            arts.markExported(rows.map { it.id }, System.currentTimeMillis())
            history.add(list.map { it.finalSlug }, list.map { it.topic })
            _ui.update { ExportUi(true, it.steps, name) }
            finishSteps(ExportStep("تم: $name", 'd'))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            finishSteps(ExportStep(clamp(e.message, 140), 'x'))
        }
    }

    /** نسخة في مجلد التنزيلات (Android 10+)، وعلى الأقدم تبقى النسخة الداخلية للمشاركة. */
    private fun saveDownload(name: String, mime: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT < 29) return
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ArticleStudio")
        }
        runCatching {
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    fun shareIntent(): Intent? {
        val name = _ui.value.lastName ?: return null
        val f = File(dir, name).takeIf { it.exists() } ?: return null
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/xml").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "تصدير XML")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
