package dev.foxlauncher.feature.wallpaper

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.foxlauncher.core.model.RotationTrigger

/** The interval trigger for 15 minutes and more (task 4.4); [RotationScheduler.syncSchedule] schedules it. */
@HiltWorker
class RotationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rotation: RotationScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        rotation.advanceIfDue(RotationTrigger.INTERVAL)
        return Result.success()
    }
}
