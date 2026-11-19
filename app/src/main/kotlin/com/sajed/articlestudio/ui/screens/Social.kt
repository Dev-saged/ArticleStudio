package com.sajed.articlestudio.ui.screens

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.data.GitHubSettings
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.data.SocialDao
import com.sajed.articlestudio.engine.BackupManager
import com.sajed.articlestudio.engine.GitHubClient
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.social.RecipeStore
import com.sajed.articlestudio.social.ShareException
import com.sajed.articlestudio.social.ShareRunner
import com.sajed.articlestudio.social.ShareScheduler
import com.sajed.articlestudio.social.ShareStore
import com.sajed.articlestudio.social.WebAutomator
import com.sajed.articlestudio.social.WebSessionPublisher
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.Panel
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
    private val telegram: com.sajed.articlestudio.social.TelegramPublisher, private val bluesky: com.sajed.articlestudio.social.BlueskyPublisher,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: Context,
) : ViewModel() {
    val accounts = store.accounts
    val jobs = store.jobs
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
    fun retry(id: Long) = viewModelScope.launch { store.retry(id) }
    fun skip(id: Long) = viewModelScope.launch { store.skip(id) }
    fun runNow() = run { val n = runner.tick(); "عولجت $n مهمة" }

    fun check(a: SocialAccountEntity) = run {
        val spec = Platforms.of(a.platform)
        val pub: com.sajed.articlestudio.social.SocialPublisher = when (a.platform) { "telegram" -> telegram; "bluesky" -> bluesky; else -> web }
        try { pub.check(a).also { dao.markOk(a.id, System.currentTimeMillis()) } } catch (e: ShareException) {
            if (e.code == "login_required") dao.setStatus(a.id, "needs_login", e.message.orEmpty())
            "${spec?.name}: ${e.message}"
        }
    }

    fun confirmLogin(a: SocialAccountEntity, done: () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _msg.value = "أتحقق من الجلسة…"
        viewModelScope.launch {
            try { web.check(a); store.markLoggedIn(a.id); _msg.value = "تم حفظ الجلسة"; done() } catch (e: ShareException) { _msg.value = e.message.orEmpty().ifEmpty { "لم تُحفظ الجلسة" } }
            _busy.value = false
        }
    }

    fun newWebView(a: SocialAccountEntity, context: Context) = automator.newWebView(context, automator.profileName(a.id))
    suspend fun account(id: String) = dao.account(id)

    fun updateRecipes() = run {
        val t = gh.getText(ghs.repo, BackupManager.BRANCH, "social/recipes.json") ?: return@run "لا ملف social/recipes.json في فرع ${BackupManager.BRANCH}"
        recipes.install(t)?.let { "وصفات مرفوضة: $it" } ?: "حُدّثت الوصفات"
    }
    fun resetRecipes() { recipes.reset(); _msg.value = "عادت الوصفات المضمّنة" }
}

private fun shareIntent(c: Context, text: String) = c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "مشاركة يدوية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun statusLabel(s: String) = when (s) { "pending" -> "بالانتظار"; "running" -> "قيد الإرسال"; "done" -> "تمّت"; "failed" -> "فشلت"; "needs_you" -> "تحتاج تدخلك"; "skipped" -> "تُخطّيت"; else -> s }
private fun accStatus(s: String) = when (s) { "ok" -> "جاهز"; "needs_login" -> "يلزم الدخول"; "new" -> "لم يُفحص"; else -> s }

