package com.sajed.articlestudio.ui.pipe

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.data.ShareJobEntity
import com.sajed.articlestudio.data.SocialAccountEntity
import com.sajed.articlestudio.social.Platforms
import com.sajed.articlestudio.social.ShareStore
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.screens.AddAccountSheet
import com.sajed.articlestudio.ui.screens.CredsSheet
import com.sajed.articlestudio.ui.screens.EditAccountSheet
import com.sajed.articlestudio.ui.screens.SocialViewModel
import com.sajed.articlestudio.ui.screens.shareIntent
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr

private val GAPS = listOf(1, 2, 5, 10, 30)

private fun accState(a: SocialAccountEntity, web: Boolean) = when {
    !a.enabled -> "معطّل" to Tone.Plain
    a.status == "ok" -> (if (web) "الجلسة نشطة" else "جاهز") to Tone.Ok
    a.status == "needs_login" -> (if (web) "يلزم الدخول" else "البيانات غير صالحة") to Tone.Red
    a.status == "attention" -> "يحتاج ضبطاً" to Tone.Red
    else -> "لم يُفحص" to Tone.Gold
}

/** لوحة المنصات: حالة كل منصة بنظرة، وربط ما لم يُربط. */
@Composable
fun PlatformsHub(pipe: PipeViewModel, go: (String) -> Unit, addOpen: Boolean = false, vm: SocialViewModel = hiltViewModel()) {
    val o by pipe.overview.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle(emptyList())
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var adding by rememberSaveable { mutableStateOf(addOpen) }
    val sums = Platforms.all.map { spec -> o.platforms.firstOrNull { it.id == spec.id } ?: PlatformSum(spec.id, 0, 0, 0, null, List(7) { 0 }, accounts.filter { it.platform == spec.id }) }
        .sortedByDescending { it.linked }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { PTop("المنصات", "${(accounts.size).toString().ltr()} حساب · ${(o.waiting).toString().ltr()} منشوراً منتظراً", actions = { PIcon(Icons.Plus, "ربط حساب", { adding = true }) }) }
        item { PKv(listOf(Triple("${o.waiting}", "منتظرة", null), Triple("${o.needsShare}", "تحتاجك", if (o.needsShare > 0) c.red else null), Triple("${o.doneToday}", "نُشرت اليوم", c.ok))) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PGhost("نفّذ المستحق الآن", Icons.Play, { vm.runNow() }, Modifier.weight(1f), enabled = !busy)
                PGhost("ربط حساب", Icons.Plus, { adding = true }, Modifier.weight(1f))
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.goldText) }
        sums.chunked(2).forEachIndexed { i, pair ->
            item("p$i") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        if (p.linked) PlatformCard(p, Modifier.weight(1f)) { go("plat/${p.id}") }
                        else PCard(Modifier.weight(1f), onClick = { adding = true }) {
                            Column(Modifier.padding(12.dp).heightIn(min = 104.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PLogo(platformIcon(p.id), c.muted, 30.dp)
                                    Text(Platforms.of(p.id)?.name.orEmpty(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp), color = c.muted)
                                }
                                Box(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Plus, null, Modifier.size(14.dp), tint = c.goldText)
                                    Text("اربط الحساب", style = MaterialTheme.typography.bodySmall, color = c.goldText)
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
    if (adding) AddAccountSheet({ adding = false }) { p, l, e, s ->
        adding = false
        vm.add(p, l, e, s) { id -> if (Platforms.of(p)?.isWeb == true) go("login/$id") else go("plat/$p/settings") }
    }
}

/** صفحة منصة: ملخص ثابت + «نشر الكل» + تبويبات وصفحات من 20. */
@Composable
fun PlatformPage(platform: String, initialTab: Int, pipe: PipeViewModel, go: (String) -> Unit, onBack: () -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val spec = Platforms.of(platform) ?: return
    val o by pipe.overview.collectAsStateWithLifecycle()
    val gaps by vm.gaps.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val sum = o.platforms.firstOrNull { it.id == platform }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var page by rememberSaveable(tab) { mutableIntStateOf(0) }
    val rows by remember(tab, page) { vm.page(platform, tab, page) }.collectAsStateWithLifecycle(emptyList())
    var allOpen by remember { mutableStateOf(false) }
    var row by remember { mutableStateOf<ShareJobEntity?>(null) }
    val gap = gaps[platform] ?: 1
    val waiting = sum?.waiting ?: 0
    val counts = listOf(waiting, sum?.needs ?: 0, sum?.done ?: 0)
    val acc = sum?.accounts.orEmpty()
    val forced = rows.any { it.forced && it.status == "pending" } && tab == 0

    Column(Modifier.fillMaxSize()) {
        PTop(spec.name, acc.joinToString(" · ") { it.label + " · " + accState(it, spec.isWeb).first }.ifBlank { "لا حساب مربوط" }, onBack, lead = { PLogo(platformIcon(platform), size = 40.dp) }) {
            PIcon(Icons.Settings, "إعدادات المنصة", { go("plat/$platform/settings") })
        }
        PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), alert = sum?.loginNeeded == true) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PNum(waiting, 38)
                    Text(if (waiting > 0) "منتظرة · تنتهي نحو ${hm(System.currentTimeMillis() + waiting.toLong() * gap * 60_000L).ltr()}" else "لا منشورات منتظرة", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    if (sum?.loginNeeded == true) Text("سجّل الدخول من الإعدادات ليستأنف النشر", style = MaterialTheme.typography.bodySmall, color = c.red)
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
                    PPill("نشر الكل", Icons.Play, { allOpen = true }, enabled = waiting + (sum?.needs ?: 0) > 0 && !busy)
                    PPill("كل ${(gap).toString().ltr()} د", Icons.Clock, { allOpen = true }, primary = false)
                }
            }
            if (forced) Row(Modifier.fillMaxWidth().clickable { vm.stopForced(platform) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PPulse(); Text("النشر الفوري يعمل · اضغط لإيقافه", style = MaterialTheme.typography.bodySmall, color = c.red)
            }
        }
        PTabs(listOf("منتظرة ${(counts[0]).toString().ltr()}", "تحتاجك ${(counts[1]).toString().ltr()}", "السجل ${(counts[2]).toString().ltr()}"), tab, { tab = it }, Modifier.padding(vertical = 6.dp))
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 20.dp, vertical = 2.dp).clickable { vm.clearMsg() }, style = MaterialTheme.typography.bodySmall, color = c.goldText)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
            if (rows.isEmpty()) item { PEmpty(listOf("لا منشورات منتظرة", "لا شيء يحتاجك هنا", "لم يُنشر شيء بعد")[tab], if (tab == 0) "يُجدول كل مقال يُنشر تلقائياً هنا، وتنشر القديمة غير المشاركة وحدها." else "", platformIcon(platform)) }
            items(rows, key = { it.id }) { j ->
                val i = rows.indexOf(j) + page * ShareStore.PAGE
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { row = j }.padding(horizontal = 16.dp).heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}".ltr(), Modifier.padding(end = 2.dp), style = MaterialTheme.typography.labelSmall, color = c.muted)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(j.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (j.forced && j.status == "pending") PTag("فوري", Tone.Red)
                            if (j.status == "running") PTag("يُرسل", Tone.Gold)
                            Text(when (j.status) {
                                "done" -> "نُشر ${(hm(j.doneAt)).toString().ltr()}"; "skipped" -> "تُخطّي"
                                "pending", "running" -> "مجدول ${(hm(j.runAt)).toString().ltr()}"; else -> j.error.substringBefore(" | ").take(60)
                            }, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = if (tab == 1) c.red else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (tab != 2 && j.status != "running") Box(Modifier.size(40.dp).clip(R13).background(c.surface).border(1.dp, c.line, R13).clickable(role = Role.Button) { vm.publishNow(j.id) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Play, "نشر الآن", Modifier.size(16.dp), tint = c.red)
                    }
                    if (tab == 2 && j.postUrl.startsWith("http")) Icon(Icons.Link, null, Modifier.size(16.dp), tint = c.muted)
                }
                Box(Modifier.padding(horizontal = 16.dp)) { PDivider() }
            }
            item { PPager(page, counts[tab], ShareStore.PAGE) { page = it } }
        }
    }

    if (allOpen) {
        var g by remember { mutableIntStateOf(gap) }
        val n = waiting + (sum?.needs ?: 0)
        PSheet({ allOpen = false }, "نشر ${n.toString().ltr()} منشوراً على ${(spec.name).toString().ltr()}", "منشور واحد كل فاصل، يبدأ فوراً ويكمل في الخلفية حتى لو أغلقت التطبيق. يتجاوز السقف اليومي ونافذة الساعات.") {
            PLabel("الفاصل بين المنشورات")
            PSeg(GAPS.map { "${it.toString().ltr()} د" }, GAPS.indexOf(g).coerceAtLeast(0)) { g = GAPS[it]; vm.setGap(platform, g) }
            VSpace(14)
            val now = System.currentTimeMillis()
            PKv(listOf(Triple(hm(now), "البداية", null), Triple(hm(now + (n - 1).coerceAtLeast(0).toLong() * g * 60_000L), "الانتهاء المتوقع", c.goldText), Triple("$n", "منشوراً", null)), Modifier.padding(horizontal = 0.dp))
            VSpace(16)
            PCta("ابدأ النشر", Icons.Play, { vm.setGap(platform, g); vm.publishAll(platform); allOpen = false })
        }
    }

    row?.let { j ->
        var text by remember(j.id) { mutableStateOf(j.text) }
        PSheet({ row = null }, j.title, when (j.status) { "done" -> "نُشر ${(hm(j.doneAt)).toString().ltr()}"; "pending" -> "مجدول ${(hm(j.runAt)).toString().ltr()}"; else -> j.error.substringBefore(" | ").take(120) }) {
            if (j.status != "done") {
                PLabel("نص المنشور" + if (j.text.isBlank()) " (يُصاغ عنوان قوي تلقائياً عند النشر إن تركته فارغاً)" else "")
                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(R16).background(c.surface).border(1.dp, c.line, R16).padding(12.dp)) {
                    BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text), cursorBrush = SolidColor(c.text))
                    if (text.isEmpty()) Text(j.title + "\n" + j.url, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                if (text != j.text) { VSpace(8); PGhost("حفظ النص", Icons.Check, { vm.setText(j.id, text) }, Modifier.fillMaxWidth()) }
                if (j.error.contains(" | ")) { PLabel("تشخيص الصفحة"); Text(j.error.substringAfter(" | ").take(500), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = c.muted) }
            } else if (j.text.isNotBlank()) Text(j.text, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.text)
            VSpace(12)
            PCard(Modifier.fillMaxWidth()) {
                if (j.status != "done" && j.status != "running") {
                    PRow("نشر الآن", "يتجاوز السقف ونافذة الساعات", { if (text != j.text) vm.setText(j.id, text); vm.publishNow(j.id); row = null }, lead = { PLogo(Icons.Play, c.red) }, chevron = false); PDivider()
                }
                PRow("نشر يدوي", "عبر قائمة المشاركة في الهاتف", { shareIntent(ctx, text.ifBlank { j.title + "\n" + j.url }) }, lead = { PLogo(Icons.Share) }, chevron = false); PDivider()
                PRow("فتح المقال", j.url.removePrefix("https://").ltr(), { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(j.url))) } }, lead = { PLogo(Icons.Eye) }, chevron = false)
                if (j.postUrl.startsWith("http")) { PDivider(); PRow("فتح المنشور", j.postUrl.removePrefix("https://").ltr(), { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(j.postUrl))) } }, lead = { PLogo(Icons.Link) }, chevron = false) }
                if (j.status != "done" && j.status != "skipped") { PDivider(); PRow("تخطَّ", "لن يُنشر على ${spec.name}", { vm.skip(j.id); row = null }, lead = { PLogo(Icons.Skip, c.muted) }, chevron = false) }
            }
        }
    }
}

