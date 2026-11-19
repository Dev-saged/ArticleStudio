package com.sajed.articlestudio

import android.app.Application
import com.sajed.articlestudio.engine.AutoRunner
import com.sajed.articlestudio.engine.AutoScheduler
import com.sajed.articlestudio.engine.SiteStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var sites: SiteStore
    @Inject lateinit var auto: AutoRunner
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch { sites.sites.collect { l -> runCatching { AutoScheduler.sync(this@App, l) } } }
        scope.launch { delay(15_000); runCatching { auto.tick() } }
        runCatching { com.sajed.articlestudio.social.ShareScheduler.ensure(this) }
    }
}
