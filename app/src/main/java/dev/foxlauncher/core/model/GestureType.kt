package dev.foxlauncher.core.model

/**
 * Every home-screen gesture that can be mapped to a [GestureAction] (task 3.1). Single-finger
 * left/right are reported only where the pager cannot page (the first or last page); `_2F` are
 * two-finger swipes; `DOCK_` swipes start on the dock. Stored by name, so never rename an entry.
 */
enum class GestureType {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP_2F,
    SWIPE_DOWN_2F,
    SWIPE_LEFT_2F,
    SWIPE_RIGHT_2F,
    DOUBLE_TAP,
    LONG_PRESS_EMPTY,
    PINCH_IN,
    PINCH_OUT,
    DOCK_SWIPE_UP,
    DOCK_SWIPE_DOWN,
    HOME_KEY,
}

/** What each gesture does until the user changes it; anything not listed does nothing. */
val DEFAULT_GESTURE_MAP: Map<GestureType, GestureAction> = GestureType.entries.associateWith { type ->
    when (type) {
        GestureType.SWIPE_UP -> GestureAction.OpenDrawer
        GestureType.SWIPE_DOWN -> GestureAction.Notifications
        GestureType.SWIPE_DOWN_2F -> GestureAction.QuickSettings
        GestureType.DOUBLE_TAP -> GestureAction.LockScreen
        GestureType.LONG_PRESS_EMPTY -> GestureAction.HomeSettings
        GestureType.PINCH_IN -> GestureAction.PageOverview
        GestureType.SWIPE_UP_2F -> GestureAction.NextWallpaper
        GestureType.DOCK_SWIPE_UP -> GestureAction.OpenDrawer
        GestureType.HOME_KEY -> GestureAction.ScrollToDefaultPage
        else -> GestureAction.None
    }
}

/** Which action a gesture runs where (task 9.3). Pure, so it is unit tested. */
object GestureMaps {

    /** On [page], its own action for [type] if it has one, else the all-pages one from [base]. */
    fun actionFor(
        type: GestureType,
        page: Int,
        base: Map<GestureType, GestureAction>,
        pages: Map<Int, Map<GestureType, GestureAction>>,
    ): GestureAction = pages[page]?.get(type) ?: base[type] ?: GestureAction.None

    /** [pages] with [page]'s action for [type] set, or cleared when [action] is null (the page follows all pages again). */
    fun withPageAction(
        pages: Map<Int, Map<GestureType, GestureAction>>,
        page: Int,
        type: GestureType,
        action: GestureAction?,
    ): Map<Int, Map<GestureType, GestureAction>> {
        val changes = pages[page].orEmpty().let { if (action == null) it - type else it + (type to action) }
        return if (changes.isEmpty()) pages - page else pages + (page to changes)
    }
}
