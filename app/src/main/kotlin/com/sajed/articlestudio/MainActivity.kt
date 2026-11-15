package com.sajed.articlestudio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sajed.articlestudio.ui.LiveStatus
import com.sajed.articlestudio.ui.UiPrefs
import com.sajed.articlestudio.ui.nav.AppNav
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.ArticleStudioTheme
import com.sajed.articlestudio.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: UiPrefs
    @Inject lateinit var live: LiveStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by prefs.mode.collectAsStateWithLifecycle()
            ArticleStudioTheme(mode) {
                val dark = AppTheme.colors.isDark
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    )
                    onDispose {}
                }
                AppNav(live) { prefs.set(if (dark) ThemeMode.Light else ThemeMode.Dark) }
            }
        }
    }
}
