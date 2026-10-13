package dev.foxlauncher.feature.widgets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.clock.ClockWidget
import javax.inject.Inject
import javax.inject.Singleton

/** What a native widget knows when it draws (task 5.1): its size in cells, and whether it is a picker preview. */
@Immutable
data class NativeWidgetScope(val spanX: Int, val spanY: Int, val isPreview: Boolean = false)

/**
 * A launcher-native widget (ADR-8, task 5.1): a composable drawn on the home grid, inside the
 * launcher's own rounded surface. [kind] is stored with each placed widget, so never rename one.
 * It can be resized between [minSpan] and [maxSpan] when those differ.
 */
class NativeWidgetSpec(
    val kind: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val minSpan: Span,
    val defaultSpan: Span,
    val maxSpan: Span,
    val content: @Composable (NativeWidgetScope) -> Unit,
) {
    init {
        require(defaultSpan.x in minSpan.x..maxSpan.x && defaultSpan.y in minSpan.y..maxSpan.y) {
            "$kind: default $defaultSpan outside $minSpan..$maxSpan"
        }
    }

    val resizable: Boolean get() = minSpan != maxSpan
}

/** Every native widget Fox has (task 5.1), from the Hilt multibinding below; the picker lists them. */
@Singleton
class NativeWidgetRegistry @Inject constructor(specs: Set<@JvmSuppressWildcards NativeWidgetSpec>) {

    private val byKind = specs.associateBy { it.kind }

    val all: List<NativeWidgetSpec> = specs.sortedBy { it.kind }

    operator fun get(kind: String): NativeWidgetSpec? = byKind[kind]
}

/** The registry, for the home grid and the picker; empty unless provided (as in tests). */
val LocalNativeWidgets = staticCompositionLocalOf { NativeWidgetRegistry(emptySet()) }

/** Adding a native widget is one line here (phase 5 adds the memory widgets). */
@Module
@InstallIn(SingletonComponent::class)
object NativeWidgetModule {

    @Provides
    @IntoSet
    fun clock(): NativeWidgetSpec = ClockWidget.spec
}
