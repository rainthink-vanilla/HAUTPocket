package cn.hautpocket.app.domain

import cn.hautpocket.app.data.StateCodec
import org.junit.Assert.*
import org.junit.Test

class TextColorsTest {
    @Test fun customThemeRoundTripsAndMalformedLegacyThemeFallsBack() {
        val state = PocketState(themeColor = "#2489AB", textColor = "#123456")
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertEquals("#ABCDEF", StateCodec.decode("""{"version":1,"themeColor":"#abcdef"}""").themeColor)
        assertEquals("green", StateCodec.decode("""{"version":1,"themeColor":"bad-css"}""").themeColor)
    }
    @Test fun invalidStoredColorsUseSafeDefaultAndCustomColorsRoundTrip() {
        assertEquals("auto", StateCodec.decode("""{"version":1,"textColor":"bad-css"}""").textColor)
        val state = PocketState(textColor = "#D5EAFF")
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
        assertEquals("#ABCDEF", TextColors.normalize("#abcdef"))
        assertFalse(TextColors.validCustom("red"))
        assertFalse(TextColors.validCustom("#ffffff;background:red"))
    }
    @Test fun adaptivePalettesRemainReadableInBothModes() {
        for ((preset, _) in TextColors.presets) for (dark in listOf(false, true)) {
            val (text, secondary) = TextColors.palette(preset, dark)
            val bg = if (dark) "#1A2724" else "#FFFFFF"
            assertTrue("$preset $dark", TextColors.contrast(text, bg) >= 4.5)
            assertTrue("$preset secondary $dark", TextColors.contrast(secondary, bg) >= 4.5)
        }
        assertEquals(21.0, TextColors.contrast("#000000", "#FFFFFF"), 0.0001)
        assertEquals("#123456" to "#123456", TextColors.palette("#123456", true))
    }
}
