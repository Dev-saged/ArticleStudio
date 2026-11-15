package com.sajed.articlestudio.di

import android.content.Context
import androidx.room.Room
import com.sajed.articlestudio.data.AppDb
import com.sajed.articlestudio.data.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AppDb =
        Room.databaseBuilder(c, AppDb::class.java, "studio.db").addMigrations(*Migrations.ALL).build()

    @Provides fun articleDao(db: AppDb) = db.articles()
    @Provides fun jobDao(db: AppDb) = db.jobs()
}
