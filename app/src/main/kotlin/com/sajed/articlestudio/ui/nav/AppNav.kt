package com.sajed.articlestudio.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sajed.articlestudio.ui.AmbientBackground
import com.sajed.articlestudio.ui.LiveBar
import com.sajed.articlestudio.ui.LiveStatus
import com.sajed.articlestudio.ui.home.HomeViewModel
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.pipe.ArticlesHub
import com.sajed.articlestudio.ui.pipe.BackStrip
import com.sajed.articlestudio.ui.pipe.MoreScreen
import com.sajed.articlestudio.ui.pipe.NeedsScreen
import com.sajed.articlestudio.ui.pipe.PipeHome
import com.sajed.articlestudio.ui.pipe.PipeViewModel
import com.sajed.articlestudio.ui.pipe.PlatformPage
import com.sajed.articlestudio.ui.pipe.PlatformSettings
import com.sajed.articlestudio.ui.pipe.PlatformsHub
import com.sajed.articlestudio.ui.pipe.RunScreen
import com.sajed.articlestudio.ui.screens.ExportScreen
import com.sajed.articlestudio.ui.screens.InsightsScreen
import com.sajed.articlestudio.ui.screens.ProvidersScreen
import com.sajed.articlestudio.ui.screens.SitesScreen
import com.sajed.articlestudio.ui.screens.SocialLoginScreen
import com.sajed.articlestudio.ui.test.SelfTestScreen
import com.sajed.articlestudio.ui.theme.AppTheme

private class Tab(val route: String, val match: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("line", "line", "الخط", Icons.Layers),
    Tab("articles?f=all", "articles?f={f}", "المقالات", Icons.Articles),
    Tab("platforms?add=false", "platforms?add={add}", "المنصات", Icons.Share),
    Tab("more", "more", "المزيد", Icons.Grid),
)

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}

@Composable
fun AppNav(live: LiveStatus, onToggleTheme: () -> Unit) {
    val nav = rememberNavController()
    val c = AppTheme.colors
    val ticker by live.text.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val home: HomeViewModel = hiltViewModel()
    val pipe: PipeViewModel = hiltViewModel()
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { r ->
        when (r) {
            "platforms" -> nav.tab("platforms?add=false")
            "add-account" -> nav.navigate("platforms?add=true")
            else -> nav.navigate(r)
        }
    }

    val edge = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    AmbientBackground {
        Column(Modifier.fillMaxSize().imePadding().windowInsetsPadding(edge.only(WindowInsetsSides.Horizontal))) {
            Box(Modifier.fillMaxWidth().background(c.bg2).windowInsetsTopHeight(edge))
            LiveBar(ticker)
            Box(Modifier.weight(1f)) {
                NavHost(
                    nav, startDestination = "line", modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(160)) }, exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(tween(160)) }, popExitTransition = { fadeOut(tween(100)) },
                ) {
                    composable("line") { PipeHome(home, pipe, go, onArticles = { f -> nav.tab("articles?f=$f") }, onPlatforms = { nav.tab("platforms?add=false") }) }
                    composable("articles?f={f}", listOf(navArgument("f") { type = NavType.StringType; defaultValue = "all" })) { e ->
                        ArticlesHub(e.arguments?.getString("f").orEmpty(), onPublishScreen = { nav.navigate("publish") })
                    }
                    composable("platforms?add={add}", listOf(navArgument("add") { type = NavType.BoolType; defaultValue = false })) { e ->
                        PlatformsHub(pipe, go, e.arguments?.getBoolean("add") == true)
                    }
                    composable("more") { MoreScreen(pipe, c.isDark, onToggleTheme, go) }
                    composable("run") { RunScreen(home, back) }
                    composable("needs") { NeedsScreen(pipe, home, go, back) }
                    composable("plat/{id}?tab={tab}", listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { e ->
                        PlatformPage(e.arguments?.getString("id").orEmpty(), e.arguments?.getInt("tab") ?: 0, pipe, go, back)
                    }
                    composable("plat/{id}/settings") { e -> PlatformSettings(e.arguments?.getString("id").orEmpty(), go, back) }
                    composable("login/{id}") { e -> SocialLoginScreen(e.arguments?.getString("id").orEmpty(), onDone = back) }
                    composable("sites") { BackStrip(back) { SitesScreen(onGenerate = { nav.tab("line") }) } }
                    composable("providers") { BackStrip(back) { ProvidersScreen() } }
                    composable("publish") { BackStrip(back) { ExportScreen() } }
                    composable("insights") { Box(Modifier.padding(bottom = 84.dp)) { InsightsScreen(onBack = back) } }
                    composable("test") { SelfTestScreen(back) }
                }
                if (route != "test" && route?.startsWith("login/") != true) PillNav(route, Modifier.align(Alignment.BottomCenter)) { nav.tab(it) }
            }
            Box(Modifier.fillMaxWidth().background(c.bg2).windowInsetsBottomHeight(edge))
        }
    }
}

@Composable
private fun PillNav(route: String?, modifier: Modifier, onTab: (String) -> Unit) {
    val c = AppTheme.colors
    val h = LocalHapticFeedback.current
    val owner = when {
        route == null -> 0
        route.startsWith("articles") -> 1
        route.startsWith("platforms") || route.startsWith("plat/") -> 2
        route == "more" || route in setOf("sites", "providers", "publish", "insights") -> 3
        else -> 0
    }
    Row(
        modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(c.surface.copy(alpha = .96f)).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TABS.forEachIndexed { i, t ->
            val on = i == owner
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (on) c.bg2 else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTab(t.route) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(t.icon, t.label, Modifier.size(21.dp), tint = if (on) c.red else c.muted)
                Text(t.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = if (on) c.text else c.muted)
                Box(Modifier.height(0.dp))
            }
        }
    }
}
