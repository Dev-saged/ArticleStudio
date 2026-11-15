package com.sajed.articlestudio.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.engine.UpdatePhase
import com.sajed.articlestudio.engine.Updater
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr

/** بطاقة التحديث: تظهر فقط عند وجود تحديث أو تنزيل أو خطأ؛ فحص يدوي يعرض نتيجته أيضاً. */
@Composable
fun UpdateCard(updater: Updater, showCurrent: Boolean = false) {
    val u by updater.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val visible = when (u.phase) {
        UpdatePhase.Idle -> false
        UpdatePhase.Current -> showCurrent
        else -> true
    }
    if (!visible) return
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (u.phase) {
                UpdatePhase.Checking -> Text("جارٍ فحص التحديث…", style = MaterialTheme.typography.bodyMedium, color = c.text)
                UpdatePhase.Current -> Text(u.message, style = MaterialTheme.typography.bodyMedium, color = c.ok)
                UpdatePhase.Error -> {
                    Text(u.message.ifEmpty { "تعذّر التحديث" }, style = MaterialTheme.typography.bodyMedium, color = c.red)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (u.info != null) UpdateAction("إعادة المحاولة", Icons.Refresh) { updater.download() } else UpdateAction("إعادة الفحص", Icons.Refresh) { updater.check() }
                        UpdateAction("إخفاء", Icons.Close) { updater.dismiss() }
                    }
                }
                else -> {
                    val i = u.info!!
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Icon(Icons.Info, null, tint = c.goldText)
                        Text("إصدار جديد ${i.version.ltr()}", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                        if (i.size > 0) Text("${(i.size / 1_048_576).toString().ltr()} MB", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                    if (i.notes.isNotEmpty() && u.phase == UpdatePhase.Available) Text(i.notes.lines().filter { it.isNotBlank() }.take(6).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    when (u.phase) {
                        UpdatePhase.Available -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UpdateAction("تنزيل وتثبيت", Icons.Export) { updater.download() }
                            UpdateAction("لاحقاً", Icons.Close) { updater.dismiss() }
                        }
                        UpdatePhase.Downloading -> {
                            if (u.progress > 0f) LinearProgressIndicator(progress = { u.progress }, Modifier.fillMaxWidth().height(8.dp), color = c.goldText, trackColor = c.line)
                            else LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = c.goldText, trackColor = c.line)
                            Text("جارٍ التنزيل ${(u.progress * 100).toInt().toString().ltr()}%", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        }
                        else -> UpdateAction("تثبيت التحديث الآن", Icons.Check) { install(ctx, updater.installIntent()) }
                    }
                }
            }
        }
    }
}

private fun install(c: Context, i: Intent?) { if (i != null) runCatching { c.startActivity(i) } }

@Composable
private fun UpdateAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.height(44.dp).androidxClick(onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.padding(0.dp), tint = c.goldText)
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.goldText)
    }
}

private fun Modifier.androidxClick(onClick: () -> Unit) = this.clickable(onClick = onClick)
