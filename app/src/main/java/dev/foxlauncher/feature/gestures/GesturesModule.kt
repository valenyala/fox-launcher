package dev.foxlauncher.feature.gestures

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.foxlauncher.feature.gestures.system.AccessibilitySystemActions
import dev.foxlauncher.feature.wallpaper.NoOpWallpaperAdvancer
import dev.foxlauncher.feature.wallpaper.WallpaperAdvancer

/** The Android side of [ActionExecutor]'s ports (task 3.4). */
@Module
@InstallIn(SingletonComponent::class)
abstract class GesturesModule {

    @Binds
    abstract fun bindSystemActions(impl: AccessibilitySystemActions): SystemActions

    @Binds
    abstract fun bindAppLauncher(impl: RepositoryAppLauncher): AppLauncher

    /** TODO(phase4): bind `RotationScheduler`. */
    @Binds
    abstract fun bindWallpaperAdvancer(impl: NoOpWallpaperAdvancer): WallpaperAdvancer
}
