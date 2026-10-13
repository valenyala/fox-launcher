package dev.foxlauncher.feature.memory.appwidget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.feature.memory.MemorySnapshot
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The RAM gauge app widget's views (task 5.5), applied as a launcher would. Run in an English locale. */
@RunWith(AndroidJUnit4::class)
class MemoryGaugeViewsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mb = 1024L * 1024
    private val gb = mb * 1024

    @Test
    fun theGaugeShowsUseAndWhatIsFree() {
        val snapshot = MemorySnapshot(6 * gb, 2 * gb + 100 * mb, gb / 4, false, null, null, null, null)
        lateinit var view: View

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = MemoryGaugeViews.build(context, snapshot).apply(context, FrameLayout(context))
        }

        assertEquals("65%", view.findViewById<TextView>(R.id.memory_gauge_percent).text.toString())
        assertEquals("2.1 GB free", view.findViewById<TextView>(R.id.memory_gauge_free).text.toString())
        assertEquals(65, view.findViewById<ProgressBar>(R.id.memory_gauge_ring).progress)
    }
}
