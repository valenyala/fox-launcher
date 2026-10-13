package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.PlaylistMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WallpaperPlaylistTest {

    private val abcd = listOf(1L, 2L, 3L, 4L)

    // --- sequential ---

    @Test
    fun theFirstNextIsTheFirstEntry() {
        assertEquals(1L, WallpaperPlaylist(abcd).next().first)
    }

    @Test
    fun sequentialGoesInOrderAndWraps() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 1L, 2L), WallpaperPlaylist(abcd).take(6))
    }

    @Test
    fun theNewCurrentIsRemembered() {
        val (id, after) = WallpaperPlaylist(abcd).next()

        assertEquals(id, after.current)
        assertEquals(listOf(1L), after.history)
    }

    // --- shuffle ---

    @Test
    fun shuffleShowsEveryEntryBeforeRepeating() {
        val ids = (1L..10L).toList()

        val pass = WallpaperPlaylist(ids, PlaylistMode.SHUFFLE, seed = 42).take(10)

        assertEquals(ids.toSet(), pass.toSet())
    }

    @Test
    fun shuffleNeverRepeatsAcrossPasses() {
        val shown = WallpaperPlaylist(listOf(1L, 2L, 3L), PlaylistMode.SHUFFLE, seed = 7).take(60)

        assertTrue(shown.zipWithNext().none { (a, b) -> a == b }, "$shown")
        assertTrue(shown.chunked(3).all { it.toSet().size == 3 }, "every pass shows each once: $shown")
    }

    @Test
    fun aSeedReplaysTheSameOrder() {
        val ids = (1L..10L).toList()

        assertEquals(
            WallpaperPlaylist(ids, PlaylistMode.SHUFFLE, seed = 5).take(25),
            WallpaperPlaylist(ids, PlaylistMode.SHUFFLE, seed = 5).take(25),
        )
    }

    @Test
    fun shuffleIsNotJustTheEntryOrder() {
        val ids = (1L..20L).toList()

        assertNotEquals(ids, WallpaperPlaylist(ids, PlaylistMode.SHUFFLE, seed = 3).take(20))
    }

    // --- previous ---

    @Test
    fun previousAfterNextReturnsTheSameId() {
        val (shown, playlist) = WallpaperPlaylist(abcd, PlaylistMode.SHUFFLE, seed = 9).next()
        val (_, further) = playlist.next()

        val (back, afterBack) = further.previous()

        assertEquals(shown, back)
        assertEquals(shown, afterBack.current)
    }

    @Test
    fun previousSkipsRemovedEntries() {
        val playlist = WallpaperPlaylist(abcd, history = listOf(1L, 2L, 3L)).withEntries(listOf(1L, 3L, 4L))

        assertEquals(1L, playlist.previous().first)
    }

    @Test
    fun previousWithNothingEarlierChangesNothing() {
        val playlist = WallpaperPlaylist(abcd, history = listOf(2L))

        val (id, same) = playlist.previous()

        assertNull(id)
        assertEquals(playlist, same)
    }

    @Test
    fun nextAfterPreviousGoesOnFromThere() {
        val (_, back) = WallpaperPlaylist(abcd, history = listOf(1L, 2L, 3L)).previous()

        assertEquals(3L, back.next().first)
    }

    // --- entries changing ---

    @Test
    fun removingTheCurrentEntryContinuesWithTheOneAfterIt() {
        val playlist = WallpaperPlaylist(abcd, history = listOf(1L, 2L, 3L)).withEntries(listOf(1L, 2L, 4L))

        assertNull(playlist.current)
        assertEquals(4L, playlist.next().first)
    }

    @Test
    fun removingAnotherEntryKeepsThePlace() {
        val playlist = WallpaperPlaylist(abcd, history = listOf(1L, 2L)).withEntries(listOf(1L, 2L, 3L))

        assertEquals(3L, playlist.next().first)
    }

    @Test
    fun addedEntriesJoinTheWalk() {
        val playlist = WallpaperPlaylist(listOf(1L, 2L), history = listOf(1L, 2L)).withEntries(listOf(1L, 2L, 3L))

        assertEquals(listOf(3L, 1L), playlist.take(2))
    }

    @Test
    fun whenNothingShownIsLeftItStartsAgain() {
        val playlist = WallpaperPlaylist(abcd, history = listOf(1L, 2L)).withEntries(listOf(7L, 8L))

        assertEquals(7L, playlist.next().first)
    }

    // --- edges ---

    @Test
    fun aSingleEntryIsAlwaysTheNextOne() {
        assertEquals(listOf(5L, 5L, 5L), WallpaperPlaylist(listOf(5L)).take(3))
        assertEquals(listOf(5L, 5L, 5L), WallpaperPlaylist(listOf(5L), PlaylistMode.SHUFFLE, seed = 1).take(3))
    }

    @Test
    fun noEntriesMeansNothingNext() {
        val empty = WallpaperPlaylist(emptyList(), history = listOf(1L))

        assertNull(empty.next().first)
        assertNull(empty.current)
    }

    @Test
    fun theHistoryKeepsTheLast50() {
        val playlist = (1..80).fold(WallpaperPlaylist((1L..100L).toList())) { p, _ -> p.next().second }

        assertEquals(WallpaperPlaylist.HISTORY_SIZE, playlist.history.size)
        assertEquals(80L, playlist.current)
    }

    @Test
    fun theActiveEntryShowsWhileItIsEnabledOtherwiseTheFirst() {
        assertEquals(2L, WallpaperPlaylist.showing(activeId = 2L, enabledIds = listOf(1L, 2L)))
        assertEquals(1L, WallpaperPlaylist.showing(activeId = null, enabledIds = listOf(1L, 2L)))
        assertEquals(1L, WallpaperPlaylist.showing(activeId = 9L, enabledIds = listOf(1L, 2L)))
        assertNull(WallpaperPlaylist.showing(activeId = 9L, enabledIds = emptyList()))
    }

    /** The next [n] ids, each next() continuing from the playlist the last one returned. */
    private fun WallpaperPlaylist.take(n: Int): List<Long> {
        var playlist = this
        return List(n) {
            val (id, after) = playlist.next()
            playlist = after
            checkNotNull(id)
        }
    }
}
