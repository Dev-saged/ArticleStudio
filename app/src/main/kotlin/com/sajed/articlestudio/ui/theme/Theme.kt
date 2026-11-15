package com.sajed.articlestudio.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sajed.articlestudio.BrandConfig

enum class ThemeMode { System, Light, Dark }

@Immutable
class AppColors(p: BrandConfig.Palette, val isDark: Boolean) {
    val bg = p.bg.toColor()
    val bg2 = p.bg2.toColor()
    val surface = p.surface.toColor()
    val line = p.line.toColor()
    val text = p.text.toColor()
    val muted = p.muted.toColor()
    val red = p.red.toColor()
    val gold = p.gold.toColor()
    val goldText = p.goldText.toColor()
    val board = p.board.toColor()
    val glow = p.glow.toColor()
    val ok = p.ok.toColor()
}

val LocalAppColors = staticCompositionLocalOf { AppColors(BrandConfig.dark, true) }

object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun ArticleStudioTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = remember(dark) { AppColors(if (dark) BrandConfig.dark else BrandConfig.light, dark) }
    val scheme = if (dark) darkColorScheme(
        primary = colors.gold, onPrimary = colors.bg, background = colors.bg, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text, outline = colors.line, error = colors.red,
    ) else lightColorScheme(
        primary = colors.goldText, onPrimary = colors.surface, background = colors.bg, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text, outline = colors.line, error = colors.red,
    )
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
