package dev.foxlauncher.feature.memory

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * The phone's memory (task 5.2, PLATFORM-NOTES M): `ActivityManager.getMemoryInfo` for total and
 * available, `/proc/meminfo` for the breakdown. No permission needed. Nothing polls on its own:
 * [observe] reads only while collected, and the memory widgets collect it with
 * `collectAsStateWithLifecycle`, so reading stops as soon as no widget is on screen.
 */
@Singleton
class MemoryStats @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val activityManager: ActivityManager get() = context.getSystemService(ActivityManager::class.java)

    /** Memory now, read off the main thread. */
    suspend fun snapshot(): MemorySnapshot = withContext(io) { read() }

    /** Memory every [intervalMs] for as long as it is collected; a reading equal to the last is not repeated. */
    fun observe(intervalMs: Long): Flow<MemorySnapshot> = flow {
        while (true) {
            emit(read())
            delay(intervalMs)
        }
    }.flowOn(io).distinctUntilChanged()

    private fun read(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val memInfo = try {
            MemInfoParser.parse(File(PROC_MEMINFO).readText())
        } catch (e: IOException) {
            FoxLog.w("$PROC_MEMINFO could not be read", e)
            null
        }
        return MemorySnapshot.of(info.totalMem, info.availMem, info.threshold, info.lowMemory, memInfo)
    }

    private companion object {
        const val PROC_MEMINFO = "/proc/meminfo"
    }
}
