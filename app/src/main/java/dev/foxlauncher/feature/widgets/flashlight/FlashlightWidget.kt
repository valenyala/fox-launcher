package dev.foxlauncher.feature.widgets.flashlight

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
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A torch to tap to turn the phone's flashlight on and off (user request, 2026-11-25). */
object FlashlightWidget {

    const val KIND = "flashlight"
    const val TEST_TAG = "flashlight_widget"

    val spec = NativeWidgetSpec(
        kind = KIND,
        nameRes = R.string.widget_flashlight,
        descriptionRes = R.string.widget_flashlight_description,
        minSpan = Span(1, 1),
        defaultSpan = Span(1, 1),
        maxSpan = Span(1, 1),
        content = { FlashlightContent(it) },
    )
}

/** The torch's state, only while a Flashlight widget is composed, and the toggle. */
@HiltViewModel
class FlashlightWidgetViewModel @Inject constructor(private val flashlight: Flashlight) : ViewModel() {

    val state: StateFlow<TorchState> =
        flashlight.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), TorchState.OFF)

    fun toggle() {
        val on = state.value != TorchState.ON
        viewModelScope.launch { FoxLog.d("Flashlight widget ${if (on) "on" else "off"}: ${flashlight.set(on)}") }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

@Composable
private fun FlashlightContent(scope: NativeWidgetScope) {
    if (scope.isPreview) {
        FlashlightButton(state = TorchState.OFF, onClick = null)
        return
    }
    val viewModel: FlashlightWidgetViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FlashlightButton(state = state, onClick = viewModel::toggle)
}

/**
 * The torch icon: lit amber when on, dim when the torch cannot be switched. A tap toggles it, except
 * without a flash or while another app holds the camera. The picker's preview takes no tap ([onClick] null).
 */
@Composable
internal fun FlashlightButton(state: TorchState, onClick: (() -> Unit)?) {
    val usable = state == TorchState.ON || state == TorchState.OFF
    val label = stringResource(if (state == TorchState.ON) R.string.widget_flashlight_turn_off else R.string.widget_flashlight_turn_on)
    val description = stringResource(
        when (state) {
            TorchState.ON -> R.string.widget_flashlight_on
            TorchState.OFF -> R.string.widget_flashlight_off
            TorchState.BUSY -> R.string.widget_flashlight_busy
            TorchState.NONE -> R.string.widget_flashlight_none
        },
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (onClick != null && usable) Modifier.clickable(role = Role.Switch, onClickLabel = label, onClick = onClick) else Modifier,
            )
            .semantics { stateDescription = description }
            .testTag(FlashlightWidget.TEST_TAG),
    ) {
        Icon(
            painterResource(if (state == TorchState.ON) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off),
            contentDescription = stringResource(R.string.widget_flashlight),
            tint = when (state) {
                TorchState.ON -> LIT
                TorchState.OFF -> Color.White
                else -> Color.White.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

private val ICON_SIZE = 32.dp
private val LIT = Color(0xFFFFD54F)