/** إعدادات المنصة: الحسابات والجلسة والقواعد والتجربة. */
@Composable
fun PlatformSettings(platform: String, go: (String) -> Unit, onBack: () -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val spec = Platforms.of(platform) ?: return
    val accounts by vm.accounts.collectAsStateWithLifecycle(emptyList())
    val msg by vm.msg.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val backfill by vm.backfill.collectAsStateWithLifecycle()
    val siteList by vm.siteList.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val mine = accounts.filter { it.platform == platform }
    var editing by remember { mutableStateOf<SocialAccountEntity?>(null) }
    var creds by remember { mutableStateOf<SocialAccountEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmTest by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { PTop("إعدادات ${spec.name}", if (spec.isWeb) "جلسة دخول محفوظة ومشفرة على هاتفك" else "رمز دائم محفوظ ومشفر", onBack, lead = { PLogo(platformIcon(platform), size = 40.dp) }) }
        if (msg.isNotEmpty()) item { PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), onClick = { vm.clearMsg() }) { Text(msg, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = c.text) } }
        if (mine.isEmpty()) item { PEmpty("لا حساب على ${spec.name}", "اربط حسابك ليبدأ النشر التلقائي.", platformIcon(platform)) }
        items(mine, key = { it.id }) { a ->
            val (st, tone) = accState(a, spec.isWeb)
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), alert = tone == Tone.Red) {
                PRow(a.label, a.statusNote.ifBlank { "سقف ${(a.dailyCap).toString().ltr()}/يوم · فاصل ${(a.gapMin).toString().ltr()} د · ${("${a.winFrom}–${a.winTo}").ltr()}" }, lead = { PAvatar(a.label.take(1), a.status == "ok", 40.dp) }, trail = { PTag(st, tone) }, chevron = false)
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (spec.isWeb) PGhost(if (a.status == "ok") "إعادة الدخول" else "تسجيل الدخول", Icons.Key, { go("login/${a.id}") }, Modifier.weight(1f), color = if (a.status == "ok") null else c.red)
                    else PGhost("تعديل البيانات", Icons.Edit, { creds = a }, Modifier.weight(1f))
                    PGhost(if (confirmTest == a.id) "تأكيد: انشر فعلاً" else "نشر تجريبي", Icons.Wand, { if (confirmTest == a.id) { confirmTest = null; vm.testPost(a) } else confirmTest = a.id }, Modifier.weight(1f), enabled = !busy)
                }
                PDivider()
                PRow("فحص الحساب", "يتحقق من الجلسة أو الرمز دون نشر", { vm.check(a) }, chevron = false, trail = { Icon(Icons.Shield, null, Modifier.size(18.dp), tint = c.muted) })
                PDivider()
                PRow("قواعد النشر التلقائي", "السقف اليومي والفاصل وساعات النشر والحذف", { editing = a })
                PDivider()
                PRow("الحساب مفعّل", "", trail = { PToggle(a.enabled) { vm.toggle(a) } }, chevron = false)
            }
        }
        item { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { PGhost("ربط حساب آخر", Icons.Plus, { adding = true }, Modifier.fillMaxWidth()) } }
        item { PSection("للمنصات كلها") }
        item {
            PGroup {
                PRow("نشر المقالات القديمة تلقائياً", "يضيف ما لم يُشارك، الأقدم أولاً ضمن السقف", trail = { PToggle(backfill, vm::setBackfill) }, chevron = false)
                siteList.forEach { s -> PDivider(); PRow("مشاركة مقالات «${s.name}»", s.host.ltr(), trail = { PToggle(s.share) { v -> vm.setSiteShare(s.id, v) } }, chevron = false) }
            }
        }
    }
    editing?.let { a -> EditAccountSheet(a, { editing = null }, { vm.update(it); editing = null }, { vm.remove(a.id); editing = null }) }
    creds?.let { a -> CredsSheet(a, { creds = null }) { e, s -> vm.saveCreds(a, e, s); creds = null } }
    if (adding) AddAccountSheet({ adding = false }) { p, l, e, s -> adding = false; vm.add(p, l, e, s) { id -> if (Platforms.of(p)?.isWeb == true) go("login/$id") } }
}
