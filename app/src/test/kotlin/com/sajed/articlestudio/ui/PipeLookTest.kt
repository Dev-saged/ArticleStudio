package com.sajed.articlestudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.sajed.articlestudio.data.ArticleRow
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.pipe.*
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ArticleStudioTheme
import com.sajed.articlestudio.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** يرسم مكونات «خط الإنتاج» بصورة حقيقية للتحقق البصري (build/outputs/roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w380dp-h820dp-xhdpi")
class PipeLookTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = true, body: @Composable () -> Unit) {
        rule.setContent {
            ArticleStudioTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { body() }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val sums = listOf(
        PlatformSum("instagram", 96, 0, 40, System.currentTimeMillis() + 60_000, listOf(9, 8, 6, 7, 4, 5, 3), emptyList()),
        PlatformSum("x", 98, 3, 52, null, listOf(8, 6, 7, 5, 6, 4, 5), emptyList()),
        PlatformSum("linkedin", 12, 0, 9, System.currentTimeMillis() + 3_600_000, listOf(3, 4, 2, 3, 2, 2, 1), emptyList()),
        PlatformSum("telegram", 7, 0, 120, System.currentTimeMillis() + 300_000, listOf(5, 3, 3, 4, 2, 3, 2), emptyList()),
    )

    @Composable
    private fun HomeLook() {
        val c = AppTheme.colors
        Column {
            PTop("TCNOPS", "تقنية · تبديل الموقع", lead = { PAvatar("ت") }, actions = { PIcon(Icons.Bell, "يحتاجك", {}, badge = 3) })
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
                    Stage("قيد التوليد", 3, c.red, true, Modifier.weight(1f)) {}
                    StageSep(); Stage("جاهزة", 12, c.text, false, Modifier.weight(1f)) {}
                    StageSep(); Stage("منشورة", 340, c.text, false, Modifier.weight(1f)) {}
                    StageSep(); Stage("للمشاركة", 213, c.goldText, true, Modifier.weight(1f)) {}
                }
            }
            Box(Modifier.padding(16.dp)) { PCta("ولّد مقالات جديدة", Icons.Generate, {}) }
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PRing(.6f, 48.dp, 4.dp, c.red, "3/5")
                    PRow("يولّد الآن · سبورت لايف", "«ليفربول يتصدر…» · Gemini · 2 بالتوازي", chevron = false)
                }
            }
            PSection("المنصات", "إدارة الحسابات")
            sums.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { PlatformCard(it, Modifier.weight(1f)) {} }
                }
            }
        }
    }

    @Test fun homeDark() = shot("home_dark") { HomeLook() }
    @Test fun homeLight() = shot("home_light", dark = false) { HomeLook() }

    @Test fun platformPage() = shot("platform") {
        val c = AppTheme.colors
        Column {
            PTop("إنستغرام", "sajedd.0 · الجلسة نشطة", {}, lead = { PLogo(Icons.Instagram, size = 40.dp) }) { PIcon(Icons.Settings, "إعدادات", {}) }
            PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { PNum(96, 38); PRow("منتظرة · تنتهي نحو 13:37", chevron = false) }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { PPill("نشر الكل", Icons.Play, {}); PPill("كل 1 د", Icons.Clock, {}, primary = false) }
                }
            }
            PTabs(listOf("منتظرة 96", "تحتاجك 3", "السجل 40"), 0, {}, Modifier.padding(vertical = 6.dp))
            PChipRow { PChip("الكل", 340, true) {}; PChip("جاهزة", 12, false) {}; PChip("منشورة", 300, false) {} }
            listOf("كيف تختار هاتفاً بكاميرا قوية دون أن تدفع ثمن الفئة العليا", "أخطاء شائعة عند شحن الهاتف ليلاً", "ريال مدريد يحسم الكلاسيكو بثلاثية").forEachIndexed { i, t ->
                ArticleLine(ArticleRow(i.toLong(), 1, i, t, "", if (i == 2) "failed" else "ready", 900, "Gemini", "انتهت المهلة", 0, 86 - i * 20, "d", if (i == 0) "https://x" else "")) {}
            }
            PPager(0, 96, 20) {}
            PKv(listOf(Triple("213", "منتظرة", null), Triple("3", "تحتاجك", c.red), Triple("20", "نُشرت اليوم", c.ok)))
        }
    }
}
