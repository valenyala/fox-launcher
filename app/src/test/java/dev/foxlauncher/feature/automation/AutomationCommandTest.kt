package dev.foxlauncher.feature.automation

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AutomationCommandTest {

    @Test
    fun theDocumentedActionsNameTheirCommands() {
        // The README documents these strings; changing one breaks users' automations.
        assertEquals(AutomationCommand.NEXT_WALLPAPER, AutomationCommand.of("dev.foxlauncher.NEXT_WALLPAPER"))
        assertEquals(AutomationCommand.PREV_WALLPAPER, AutomationCommand.of("dev.foxlauncher.PREV_WALLPAPER"))
        assertEquals(AutomationCommand.BOOST, AutomationCommand.of("dev.foxlauncher.BOOST"))
    }

    @Test
    fun anythingElseIsNoCommand() {
        assertNull(AutomationCommand.of(null))
        assertNull(AutomationCommand.of("dev.foxlauncher.next_wallpaper"))
        assertNull(AutomationCommand.of("android.intent.action.BOOT_COMPLETED"))
    }
}
