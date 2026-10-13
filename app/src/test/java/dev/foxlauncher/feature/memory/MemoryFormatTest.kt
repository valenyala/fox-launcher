package dev.foxlauncher.feature.memory

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class MemoryFormatTest {

    private val mb = 1024L * 1024
    private val gb = mb * 1024

    @Test
    fun gigabytesHaveOneDecimalInTheLocalesStyle() {
        assertEquals("2.1", MemoryFormat.gb(2 * gb + 100 * mb, Locale.US))
        assertEquals("2,1", MemoryFormat.gb(2 * gb + 100 * mb, Locale.GERMANY))
        assertEquals("0.6", MemoryFormat.gb(640 * mb, Locale.US))
    }

    @Test
    fun megabytesAreWhole() {
        assertEquals(123, MemoryFormat.mb(123 * mb + 300 * 1024))
        assertEquals(0, MemoryFormat.mb(100 * 1024))
    }

    @Test
    fun sizesSwitchToGigabytesAtOne() {
        assertEquals("640" to false, MemoryFormat.size(640 * mb, Locale.US))
        assertEquals("1.0" to true, MemoryFormat.size(gb, Locale.US))
    }

    @Test
    fun percentIsWholeAndBounded() {
        assertEquals(62, MemoryFormat.percent(0.6249f))
        assertEquals(100, MemoryFormat.percent(1.4f))
        assertEquals(0, MemoryFormat.percent(-0.2f))
    }

    @Test
    fun onlyUpToAndroid13CanABoostCloseOtherApps() {
        assertNull(BoostCapability.limit(29, "Google"))
        assertNull(BoostCapability.limit(33, "samsung"))
        assertEquals(BoostLimit.ANDROID_14, BoostCapability.limit(34, "Google"))
        assertEquals(BoostLimit.ANDROID_14, BoostCapability.limit(37, "Xiaomi"), "Android 14 is the stronger reason")
    }

    @Test
    fun onMiuiABoostCannotCloseOtherApps() {
        assertEquals(BoostLimit.MIUI, BoostCapability.limit(33, "Xiaomi"))
        assertEquals(BoostLimit.MIUI, BoostCapability.limit(30, "xiaomi"))
    }
}
