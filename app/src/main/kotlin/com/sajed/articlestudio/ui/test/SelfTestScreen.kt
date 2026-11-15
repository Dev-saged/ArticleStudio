package com.sajed.articlestudio.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.ArticleEntity
import com.sajed.articlestudio.data.SecretStore
import com.sajed.articlestudio.schema.Schemas
import com.sajed.articlestudio.engine.Article
import com.sajed.articlestudio.engine.Faq
import com.sajed.articlestudio.engine.Fact
import com.sajed.articlestudio.engine.ImageRenderer
import com.sajed.articlestudio.engine.Section
import com.sajed.articlestudio.engine.StudioData
import com.sajed.articlestudio.engine.XmlBuilder
import com.sajed.articlestudio.engine.parseTags
import com.sajed.articlestudio.engine.toHtml
import javax.xml.parsers.DocumentBuilderFactory
import com.sajed.articlestudio.schema.SchemaRules
import com.sajed.articlestudio.ui.IconBtn
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.Oklch

private class Check(val name: String, val ok: Boolean, val note: String = "")

@Composable
fun SelfTestScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = AppTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var checks by remember { mutableStateOf<List<Check>?>(null) }

    LaunchedEffect(Unit) {
        val out = mutableListOf<Check>()
        val white = Oklch(1f, 0f, 0f).toColor()
        out += Check("OKLCH: الأبيض", white.red > 0.99f && white.green > 0.99f && white.blue > 0.99f)
        out += Check("اتجاه الواجهة RTL", rtl)
        val bad = SchemaRules.validate(Schemas.article, mapOf("title" to "", "words" to "-3", "status" to "x"))
        out += Check("المخطط: رفض الحقول الخاطئة", bad.keys == setOf("title", "words", "status"), bad.keys.joinToString())
        out += Check("تنقية المدخلات", SchemaRules.sanitize("  أ\u0000ب\u0007 ") == "أب")
        out += runCatching {
            val db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).build()
            val id = db.articles().upsert(ArticleEntity(jobId = 1, idx = 0, title = "اختبار", slug = "t", status = "ready", words = 700, updatedAt = 0))
            val n = db.articles().countByStatus("ready")
            db.close()
            Check("Room: كتابة وقراءة", id > 0 && n == 1)
        }.getOrElse { Check("Room: كتابة وقراءة", false, it.javaClass.simpleName) }
        out += runCatching {
            val s = SecretStore(ctx)
            s.put("selftest", "مفتاح-123")
            val ok = s.get("selftest") == "مفتاح-123"
            s.put("selftest", "")
            Check("Keystore: تشفير وفك", ok && !s.has("selftest"))
        }.getOrElse { Check("Keystore: تشفير وفك", false, it.javaClass.simpleName) }
        out += Check("تحليل الوسوم وتنقية HTML", parseTags("[[title]] س\n[[fact]] 1|أ").size == 2 && !toHtml("<b>x</b>").contains("<b>"))
        val art = Article(
            "عنوان اختبار طويل بما يكفي للفحص", "وصف", "self-test-slug", listOf("أخبار"), "تحليل", "ball", 40, "أرقام",
            List(4) { Fact("${it + 1}", "حقيقة") }, "خلاصة", "<p>مقدمة</p>", listOf(Section("قسم", "<p>نص</p>")),
            listOf(Faq("سؤال؟", "جواب")), "<p>خاتمة</p>", null, 400, "test", "m", "t",
        )
        out += runCatching {
            val xml = XmlBuilder.build(XmlBuilder.assignSlugs(listOf(art), emptySet()), "b/", 0)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            Check("بناء XML سليم", true)
        }.getOrElse { Check("بناء XML سليم", false, it.javaClass.simpleName) }
        out += runCatching {
            val bmp = ImageRenderer(ctx, StudioData(ctx)).coverBitmap(art)
            Check("رسم الغلاف بالخط العربي", IntArray(40) { bmp.getPixel(30 * it + 5, 315) }.toSet().size > 3)
        }.getOrElse { Check("رسم الغلاف بالخط العربي", false, it.javaClass.simpleName) }
        checks = out
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(Icons.Back, "رجوع", onBack)
            Text("فحص ذاتي", style = MaterialTheme.typography.headlineLarge, color = c.text)
        }
        val list = checks
        if (list == null) Text("جارٍ الفحص…", color = c.muted) else {
            val pass = list.count { it.ok }
            Text("${pass}/${list.size}", style = MaterialTheme.typography.titleLarge, color = if (pass == list.size) c.ok else c.red)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.name }) { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (k.ok) Icons.Check else Icons.Warning, null, Modifier.size(22.dp), tint = if (k.ok) c.ok else c.red)
                        Text(k.name + if (k.note.isNotEmpty() && !k.ok) " (${k.note})" else "", style = MaterialTheme.typography.bodyLarge, color = c.text)
                    }
                }
            }
        }
    }
}
