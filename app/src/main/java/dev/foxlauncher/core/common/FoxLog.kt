package dev.foxlauncher.core.common

import android.util.Log
import dev.foxlauncher.BuildConfig

/** Single logging entry point. Debug logs are compiled out of release builds. */
object FoxLog {
    const val TAG: String = "FoxLauncher"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
