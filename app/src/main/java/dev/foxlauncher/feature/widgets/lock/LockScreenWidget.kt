package dev.foxlauncher.feature.widgets.lock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.ActionExecutor
import dev.foxlauncher.feature.gestures.ActionResult
import dev.foxlauncher.feature.gestures.SystemAction
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog
import dev.foxlauncher.feature.gestures.system.AccessibilityRepair
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A padlock to tap to lock the screen (task 5.7), as the Lock screen gesture does. */
object LockScreenWidget {

    const val KIND = "lock_screen"
    const val TEST_TAG = "lock_screen_widget"

    val spec = NativeWidgetSpec(
        kind = KIND,
        nameRes = R.string.widget_lock_screen,
        descriptionRes = R.string.widget_lock_screen_description,
        minSpan = Span(1, 1),
        defaultSpan = Span(1, 1),
        maxSpan = Span(1, 1),
        content = { LockScreenContent(it) },
    )
}

/**
 * Locks the screen through Fox's accessibility service (ADR-4), the way the gestures do: a stopped
 * service is restarted first when Fox may (ADR-18); otherwise [prompt] says which dialog to show.
 */
@HiltViewModel
class LockScreenWidgetViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actions: ActionExecutor,
    private val accessibilityRepair: AccessibilityRepair,
) : ViewModel() {

    private val _prompt = MutableStateFlow<AccessibilityStatus?>(null)

    /** Set while the accessibility dialog should show: the disclosure ([AccessibilityStatus.OFF]) or "turn it off and on". */
    val prompt: StateFlow<AccessibilityStatus?> = _prompt.asStateFlow()

    fun lock() {
        val result = actions.perform(SystemAction.LOCK_SCREEN)
        FoxLog.d("Lock screen widget: $result")
        if (result != ActionResult.NEEDS_ACCESSIBILITY && result != ActionResult.ACCESSIBILITY_STOPPED) return
        viewModelScope.launch {
            when (val prompt = accessibilityRepair.recover(stopped = result == ActionResult.ACCESSIBILITY_STOPPED)) {
                null -> FoxLog.d("Lock screen widget after restarting the service: ${actions.perform(SystemAction.LOCK_SCREEN)}")
                else -> _prompt.value = prompt
            }
        }
    }

    fun dismissPrompt() {
        _prompt.value = null
    }

    fun openAccessibilitySettings() {
        _prompt.value = null
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            FoxLog.e("No accessibility settings screen", e)
        }
    }
}

@Composable
private fun LockScreenContent(scope: NativeWidgetScope) {
    if (scope.isPreview) {
        LockButton(onClick = null)
        return
    }
    val viewModel: LockScreenWidgetViewModel = hiltViewModel()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    LockButton(onClick = viewModel::lock)
    prompt?.let { status ->
        AccessibilityDisclosureDialog(
            stopped = status == AccessibilityStatus.STOPPED,
            onOpenSettings = viewModel::openAccessibilitySettings,
            onDismiss = viewModel::dismissPrompt,
        )
    }
}

/** The padlock; a tap locks the screen. The picker's preview takes no tap ([onClick] null). */
@Composable
internal fun LockButton(onClick: (() -> Unit)?) {
    val label = stringResource(R.string.widget_lock_screen_action)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier)
            .testTag(LockScreenWidget.TEST_TAG),
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = label, tint = Color.White, modifier = Modifier.size(ICON_SIZE))
    }
}

private val ICON_SIZE = 32.dp
