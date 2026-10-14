package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.FolderEntity
import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePage
import dev.foxlauncher.core.model.HomePosition
import kotlinx.collections.immutable.toImmutableList

/**
 * Pure conversion between Room rows and the [HomeLayout] model. Rows that do not make sense for
 * their type (an APP without a component, a child whose folder is gone) are dropped rather than
 * crashing the home screen.
 */
object HomeLayoutMapper {

    fun toLayout(items: List<HomeItemEntity>, folders: List<FolderEntity>): HomeLayout {
        val folderTitles = folders.associate { it.id to it.title }
        val children = items
            .filter { it.container == HomeContainer.FOLDER && it.folderId != null }
            .groupBy { it.folderId!! }
            .mapValues { (_, rows) -> rows.sortedBy { it.col }.mapNotNull { toChild(it) } }

        fun top(container: HomeContainer) = items
            .filter { it.container == container }
            .sortedWith(compareBy({ it.page }, { it.row }, { it.col }, { it.id }))
            .mapNotNull { toModel(it, folderTitles, children) }

        val byPage = top(HomeContainer.PAGE).groupBy { it.position.page }
        val lastPage = maxOf(0, byPage.keys.maxOrNull() ?: 0)
        val pages = (0..lastPage).map { HomePage(it, byPage[it].orEmpty().toImmutableList()) }
        return HomeLayout(
            pages = pages.toImmutableList(),
            dock = HomePage(0, top(HomeContainer.DOCK).toImmutableList()),
        )
    }

    /** The row for [item]. For a folder this is only its tile; children are rows of their own. */
    fun toEntity(item: HomeItem, createdAt: Long): HomeItemEntity {
        val p = item.position
        val base = HomeItemEntity(
            id = item.id,
            container = p.container,
            page = p.page,
            col = p.col,
            row = p.row,
            spanX = p.spanX,
            spanY = p.spanY,
            type = HomeItemType.APP,
            createdAt = createdAt,
        )
        return when (item) {
            is HomeItem.App -> base.copy(component = item.component, userSerial = item.userSerial)
            is HomeItem.Shortcut -> base.copy(
                type = HomeItemType.SHORTCUT,
                component = "${item.packageName}/",
                userSerial = item.userSerial,
                shortcutId = item.shortcutId,
                title = item.title,
            )
            is HomeItem.Folder -> base.copy(type = HomeItemType.FOLDER, folderId = item.folderId, title = item.title)
            is HomeItem.AppWidget -> base.copy(
                type = HomeItemType.APP_WIDGET,
                appWidgetId = item.appWidgetId,
                component = item.provider,
                userSerial = item.userSerial,
            )
            is HomeItem.NativeWidget -> base.copy(type = HomeItemType.NATIVE_WIDGET, nativeWidgetKind = item.kind)
        }
    }

    private fun toModel(
        row: HomeItemEntity,
        folderTitles: Map<Long, String>,
        children: Map<Long, List<HomeItem>>,
    ): HomeItem? {
        val position = row.position()
        return when (row.type) {
            HomeItemType.APP, HomeItemType.SHORTCUT -> toChild(row)
            HomeItemType.FOLDER -> {
                val folderId = row.folderId ?: return null
                HomeItem.Folder(
                    id = row.id,
                    position = position,
                    folderId = folderId,
                    title = folderTitles[folderId] ?: row.title.orEmpty(),
                    items = children[folderId].orEmpty().toImmutableList(),
                )
            }
            HomeItemType.APP_WIDGET -> {
                val appWidgetId = row.appWidgetId ?: return null
                val provider = row.component ?: return null
                val serial = row.userSerial ?: return null
                HomeItem.AppWidget(row.id, position, appWidgetId, provider, serial)
            }
            HomeItemType.NATIVE_WIDGET -> row.nativeWidgetKind?.let { HomeItem.NativeWidget(row.id, position, it) }
        }
    }

    /** Apps and shortcuts: the only things allowed inside a folder. */
    private fun toChild(row: HomeItemEntity): HomeItem? {
        val component = row.component ?: return null
        val serial = row.userSerial ?: return null
        return when (row.type) {
            HomeItemType.APP -> HomeItem.App(row.id, row.position(), component, serial)
            HomeItemType.SHORTCUT -> HomeItem.Shortcut(
                id = row.id,
                position = row.position(),
                packageName = component.substringBefore('/'),
                shortcutId = row.shortcutId ?: return null,
                userSerial = serial,
                title = row.title.orEmpty(),
            )
            else -> null
        }
    }

    private fun HomeItemEntity.position() = HomePosition(container, page, col, row, spanX, spanY)
}

/**
 * New indices that close the gaps between [usedPages]: the pages that hold something become
 * 0, 1, 2… in order, so no empty page stays before or between others. Only the indices that
 * change are returned.
 */
fun pageRenumbering(usedPages: Collection<Int>): Map<Int, Int> =
    usedPages.toSortedSet().withIndex()
        .filter { (new, old) -> new != old }
        .associate { (new, old) -> old to new }

/**
 * Ids of APP rows whose `component|serial` is not in [installedAppKeys] (`AppEntry.key`).
 * An empty catalog prunes nothing: a transient empty `LauncherApps` answer must never wipe the
 * user's layout.
 */
fun orphanedAppItemIds(items: List<HomeItemEntity>, installedAppKeys: Set<String>): List<Long> {
    if (installedAppKeys.isEmpty()) return emptyList()
    return items
        .filter { it.type == HomeItemType.APP && "${it.component}|${it.userSerial}" !in installedAppKeys }
        .map { it.id }
}
