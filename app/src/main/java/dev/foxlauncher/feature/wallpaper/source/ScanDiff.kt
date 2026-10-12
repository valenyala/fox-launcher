package dev.foxlauncher.feature.wallpaper.source

import dev.foxlauncher.core.data.db.WallpaperEntryEntity

/** An image a scan found in a source (task 4.1). [uri] is its identity within the source. */
data class FoundImage(val uri: String, val displayName: String, val sizeBytes: Long = 0, val lastModified: Long = 0)

/** What one scan changes in a source's entries. */
data class ScanDiff(
    val added: List<FoundImage>,
    /** Existing entries whose file changed, already updated; they keep their id and history. */
    val changed: List<WallpaperEntryEntity>,
    val removedIds: List<Long>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removedIds.isEmpty()
}

/**
 * Compares a source's [existing] entries with what a scan [found], by URI. A file whose name, size
 * or modification time changed is updated in place and gets a fresh start (its failure count goes
 * back to zero); one that vanished is removed; duplicates in [found] count once.
 */
fun diffScan(existing: List<WallpaperEntryEntity>, found: List<FoundImage>): ScanDiff {
    val foundByUri = found.distinctBy { it.uri }.associateBy { it.uri }
    val existingByUri = existing.associateBy { it.uri }
    val added = foundByUri.values.filter { it.uri !in existingByUri }
    val changed = existing.mapNotNull { entry ->
        val image = foundByUri[entry.uri] ?: return@mapNotNull null
        if (image.displayName == entry.displayName && image.sizeBytes == entry.sizeBytes && image.lastModified == entry.lastModified) {
            null
        } else {
            entry.copy(displayName = image.displayName, sizeBytes = image.sizeBytes, lastModified = image.lastModified, failedCount = 0)
        }
    }
    val removedIds = existing.filter { it.uri !in foundByUri }.map { it.id }
    return ScanDiff(added, changed, removedIds)
}

fun FoundImage.toEntry(sourceId: Long) = WallpaperEntryEntity(
    sourceId = sourceId,
    uri = uri,
    displayName = displayName,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
)
