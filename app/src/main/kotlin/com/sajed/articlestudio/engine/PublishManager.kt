package com.sajed.articlestudio.engine

import android.content.IntentSender
import com.sajed.articlestudio.data.ArticleDao
import com.sajed.articlestudio.data.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

class PublishReport(
    val ok: Int = 0, val skipped: Int = 0, val failed: List<String> = emptyList(), val urls: List<String> = emptyList(),
    val state: PubState = PubState.Draft, val consent: IntentSender? = null, val fatal: String? = null,
) { val total get() = ok + skipped + failed.size }

/** قطعة نشر: العنصر مع معرّف المقال المحلي إن وُجد لتسجيل رابطه ومنع تكرار نشره. */
class PublishEntry(val item: PublishItem, val articleId: Long? = null)

@Singleton
class PublishManager @Inject constructor(
    private val http: OkHttpClient, private val secrets: SecretStore, private val sites: SiteStore,
    private val arts: ArticleDao, private val auth: GoogleAuth, private val blog: BlogIndex,
) {
    private fun key(siteId: String) = "bridge-$siteId"

    /** سر الجسر لكل موقع: يُولَّد مرة ويُحفظ مشفراً، ويُضمَّن في سكربت المستخدم فقط. */
    fun secretFor(siteId: String): String =
        secrets.get(key(siteId)).ifEmpty { newSecret().also { secrets.put(key(siteId), it) } }

    fun resetSecret(siteId: String): String = newSecret().also { secrets.put(key(siteId), it) }

    fun script(site: SiteConfig): String = BridgeScript.TEMPLATE.replace("__SECRET__", secretFor(site.id))

    private fun newSecret(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..28).map { a[r.nextInt(a.length)] }.joinToString("")
    }

    fun enabled(site: SiteConfig) = site.pubMode == "script" || site.pubMode == "google"

    /** يبني الناشر المناسب؛ لوضع جوجل يلزم رمز وصول صالح (Consent يعيده كتقرير). */
    private suspend fun build(site: SiteConfig): Pair<BlogPublisher?, PublishReport?> = when (site.pubMode) {
        "script" -> ScriptPublisher(http, site.scriptUrl, secretFor(site.id), site.url) to null
        "google" -> when (val r = auth.authorize(site.googleAccount.ifBlank { null })) {
            is GoogleAuth.Result.Token -> {
                if (site.googleAccount.isBlank() && !r.email.isNullOrBlank()) sites.upsert(site.copy(googleAccount = r.email))
                GooglePublisher(http, { r.token }, site.url) to null
            }
            is GoogleAuth.Result.Consent -> null to PublishReport(consent = r.sender, fatal = "يلزم ربط حساب جوجل أولاً")
            is GoogleAuth.Result.Failure -> null to PublishReport(fatal = r.message)
        }
        else -> null to PublishReport(fatal = "النشر المباشر غير مفعّل لهذا الموقع")
    }

    suspend fun test(site: SiteConfig): Result<String> {
        val (p, rep) = build(site)
        if (p == null) return Result.failure(PublishException(rep?.fatal ?: "غير مفعّل"))
        return try { Result.success(p.test()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    /** ينشر العناصر تباعاً. فشل عنصر لا يوقف البقية، أما خطأ الصلاحية فيوقف كل شيء. */
    suspend fun publish(siteId: String, entries: List<PublishEntry>, step: (Int, Int) -> Unit = { _, _ -> }): PublishReport {
        val site = sites.get(siteId)
        val state = PubState.of(site.pubState)
        val (p, rep) = build(site)
        if (p == null) return rep ?: PublishReport(fatal = "غير مفعّل")
        var ok = 0; var skipped = 0
        val failed = ArrayList<String>(); val urls = ArrayList<String>()
        entries.forEachIndexed { i, e ->
            step(i, entries.size)
            val local = e.articleId?.let { arts.byId(it) } ?: arts.byTitle(site.id, e.item.title)
            if (local != null && local.remoteUrl.isNotEmpty()) { skipped++; return@forEachIndexed }
            try {
                val r = p.publish(e.item, state)
                ok++; if (r.url.isNotEmpty()) urls += r.url
                (e.articleId ?: local?.id)?.let { arts.setRemote(it, r.url.ifEmpty { "published" }) }
                if (i < entries.lastIndex) delay(400)
            } catch (x: CancellationException) { throw x } catch (x: PublishException) {
                if (x.auth) return PublishReport(ok, skipped, failed + "${e.item.title}: ${x.message}", urls, state, fatal = x.message)
                failed += "${e.item.title}: ${x.message}"
            } catch (x: Exception) { failed += "${e.item.title}: ${clamp(x.message, 80)}" }
        }
        step(entries.size, entries.size)
        if (ok > 0) runCatching { blog.refresh(site.url, force = true) }
        return PublishReport(ok, skipped, failed, urls, state)
    }

    /** ينشر محتوى ملف XML (مُصدَّر سابقاً أو مختار يدوياً). */
    suspend fun publishXml(siteId: String, xml: String, step: (Int, Int) -> Unit = { _, _ -> }): PublishReport {
        val items = try { XmlImport.parse(xml) } catch (e: Exception) { return PublishReport(fatal = "الملف ليس XML صالحاً بصيغة بلوجر") }
        if (items.isEmpty()) return PublishReport(fatal = "لا تدوينات داخل الملف")
        return publish(siteId, items.map { PublishEntry(it) }, step)
    }
}

object BridgeScript {
    /** سكربت Apps Script الذي ينسخه المستخدم؛ السر يطابق ما يرسله التطبيق. */
    val TEMPLATE = """
const SECRET = '__SECRET__';

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents);
    if (req.secret !== SECRET) return out({ ok: false, error: 'السر غير مطابق (secret)' });
    const blog = Blogger.Blogs.getByUrl(String(req.blogUrl).replace(/\/+${'$'}/, '') + '/');
    if (req.action === 'test') return out({ ok: true, name: blog.name, blogId: blog.id });
    if (req.action === 'post') {
      const post = { kind: 'blogger#post', title: req.title, content: req.content, labels: req.labels || [] };
      if (req.publishDate) post.published = req.publishDate;
      const res = Blogger.Posts.insert(post, blog.id, { isDraft: !!req.draft });
      return out({ ok: true, id: res.id, url: res.url });
    }
    return out({ ok: false, error: 'إجراء غير معروف' });
  } catch (err) {
    return out({ ok: false, error: String(err) });
  }
}

function out(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
""".trimIndent()
}
