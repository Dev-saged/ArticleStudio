package com.sajed.articlestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sajed.articlestudio.data.JobMark
import com.sajed.articlestudio.data.ShareCandidate
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.PrimaryButton
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme

@Composable
internal fun ArticlesTab(items: List<ShareCandidate>, accounts: List<SocialAccountEntity>, marks: List<JobMark>, busy: Boolean, onShare: (Long) -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    val byArticle = marks.groupBy { it.articleId }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) item { Text("تظهر هنا المقالات المنشورة برابط عام، وحالة مشاركة كل منها على كل حساب.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        items(items, key = { it.id }) { a ->
            val m = byArticle[a.id].orEmpty().associateBy { it.accountId }
            val open = accounts.any { it.enabled && m[it.id]?.status.let { s -> s == null || s == "failed" || s == "skipped" || s == "needs_you" } }
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 2)
                accounts.forEach { ac -> Text(ac.label + ": " + (m[ac.id]?.status?.let(::jobStatusLabel) ?: "لم تُشارك"), style = MaterialTheme.typography.bodySmall, color = if (m[ac.id]?.status == "done") c.text else c.muted) }
                if (open) AppChip("شارك الآن", false, { if (!busy) onShare(a.id) })
            }
        }
    }
}

internal val PLATFORM_ORDER = listOf("instagram", "x", "linkedin", "facebook", "threads", "telegram", "bluesky")
internal val GAP_CHOICES = listOf(1, 2, 5, 10, 30)
internal val TIME_FMT = java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale.ENGLISH)

@Composable
internal fun QueueTab(
    jobs: List<ShareJobEntity>, accounts: List<SocialAccountEntity>, gaps: Map<String, Int>, busy: Boolean, onRunDue: () -> Unit, onGap: (String, Int) -> Unit,
    onAll: (String) -> Unit, onStop: (String) -> Unit, onNow: (Long) -> Unit, onSkip: (Long) -> Unit, onManual: (ShareJobEntity) -> Unit, modifier: Modifier,
) {
    val c = AppTheme.colors
    val open = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val byPlatform = jobs.groupBy { it.platform }
    val platforms = PLATFORM_ORDER.filter { p -> byPlatform.containsKey(p) || accounts.any { it.platform == p } }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "run") { PrimaryButton("نفّذ المستحق الآن", Icons.Play, onRunDue, Modifier.fillMaxWidth(), enabled = !busy) }
        if (platforms.isEmpty()) item(key = "empty") { Text("أضف حساباً أولاً؛ تظهر هنا مشاركات كل منصة بعد نشر المقالات أو جدولتها.", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        platforms.forEach { p ->
            val spec = Platforms.of(p) ?: return@forEach
            val list = byPlatform[p].orEmpty()
            val active = list.filter { it.status != "done" && it.status != "skipped" }.sortedBy { it.runAt }
            val done = list.filter { it.status == "done" }.sortedByDescending { it.doneAt }
            val gap = gaps[p] ?: 1
            item(key = "h_$p") {
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(spec.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("بالانتظار ${active.count { it.status == "pending" }} · تمّت ${done.size}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Text("الفاصل بين المنشورات عند «نشر الكل»", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GAP_CHOICES.forEach { m -> AppChip("$m د", gap == m, { onGap(p, m) }) }
                    }
                    PrimaryButton("نشر الكل (${active.size})", Icons.Play, { onAll(p) }, Modifier.fillMaxWidth(), enabled = active.isNotEmpty() && !busy)
                    if (active.any { it.forced && it.status == "pending" }) AppChip("إيقاف النشر الفوري", false, { onStop(p) })
                }
            }
            if (active.isEmpty()) item(key = "e_$p") { Text("لا منشورات منتظرة على ${spec.name}.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.muted) }
            items(active, key = { "j_${it.id}" }) { j -> JobRow(j, accounts.firstOrNull { it.id == j.accountId }, busy, { onNow(j.id) }, { onSkip(j.id) }, { onManual(j) }) }
            if (done.isNotEmpty()) item(key = "d_$p") { AppChip(if (open[p] == true) "إخفاء السجل" else "سجل ${spec.name} (${done.size})", open[p] == true, { open[p] = open[p] != true }) }
            if (open[p] == true) items(done.take(30), key = { "x_${it.id}" }) { j -> JobRow(j, accounts.firstOrNull { it.id == j.accountId }, busy, {}, {}, {}) }
        }
    }
}

@Composable
internal fun JobRow(j: ShareJobEntity, a: SocialAccountEntity?, busy: Boolean, onNow: () -> Unit, onSkip: () -> Unit, onManual: () -> Unit) {
    val c = AppTheme.colors
    val bad = j.status == "failed" || j.status == "needs_you"
    val whenText = when {
        j.status == "done" -> "نُشر " + TIME_FMT.format(java.util.Date(j.doneAt))
        j.status == "pending" && j.forced -> "فوري · " + TIME_FMT.format(java.util.Date(j.runAt))
        j.status == "pending" -> "مجدول " + TIME_FMT.format(java.util.Date(j.runAt))
        else -> TIME_FMT.format(java.util.Date(j.runAt))
    }
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).border(1.dp, if (bad) c.red else c.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(j.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 2)
            Text(jobStatusLabel(j.status), style = MaterialTheme.typography.labelLarge, color = if (bad) c.red else c.muted)
        }
        Text((a?.label ?: j.platform) + " · " + whenText, style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (j.error.isNotBlank() && j.status != "done") Text(j.error, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 4)
        if (j.status == "done" && j.postUrl.isNotBlank()) Text(j.postUrl, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
        if (j.status != "done" && j.status != "running") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("نشر الآن", true, { if (!busy) onNow() })
            AppChip("نشر يدوي", false, onManual)
            AppChip("تخطَّ", false, onSkip)
        }
    }
}
