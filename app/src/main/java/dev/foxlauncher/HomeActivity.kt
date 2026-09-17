package dev.foxlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import dagger.hilt.android.AndroidEntryPoint
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.HomeRoot
import dev.foxlauncher.feature.home.HomeViewModel
import dev.foxlauncher.feature.home.isDefaultLauncher
import dev.foxlauncher.feature.home.requestDefaultLauncherRole

/**
 * The one and only activity. It is `singleTask` and handles every configuration change itself, so
 * it must never be recreated: a launcher that restarts looks like a crash. See
 * docs/PLATFORM-NOTES.md section H.
 */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FoxTheme {
                val viewModel: HomeViewModel = hiltViewModel()

                var isDefault by remember { mutableStateOf(isDefaultLauncher()) }

                val roleRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { isDefault = isDefaultLauncher() }

                // The role can also be granted or revoked while we are backgrounded (from Settings,
                // or by an OEM dialog that returns no result), so re-read it on every resume.
                LifecycleResumeEffect(Unit) {
                    isDefault = isDefaultLauncher()
                    // Idempotent: the ViewModel ignores this once first run is recorded.
                    if (isDefault) viewModel.onLauncherReady()
                    onPauseOrDispose {}
                }

                HomeRoot(
                    isDefaultLauncher = isDefault,
                    onRequestDefaultLauncher = { requestDefaultLauncherRole(roleRequest) },
                )
            }
        }
    }

    /**
     * Pressing Home while already home arrives here, because of `singleTask`. Later phases close
     * the drawer and scroll back to the default page; never finish or recreate the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        FoxLog.d("onNewIntent: ${intent.action}")
    }
}
