package dev.foxlauncher.feature.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [DefaultAppResolver] for this device (task 2.7): whatever opens a dial intent, a web link and the
 * still camera, plus the default SMS app. The activity that handles an intent is often not the
 * app's launcher icon (the Phone app answers DIAL with its main activity, MIUI's camera with an
 * assistant entry), so each role maps to its package's launch activity. Blocking `PackageManager`
 * calls: run off the main thread.
 */
class DeviceDefaultApps @Inject constructor(@param:ApplicationContext private val context: Context) : DefaultAppResolver {

    private val packageManager = context.packageManager

    override fun launcherComponentFor(app: DefaultApp): String? {
        val packageName = when (app) {
            DefaultApp.DIALER -> handlerOf(Intent(Intent.ACTION_DIAL))
            DefaultApp.SMS -> Telephony.Sms.getDefaultSmsPackage(context)
            DefaultApp.BROWSER -> handlerOf(
                Intent(Intent.ACTION_VIEW, "http://www.example.com/".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
            )
            DefaultApp.CAMERA -> handlerOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } ?: return null
        return packageManager.getLaunchIntentForPackage(packageName)?.component?.flattenToString()
    }

    /**
     * The package [intent] opens without asking. With several handlers and no default the system
     * resolves to its chooser, which is not among the handlers; then take the only system app among
     * them, as Launcher3 does, or nothing.
     */
    private fun handlerOf(intent: Intent): String? {
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        val handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo }
        if (handlers.any { it.packageName == resolved.packageName }) return resolved.packageName
        return handlers
            .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.packageName }
            .distinct()
            .singleOrNull()
    }
}
