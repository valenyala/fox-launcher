package dev.foxlauncher.core.apps.iconpack

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AppFilter] on Android's own regex engine (ICU), which rejected a pattern the JVM's accepted and
 * crashed the home as soon as a pack was chosen (task 7.1). The JVM tests cover the rest.
 */
@RunWith(AndroidJUnit4::class)
class AppFilterOnDeviceTest {

    @Test
    fun componentInfoParsesOnAndroid() {
        assertEquals("org.example/org.example.Main", AppFilter.componentOf("ComponentInfo{org.example/.Main}"))
        val filter = AppFilter.parse(
            sequenceOf(XmlTag("item", mapOf("component" to "ComponentInfo{a.b/a.b.Main}", "drawable" to "ab"))),
        )
        assertEquals("ab", filter.drawableFor("a.b/a.b.Main"))
    }
}
