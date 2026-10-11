package dev.foxlauncher.core.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.AppShortcut
import dev.foxlauncher.core.ui.IconShape
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App icons rendered once, off the main thread, and cached in memory and on disk. Icon loading is
 * the classic launcher jank source, so composition only ever sees finished [ImageBitmap]s.
 *
 * Disk files live under `filesDir/icons/<sizePx>-<shape>/` as `<package>@<serial>@<hash>.png`, where
 * the hash covers `component|serial|versionCode|sizePx|shape`. The package/serial prefix is what lets
 * [invalidate] find a package's files; the per-config directory is what lets a settings change drop
 * every stale icon at once.
 */
@Singleton
class IconCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val appRepository: AppRepository,
    settings: FoxSettings,
) {
    private data class Config(val sizePx: Int, val shape: IconShape) {
        val dirName: String get() = "$sizePx-${shape.id}"
    }

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val rootDir = File(context.filesDir, ICONS_DIR)

    @Volatile
    private var config = Config(dpToPx(FoxSettings.DEFAULT_ICON_SIZE_DP), IconShape.DEFAULT)

    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.asAndroidBitmap().allocationByteCount / BYTES_PER_KB
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap>>()

    private val _generation = MutableStateFlow(0L)

    /**
     * Bumped whenever cached icons stop being valid (package changed, icon settings changed).
     * Composables key their icon loads on it so they re-fetch.
     */
    val generation: StateFlow<Long> = _generation.asStateFlow()

    init {
        scope.launch {
            combine(settings.iconSizeDp, settings.iconShapeId) { sizeDp, shapeId ->
                Config(dpToPx(sizeDp), IconShape.fromId(shapeId))
            }.distinctUntilChanged().collect(::applyConfig)
        }
        scope.launch {
            appRepository.packageChanges.collect { invalidate(it.packageName, it.userSerial) }
        }
    }

    /** The icon if it is already in memory. Never touches disk; safe to call during composition. */
    fun cached(entry: AppEntry): ImageBitmap? = memory.get(keyOf(entry, config))

    /** The icon for [entry], from memory, then disk, then rendered from `LauncherApps`. */
    suspend fun icon(entry: AppEntry): ImageBitmap {
        val cfg = config
        val key = keyOf(entry, cfg)
        memory.get(key)?.let { return it }
        // One load per key even when several composables ask at once. The load runs in the
        // application scope so a scrolled-away caller does not throw away finished work.
        val load = inFlight.computeIfAbsent(key) { scope.async(io) { load(entry, cfg, key) } }
        return try {
            load.await()
        } finally {
            inFlight.remove(key, load)
        }
    }

    /**
     * [shortcut]'s icon at the home icon size and shape, kept in memory only (apps change them at
     * will); null when the shortcut is gone or Fox is not the default launcher. The key starts like
     * an app icon's, `<package>/|<serial>|…`, so [invalidate] drops it with its app's icons.
     */
    suspend fun shortcutIcon(shortcut: AppShortcut): ImageBitmap? {
        val cfg = config
        val key = listOf("${shortcut.packageName}/", shortcut.userSerial, "shortcut:${shortcut.id}", cfg.sizePx, cfg.shape.id)
            .joinToString(KEY_SEPARATOR)
        memory.get(key)?.let { return it }
        val info = appRepository.shortcutInfo(shortcut.packageName, shortcut.id, shortcut.userSerial) ?: return null
        return withContext(io) {
            launcherApps.getShortcutIconDrawable(info, densityDpi)
                ?.let { IconRenderer.render(it, cfg.sizePx, cfg.shape).asImageBitmap() }
                ?.also { memory.put(key, it) }
        }
    }

    /** Drops every cached icon of [packageName] in the profile [userSerial], in memory and on disk. */
    suspend fun invalidate(packageName: String, userSerial: Long) {
        memory.snapshot().keys
            .filter { key ->
                val parts = key.split(KEY_SEPARATOR)
                parts[0].substringBefore('/') == packageName && parts[1] == userSerial.toString()
            }
            .forEach(memory::remove)
        withContext(io) {
            val prefix = filePrefix(packageName, userSerial)
            File(rootDir, config.dirName).listFiles { file -> file.name.startsWith(prefix) }
                ?.forEach(File::delete)
        }
        _generation.update { it + 1 }
    }

    fun clearMemory() {
        memory.evictAll()
    }

    private suspend fun applyConfig(newConfig: Config) {
        if (newConfig == config) return
        config = newConfig
        clearMemory()
        withContext(io) {
            rootDir.listFiles { file -> file.isDirectory && file.name != newConfig.dirName }
                ?.forEach(File::deleteRecursively)
        }
        _generation.update { it + 1 }
    }

    private fun load(entry: AppEntry, cfg: Config, key: String): ImageBitmap {
        val start = SystemClock.elapsedRealtimeNanos()
        val file = File(File(rootDir, cfg.dirName), filePrefix(entry.packageName, entry.userSerial) + sha1(key) + ".png")
        val fromDisk = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        val bitmap = fromDisk ?: render(entry, cfg).also { write(it, file) }
        val image = bitmap.asImageBitmap()
        memory.put(key, image)
        val micros = (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MICRO
        FoxLog.d("icon ${if (fromDisk != null) "disk" else "render"} ${entry.component} in ${micros}µs")
        return image
    }

    private fun render(entry: AppEntry, cfg: Config): Bitmap {
        val user = appRepository.userFor(entry.userSerial)
        val component = ComponentName.unflattenFromString(entry.component)
        val info = if (user != null && component != null) {
            launcherApps.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component),
                user,
            )
        } else {
            null
        }
        val drawable = info?.getIcon(densityDpi) ?: context.packageManager.defaultActivityIcon
        val bitmap = IconRenderer.render(drawable, cfg.sizePx, cfg.shape)
        if (!entry.isWorkProfile || user == null) return bitmap

        // Badged icons come back as a drawable of the same bounds with the briefcase drawn on top.
        val badged = context.packageManager.getUserBadgedIcon(bitmap.toDrawable(context.resources), user)
        return createBitmap(cfg.sizePx, cfg.sizePx).also { out ->
            badged.setBounds(0, 0, cfg.sizePx, cfg.sizePx)
            badged.draw(Canvas(out))
        }
    }

    /** Writes via a temp file and rename, so a crash mid-write never leaves a truncated PNG behind. */
    private fun write(bitmap: Bitmap, file: File) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (e: IOException) {
            FoxLog.w("Could not write icon ${file.name}", e)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()).roundToInt()

    private companion object {
        const val ICONS_DIR = "icons"
        const val KEY_SEPARATOR = "|"
        const val MEMORY_CACHE_KB = 48 * 1024
        const val BYTES_PER_KB = 1024
        const val PNG_QUALITY = 100
        const val NANOS_PER_MICRO = 1_000L
        const val HASH_HEX_CHARS = 16

        fun keyOf(entry: AppEntry, cfg: Config): String = listOf(
            entry.component,
            entry.userSerial,
            entry.versionCode,
            cfg.sizePx,
            cfg.shape.id,
        ).joinToString(KEY_SEPARATOR)

        fun filePrefix(packageName: String, userSerial: Long) = "$packageName@$userSerial@"

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(HASH_HEX_CHARS)
    }
}
