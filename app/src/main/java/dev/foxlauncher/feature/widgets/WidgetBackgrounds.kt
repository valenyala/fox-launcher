package dev.foxlauncher.feature.widgets

import dev.foxlauncher.core.model.HomeItem

/** The background the user gave this widget, in percent (task 5.8); null when it has none of its own. */
val HomeItem.ownBackgroundOpacity: Int?
    get() = when (this) {
        is HomeItem.NativeWidget -> backgroundOpacity
        is HomeItem.AppWidget -> backgroundOpacity
        is HomeItem.App, is HomeItem.Shortcut, is HomeItem.Folder -> null
    }

/** This widget with [opacity] as its own background (null: none of its own); anything else as it is. */
fun HomeItem.withOwnBackgroundOpacity(opacity: Int?): HomeItem = when (this) {
    is HomeItem.NativeWidget -> copy(backgroundOpacity = opacity)
    is HomeItem.AppWidget -> copy(backgroundOpacity = opacity)
    is HomeItem.App, is HomeItem.Shortcut, is HomeItem.Folder -> this
}

/**
 * How opaque the surface behind [item] on the home is, in percent (tasks 5.6, 5.8): its own when it
 * has one; otherwise a Fox widget follows the setting, [defaultOpacity], and an app widget, which
 * draws its own background, has none. Items that are no widgets have no such surface.
 */
fun widgetBackgroundOpacity(item: HomeItem, defaultOpacity: Int): Int = when (item) {
    is HomeItem.NativeWidget -> item.backgroundOpacity ?: defaultOpacity
    is HomeItem.AppWidget -> item.backgroundOpacity ?: 0
    is HomeItem.App, is HomeItem.Shortcut, is HomeItem.Folder -> 0
}
