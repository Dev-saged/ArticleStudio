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

class ExportSummary(val name: String, val articles: Int, val images: Int, val pushed: Boolean, val titles: List<String>, val folder: String = "")
class ExportFile(val name: String, val size: Long, val modified: Long, val articles: Int)
class ExportUi(
    val running: Boolean = false, val stage: String = "", val stageIdx: Int = 0, val stageCount: Int = 0,
    val done: Int = 0, val total: Int = 0, val error: String? = null, val summary: ExportSummary? = null,
)

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val arts: ArticleDao, private val renderer: ImageRenderer, private val photos: PhotoService,
    private val gh: GitHubClient, private val cfg: GitHubSettings, private val history: HistoryStore, private val blog: BlogIndex,
    private val sites: SiteStore, private val publisher: PublishManager, private val http: okhttp3.OkHttpClient, private val bus: RunBus,
) {
    private val dir get() = File(ctx.filesDir, "exports").apply { mkdirs() }
    private val _ui = MutableStateFlow(ExportUi())
    val ui: StateFlow<ExportUi> = _ui.asStateFlow()
    private val _files = MutableStateFlow<List<ExportFile>>(emptyList())
    val files: StateFlow<List<ExportFile>> = _files.asStateFlow()

    /** حالة النشر المباشر المنفصلة عن مراحل التصدير. */
    class PubUi(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val report: PublishReport? = null, val siteId: String = "")
    private val _pub = MutableStateFlow(PubUi())
    val pub: StateFlow<PubUi> = _pub.asStateFlow()
    private class Pending(val siteId: String, val entries: List<PublishEntry>, val probes: List<String>)
    private var pending: Pending? = null

    init { refreshFiles() }

    /** يتأكد أن صور المقالات صارت متاحة على الشبكة قبل النشر، فلا تظهر المنشورات بصور مكسورة. */
    private suspend fun imagesLive(probes: List<String>, step: (Int) -> Unit): Boolean {
        val client = http.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
        probes.forEachIndexed { i, u ->
            step(i)
            var ok = false
            for (attempt in 0 until 8) {
                ok = try { client.newCall(okhttp3.Request.Builder().url(u).head().build()).await().use { it.isSuccessful } } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
                if (ok) break
                kotlinx.coroutines.delay(5_000)
            }
            if (!ok) return false
        }
        step(probes.size)
        return true
    }

    /** مرحلتا التأكد من الصور ثم النشر؛ تُحدّثان شريط المراحل نفسه الذي يرى المستخدم. */
    private suspend fun publishStages(siteId: String, entries: List<PublishEntry>, probes: List<String>, idx: Int, count: Int): PublishReport {
        if (probes.isNotEmpty()) {
            stage("تأكيد ظهور الصور", idx, count, 0, probes.size)
            val live = imagesLive(probes) { d -> stage("تأكيد ظهور الصور", idx, count, d, probes.size) }
            if (!live) {
                pending = Pending(siteId, entries, probes)
                return PublishReport(fatal = "الصور لم تظهر بعد على الشبكة (قد يتأخر jsDelivr دقائق). اضغط «متابعة النشر» بعد قليل.", retryable = true)
            }
        }
        stage("النشر على بلوجر", idx + 1, count, 0, entries.size)
        val r = try { publisher.publish(siteId, entries) { d, t -> stage("النشر على بلوجر", idx + 1, count, d, t) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { PublishReport(fatal = clamp(e.message, 140)) }
        pending = if (r.consent != null || r.fatal != null) Pending(siteId, entries, emptyList()) else null
        return r
    }

    /** إعادة محاولة آخر نشر توقّف لانتظار ظهور الصور أو ربط الحساب أو لخطأ صلاحية. */
    suspend fun retryPublish() = withContext(Dispatchers.Default) {
        val p = pending ?: return@withContext
        _ui.value = ExportUi(true, "", 0, 0)
        val r = publishStages(p.siteId, p.entries, p.probes, 1, 2)
        _ui.update { ExportUi(false, summary = it.summary) }
        _pub.value = PubUi(false, r.total, r.total, r, p.siteId)
    }

    private suspend fun runPublish(siteId: String, entries: List<PublishEntry>, recheck: Boolean = false): PublishReport {
        _pub.value = PubUi(true, 0, entries.size, siteId = siteId)
        val r = try { publisher.publish(siteId, entries, recheck) { d, t -> _pub.value = PubUi(true, d, t, siteId = siteId) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { PublishReport(fatal = clamp(e.message, 140)) }
        pending = if (r.consent != null || r.fatal != null) Pending(siteId, entries, emptyList()) else null
        _pub.value = PubUi(false, r.total, r.total, r, siteId)
        return r
    }

    suspend fun publishFile(siteId: String, name: String) = withContext(Dispatchers.Default) {
        val f = File(dir, name).takeIf { it.exists() && it.parentFile == dir } ?: return@withContext
        publishText(siteId, f.readText())
    }

    suspend fun publishText(siteId: String, xml: String) = withContext(Dispatchers.Default) {
        val items = try { XmlImport.parse(xml) } catch (e: Exception) { _pub.value = PubUi(report = PublishReport(fatal = "الملف ليس XML صالحاً بصيغة بلوجر"), siteId = siteId); return@withContext }
        if (items.isEmpty()) { _pub.value = PubUi(report = PublishReport(fatal = "لا تدوينات داخل الملف"), siteId = siteId); return@withContext }
        runPublish(siteId, items.map { PublishEntry(it) })
    }

    /** ينشر المقالات المصدّرة التي لم تُنشر (توقفت بسبب مهلة أو ربط) من ملفات XML المحفوظة. */
    suspend fun publishPending(siteId: String) = withContext(Dispatchers.Default) {
        val site = sites.get(siteId)
        if (!publisher.enabled(site)) { _pub.value = PubUi(report = PublishReport(fatal = "النشر المباشر غير مفعّل لهذا الموقع"), siteId = siteId); return@withContext }
        val rows = arts.unpublished(site.id).associateBy { it.title }
        val seen = HashSet<String>(); val entries = ArrayList<PublishEntry>()
        (dir.listFiles() ?: emptyArray()).filter { it.extension == "xml" }.sortedByDescending { it.lastModified() }.forEach { f ->
            runCatching { XmlImport.parse(f.readText()) }.getOrNull()?.forEach { it -> val r = rows[it.title]; if (r != null && seen.add(it.title)) entries += PublishEntry(it, r.id) }
        }
        if (entries.isEmpty()) { _pub.value = PubUi(report = PublishReport(fatal = "لا ملف XML محفوظ يحوي المقالات المتوقفة"), siteId = siteId); return@withContext }
        runPublish(siteId, entries, recheck = true)
    }

    fun clearPub() { _pub.value = PubUi(); pending = null }

    fun refreshFiles() {
        _files.value = (dir.listFiles() ?: emptyArray()).filter { it.extension == "xml" }.sortedByDescending { it.lastModified() }
            .map { f -> ExportFile(f.name, f.length(), f.lastModified(), runCatching { Regex("<entry>").findAll(f.readText()).count() }.getOrDefault(0)) }
    }

    private fun stage(t: String, idx: Int, count: Int, done: Int = 0, total: Int = 0) = _ui.update { ExportUi(true, t, idx, count, done, total, summary = it.summary) }

    private fun repoOf(site: SiteConfig) = site.repo.ifBlank { cfg.repo }
    private fun branchOf(site: SiteConfig) = site.branch.ifBlank { cfg.branch }

    suspend fun check(siteId: String): List<String> = gh.check(repoOf(sites.get(siteId)))

    suspend fun export(siteId: String) = withContext(Dispatchers.Default) {
        _ui.value = ExportUi(true, "تهيئة", 0, 4)
        try {
            val site = sites.get(siteId)
            val rows = arts.readyFor(site.id)
            if (rows.isEmpty()) throw IllegalStateException("لا مقالات جاهزة للتصدير")
            val auto = cfg.autoPush
            val willPublish = publisher.enabled(site) && auto
            val count = if (willPublish) 6 else 4
            if (auto) { if (cfg.token.isEmpty()) throw IllegalStateException("أدخل GitHub Token أو عطّل الرفع التلقائي"); stage("فحص صلاحيات GitHub", 1, count); gh.check() }
            val parsed = rows.map { it to AppJson.decodeFromString(Article.serializer(), it.data) }
            val list = XmlBuilder.assignSlugs(parsed.map { it.second }, history.load(site.id).slugs.toSet() + blog.slugs(site.url))
            val name = ExportNaming.fileName(list.size, LocalDate.now(), site.name) { File(dir, it).exists() }
            val folder = "Download/ArticleStudio/" + site.name.replace(Regex("[\\\\/:*?\"<>|]"), "-") + "/" + name.removeSuffix(".xml")
            val files = ArrayList<ImageFile>()
            list.forEachIndexed { i, a ->
                stage(if (site.photos) "توليد الصور" else "رسم الصور", 2, count, i, list.size)
                val photo = if (site.photos) runCatching { photos.fetch(site, a) }.getOrNull() else null
                if (site.photos && photo == null) bus.log("تعذّرت الصورة الواقعية لـ«${clamp(a.title, 30)}»، رسم تلقائي بدلاً منها", "w")
                val im = renderer.render(a, site, photo); photo?.recycle(); files += ImageFile("${a.finalSlug}-cover.jpg", im.cover); files += ImageFile("${a.finalSlug}-info.jpg", im.info)
            }
            val base: String
            if (auto) base = gh.pushImages(files, repoOf(site), branchOf(site)) { t, i, n -> stage(t, 3, count, i, n) }
            else {
                stage("تجميع ZIP", 3, count)
                base = "https://cdn.jsdelivr.net/gh/${repoOf(site)}@${branchOf(site).ifBlank { "main" }}/images/articles/"
                val zip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z -> files.forEach { f -> z.putNextEntry(ZipEntry(f.name)); z.write(f.bytes); z.closeEntry() } } }.toByteArray()
                saveDownload("images.zip", "application/zip", zip, folder)
            }
            stage("بناء XML وحفظه", 4, count)
            files.forEach { f -> saveDownload(f.name, "image/jpeg", f.bytes, "$folder/images") }
            val xml = XmlBuilder.build(list, base, if (site.schedule > 0) site.schedule else cfg.scheduleHours, blog = blog.all(site.url), site = site)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            File(dir, name).writeText(xml)
            saveDownload(name, "text/xml", xml.toByteArray(), folder)
            runCatching {
                val day = LocalDate.now().toString()
                val items = SiteIndex.items(list, name.removeSuffix(".xml"), day)
                saveDownload("index.json", "application/json", SiteIndex.encode(SiteIndex.doc(site, items, day)).toByteArray(), folder)
                SiteIndex.merge(File(ctx.filesDir, "index"), site, items, day)
            }
            arts.markExported(rows.map { it.id }, System.currentTimeMillis())
            val pubEntries = if (publisher.enabled(site) && auto) runCatching { XmlImport.parse(xml) }.getOrDefault(emptyList()).let { items -> items.mapIndexed { i, it -> PublishEntry(it, rows.getOrNull(i)?.takeIf { items.size == rows.size }?.id) } } else emptyList()
            history.add(list.map { it.finalSlug }, list.map { it.topic }, list.flatMap { a -> a.sources.map { it.url } }, site.id)
            refreshFiles()
            val summary = ExportSummary(name, list.size, files.size, auto, list.map { it.title }, name.removeSuffix(".xml"))
            if (pubEntries.isNotEmpty()) {
                _ui.update { ExportUi(true, "", 5, count, summary = summary) }
                val probes = listOfNotNull(list.first(), list.last()).map { "$base${it.finalSlug}-cover.jpg" }.distinct()
                val r = publishStages(site.id, pubEntries, probes, 5, count)
                _pub.value = PubUi(false, r.total, r.total, r, site.id)
            }
            _ui.value = ExportUi(false, summary = summary)
            if (pubEntries.isEmpty() && publisher.enabled(site)) _pub.value = PubUi(report = PublishReport(fatal = "النشر المباشر يتطلب رفع الصور تلقائياً؛ فعّل «الرفع التلقائي» أو انشر الملف المحفوظ لاحقاً"), siteId = site.id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _ui.value = ExportUi(false, error = clamp(e.message, 160), summary = _ui.value.summary)
        }
    }

    fun clearResult() { _ui.update { ExportUi(summary = null) } }

    private fun saveDownload(name: String, mime: String, bytes: ByteArray, folder: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
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
