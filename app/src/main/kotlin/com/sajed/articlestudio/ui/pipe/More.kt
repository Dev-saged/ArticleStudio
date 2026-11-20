package com.sajed.articlestudio.ui.pipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.BuildConfig
import com.sajed.articlestudio.ui.UpdateCard
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.screens.SocialViewModel
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ltr

/** المزيد: كل ما ليس يومياً، في ثلاث مجموعات واضحة. */
@Composable
fun MoreScreen(pipe: PipeViewModel, isDark: Boolean, onToggleTheme: () -> Unit, go: (String) -> Unit, social: SocialViewModel = hiltViewModel()) {
    val o by pipe.overview.collectAsStateWithLifecycle()
    val sites by pipe.sites.sites.collectAsStateWithLifecycle()
    val msg by social.msg.collectAsStateWithLifecycle()
    val busy by social.busy.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    @Composable fun item(icon: ImageVector, t: String, s: String, onClick: () -> Unit, trail: (@Composable () -> Unit)? = null) =
        PRow(t, s, onClick, lead = { PLogo(icon, size = 34.dp) }, trail = trail)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { PTop("المزيد", "الإعدادات والأدوات · الإصدار ${(BuildConfig.VERSION_NAME).toString().ltr()}") }
        item { Box(Modifier.padding(horizontal = 16.dp)) { UpdateCard(pipe.updater, showCurrent = true) } }
        item { PSection("المحتوى والمصادر") }
        item {
            PGroup {
                item(Icons.Sites, "المواقع", "${(sites.size).toString().ltr()} مواقع · قواعد ومصادر وجدولة كل موقع", { go("sites") }); PDivider()
                item(Icons.Providers, "المزودون", "مفاتيح الكتابة ومصادر الصور ونسخ المفاتيح", { go("providers") })
            }
        }
        item { PSection("النشر والمتابعة") }
        item {
            PGroup {
                item(Icons.Export, "النشر والملفات", "بلوجر · ملفات XML · المتوقف", { go("publish") }, trail = { if (o.stuck > 0) PTag("${(o.stuck).toString().ltr()} متوقف", Tone.Red) }); PDivider()
                item(Icons.Chart, "الرؤى والمراجعة", "الإحصاءات والتقييم والنسخة الاحتياطية", { go("insights") }); PDivider()
                item(Icons.Document, "التشغيل وسجل العمليات", "كل ما جرى في التوليد والنشر", { go("run") }); PDivider()
                item(Icons.Bell, "يحتاجك", "${(o.needs.size).toString().ltr()} عنصر", { go("needs") })
            }
        }
        item { PSection("المشاركة") }
        item {
            PGroup {
                item(Icons.Share, "المنصات والحسابات", "الربط والجلسات وقواعد النشر", { go("platforms") }); PDivider()
                item(Icons.Refresh, "تحديث وصفات النشر", "من social/recipes.json على فرع app-backup", { if (!busy) social.updateRecipes() }); PDivider()
                item(Icons.Close, "العودة للوصفات المضمّنة", "إن تعطلت وصفة محدّثة", { social.resetRecipes() })
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.goldText) }
        item { PSection("التطبيق") }
        item {
            PGroup {
                PRow("الوضع الداكن", if (isDark) "مفعّل" else "متوقف", lead = { PLogo(Icons.Moon, size = 34.dp) }, trail = { PToggle(isDark) { onToggleTheme() } }, chevron = false); PDivider()
                item(Icons.Shield, "الفحص الذاتي", "اختبار الأنظمة والمزودين والنشر", { go("test") })
            }
        }
    }
}

/** شريط رجوع للشاشات القديمة التي لها عناوينها الخاصة. */
@Composable
fun BackStrip(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, top = 8.dp)) { PIcon(Icons.Back, "رجوع", onBack) }
        Box(Modifier.weight(1f).padding(bottom = 84.dp)) { content() }
    }
}

