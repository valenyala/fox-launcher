package dev.foxlauncher.core.apps.iconpack

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** An installed icon pack, as the picker lists it. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A pack's [filter] with its resources, for drawing (task 7.1). The filter holds only the apps that
 * were installed when it was read, [readFor]; null means all of them. Used on IO only.
 */
class LoadedIconPack internal constructor(
    val packageName: String,
    private val resources: Resources,
    val filter: AppFilter,
    private val readFor: Set<String>?,
) {
    /** Whether the filter was read with [appPackage] in mind; if not, read it again for that app. */
    fun covers(appPackage: String): Boolean = readFor == null || appPackage in readFor

    private val ids = ConcurrentHashMap<String, Int>()

    /** The pack's drawable [name] at [densityDpi], or null when the pack lacks it. */
    fun drawable(name: String, densityDpi: Int): Drawable? {
        val id = ids.getOrPut(name) { idOf(name) }
        if (id == 0) return null
        return try {
            resources.getDrawableForDensity(id, densityDpi, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    // Icon packs name their drawables in appfilter.xml; looking them up by name is the format.
    @SuppressLint("DiscouragedApi")
    private fun idOf(name: String): Int =
        resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(name, "mipmap", packageName)
}

/**
 * The ADW / Nova icon packs installed (task 7.1): found by the actions packs declare (the manifest
 * names them in `<queries>`), and loaded on demand from their `res/xml/appfilter.xml`, or
 * `assets/appfilter.xml` for older packs. The loaded pack is kept until it changes or another is chosen,
 * holding only the installed apps' entries; an app installed later makes it read again once.
 */
@Singleton
class IconPacks @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope scope: CoroutineScope,
    private val appRepository: AppRepository,
) {
    private val _installed = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installed: StateFlow<List<IconPackInfo>> = _installed.asStateFlow()

    private val _changed = MutableSharedFlow<String>(extraBufferCapacity = CHANGE_BUFFER)

    /** The package name of an icon pack that was updated or removed, after its cached copy was dropped. */
    val changed: SharedFlow<String> = _changed.asSharedFlow()

    private val mutex = Mutex()
    private var loaded: LoadedIconPack? = null

    init {
        scope.launch { refresh() }
        scope.launch {
            appRepository.packageChanges.collect { change ->
                val wasLoaded = mutex.withLock {
                    (loaded?.packageName == change.packageName).also { if (it) loaded = null }
                }
                refresh()
                if (wasLoaded || _installed.value.any { it.packageName == change.packageName }) _changed.emit(change.packageName)
            }
        }
    }

    /** Looks the installed packs up again, off the main thread. */
    suspend fun refresh() {
        _installed.value = withContext(io) {
            val pm = context.packageManager
            ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }

    /**
     * [packageName]'s pack for drawing [appPackage]'s icon, loaded once; null when it is not
     * installed or has no readable appfilter.
     */
    suspend fun load(packageName: String, appPackage: String): LoadedIconPack? = mutex.withLock {
        loaded?.takeIf { it.packageName == packageName && it.covers(appPackage) }?.let { return it }
        val installed = appRepository.apps.value.mapTo(HashSet()) { it.packageName }.takeIf { it.isNotEmpty() }?.plus(appPackage)
        withContext(io) { read(packageName, installed) }.also { loaded = it }
    }

    private fun read(packageName: String, installed: Set<String>?): LoadedIconPack? = try {
        val start = SystemClock.elapsedRealtime()
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val keep: (String) -> Boolean = if (installed == null) ({ true }) else ({ it in installed })
        val filter = readAppFilter(packageName, resources) { tags -> AppFilter.parse(tags, keep) }
        if (filter == null) {
            FoxLog.w("Icon pack $packageName has no appfilter.xml")
            null
        } else {
            LoadedIconPack(packageName, resources, filter, installed).also {
                FoxLog.d("Icon pack $packageName: ${it.filter.drawables.size} icons kept in ${SystemClock.elapsedRealtime() - start} ms")
            }
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: XmlPullParserException) {
        FoxLog.w("Icon pack $packageName: unreadable appfilter.xml", e)
        null
    } catch (e: IOException) {
        FoxLog.w("Icon pack $packageName: unreadable appfilter.xml", e)
        null
    } catch (e: RuntimeException) {
        // Another app's resources: whatever is wrong with them must not take the home down.
        FoxLog.w("Icon pack $packageName could not be read", e)
        null
    }

    /** Runs [parse] over the pack's appfilter tags while the file is open; null when there is none. */
    @SuppressLint("DiscouragedApi")
    private fun readAppFilter(packageName: String, resources: Resources, parse: (Sequence<XmlTag>) -> AppFilter): AppFilter? {
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return resources.getXml(id).use { parse(tagsOf(it)) }
        val assets = context.createPackageContext(packageName, 0).assets
        val stream = try {
            assets.open("appfilter.xml")
        } catch (e: IOException) {
            return null
        }
        return stream.use {
            val parser = Xml.newPullParser()
            parser.setInput(it, null)
            parse(tagsOf(parser))
        }
    }

    /** Every start tag with its attributes, read as consumed: a pack's tens of thousands of tags are never all held at once. */
    private fun tagsOf(parser: XmlPullParser): Sequence<XmlTag> = sequence {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                yield(XmlTag(parser.name, attributes))
            }
            event = parser.next()
        }
    }

    private companion object {
        /** What ADW, Nova, Go and Apex packs declare; most packs declare several. */
        val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
        )
        const val CHANGE_BUFFER = 8
    }
}