@Composable
fun SocialScreen(onBack: () -> Unit, onLogin: (String) -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsStateWithLifecycle(emptyList())
    val jobs by vm.jobs.collectAsStateWithLifecycle(emptyList())
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SocialAccountEntity?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.Back, "رجوع", onBack)
            Text("مشاركة المقالات", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
            IconBtn(Icons.Plus, "إضافة حساب", { adding = true })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("الحسابات (${accounts.size})", tab == 0, { tab = 0 })
            AppChip("الطابور (${jobs.count { it.status == "pending" || it.status == "needs_you" }})", tab == 1, { tab = 1 })
            AppChip("الوصفات", tab == 2, { tab = 2 })
        }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.text)
        when (tab) {
            0 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (accounts.isEmpty()) item { Text("أضف حساباً: تيليغرام وبلوسكي برمز دائم، وفيسبوك وX وثريدز ولينكدإن بجلسة دخول تُحفظ داخل التطبيق.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                items(accounts, key = { it.id }) { a -> AccountCard(a, busy, { vm.check(a) }, { onLogin(a.id) }, { vm.toggle(a) }, { editing = a }) }
                item { Text("لكل موقع مفتاح «شارك المقال على حساباتي» في إعدادات الموقع. المسودات لا تُشارك لأن رابطها غير عام.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
            }
            1 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { PrimaryButton("نفّذ المستحق الآن", Icons.Play, { vm.runNow() }, Modifier.fillMaxWidth(), enabled = !busy) }
                if (jobs.isEmpty()) item { Text("تظهر هنا كل مشاركة تُجدول بعد نشر مقال.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                items(jobs, key = { it.id }) { j -> JobCard(j, accounts.firstOrNull { it.id == j.accountId }, { vm.retry(j.id) }, { vm.skip(j.id) }, { shareIntent(ctx, j.text.ifBlank { j.title + "\n" + j.url }) }) }
            }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("وصفات الواجهة (محددات الأزرار) بيانات قابلة للتحديث دون إصدار نسخة جديدة: ضع الملف في social/recipes.json على فرع app-backup بمستودع GitHub المضبوط ثم اضغط تحديث.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                PrimaryButton("تحديث الوصفات من GitHub", Icons.Refresh, { vm.updateRecipes() }, Modifier.fillMaxWidth(), enabled = !busy)
                AppChip("العودة للوصفات المضمّنة", false, { vm.resetRecipes() })
                if (!vm.multiProfile) Text("هذا الجهاز لا يدعم فصل الجلسات: يُسمح بحساب ويب واحد لكل منصة.", style = MaterialTheme.typography.bodySmall, color = c.red)
            }
        }
    }
    if (adding) AddAccountSheet({ adding = false }) { p, l, e, s ->
        adding = false
        vm.add(p, l, e, s) { id -> if (Platforms.of(p)?.isWeb == true) onLogin(id) }
    }
    editing?.let { a -> EditAccountSheet(a, { editing = null }, { vm.update(it); editing = null }, { vm.remove(a.id); editing = null }) }
}

@Composable
private fun AccountCard(a: SocialAccountEntity, busy: Boolean, onCheck: () -> Unit, onLogin: () -> Unit, onToggle: () -> Unit, onEdit: () -> Unit) {
    val c = AppTheme.colors
    val spec = Platforms.of(a.platform)
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("${spec?.name.orEmpty()} · ${if (spec?.isWeb == true) "جلسة دائمة" else "رمز دائم"} · سقف ${a.dailyCap}/يوم · فاصل ${a.gapMin} د", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(if (!a.enabled) "معطّل" else accStatus(a.status), style = MaterialTheme.typography.labelLarge, color = if (a.enabled && a.status == "ok") c.text else c.red)
        }
        if (a.statusNote.isNotBlank()) Text(a.statusNote, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (spec?.isWeb == true) AppChip(if (a.status == "ok") "إعادة تسجيل الدخول" else "تسجيل الدخول", a.status != "ok", onLogin)
            AppChip("فحص الحساب", false, { if (!busy) onCheck() })
            AppChip(if (a.enabled) "تعطيل" else "تفعيل", false, onToggle)
            AppChip("إعدادات", false, onEdit)
        }
    }
}

@Composable
private fun JobCard(j: ShareJobEntity, a: SocialAccountEntity?, onRetry: () -> Unit, onSkip: () -> Unit, onManual: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(j.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 2)
            Text(statusLabel(j.status), style = MaterialTheme.typography.labelLarge, color = if (j.status == "failed" || j.status == "needs_you") c.red else c.muted)
        }
        Text((a?.label ?: j.platform) + " · " + java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(if (j.status == "done") j.doneAt else j.runAt)), style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (j.text.isNotBlank()) Text(j.text, style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 4)
        if (j.error.isNotBlank()) Text(j.error, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 6)
        if (j.status == "needs_you" || j.status == "failed" || j.status == "pending") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (j.status != "pending") AppChip("أعد المحاولة", false, onRetry)
            AppChip("انشر يدوياً", false, onManual)
            AppChip("تخطَّ", false, onSkip)
        }
    }
}

