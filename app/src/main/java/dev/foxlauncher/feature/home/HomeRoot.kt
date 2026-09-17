package dev.foxlauncher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R

/**
 * The whole home screen, for now. Stateless on purpose: the role state is passed in so this can be
 * exercised from a Compose UI test without touching `RoleManager`.
 */
@Composable
fun HomeRoot(
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        if (isDefaultLauncher) {
            // Placeholder chrome until the home grid lands in Phase 2. White with a shadow,
            // because it sits directly on an arbitrary wallpaper.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge.copy(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        } else {
            SetDefaultLauncherPrompt(onRequestDefaultLauncher)
        }
    }
}

@Composable
private fun SetDefaultLauncherPrompt(onRequestDefaultLauncher: () -> Unit) {
    // A surface, because the window is transparent and plain text over an arbitrary wallpaper is
    // unreadable.
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        modifier = Modifier.padding(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.default_launcher_explainer),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRequestDefaultLauncher) {
                Text(stringResource(R.string.set_as_default_launcher))
            }
        }
    }
}
