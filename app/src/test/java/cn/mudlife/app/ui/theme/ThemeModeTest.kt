package cn.mudlife.app.ui.theme

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ThemeModeTest {
    @Test fun automaticModeTracksBothSystemAppearances() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.resolve(false))
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.resolve(true))
    }

    @Test fun explicitChoiceDoesNotChangeWithSystem() {
        assertEquals(ThemeMode.DARK, ThemeMode.DARK.resolve(false))
        assertEquals(ThemeMode.LIGHT, ThemeMode.LIGHT.resolve(true))
    }

    @Test fun savedChoicesRestoreAndUnknownValueFallsBackToSystem() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.restore(it.name)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.restore(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.restore("obsolete"))
    }

    @Test fun revealCoversEveryCornerFromCenterEdgesAndOffCenterPresses() {
        for ((width, height) in listOf(1080f to 2400f, 2400f to 1080f)) {
            for ((x, y) in listOf(0f to 0f, width to height, width / 2 to height / 2, 17f to height - 91f)) {
                val radius = ThemeRevealGeometry.radius(width, height, x, y)
                for ((cx, cy) in listOf(0f to 0f, width to 0f, 0f to height, width to height))
                    assertTrue("Uncovered corner for $width x $height from $x,$y", radius > hypot(cx - x, cy - y))
            }
        }
    }
}
