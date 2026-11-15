package com.sajed.articlestudio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sajed.articlestudio.engine.GoogleAuth
import com.sajed.articlestudio.engine.PublishManager
import com.sajed.articlestudio.engine.SigningInfo
import com.sajed.articlestudio.engine.SiteConfig
import com.sajed.articlestudio.engine.SiteRules
import com.sajed.articlestudio.engine.SiteStore
import com.sajed.articlestudio.engine.clamp
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** نص دليل الربط لكل طريقة؛ كل خطوة عنوان وشرح قصير وإجراء نسخ اختياري، فلا يتداخل الكلام. */
class GuideStep(val title: String, val body: String, val copy: String? = null, val copyLabel: String = "نسخ")

object PublishGuide {
    const val CLOUD = "https://console.cloud.google.com/"
    const val SCRIPT = "https://script.google.com/"

    fun script(code: String) = listOf(
        GuideStep("افتح Apps Script", "ادخل إلى script.google.com بنفس حساب جوجل الذي يملك المدونة، ثم اضغط «مشروع جديد»."),
        GuideStep("الصق الكود", "احذف الكود الافتراضي والصق الكود التالي كما هو. فيه سرّ خاص بهذا الموقع، فلا تشاركه مع أحد.", code, "نسخ الكود"),
        GuideStep("أضف خدمة Blogger", "من القائمة اليسرى اضغط «الخدمات» (+)، اختر «Blogger API» ثم «إضافة»."),
        GuideStep("انشره كتطبيق ويب", "اضغط «نشر» ثم «عملية نشر جديدة» ثم اختر النوع «تطبيق ويب». اجعل «تنفيذ بصفتي: أنا» و«من لديه حق الوصول: أي شخص» ثم «نشر» ووافق على الصلاحيات. إن ظهرت «التطبيق غير موثّق» فاختر «متقدم» ثم «المتابعة»."),
        GuideStep("الصق الرابط هنا", "انسخ «عنوان URL لتطبيق الويب» (ينتهي بـ /exec) والصقه في الحقل أدناه، ثم اضغط «حفظ واختبار»."),
    )

    fun google(pkg: String, sha1: String) = listOf(
        GuideStep("أنشئ مشروعاً", "افتح console.cloud.google.com بحساب المدونة، ثم أنشئ مشروعاً جديداً بأي اسم."),
        GuideStep("فعّل Blogger API", "من «APIs & Services» ← «Library» ابحث عن «Blogger API v3» ثم اضغط «Enable»."),
        GuideStep("جهّز شاشة الموافقة", "من «Google Auth Platform» اضغط «Get started»، اكتب اسماً وبريدك، واختر الجمهور «External». ثم من «Audience» أضف بريدك ضمن «Test users»، ومن «Data Access» أضف صلاحية Blogger: auth/blogger."),
        GuideStep("أنشئ عميل Android", "من «Clients» اضغط «Create client» واختر النوع «Android». ضع اسم الحزمة التالي وبصمة SHA-1 أدناه ثم «Create».", pkg, "نسخ اسم الحزمة"),
        GuideStep("بصمة SHA-1 لهذا التطبيق", if (sha1.isEmpty()) "تعذّر قراءة البصمة على هذا الجهاز؛ استخرجها بالأمر keytool من ملف التوقيع." else "هذه بصمة النسخة المثبّتة حالياً. إن نزّلت نسخة موقّعة بمفتاح آخر فستتغير.", sha1.ifEmpty { null }, "نسخ البصمة"),
        GuideStep("اربط الحساب هنا", "اضغط «ربط حساب جوجل» أدناه ووافق. إن ظهر تحذير «غير موثّق» فاختر «متقدم» ثم «المتابعة». بعدها اضغط «اختبار الاتصال»."),
    )

    const val LIMIT = "ملاحظة: النشر عبر واجهة Blogger لا يضبط الرابط الدائم ولا وصف الميتا للمنشور (يحدّدهما بلوجر من العنوان والمقدمة). الاستيراد عبر ملف XML يضبطهما، فاستعمله حين يهمك الرابط الدقيق."
}

sealed interface TestUi {
    data object Idle : TestUi
    data object Running : TestUi
    class Ok(val name: String) : TestUi
    class Fail(val message: String) : TestUi
}

