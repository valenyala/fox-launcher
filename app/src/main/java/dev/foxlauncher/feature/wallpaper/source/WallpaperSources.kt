package dev.foxlauncher.feature.wallpaper.source

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.SourceEntryCount
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import dev.foxlauncher.core.data.db.WallpaperSourceEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The wallpaper sources (task 4.1): the built-in pictures, folders the user picked and pictures
 * picked one by one (task 4.7). Read access to the user's are persisted URI permissions, taken when
 * added and given back when removed (PLATFORM-NOTES W).
 */
@Singleton
class WallpaperSources @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WallpaperDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    val sources: Flow<List<WallpaperSourceEntity>> = dao.observeSources()

    val entryCounts: Flow<List<SourceEntryCount>> = dao.observeEntryCounts()

    /** The images the rotation walks (see [WallpaperDao.observeRotationEntries]). */
    val rotationEntries: Flow<List<WallpaperEntryEntity>> = dao.observeRotationEntries()

    /**
     * Makes sure the built-in source exists and lists exactly the wallpapers this version ships, so
     * rotation has images with no setup. Their URIs name the resource, which stays valid across builds.
     */
    suspend fun ensureBuiltIn() {
        val source = dao.getSourceOfType(WallpaperSourceType.BUILT_IN)
        val sourceId = source?.id ?: dao.insertSource(
            WallpaperSourceEntity(type = WallpaperSourceType.BUILT_IN, displayName = BUILT_IN_NAME),
        )
        val shipped = BUILT_IN.map { (resId, name) -> FoundImage(uri = builtInUri(resId), displayName = name) }
        val diff = diffScan(dao.getEntries(sourceId), shipped)
        if (!diff.isEmpty) dao.applyScan(sourceId, diff.added.map { it.toEntry(sourceId) }, diff.changed, diff.removedIds)
    }

    /**
     * Adds the folder [tree] the user picked and scans it; a folder that already is a source is only
     * rescanned. False when its access could not be kept.
     */
    suspend fun addFolder(tree: Uri): Boolean = withContext(io) {
        if (!keepAccess(tree)) return@withContext false
        val existing = dao.getSources().firstOrNull { it.type == WallpaperSourceType.FOLDER_TREE && it.uri == tree.toString() }
        val id = existing?.id
            ?: dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.FOLDER_TREE, uri = tree.toString(), displayName = folderName(tree)))
        if (existing?.needsReauth == true) dao.reauthorizeSource(id, tree.toString(), folderName(tree))
        SourceScanner.scanNow(WorkManager.getInstance(context), id)
        true
    }

    /** The folder of source [sourceId] lost its access and the user picked one again: possibly another one. */
    suspend fun reauthorize(sourceId: Long, tree: Uri): Boolean = withContext(io) {
        val source = dao.getSource(sourceId) ?: return@withContext false
        if (!keepAccess(tree)) return@withContext false
        if (source.uri != tree.toString()) source.uri?.let { giveBack(it.toUri()) }
        dao.reauthorizeSource(sourceId, tree.toString(), folderName(tree))
        SourceScanner.scanNow(WorkManager.getInstance(context), sourceId)
        true
    }

    /**
     * Adds pictures picked one by one to the "picked pictures" source, created on first use. Ones
     * already there, or whose access could not be kept, are skipped. Returns how many were added.
     */
    suspend fun addPictures(uris: List<Uri>): Int = withContext(io) {
        val sourceId = dao.getSourceOfType(WallpaperSourceType.PICKED_IMAGES)?.id
            ?: dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.PICKED_IMAGES, displayName = PICKED_NAME))
        val known = dao.getEntries(sourceId).mapTo(HashSet()) { it.uri }
        val added = uris.distinct()
            .filter { it.toString() !in known && keepAccess(it) }
            .map { WallpaperEntryEntity(sourceId = sourceId, uri = it.toString(), displayName = pictureName(it)) }
        if (added.isNotEmpty()) dao.applyScan(sourceId, added, emptyList(), emptyList())
        added.size
    }

    /**
     * Flags what lost its access since it was added (task 4.9): a folder whose lasting grant is gone
     * must be picked again; picked pictures whose grant is gone are skipped. Nothing is read, so it is
     * cheap: run at start, and the scanner checks folders too.
     */
    suspend fun checkAccess() = withContext(io) {
        val granted = grantedUris()
        dao.getSources().forEach { source ->
            when (source.type) {
                WallpaperSourceType.FOLDER_TREE -> if (!source.needsReauth && source.uri !in granted) {
                    FoxLog.w("Wallpaper folder ${source.displayName} lost its access")
                    dao.setNeedsReauth(source.id, true)
                }
                WallpaperSourceType.PICKED_IMAGES -> dao.getEntries(source.id)
                    .filter { it.uri !in granted && it.failedCount < WallpaperEntryEntity.MAX_FAILURES }
                    .map { it.id }
                    .takeIf { it.isNotEmpty() }
                    ?.let { dao.markUnreadable(it) }
                WallpaperSourceType.BUILT_IN -> Unit
            }
        }
    }

    /**
     * Entry [entryId] could not be read (task 4.9): it counts a failure, and its folder is checked
     * at once rather than at the next daily scan. Without its grant the folder needs picking again;
     * with it, a rescan finds out whether the file or the folder itself is gone.
     */
    suspend fun reportUnreadable(entryId: Long) = withContext(io) {
        dao.markFailed(entryId)
        val source = dao.getEntry(entryId)?.let { dao.getSource(it.sourceId) } ?: return@withContext
        if (source.type != WallpaperSourceType.FOLDER_TREE) return@withContext
        if (source.uri in grantedUris()) {
            SourceScanner.scanNow(WorkManager.getInstance(context), source.id)
        } else {
            FoxLog.w("Wallpaper folder ${source.displayName} lost its access")
            dao.setNeedsReauth(source.id, true)
        }
    }

    suspend fun setEnabled(sourceId: Long, enabled: Boolean) = withContext(io) {
        dao.setSourceEnabled(sourceId, enabled)
    }

    /** Removes a folder or the picked pictures, giving their access back. The built-in pictures stay. */
    suspend fun remove(sourceId: Long) = withContext(io) {
        val source = dao.getSource(sourceId) ?: return@withContext
        when (source.type) {
            WallpaperSourceType.BUILT_IN -> return@withContext
            WallpaperSourceType.FOLDER_TREE -> source.uri?.let { giveBack(it.toUri()) }
            WallpaperSourceType.PICKED_IMAGES -> dao.getEntries(sourceId).forEach { giveBack(it.uri.toUri()) }
        }
        dao.deleteSource(sourceId)
    }

    /**
     * What Coil loads for an entry's thumbnail. Coil 3 reads `android.resource` URIs by resource id
     * only, so a built-in picture (stored by name, see [ensureBuiltIn]) is its id.
     */
    fun thumbnailModel(uri: String): Any = builtInIds[uri] ?: uri

    private val builtInIds: Map<String, Int> by lazy { BUILT_IN.associate { (resId, _) -> builtInUri(resId) to resId } }

    private fun builtInUri(resId: Int): String =
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/${context.resources.getResourceEntryName(resId)}"

    /** Keeps read access to [uri] across reboots; false when the provider does not allow it. */
    private fun keepAccess(uri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        FoxLog.w("No lasting access to $uri", e)
        false
    }

    /** The URIs Fox holds lasting read access to. */
    private fun grantedUris(): Set<String> =
        resolver.persistedUriPermissions.filter { it.isReadPermission }.mapTo(HashSet()) { it.uri.toString() }

    private fun giveBack(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            FoxLog.w("No access to give back for $uri", e)
        }
    }

    private fun folderName(tree: Uri): String =
        displayName(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            ?: tree.lastPathSegment.orEmpty()

    private fun pictureName(uri: Uri): String = displayName(uri, OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment.orEmpty()

    private fun displayName(uri: Uri, column: String): String? = try {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) {
        FoxLog.w("No name for $uri", e)
        null
    } catch (e: IllegalArgumentException) {
        FoxLog.w("No name for $uri", e)
        null
    }

    private companion object {
        const val BUILT_IN_NAME = "Fox"
        const val PICKED_NAME = "Pictures"
        val BUILT_IN = listOf(
            R.raw.wallpaper_dawn to "Dawn",
            R.raw.wallpaper_dusk to "Dusk",
            R.raw.wallpaper_forest to "Forest",
        )
    }
}
