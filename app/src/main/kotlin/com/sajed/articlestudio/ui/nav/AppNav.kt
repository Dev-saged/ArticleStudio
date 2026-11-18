package com.sajed.articlestudio.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sajed.articlestudio.ui.AmbientBackground
import com.sajed.articlestudio.ui.LiveBar
import com.sajed.articlestudio.ui.LiveStatus
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.home.HomeScreen
import com.sajed.articlestudio.ui.screens.ArticlesScreen
import com.sajed.articlestudio.ui.screens.ExportScreen
import com.sajed.articlestudio.ui.screens.InsightsScreen
import com.sajed.articlestudio.ui.screens.ProvidersScreen
import com.sajed.articlestudio.ui.screens.SitesScreen
import com.sajed.articlestudio.ui.test.SelfTestScreen
import com.sajed.articlestudio.ui.theme.AppTheme

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "توليد", Icons.Generate),
    Tab("sites", "المواقع", Icons.Sites),
    Tab("articles", "المقالات", Icons.Articles),
    Tab("providers", "المزودون", Icons.Providers),
    Tab("export", "السجل", Icons.Export),
)

private const val TEST = "test"

@Composable
fun AppNav(live: LiveStatus, onToggleTheme: () -> Unit) {
    val nav = rememberNavController()
    val c = AppTheme.colors
    val ticker by live.text.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val homeVm: com.sajed.articlestudio.ui.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    val edge = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    AmbientBackground {
        Column(Modifier.fillMaxSize().imePadding().windowInsetsPadding(edge.only(WindowInsetsSides.Horizontal))) {
            EdgeBar(top = true)
            LiveBar(ticker)
            NavHost(
                nav, startDestination = "home", modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(140)) },
                exitTransition = { fadeOut(tween(90)) },
                popEnterTransition = { fadeIn(tween(140)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable("home") { HomeScreen(c.isDark, onToggleTheme, onOpenTest = { nav.navigate(TEST) }, onOpenInsights = { nav.navigate("insights") }, vm = homeVm) }
                composable("sites") { SitesScreen(onGenerate = { homeVm.openRoom(homeVm.sites.activeId.value); nav.navigate("home") { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }) }
                composable("articles") { ArticlesScreen() }
                composable("providers") { ProvidersScreen() }
                composable("export") { ExportScreen() }
                composable("insights") { InsightsScreen(onBack = { nav.popBackStack() }) }
                composable(TEST) { SelfTestScreen { nav.popBackStack() } }
            }
            if (route != TEST) {
                Row(
                    Modifier.fillMaxWidth().background(c.bg2).padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    TABS.forEach { t ->
                        val on = route == t.route
                        Column(
                            Modifier.weight(1f).defaultMinSize(minHeight = 52.dp)
                                .clickable(MutableInteractionSource(), null, role = Role.Tab) {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            Box(Modifier.width(26.dp).height(3.dp).background(if (on) c.gold else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(3.dp)))
                            Icon(t.icon, null, Modifier.size(22.dp), tint = if (on) c.goldText else c.muted)
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (on) c.goldText else c.muted)
                        }
                    }
                }
            }
            EdgeBar(top = false)
        }
    }
}

/** شريط حافة صلب بارتفاع أشرطة النظام (الحالة/القطع في الأعلى، التنقل/الإيماءات في الأسفل) يحدد حدود التطبيق تلقائياً. */
@Composable
private fun EdgeBar(top: Boolean) {
    val c = AppTheme.colors
    val ins = if (top) WindowInsets.systemBars.union(WindowInsets.displayCutout) else WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Column(Modifier.fillMaxWidth()) {
        if (!top) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Box(Modifier.fillMaxWidth().background(c.bg2).then(if (top) Modifier.windowInsetsTopHeight(ins) else Modifier.windowInsetsBottomHeight(ins)))
        if (top) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}
