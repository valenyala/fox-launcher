package dev.foxlauncher.feature.gestures.system

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.foxlauncher.R

const val ACCESSIBILITY_DISCLOSURE_TAG = "accessibility_disclosure"

/**
 * Shown when a gesture maps to a system action while Fox's accessibility service is off (task
 * 3.4). It says what the service does and does not do before sending the user to the system's
 * accessibility settings (PLATFORM-NOTES G). When the service is on but [stopped], it says to turn
 * it off and on again instead.
 */
@Composable
fun AccessibilityDisclosureDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit, stopped: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.accessibility_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_not_now)) } },
        title = {
            Text(stringResource(if (stopped) R.string.accessibility_stopped_title else R.string.accessibility_disclosure_title))
        },
        text = { Text(stringResource(if (stopped) R.string.accessibility_stopped else R.string.accessibility_disclosure)) },
        modifier = Modifier.testTag(ACCESSIBILITY_DISCLOSURE_TAG),
    )
}
