package com.sajed.articlestudio.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme

@Composable
internal fun AccountCard(a: SocialAccountEntity, busy: Boolean, onCheck: () -> Unit, onLogin: () -> Unit, onToggle: () -> Unit, onEdit: () -> Unit, onTest: () -> Unit, onCreds: () -> Unit) {
    val c = AppTheme.colors
    val spec = Platforms.of(a.platform)
    var confirmTest by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("${spec?.name.orEmpty()} · ${if (spec?.isWeb == true) "جلسة دائمة" else "رمز دائم"} · سقف ${a.dailyCap}/يوم · فاصل ${a.gapMin} د", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(if (!a.enabled) "معطّل" else accStatus(a.status, spec?.isWeb == true), style = MaterialTheme.typography.labelLarge, color = if (a.enabled && a.status == "ok") c.text else c.red)
        }
        if (a.statusNote.isNotBlank()) Text(a.statusNote, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (spec?.isWeb == true) AppChip(if (a.status == "ok") "إعادة تسجيل الدخول" else "تسجيل الدخول", a.status != "ok", onLogin)
            if (spec?.isWeb != true) AppChip("تعديل البيانات", a.status != "ok", onCreds)
            AppChip("فحص الحساب", false, { if (!busy) onCheck() })
            AppChip(if (confirmTest) "تأكيد: انشر الآن فعلاً" else "نشر تجريبي", confirmTest, { if (busy) Unit else if (confirmTest) { confirmTest = false; onTest() } else confirmTest = true })
            AppChip(if (a.enabled) "تعطيل" else "تفعيل", false, onToggle)
            AppChip("إعدادات", false, onEdit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CredsSheet(a: SocialAccountEntity, onClose: () -> Unit, onSave: (Map<String, String>, String) -> Unit) {
    val c = AppTheme.colors
    val tg = a.platform == "telegram"
    var f1 by remember { mutableStateOf(if (tg) "" else extraVal(a, "handle")) }
    var f2 by remember { mutableStateOf(if (tg) extraVal(a, "chat") else "") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("بيانات الحساب", style = MaterialTheme.typography.titleLarge, color = c.text)
            if (tg) {
                SocialInput("توكن البوت (اتركه فارغاً للإبقاء على المحفوظ)", f1, { f1 = it }, secret = true)
                SocialInput("معرّف القناة أو المحادثة (@channel أو رقم)", f2, { f2 = it })
                Text("للقناة: أضف البوت مشرفاً بصلاحية نشر الرسائل. للمحادثة الخاصة: افتح البوت واضغط Start أولاً.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            } else {
                SocialInput("المعرّف (name.bsky.social)", f1, { f1 = it })
                SocialInput("كلمة مرور التطبيق (اتركها فارغة للإبقاء)", f2, { f2 = it }, secret = true)
            }
            PrimaryButton("حفظ وفحص", Icons.Check, {
                if (tg) onSave(mapOf("chat" to f2), f1) else onSave(mapOf("handle" to f1.removePrefix("@")), f2)
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun SocialInput(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, number: Boolean = false) {
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
internal fun AddAccountSheet(onClose: () -> Unit, onAdd: (String, String, Map<String, String>, String) -> Unit) {
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
            SocialInput("اسم للحساب (اختياري)", label, { label = it })
            when (platform) {
                "telegram" -> { SocialInput("توكن البوت من BotFather", f1, { f1 = it }, secret = true); SocialInput("معرّف القناة أو المحادثة (@channel أو رقم)", f2, { f2 = it }) }
                "bluesky" -> { SocialInput("المعرّف (name.bsky.social)", f1, { f1 = it }); SocialInput("كلمة مرور التطبيق (App Password)", f2, { f2 = it }, secret = true) }
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
internal fun EditAccountSheet(a: SocialAccountEntity, onClose: () -> Unit, onSave: (SocialAccountEntity) -> Unit, onDelete: () -> Unit) {
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
            SocialInput("الاسم", label, { label = it })
            SocialInput("الحد الأقصى للمشاركات يومياً", cap, { cap = it.filter(Char::isDigit).take(2) }, number = true)
            SocialInput("أقل فاصل بين مشاركتين (دقائق)", gap, { gap = it.filter(Char::isDigit).take(3) }, number = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { SocialInput("من الساعة", from, { from = it.filter(Char::isDigit).take(2) }, number = true) }
                Box(Modifier.weight(1f)) { SocialInput("إلى الساعة", to, { to = it.filter(Char::isDigit).take(2) }, number = true) }
            }
            PrimaryButton("حفظ", Icons.Check, {
                onSave(a.copy(label = label, dailyCap = cap.toIntOrNull() ?: a.dailyCap, gapMin = gap.toIntOrNull() ?: a.gapMin, winFrom = from.toIntOrNull() ?: a.winFrom, winTo = to.toIntOrNull() ?: a.winTo))
            }, Modifier.fillMaxWidth())
            AppChip(if (confirm) "تأكيد الحذف (يمسح الجلسة والطابور)" else "حذف الحساب", confirm, { if (confirm) onDelete() else confirm = true })
        }
    }
}
