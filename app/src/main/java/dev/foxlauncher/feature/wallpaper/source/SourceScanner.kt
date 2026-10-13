package dev.foxlauncher.feature.wallpaper.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperSourceEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

/**
 * Lists the images of folder sources and brings their entries up to date (task 4.1): every folder
 * source, or only [KEY_SOURCE_ID]. Runs when a folder is added and once a day. A folder whose
 * permission is gone is marked as needing re-authorization (PLATFORM-NOTES W).
 */
@HiltWorker
class SourceScanner @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: WallpaperDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val only = inputData.getLong(KEY_SOURCE_ID, NO_SOURCE).takeIf { it != NO_SOURCE }
        dao.getSources()
            .filter { it.type == WallpaperSourceType.FOLDER_TREE && (only == null || it.id == only) }
            .forEach { scan(it) }
        return Result.success()
    }

    private suspend fun scan(source: WallpaperSourceEntity) {
        val tree = source.uri?.toUri() ?: return
        // Checked first (task 4.9): without a lasting grant the folder must be picked again, whatever listing it might still allow.
        if (applicationContext.contentResolver.persistedUriPermissions.none { it.isReadPermission && it.uri == tree }) {
            FoxLog.w("Wallpaper folder ${source.displayName} is no longer granted")
            dao.setNeedsReauth(source.id, true)
            return
        }
        val found = try {
            listImages(tree)
        } catch (e: SecurityException) {
            FoxLog.w("Wallpaper folder ${source.displayName} lost its permission", e)
            dao.setNeedsReauth(source.id, true)
            return
        } catch (e: FileNotFoundException) {
            FoxLog.w("Wallpaper folder ${source.displayName} is gone", e)
            dao.setNeedsReauth(source.id, true)
            return
        } catch (e: IllegalArgumentException) {
            // A documents provider's answer for a tree whose folder was moved or deleted (task 4.9).
            FoxLog.w("Wallpaper folder ${source.displayName} is gone", e)
            dao.setNeedsReauth(source.id, true)
            return
        }
        if (source.needsReauth) dao.setNeedsReauth(source.id, false)
        val diff = diffScan(dao.getEntries(source.id), found)
        if (diff.isEmpty) return
        dao.applyScan(source.id, diff.added.map { it.toEntry(source.id) }, diff.changed, diff.removedIds)
        FoxLog.i("Wallpaper folder ${source.displayName}: +${diff.added.size} ~${diff.changed.size} -${diff.removedIds.size}")
    }

    /**
     * The folder's own images (not its subfolders'), through one child-documents query. The folder
     * itself is looked up first: a moved or deleted folder lists as empty, which would silently drop
     * all its entries, so it throws [FileNotFoundException] instead (task 4.9, seen on fox-api33).
     */
    private fun listImages(tree: Uri): List<FoundImage> {
        val folderId = DocumentsContract.getTreeDocumentId(tree)
        val resolver = applicationContext.contentResolver
        val exists = resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, folderId), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
        if (!exists) throw FileNotFoundException("$tree is gone")
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = resolver.query(children, PROJECTION, null, null, null) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val mime = c.getString(COLUMN_MIME).orEmpty()
                    if (!mime.startsWith("image/")) continue
                    add(
                        FoundImage(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(COLUMN_ID)).toString(),
                            displayName = c.getString(COLUMN_NAME).orEmpty(),
                            sizeBytes = if (c.isNull(COLUMN_SIZE)) 0 else c.getLong(COLUMN_SIZE),
                            lastModified = if (c.isNull(COLUMN_MODIFIED)) 0 else c.getLong(COLUMN_MODIFIED),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        private const val NO_SOURCE = -1L
        private const val DAILY_WORK = "wallpaper_scan_daily"
        private const val SCAN_WORK = "wallpaper_scan_"

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        private const val COLUMN_ID = 0
        private const val COLUMN_NAME = 1
        private const val COLUMN_MIME = 2
        private const val COLUMN_SIZE = 3
        private const val COLUMN_MODIFIED = 4

        /** Scans one source now, e.g. right after it was added. */
        fun scanNow(workManager: WorkManager, sourceId: Long) {
            val request = OneTimeWorkRequestBuilder<SourceScanner>().setInputData(workDataOf(KEY_SOURCE_ID to sourceId)).build()
            workManager.enqueueUniqueWork(SCAN_WORK + sourceId, ExistingWorkPolicy.REPLACE, request)
        }

        /** Rescans every folder once a day; keeps an already scheduled schedule. */
        fun scheduleDaily(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SourceScanner>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
