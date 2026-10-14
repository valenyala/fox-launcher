package dev.foxlauncher.feature.shizuku

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ShellCommandsTest {

    @Test
    fun aPackageIsClosedByItsOwnArgument() {
        assertEquals(listOf("cmd", "activity", "kill", "--user", "current", "com.whatsapp"), ShellCommands.killBackgroundProcesses("com.whatsapp"))
    }

    @Test
    fun anythingButAPackageNameIsRefused() {
        assertNull(ShellCommands.killBackgroundProcesses("com.app; reboot"))
        assertNull(ShellCommands.killBackgroundProcesses("--user"))
        assertNull(ShellCommands.killBackgroundProcesses("nodots"))
        assertNull(ShellCommands.killBackgroundProcesses(""))
    }

    @Test
    fun miuiHidesTheButtonsWithItsOwnSetting() {
        assertEquals(listOf("settings", "put", "global", "force_fsg_nav_bar", "1"), ShellCommands.navigationButtons(miui = true, hidden = true))
        assertEquals(listOf("settings", "put", "global", "force_fsg_nav_bar", "0"), ShellCommands.navigationButtons(miui = true, hidden = false))
    }

    @Test
    fun otherPhonesSwitchAndroidsNavigationOverlay() {
        assertEquals(
            listOf("cmd", "overlay", "enable-exclusive", "--category", "com.android.internal.systemui.navbar.gestural"),
            ShellCommands.navigationButtons(miui = false, hidden = true),
        )
        assertEquals(
            listOf("cmd", "overlay", "enable-exclusive", "--category", "com.android.internal.systemui.navbar.threebutton"),
            ShellCommands.navigationButtons(miui = false, hidden = false),
        )
    }

    @Test
    fun hiddenIsReadFromTheSettingThatCounts() {
        assertEquals(true, ShellCommands.navigationButtonsHidden(miui = true, miuiSetting = 1, navigationMode = 0))
        assertEquals(false, ShellCommands.navigationButtonsHidden(miui = true, miuiSetting = 0, navigationMode = 2))
        assertEquals(true, ShellCommands.navigationButtonsHidden(miui = false, miuiSetting = null, navigationMode = 2))
        assertEquals(false, ShellCommands.navigationButtonsHidden(miui = false, miuiSetting = 1, navigationMode = 0))
        assertNull(ShellCommands.navigationButtonsHidden(miui = true, miuiSetting = null, navigationMode = 2))
        assertNull(ShellCommands.navigationButtonsHidden(miui = false, miuiSetting = 1, navigationMode = null))
    }
}
