package dev.foxlauncher.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FoxDatabase =
        Room.databaseBuilder(context, FoxDatabase::class.java, FoxDatabase.NAME).build()

    @Provides
    fun provideHomeDao(database: FoxDatabase): HomeDao = database.homeDao()

    @Provides
    fun provideWallpaperDao(database: FoxDatabase): WallpaperDao = database.wallpaperDao()
}
