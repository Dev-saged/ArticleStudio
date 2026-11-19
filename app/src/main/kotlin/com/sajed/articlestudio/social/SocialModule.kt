package com.sajed.articlestudio.social

import com.sajed.articlestudio.data.SecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SocialModule {
    @Provides @Singleton fun runner(
        dao: com.sajed.articlestudio.data.SocialDao, arts: com.sajed.articlestudio.data.ArticleDao, writer: ShareWriter, web: WebSessionPublisher,
        tg: TelegramPublisher, bs: BlueskyPublisher, notifier: ShareNotifier, bus: com.sajed.articlestudio.engine.RunBus,
    ) = ShareRunner(dao, arts, writer, { a -> when (a.platform) { "telegram" -> tg; "bluesky" -> bs; else -> web } }, notifier, bus)

    @Provides @Singleton fun telegram(http: OkHttpClient, s: SecretStore) = TelegramPublisher(http, s)
    @Provides @Singleton fun bluesky(http: OkHttpClient, s: SecretStore) = BlueskyPublisher(http, s)
}