@HiltViewModel
class PublishViewModel @Inject constructor(val store: SiteStore, private val pm: PublishManager, private val auth: GoogleAuth) : ViewModel() {
    private val _test = MutableStateFlow<TestUi>(TestUi.Idle)
    val test: StateFlow<TestUi> = _test.asStateFlow()
    private val _consent = MutableStateFlow<android.content.IntentSender?>(null)
    val consent: StateFlow<android.content.IntentSender?> = _consent.asStateFlow()

    fun update(s: SiteConfig) { store.upsert(s); _test.value = TestUi.Idle }
    fun script(s: SiteConfig) = pm.script(s)
    fun resetSecret(s: SiteConfig) { pm.resetSecret(s.id); _test.value = TestUi.Idle }

    fun runTest(siteId: String) = viewModelScope.launch {
        _test.value = TestUi.Running
        val r = pm.test(store.get(siteId))
        _test.value = r.fold({ TestUi.Ok(it) }, { TestUi.Fail(clamp(it.message, 200)) })
    }

    /** يحفظ رابط الجسر بعد التحقق منه ثم يختبره. */
    fun saveScript(s: SiteConfig, url: String) {
        val n = s.copy(scriptUrl = url.trim())
        val e = SiteRules.validate(n)["scriptUrl"]
        if (e != null) { _test.value = TestUi.Fail(e); return }
        store.upsert(n); runTest(s.id)
    }

    fun connect(siteId: String) = viewModelScope.launch {
        _test.value = TestUi.Running
        when (val r = auth.authorize(store.get(siteId).googleAccount.ifBlank { null })) {
            is GoogleAuth.Result.Consent -> { _consent.value = r.sender; _test.value = TestUi.Idle }
            is GoogleAuth.Result.Token -> { r.email?.let { store.upsert(store.get(siteId).copy(googleAccount = it)) }; runTest(siteId) }
            is GoogleAuth.Result.Failure -> _test.value = TestUi.Fail(r.message)
        }
    }

    fun onConsent(siteId: String, data: android.content.Intent?) {
        _consent.value = null
        when (val r = auth.fromIntent(data)) {
            is GoogleAuth.Result.Token -> { r.email?.let { store.upsert(store.get(siteId).copy(googleAccount = it)) }; runTest(siteId) }
            is GoogleAuth.Result.Failure -> _test.value = TestUi.Fail(r.message)
            else -> Unit
        }
    }

    fun disconnect(siteId: String) { store.upsert(store.get(siteId).copy(googleAccount = "")); _test.value = TestUi.Idle }
}

private fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishSheet(siteId: String, onClose: () -> Unit, vm: PublishViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val sites by vm.store.sites.collectAsStateWithLifecycle()
    val site = sites.firstOrNull { it.id == siteId } ?: return
    val test by vm.test.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    var url by remember(site.id) { mutableStateOf(site.scriptUrl) }
    var copied by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onConsent(site.id, it.data) }
    LaunchedEffect(consent) { consent?.let { launcher.launch(IntentSenderRequest.Builder(it).build()) } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("النشر المباشر", style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(site.name, style = MaterialTheme.typography.bodyMedium, color = c.muted)

            Section("طريقة النشر")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("متوقف", site.pubMode == "off", { vm.update(site.copy(pubMode = "off")) })
                AppChip("جسر Apps Script", site.pubMode == "script", { vm.update(site.copy(pubMode = "script")) })
                AppChip("حساب جوجل", site.pubMode == "google", { vm.update(site.copy(pubMode = "google")) })
            }
            if (site.pubMode == "off") {
                Compare()
            } else {
                Section("حالة المنشور")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip("مسودة", site.pubState == "draft", { vm.update(site.copy(pubState = "draft")) })
                    AppChip("نشر مباشر", site.pubState == "live", { vm.update(site.copy(pubState = "live")) })
                    AppChip("مجدول", site.pubState == "scheduled", { vm.update(site.copy(pubState = "scheduled")) })
                }
                Text(
                    when (site.pubState) {
                        "live" -> "تظهر المقالات للقراء فور نشرها. راجع جودتها أولاً."
                        "scheduled" -> "تُنشر بفارق الساعات المحدد في إعدادات الموقع، وإن كان 0 فتُنشر فوراً."
                        else -> "تصل المقالات إلى مسودات بلوجر لتراجعها وتنشرها بنفسك. هذا الأكثر أماناً."
                    }, style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
                Section(if (site.pubMode == "script") "ربط جسر Apps Script" else "ربط حساب جوجل")
                val steps = if (site.pubMode == "script") PublishGuide.script(vm.script(site)) else PublishGuide.google(ctx.packageName, SigningInfo.sha1(ctx))
                steps.forEachIndexed { i, st ->
                    StepCard(i + 1, st, copied == st.title) { st.copy?.let { t -> copy(ctx, st.title, t); copied = st.title } }
                }
                if (site.pubMode == "script") {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it.filter { ch -> !ch.isWhitespace() }.take(300) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("رابط تطبيق الويب") }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text, textDirection = TextDirection.Ltr),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.goldText, unfocusedBorderColor = c.line, focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted, cursorColor = c.goldText),
                    )
                    PrimaryButton("حفظ واختبار", Icons.Check, { vm.saveScript(site, url) }, enabled = test !is TestUi.Running)
                    Text("إعادة توليد السر (تحتاج لصق الكود ونشر نسخة جديدة)", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.clickable { vm.resetSecret(site) }.heightIn(min = 44.dp).padding(top = 12.dp))
                } else {
                    if (site.googleAccount.isNotBlank()) Text("الحساب المربوط: ${site.googleAccount.ltr()}", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    PrimaryButton(if (site.googleAccount.isBlank()) "ربط حساب جوجل" else "إعادة الربط واختبار", Icons.Link, { vm.connect(site.id) }, enabled = test !is TestUi.Running)
                    if (site.googleAccount.isNotBlank()) Text("فك الربط", style = MaterialTheme.typography.labelMedium, color = c.red, modifier = Modifier.clickable { vm.disconnect(site.id) }.heightIn(min = 44.dp).padding(top = 12.dp))
                }
                when (val t = test) {
                    TestUi.Idle -> Unit
                    TestUi.Running -> Text("جارٍ الاتصال…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    is TestUi.Ok -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Check, null, Modifier.size(22.dp), tint = c.ok); Text("اتصال سليم بمدونة «${t.name}»", style = MaterialTheme.typography.bodyMedium, color = c.ok)
                    }
                    is TestUi.Fail -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Warning, null, Modifier.size(22.dp), tint = c.red); Text(t.message, style = MaterialTheme.typography.bodyMedium, color = c.red, modifier = Modifier.weight(1f))
                    }
                }
                Text(PublishGuide.LIMIT, style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text("يتطلب النشر المباشر تفعيل «الرفع التلقائي» لصور المقالات في تبويب التصدير ليظهر الغلاف.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Box(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Section(t: String) { Text(t, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.goldText) }

@Composable
private fun Compare() {
    val c = AppTheme.colors
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("أيهما أنسب لك؟", style = MaterialTheme.typography.titleMedium, color = c.text)
            Text("جسر Apps Script: الأسرع إعداداً، بلا Google Cloud. تلصق كوداً مرة واحدة لكل حساب. مناسب لمن يسلّم التطبيق لغيره أو يدير حسابات كثيرة.", style = MaterialTheme.typography.bodyMedium, color = c.text)
            Text("حساب جوجل: ضغطة واحدة بعد إعداد مشروع Cloud مرة واحدة. أنعم استعمالاً وأقل خطوات يدوية لاحقاً.", style = MaterialTheme.typography.bodyMedium, color = c.text)
            Text("اختر إحداهما أعلاه لتظهر خطواتها.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun StepCard(n: Int, st: GuideStep, copied: Boolean, onCopy: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(c.gold.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(n.toString().ltr(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = c.goldText)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(st.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.text)
            Text(st.body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            st.copy?.let { code ->
                if (code.length < 120) SelectionContainer { Text(code.ltr(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = c.text, modifier = Modifier.fillMaxWidth().background(c.bg2, RoundedCornerShape(8.dp)).padding(8.dp)) }
                Row(Modifier.heightIn(min = 44.dp).clickable(onClick = onCopy), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (copied) Icons.Check else Icons.Copy, null, Modifier.size(18.dp), tint = if (copied) c.ok else c.goldText)
                    Text(if (copied) "تم النسخ" else st.copyLabel, style = MaterialTheme.typography.labelLarge, color = if (copied) c.ok else c.goldText)
                }
            }
        }
    }
}
