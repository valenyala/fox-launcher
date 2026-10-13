package dev.foxlauncher.feature.wallpaper

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.foxlauncher.R

/**
 * Shown when a wallpaper gesture meets a system wallpaper that is not Fox's (task 4.5): Fox does
 * not replace the user's own wallpaper from a gesture, it offers its live wallpaper instead.
 */
@Composable
fun LiveWallpaperPromptDialog(onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.wallpaper_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_not_now)) } },
        title = { Text(stringResource(R.string.wallpaper_prompt_title)) },
        text = { Text(stringResource(R.string.wallpaper_prompt)) },
    )
}
