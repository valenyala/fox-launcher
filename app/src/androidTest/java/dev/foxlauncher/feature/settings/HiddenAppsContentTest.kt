package dev.foxlauncher.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HiddenAppsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rowsReflectHiddenStateAndReportToggles() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        val state = HiddenAppsUiState(
            persistentListOf(HiddenAppRow(app("Bank"), isHidden = true), HiddenAppRow(app("Maps"), isHidden = false)),
        )
        composeRule.setContent {
            FoxTheme {
                HiddenAppsContent(
                    state = state,
                    onHiddenChange = { entry, hidden -> changes += entry.label to hidden },
                    onBack = {},
                    iconContent = { Box(Modifier.size(40.dp)) },
                )
            }
        }

        composeRule.onNodeWithText("Bank").assertIsOn()
        composeRule.onNodeWithText("Maps").assertIsOff()
        composeRule.onNodeWithText("Maps").performClick()

        assertEquals(listOf("Maps" to true), changes)
    }

    private fun app(label: String) = AppEntry(
        component = "test.${label.lowercase()}/.Main",
        userSerial = 0,
        label = label,
        packageName = "test.${label.lowercase()}",
        isWorkProfile = false,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}
