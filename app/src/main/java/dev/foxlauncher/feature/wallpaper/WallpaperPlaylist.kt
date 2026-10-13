package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.PlaylistMode
import kotlin.random.Random

/**
 * Which wallpaper comes next (task 4.2). Pure and immutable: [next] and [previous] return the id
 * and the playlist to keep; the rotation stores [mode], [seed] and [history] between changes.
 *
 * - **Sequential** walks [entries] in order and wraps.
 * - **Shuffle** walks a permutation of [entries] drawn from [seed]: no repeats until every entry was
 *   shown, then a new seed whose permutation does not start with the entry just shown.
 * - When entries come and go ([withEntries]) the walk goes on after the most recent history entry
 *   still there, so removing the current wallpaper continues with the one after it.
 * - [previous] steps back through the history, skipping entries that are gone.
 * - [jumpTo] shows a chosen entry; the walk goes on from there.
 */
data class WallpaperPlaylist(
    val entries: List<Long>,
    val mode: PlaylistMode = PlaylistMode.SEQUENTIAL,
    val seed: Long = 0L,
    /** Shown entry ids, oldest first, the current one last; at most [HISTORY_SIZE]. */
    val history: List<Long> = emptyList(),
) {
    /** The wallpaper shown now, if it is still an entry. */
    val current: Long? get() = history.lastOrNull()?.takeIf { it in entries }

    /** The id to show next, with the playlist that has it as current; null when there are no entries. */
    fun next(): Pair<Long?, WallpaperPlaylist> {
        if (entries.isEmpty()) return null to this
        val order = order(seed)
        val anchor = history.lastOrNull { it in entries }
        val position = if (anchor == null) -1 else order.indexOf(anchor)
        if (position + 1 < order.size) return order[position + 1].let { it to shown(it, seed) }
        return when (mode) {
            PlaylistMode.SEQUENTIAL -> order.first().let { it to shown(it, seed) }
            PlaylistMode.SHUFFLE -> {
                // A new pass: a new permutation that does not start with what is showing.
                var newSeed = nextSeed(seed)
                while (entries.size > 1 && order(newSeed).first() == anchor) newSeed = nextSeed(newSeed)
                order(newSeed).first().let { it to shown(it, newSeed) }
            }
        }
    }

    /** The wallpaper shown before the current one, and the playlist back there; null id when there is none. */
    fun previous(): Pair<Long?, WallpaperPlaylist> {
        val earlier = history.dropLast(1).dropLastWhile { it !in entries }
        val id = earlier.lastOrNull() ?: return null to this
        return id to copy(history = earlier)
    }

    /** The playlist with entry [id] shown now (the user picked it, task 4.7); null when it is not an entry. */
    fun jumpTo(id: Long): WallpaperPlaylist? = if (id in entries) shown(id, seed) else null

    fun withEntries(newEntries: List<Long>): WallpaperPlaylist = copy(entries = newEntries)

    private fun order(seed: Long): List<Long> = when (mode) {
        PlaylistMode.SEQUENTIAL -> entries
        PlaylistMode.SHUFFLE -> entries.shuffled(Random(seed))
    }

    private fun shown(id: Long, seed: Long) = copy(seed = seed, history = (history + id).takeLast(HISTORY_SIZE))

    companion object {
        const val HISTORY_SIZE = 50

        /**
         * The entry on screen: the active one while it is still enabled, otherwise the first (before
         * the rotation first picks one, or after the active one went away).
         */
        fun showing(activeId: Long?, enabledIds: List<Long>): Long? = activeId?.takeIf { it in enabledIds } ?: enabledIds.firstOrNull()

        /** A fresh seed from the last one; deterministic, so a stored playlist replays the same. */
        fun nextSeed(seed: Long): Long = seed * SEED_MULTIPLIER + SEED_INCREMENT

        private const val SEED_MULTIPLIER = 6364136223846793005L
        private const val SEED_INCREMENT = 1442695040888963407L
    }
}
