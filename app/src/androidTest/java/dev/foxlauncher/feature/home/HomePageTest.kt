package dev.foxlauncher.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import dev.foxlauncher.feature.home.grid.GridSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itemsArePlacedAtTheirCellWithTheirSpan() {
        // 4×5 grid in a 320×400 dp box → 80 dp cells. Small enough for any phone: a box wider than
        // the screen is squeezed, and every cell with it.
        val engine = GridLayoutEngine(GridSpec(cols = 4, rows = 5, dockSlots = 4))
        val items = listOf(
            HomeItem.App(1, HomePosition(HomeContainer.PAGE, 0, col = 2, row = 1), "a/.A", 0),
            HomeItem.NativeWidget(2, HomePosition(HomeContainer.PAGE, 0, col = 0, row = 3, spanX = 2, spanY = 2), "ram"),
        )
        composeRule.setContent {
            Box(Modifier.size(320.dp, 400.dp)) {
                HomePage(items, engine, Modifier.fillMaxSize()) { item ->
                    Box(Modifier.fillMaxSize().testTag("item-${item.id}"))
                }
            }
        }

        composeRule.onNodeWithTag("item-1")
            .assertLeftPositionInRootIsEqualTo(160.dp)
            .assertTopPositionInRootIsEqualTo(80.dp)
            .assertWidthIsEqualTo(80.dp)
        composeRule.onNodeWithTag("item-2")
            .assertLeftPositionInRootIsEqualTo(0.dp)
            .assertTopPositionInRootIsEqualTo(240.dp)
            .assertWidthIsEqualTo(160.dp)
            .assertHeightIsEqualTo(160.dp)
    }
}
