package dev.foxlauncher.core.model

/**
 * Which part of a wallpaper image shows, chosen per image by the user (it replaces the page
 * parallax, ADR-26): the image point at the centre of the screen, as fractions of the image's
 * width and height, and how far in it is zoomed (1 = the image just covers the screen). Kept in
 * fractions, so it holds for any screen size; where it would show past the image's edge, the
 * image stops at the edge instead.
 */
data class WallpaperFraming(val focusX: Float = 0.5f, val focusY: Float = 0.5f, val zoom: Float = 1f) {
    companion object {
        val CENTERED = WallpaperFraming()
        const val MAX_ZOOM = 3f
    }
}
