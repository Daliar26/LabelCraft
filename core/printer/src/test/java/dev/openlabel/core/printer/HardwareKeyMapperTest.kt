package dev.openlabel.core.printer

import android.view.KeyEvent
import dev.openlabel.core.printer.hardware.HardwareAction
import dev.openlabel.core.printer.hardware.HardwareKeyMapper
import dev.openlabel.core.printer.hardware.HardwareScrollProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyMapperTest {
    private val profile = HardwareScrollProfile("00:11:22:33:44:55")
    @Test fun volumeUpScrollsUp() = assertEquals(HardwareAction.SCROLL_UP, HardwareKeyMapper.map(KeyEvent.KEYCODE_VOLUME_UP, profile))
    @Test fun volumeDownScrollsDown() = assertEquals(HardwareAction.SCROLL_DOWN, HardwareKeyMapper.map(KeyEvent.KEYCODE_VOLUME_DOWN, profile))
    @Test fun dpadUpScrollsUp() = assertEquals(HardwareAction.SCROLL_UP, HardwareKeyMapper.map(KeyEvent.KEYCODE_DPAD_UP, profile))
    @Test fun dpadDownScrollsDown() = assertEquals(HardwareAction.SCROLL_DOWN, HardwareKeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN, profile))
    @Test fun pageUpScrollsPageUp() = assertEquals(HardwareAction.PAGE_UP, HardwareKeyMapper.map(KeyEvent.KEYCODE_PAGE_UP, profile))
    @Test fun customMappingCanRemapKey() {
        val custom = profile.copy(keyMap = mapOf(KeyEvent.KEYCODE_BUTTON_R1 to HardwareAction.SCROLL_DOWN))
        assertEquals(HardwareAction.SCROLL_DOWN, HardwareKeyMapper.map(KeyEvent.KEYCODE_BUTTON_R1, custom))
    }
    @Test fun inversionReversesDirection() {
        assertEquals(HardwareAction.SCROLL_DOWN, HardwareKeyMapper.map(KeyEvent.KEYCODE_VOLUME_UP, profile.copy(inverted = true)))
    }
    @Test fun unknownKeyDoesNothing() = assertEquals(HardwareAction.NONE, HardwareKeyMapper.map(KeyEvent.KEYCODE_A, profile))
    @Test fun scrollDeltaHasCorrectSign() {
        assertTrue(HardwareKeyMapper.deltaPx(HardwareAction.SCROLL_UP, profile, 1f, 203) < 0f)
        assertTrue(HardwareKeyMapper.deltaPx(HardwareAction.SCROLL_DOWN, profile, 1f, 203) > 0f)
    }
    @Test fun pageStepIsLargerThanRegularStep() {
        val normal = HardwareKeyMapper.deltaPx(HardwareAction.SCROLL_DOWN, profile, 1f, 203)
        val page = HardwareKeyMapper.deltaPx(HardwareAction.PAGE_DOWN, profile, 1f, 203)
        assertTrue(page > normal)
    }
}
