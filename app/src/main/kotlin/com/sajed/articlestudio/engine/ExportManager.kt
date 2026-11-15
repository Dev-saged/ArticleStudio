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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

class ExportSummary(val name: String, val articles: Int, val images: Int, val pushed: Boolean, val titles: List<String>)
class ExportFile(val name: String, val size: Long, val modified: Long, val articles: Int)
class ExportUi(
    val running: Boolean = false, val stage: String = "", val stageIdx: Int = 0, val stageCount: Int = 0,
    val done: Int = 0, val total: Int = 0, val error: String? = null, val summary: ExportSummary? = null,
)

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val arts: ArticleDao, private val renderer: ImageRenderer,
    private val gh: GitHubClient, private val cfg: GitHubSettings, private val history: HistoryStore, private val blog: BlogIndex,
) {
    private val dir get() = File(ctx.filesDir, "exports").apply { mkdirs() }
    private val _ui = MutableStateFlow(ExportUi())
    val ui: StateFlow<ExportUi> = _ui.asStateFlow()
    private val _files = MutableStateFlow<List<ExportFile>>(emptyList())
    val files: StateFlow<List<ExportFile>> = _files.asStateFlow()

    init { refreshFiles() }

    fun refreshFiles() {
        _files.value = (dir.listFiles() ?: emptyArray()).filter { it.extension == "xml" }.sortedByDescending { it.lastModified() }
            .map { f -> ExportFile(f.name, f.length(), f.lastModified(), runCatching { Regex("<entry>").findAll(f.readText()).count() }.getOrDefault(0)) }
    }

    private fun stage(t: String, idx: Int, count: Int, done: Int = 0, total: Int = 0) = _ui.update { ExportUi(true, t, idx, count, done, total, summary = it.summary) }

    suspend fun check(): List<String> = gh.check()

    suspend fun export() = withContext(Dispatchers.Default) {
        _ui.value = ExportUi(true, "تهيئة", 0, 4)
        try {
            val rows = arts.ready()
            if (rows.isEmpty()) throw IllegalStateException("لا مقالات جاهزة للتصدير")
            val auto = cfg.autoPush
            val count = 4
            if (auto) { if (cfg.token.isEmpty()) throw IllegalStateException("أدخل GitHub Token أو عطّل الرفع التلقائي"); stage("فحص صلاحيات GitHub", 1, count); gh.check() }
            val parsed = rows.map { it to AppJson.decodeFromString(Article.serializer(), it.data) }
            val list = XmlBuilder.assignSlugs(parsed.map { it.second }, history.load().slugs.toSet() + blog.slugs())
            val files = ArrayList<ImageFile>()
            list.forEachIndexed { i, a ->
                stage("رسم الصور", 2, count, i, list.size)
                val im = renderer.render(a); files += ImageFile("${a.finalSlug}-cover.jpg", im.cover); files += ImageFile("${a.finalSlug}-info.jpg", im.info)
            }
            val base: String
            if (auto) base = gh.pushImages(files) { t, i, n -> stage(t, 3, count, i, n) }
            else {
                stage("تجميع ZIP", 3, count)
                base = "https://cdn.jsdelivr.net/gh/${cfg.repo}@${cfg.branch.ifBlank { "main" }}/images/articles/"
                val zip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z -> files.forEach { f -> z.putNextEntry(ZipEntry(f.name)); z.write(f.bytes); z.closeEntry() } } }.toByteArray()
                saveDownload("images-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip", "application/zip", zip)
            }
            stage("بناء XML وحفظه", 4, count)
            val xml = XmlBuilder.build(list, base, cfg.scheduleHours)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            val name = ExportNaming.fileName(list.size, LocalDate.now()) { File(dir, it).exists() }
            File(dir, name).writeText(xml)
            saveDownload(name, "text/xml", xml.toByteArray())
            arts.markExported(rows.map { it.id }, System.currentTimeMillis())
            history.add(list.map { it.finalSlug }, list.map { it.topic }, list.flatMap { a -> a.sources.map { it.url } })
            refreshFiles()
            _ui.value = ExportUi(false, summary = ExportSummary(name, list.size, files.size, auto, list.map { it.title }))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _ui.value = ExportUi(false, error = clamp(e.message, 160), summary = _ui.value.summary)
        }
    }

    fun clearResult() { _ui.update { ExportUi(summary = null) } }

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

    fun delete(name: String) { File(dir, name).takeIf { it.parentFile == dir }?.delete(); refreshFiles() }

    fun shareIntent(name: String, view: Boolean = false): Intent? {
        val f = File(dir, name).takeIf { it.exists() && it.parentFile == dir } ?: return null
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val it = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/xml") else Intent(Intent.ACTION_SEND).setType("text/xml").putExtra(Intent.EXTRA_STREAM, uri)
        return Intent.createChooser(it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), if (view) "فتح الملف" else "مشاركة الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
