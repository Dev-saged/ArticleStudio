package com.sajed.articlestudio.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.GitHubSettings
import com.sajed.articlestudio.engine.AppJson
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.social.Post
import com.sajed.articlestudio.social.ShareText
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.data.SocialDao
import com.sajed.articlestudio.engine.BackupManager
import com.sajed.articlestudio.engine.GitHubClient
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.social.RecipeStore
import com.sajed.articlestudio.social.ShareException
import com.sajed.articlestudio.social.ShareRunner
import com.sajed.articlestudio.social.ShareStore
import com.sajed.articlestudio.social.WebAutomator
import com.sajed.articlestudio.social.WebSessionPublisher
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val store: ShareStore, private val dao: SocialDao, private val automator: WebAutomator, private val web: WebSessionPublisher,
    private val runner: ShareRunner, private val recipes: RecipeStore, private val gh: GitHubClient, private val ghs: GitHubSettings,
    private val sites: SiteStore, private val telegram: com.sajed.articlestudio.social.TelegramPublisher, private val bluesky: com.sajed.articlestudio.social.BlueskyPublisher,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: Context,
) : ViewModel() {
    val accounts = store.accounts
    val jobs = store.jobs
    val marks = store.marks
    val shareable = store.shareable
    val backfill = store.backfill
    val siteList = sites.sites
    private val _msg = MutableStateFlow("")
    val msg: StateFlow<String> = _msg.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val multiProfile get() = automator.multiProfile()

    private fun run(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true; _msg.value = "جارٍ التنفيذ…"
        viewModelScope.launch {
            _msg.value = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: ShareException) { e.message.orEmpty() } catch (e: Exception) { e.message ?: "تعذّر التنفيذ" }
            _busy.value = false
        }
    }

    fun add(platform: String, label: String, extra: Map<String, String>, secret: String, done: (String) -> Unit) = viewModelScope.launch {
        val spec = Platforms.of(platform) ?: return@launch
        if (spec.isWeb && !automator.multiProfile() && dao.accounts().any { it.platform == platform }) { _msg.value = "هذا الجهاز لا يدعم فصل الجلسات: حساب واحد لكل منصة ويب"; return@launch }
        done(store.addAccount(platform, label, extra, secret))
    }

    fun toggle(a: SocialAccountEntity) = viewModelScope.launch { store.update(a.copy(enabled = !a.enabled)) }
    fun update(a: SocialAccountEntity) = viewModelScope.launch { store.update(a) }
    fun remove(id: String) = viewModelScope.launch { store.remove(id) }
    val gaps = store.gaps
    fun setGap(platform: String, m: Int) = store.setGap(platform, m)
    fun publishNow(id: Long) = run { if (store.publishNow(id)) "بدأ النشر الآن" else "المنشور قيد الإرسال أو منشور مسبقاً" }
    fun publishAll(platform: String) = run {
        val m = store.gaps.value[platform] ?: 1
        val n = store.publishAll(platform, m)
        if (n == 0) "لا منشورات منتظرة" else "سيُنشر $n منشور على ${Platforms.of(platform)?.name.orEmpty()} بفاصل $m د"
    }
    fun stopForced(platform: String) = viewModelScope.launch { store.stopForced(platform); _msg.value = "عادت المنشورات لقواعد الحساب" }
    fun skip(id: Long) = viewModelScope.launch { store.skip(id) }
    fun setText(id: Long, text: String) = viewModelScope.launch { store.setText(id, text); _msg.value = "حُفظ النص" }
    fun clearMsg() { _msg.value = "" }
    fun page(platform: String, tab: Int, page: Int) = store.page(platform, tab, page)
    fun runNow() = run { val n = runner.tick(); "عولجت $n مهمة" }

    fun check(a: SocialAccountEntity) = run {
        val spec = Platforms.of(a.platform)
        try { publisher(a).check(a).also { store.markLoggedIn(a.id) } } catch (e: ShareException) {
            dao.setStatus(a.id, if (e.code == "login_required") "needs_login" else "attention", e.message.orEmpty())
            "${spec?.name}: ${e.message}"
        }
    }

    private fun publisher(a: SocialAccountEntity): com.sajed.articlestudio.social.SocialPublisher = when (a.platform) { "telegram" -> telegram; "bluesky" -> bluesky; else -> web }

    fun testPost(a: SocialAccountEntity) = run {
        val spec = Platforms.of(a.platform) ?: return@run "منصة غير معروفة"
        val siteUrl = sites.sites.value.firstOrNull { it.share }?.url?.takeIf { it.startsWith("http") } ?: "https://example.com"
        val text = ShareText.compose(spec, "تجربة نشر تلقائي من مولد المقالات", siteUrl, emptyList())
        try {
            val r = publisher(a).share(a, Post(text, "تجربة نشر تلقائي من مولد المقالات", siteUrl))
            store.markLoggedIn(a.id)
            "تم النشر التجريبي على ${a.label}" + if (r.postUrl.isNotBlank()) "\n${r.postUrl}" else ""
        } catch (e: ShareException) {
            dao.setStatus(a.id, if (e.code == "login_required") "needs_login" else "attention", e.message.orEmpty())
            e.message.orEmpty() + if (e.diag.isNotBlank()) "\n" + e.diag.take(400) else ""
        }
    }

    fun saveCreds(a: SocialAccountEntity, extra: Map<String, String>, secret: String) = run {
        store.setCredentials(a.id, extra, secret)
        val fresh = dao.account(a.id) ?: return@run "الحساب غير موجود"
        try { publisher(fresh).check(fresh).also { store.markLoggedIn(a.id) } } catch (e: ShareException) {
            dao.setStatus(a.id, if (e.code == "login_required") "needs_login" else "attention", e.message.orEmpty()); e.message.orEmpty()
        }
    }

    fun setBackfill(v: Boolean) = store.setBackfill(v)
    fun setSiteShare(id: String, v: Boolean) = sites.upsert(sites.get(id).copy(share = v))
    fun shareNow(articleId: Long) = run { if (store.shareNow(articleId) > 0) "أُضيف للطابور وسيُنشر خلال دقائق" else "لا حساب مفعّل أو المقال مسجّل مسبقاً" }

    fun confirmLogin(a: SocialAccountEntity, done: () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _msg.value = "أتحقق من الجلسة…"
        viewModelScope.launch {
            try { web.check(a); store.markLoggedIn(a.id); _msg.value = "تم حفظ الجلسة"; done() } catch (e: ShareException) { _msg.value = e.message.orEmpty().ifEmpty { "لم تُحفظ الجلسة" } }
            _busy.value = false
        }
    }

    fun newWebView(a: SocialAccountEntity, context: Context) = automator.newWebView(context, automator.profileName(a.id), Platforms.of(a.platform)?.desktop == true)
    suspend fun account(id: String) = dao.account(id)

    fun updateRecipes() = run {
        val t = gh.getText(ghs.repo, BackupManager.BRANCH, "social/recipes.json") ?: return@run "لا ملف social/recipes.json في فرع ${BackupManager.BRANCH}"
        recipes.install(t)?.let { "وصفات مرفوضة: $it" } ?: "حُدّثت الوصفات"
    }
    fun resetRecipes() { recipes.reset(); _msg.value = "عادت الوصفات المضمّنة" }
}