@Composable
private fun Input(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, number: Boolean = false) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Box(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp)) {
            BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.text),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else if (number) KeyboardType.Number else KeyboardType.Text))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onClose: () -> Unit, onAdd: (String, String, Map<String, String>, String) -> Unit) {
    val c = AppTheme.colors
    var platform by remember { mutableStateOf("facebook") }
    var label by remember { mutableStateOf("") }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    val spec = Platforms.of(platform)!!
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إضافة حساب", style = MaterialTheme.typography.titleLarge, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Platforms.all.forEach { p -> AppChip(p.name, platform == p.id, { platform = p.id; f1 = ""; f2 = "" }) }
            }
            Input("اسم للحساب (اختياري)", label, { label = it })
            when (platform) {
                "telegram" -> { Input("توكن البوت من BotFather", f1, { f1 = it }, secret = true); Input("معرّف القناة أو المحادثة (@channel أو رقم)", f2, { f2 = it }) }
                "bluesky" -> { Input("المعرّف (name.bsky.social)", f1, { f1 = it }); Input("كلمة مرور التطبيق (App Password)", f2, { f2 = it }, secret = true) }
                else -> Text("بعد الإضافة تُفتح صفحة دخول ${spec.name} داخل التطبيق: سجّل الدخول مرة واحدة وتبقى الجلسة محفوظة على جهازك.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            val ready = when (platform) { "telegram", "bluesky" -> f1.isNotBlank() && f2.isNotBlank(); else -> true }
            PrimaryButton("إضافة", Icons.Check, {
                when (platform) {
                    "telegram" -> onAdd(platform, label, mapOf("chat" to f2), f1)
                    "bluesky" -> onAdd(platform, label, mapOf("handle" to f1.removePrefix("@")), f2)
                    else -> onAdd(platform, label, emptyMap(), "")
                }
            }, Modifier.fillMaxWidth(), enabled = ready)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountSheet(a: SocialAccountEntity, onClose: () -> Unit, onSave: (SocialAccountEntity) -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    var label by remember { mutableStateOf(a.label) }
    var cap by remember { mutableStateOf(a.dailyCap.toString()) }
    var gap by remember { mutableStateOf(a.gapMin.toString()) }
    var from by remember { mutableStateOf(a.winFrom.toString()) }
    var to by remember { mutableStateOf(a.winTo.toString()) }
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إعدادات الحساب", style = MaterialTheme.typography.titleLarge, color = c.text)
            Input("الاسم", label, { label = it })
            Input("الحد الأقصى للمشاركات يومياً", cap, { cap = it.filter(Char::isDigit).take(2) }, number = true)
            Input("أقل فاصل بين مشاركتين (دقائق)", gap, { gap = it.filter(Char::isDigit).take(3) }, number = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Input("من الساعة", from, { from = it.filter(Char::isDigit).take(2) }, number = true) }
                Box(Modifier.weight(1f)) { Input("إلى الساعة", to, { to = it.filter(Char::isDigit).take(2) }, number = true) }
            }
            PrimaryButton("حفظ", Icons.Check, {
                onSave(a.copy(label = label, dailyCap = cap.toIntOrNull() ?: a.dailyCap, gapMin = gap.toIntOrNull() ?: a.gapMin, winFrom = from.toIntOrNull() ?: a.winFrom, winTo = to.toIntOrNull() ?: a.winTo))
            }, Modifier.fillMaxWidth())
            AppChip(if (confirm) "تأكيد الحذف (يمسح الجلسة والطابور)" else "حذف الحساب", confirm, { if (confirm) onDelete() else confirm = true })
        }
    }
}

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
