package dev.foxlauncher.feature.widgets.privatewallpapers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.R
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A switch for the pictures marked private (user request, 2026-11-25): on, they are in the wallpaper
 * list and the rotation; off, they are neither.
 */
object PrivateWallpapersWidget {

    const val KIND = "private_wallpapers"
    const val TEST_TAG = "private_wallpapers_widget"

    val spec = NativeWidgetSpec(
        kind = KIND,
        nameRes = R.string.widget_private_wallpapers,
        descriptionRes = R.string.widget_private_wallpapers_description,
        minSpan = Span(1, 1),
        defaultSpan = Span(1, 1),
        maxSpan = Span(1, 1),
        content = { PrivateWallpapersContent(it) },
    )
}

/** The switch's state, only while the widget is composed, and the toggle. */
@HiltViewModel
class PrivateWallpapersWidgetViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val rotation: RotationScheduler,
) : ViewModel() {

    val included: StateFlow<Boolean> =
        settings.showPrivateWallpapers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

    fun toggle() {
        viewModelScope.launch {
            settings.setShowPrivateWallpapers(!settings.showPrivateWallpapers.first())
            rotation.privateChanged()
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

@Composable
private fun PrivateWallpapersContent(scope: NativeWidgetScope) {
    if (scope.isPreview) {
        PrivateWallpapersSwitch(included = false, onClick = null)
        return
    }
    val viewModel: PrivateWallpapersWidgetViewModel = hiltViewModel()
    val included by viewModel.included.collectAsStateWithLifecycle()
    PrivateWallpapersSwitch(included = included, onClick = viewModel::toggle)
}

/** An eye, open while private pictures are included. A tap toggles it; the picker's preview takes none ([onClick] null). */
@Composable
internal fun PrivateWallpapersSwitch(included: Boolean, onClick: (() -> Unit)?) {
    val label = stringResource(if (included) R.string.widget_private_wallpapers_turn_off else R.string.widget_private_wallpapers_turn_on)
    val description = stringResource(if (included) R.string.widget_private_wallpapers_on else R.string.widget_private_wallpapers_off)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(role = Role.Switch, onClickLabel = label, onClick = onClick) else Modifier)
            .semantics { stateDescription = description }
            .testTag(PrivateWallpapersWidget.TEST_TAG),
    ) {
        Icon(
            painterResource(if (included) R.drawable.ic_private_wallpapers_on else R.drawable.ic_private_wallpapers_off),
            contentDescription = stringResource(R.string.widget_private_wallpapers),
            tint = if (included) LIT else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
    }
}

private val LIT = Color(0xFFFFD54F)