internal fun shareIntent(c: Context, text: String) = c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "مشاركة يدوية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

internal fun extraVal(a: SocialAccountEntity, k: String) = runCatching { AppJson.parseToJsonElement(a.extra).jsonObject[k]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")

@Composable
fun SocialLoginScreen(accountId: String, onDone: () -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    var acc by remember { mutableStateOf<SocialAccountEntity?>(null) }
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(accountId) { acc = vm.account(accountId) }
    val a = acc
    BackHandler { onDone() }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.Back, "رجوع", onDone)
            Text(a?.label.orEmpty(), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        if (a == null) return@Column
        Text("سجّل الدخول كالمعتاد (قد يطلب رمزاً أو تأكيداً). حين يظهر حسابك اضغط الزر أسفل الصفحة لحفظ الجلسة.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
        val url = Platforms.of(a.platform)?.loginUrl.orEmpty()
        var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
        DisposableEffect(Unit) { onDispose { web?.destroy(); web = null } }
        AndroidView(factory = { ctx -> vm.newWebView(a, ctx).also { w -> w.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); w.webViewClient = android.webkit.WebViewClient(); w.loadUrl(url); web = w } }, modifier = Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, color = c.text)
            PrimaryButton("تم تسجيل الدخول، احفظ الجلسة", Icons.Check, { vm.confirmLogin(a) { onDone() } }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}
