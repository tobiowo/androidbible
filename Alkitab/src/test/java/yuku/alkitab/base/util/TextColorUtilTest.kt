package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.dialog.TypeHighlightDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextColorUtilTest {

    private val BLACK = 0xff000000.toInt()
    private val WHITE = 0xffffffff.toInt()
    private val LIGHT_PAGE = 0xfff0f0f0.toInt()
    private val LIGHT_PAGE_TEXT = 0xff212121.toInt()
    private val NIGHT_PAGE = 0xff000000.toInt()
    private val NIGHT_PAGE_TEXT = 0xffaeaeae.toInt()
    private val BLUE_SELECTION = 0xff0277bd.toInt()
    private val LIGHT_YELLOW_SELECTION = 0xfffff59d.toInt()

    @Test
    fun `getForCheckedVerse picks white on a dark selection and black on a light one`() {
        assertEquals(WHITE, TextColorUtil.getForCheckedVerse(BLUE_SELECTION))
        assertEquals(BLACK, TextColorUtil.getForCheckedVerse(LIGHT_YELLOW_SELECTION))
    }

    @Test
    fun `a yellow band on a light page keeps the reading color even though the blue selection alone would force white`() {
        val band = Highlights.bandColor(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(LIGHT_PAGE_TEXT, BLUE_SELECTION, LIGHT_PAGE, band)
        assertEquals(LIGHT_PAGE_TEXT, color)
    }

    @Test
    fun `a checked verse on the night page keeps the dark text the band needs rather than the white the blue selection picks`() {
        val band = Highlights.bandColor(0x0080ff, NIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(TextColorUtil.getForHighlight(NIGHT_PAGE_TEXT, band), BLUE_SELECTION, NIGHT_PAGE, band)
        assertEquals(BLACK, color)
    }

    @Test
    fun `getForHighlight keeps the reading color on every preset band on the light page`() {
        for (rgb in TypeHighlightDialog.PRESET_RGBS) {
            assertEquals(LIGHT_PAGE_TEXT, TextColorUtil.getForHighlight(LIGHT_PAGE_TEXT, Highlights.bandColor(rgb, LIGHT_PAGE)))
        }
    }

    @Test
    fun `getForHighlight swaps the dim night reading color for black on every preset band on the night page`() {
        for (rgb in TypeHighlightDialog.PRESET_RGBS) {
            assertEquals(BLACK, TextColorUtil.getForHighlight(NIGHT_PAGE_TEXT, Highlights.bandColor(rgb, NIGHT_PAGE)))
        }
    }

    @Test
    fun `getForHighlight leaves text on every preset band at least 4_5 to 1 contrast on light, sepia, grey and dark pages`() {
        val pages = listOf(LIGHT_PAGE to LIGHT_PAGE_TEXT, 0xfffff8e7.toInt() to 0xff3b3021.toInt(), 0xff808080.toInt() to 0xff101010.toInt(), 0xff303030.toInt() to NIGHT_PAGE_TEXT, NIGHT_PAGE to NIGHT_PAGE_TEXT)
        for ((page, text) in pages) {
            for (rgb in TypeHighlightDialog.PRESET_RGBS) {
                val band = Highlights.bandColor(rgb, page)
                val contrast = ColorUtils.calculateContrast(TextColorUtil.getForHighlight(text, band), band)
                assertTrue("%06x on page %08x: contrast %.2f".format(rgb, page, contrast), contrast >= 4.5)
            }
        }
    }

    @Test
    fun `a light selection under a yellow band stays black because black reads better than the grey reading color`() {
        val band = Highlights.bandColor(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(0xff606060.toInt(), LIGHT_YELLOW_SELECTION, LIGHT_PAGE, band)
        assertEquals(BLACK, color)
    }

    @Test
    fun `a reading color without an alpha channel is treated as opaque`() {
        val band = Highlights.bandColor(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(0x212121, BLUE_SELECTION, LIGHT_PAGE, band)
        assertEquals(LIGHT_PAGE_TEXT, color)
    }
}
