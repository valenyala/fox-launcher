package dev.foxlauncher.feature.widgets.app

import androidx.compose.runtime.Immutable

/**
 * One installed app widget provider, as the picker lists it (task 6.2). [provider] is
 * `ComponentName.flattenToString()` and [userSerial] its profile, as home items keep apps.
 * [previewImage] and [previewLayout] are resource ids in [packageName], 0 when it has none.
 */
@Immutable
data class AppWidgetOption(
    val provider: String,
    val userSerial: Long,
    val packageName: String,
    val appLabel: String,
    val label: String,
    val description: String?,
    val isWorkProfile: Boolean,
    val size: ProviderSize,
    val previewImage: Int = 0,
    val previewLayout: Int = 0,
    val icon: Int = 0,
) {
    /** Stable key for lists and lookups: `provider|serial`. */
    val key: String get() = "$provider|$userSerial"
}

/** One app's widgets in one profile, under its label in the picker. */
@Immutable
data class AppWidgetGroup(
    val packageName: String,
    val userSerial: Long,
    val appLabel: String,
    val isWorkProfile: Boolean,
    val widgets: List<AppWidgetOption>,
) {
    val key: String get() = "$packageName|$userSerial"
}

/** Grouping the picker's app widgets (task 6.2). Pure, so it is unit tested. */
object AppWidgetCatalog {

    /**
     * [options] by app and profile: groups in [labelOrder] of their app labels, the personal
     * profile's before the work profile's for the same app, and each group's widgets by label.
     */
    fun group(options: List<AppWidgetOption>, labelOrder: Comparator<String>): List<AppWidgetGroup> =
        options
            .groupBy { it.packageName to it.userSerial }
            .map { (_, widgets) ->
                val first = widgets.first()
                AppWidgetGroup(
                    packageName = first.packageName,
                    userSerial = first.userSerial,
                    appLabel = first.appLabel,
                    isWorkProfile = first.isWorkProfile,
                    widgets = widgets.sortedWith(compareBy(labelOrder) { it.label }),
                )
            }
            .sortedWith(
                compareBy<AppWidgetGroup, String>(labelOrder) { it.appLabel }
                    .thenBy { it.isWorkProfile }
                    .thenBy { it.packageName }
                    .thenBy { it.userSerial },
            )
}
