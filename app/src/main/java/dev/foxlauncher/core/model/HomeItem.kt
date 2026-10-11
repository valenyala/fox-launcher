package dev.foxlauncher.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Where a home item lives. Folder children are ordered by [HomePosition.col]. */
enum class HomeContainer { PAGE, DOCK, FOLDER }

/** Cell placement. Dock items use [col] as their slot and row 0; folder children use [col] as their rank. */
@Immutable
data class HomePosition(
    val container: HomeContainer,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

/** Anything placed on the home screen, the dock or inside a folder. */
@Immutable
sealed interface HomeItem {
    val id: Long
    val position: HomePosition

    data class App(
        override val id: Long,
        override val position: HomePosition,
        val component: String,
        val userSerial: Long,
    ) : HomeItem {
        /** Same shape as [AppEntry.key], to look the app up in the catalog. */
        val appKey: String get() = "$component|$userSerial"
    }

    data class Shortcut(
        override val id: Long,
        override val position: HomePosition,
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val title: String,
    ) : HomeItem {
        /** Same shape as [AppShortcut.key], to look its icon up. */
        val shortcutKey: String get() = AppShortcut.keyOf(packageName, shortcutId, userSerial)
    }

    data class Folder(
        override val id: Long,
        override val position: HomePosition,
        val folderId: Long,
        val title: String,
        val items: ImmutableList<HomeItem>,
    ) : HomeItem

    data class AppWidget(
        override val id: Long,
        override val position: HomePosition,
        val appWidgetId: Int,
    ) : HomeItem

    data class NativeWidget(
        override val id: Long,
        override val position: HomePosition,
        val kind: String,
    ) : HomeItem
}

@Immutable
data class HomePage(val index: Int, val items: ImmutableList<HomeItem>)

/** The whole home: always at least one page, plus the dock. */
@Immutable
data class HomeLayout(
    val pages: ImmutableList<HomePage> = persistentListOf(HomePage(0, persistentListOf())),
    val dock: HomePage = HomePage(0, persistentListOf()),
)
