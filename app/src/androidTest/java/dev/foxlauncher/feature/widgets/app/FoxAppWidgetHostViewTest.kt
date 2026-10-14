package dev.foxlauncher.feature.widgets.app

import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Which touches on an app widget belong to a list in it (task 6.4): those the home's gestures leave alone. */
@RunWith(AndroidJUnit4::class)
class FoxAppWidgetHostViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var widget: FoxAppWidgetHostView
    private lateinit var list: ScrollView
    private lateinit var title: TextView

    /** A widget with a title above a list of [rows] rows, 200 dp square. */
    private fun show(rows: Int) {
        composeRule.setContent {
            AndroidView(
                factory = { context ->
                    FoxAppWidgetHostView(context).also { host ->
                        widget = host
                        title = TextView(context).apply { text = "Title" }
                        list = ScrollView(context).apply {
                            addView(
                                LinearLayout(context).apply {
                                    orientation = LinearLayout.VERTICAL
                                    repeat(rows) { addView(TextView(context).apply { text = "Row $it" }, LayoutParams(LayoutParams.MATCH_PARENT, ROW_PX)) }
                                },
                            )
                        }
                        host.addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(title, LayoutParams(LayoutParams.MATCH_PARENT, ROW_PX))
                                addView(list, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                            },
                        )
                    }
                },
                modifier = Modifier.size(200.dp),
            )
        }
        composeRule.waitForIdle()
    }

    private fun centreOf(view: View): Pair<Float, Float> {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[0] + view.width / 2f to location[1] + view.height / 2f
    }

    @Test
    fun aListLongerThanTheWidgetTakesTouchesOnIt() {
        show(rows = 40)

        val (x, y) = composeRule.runOnIdle { centreOf(list) }
        assertTrue(composeRule.runOnIdle { widget.scrollsVerticallyAt(x, y) })
    }

    @Test
    fun theRestOfTheWidgetDoesNot() {
        show(rows = 40)

        val (x, y) = composeRule.runOnIdle { centreOf(title) }
        assertFalse(composeRule.runOnIdle { widget.scrollsVerticallyAt(x, y) }, "the title above the list")
        assertFalse(composeRule.runOnIdle { widget.scrollsVerticallyAt(-10f, -10f) }, "outside the widget")
    }

    @Test
    fun aListThatFitsDoesNotScrollSoTheHomeKeepsTheSwipe() {
        show(rows = 1)

        val (x, y) = composeRule.runOnIdle { centreOf(list) }
        assertFalse(composeRule.runOnIdle { widget.scrollsVerticallyAt(x, y) })
    }

    @Test
    fun aLaidOutWidgetHasAStillForTheDragOverlay() {
        show(rows = 1)

        assertNotNull(composeRule.runOnIdle { widget.snapshot() })
    }

    private companion object {
        const val ROW_PX = 60
    }
}
