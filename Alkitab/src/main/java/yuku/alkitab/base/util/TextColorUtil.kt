package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils

object TextColorUtil {

    /** Alpha for the selection overlay on a checked verse row. */
    const val CHECKED_VERSE_OVERLAY_ALPHA = 0xa0

    @JvmStatic
    fun getSearchKeywordByBrightness(brightness: Float): Int {
        return if (brightness < 0.5f) {
            0xff69f0ae.toInt() // Green A200
        } else {
            0xff00c853.toInt() // Green A700
        }
    }

    /** WCAG AA contrast for body text. */
    private const val MIN_TEXT_CONTRAST = 4.5

    /**
     * Text color for a highlighted run outside a checked verse. The reading color is kept
     * whenever it stays legible on [highlightBand]; on a dark page the band is brighter than the
     * page, so the dim reading color usually gives way to black.
     */
    @JvmStatic
    fun getForHighlight(fontColor: Int, highlightBand: Int): Int {
        val readingColor = fontColor or 0xff000000.toInt()
        val band = highlightBand or 0xff000000.toInt()
        if (ColorUtils.calculateContrast(readingColor, band) >= MIN_TEXT_CONTRAST) return readingColor
        val black = 0xff000000.toInt()
        val white = 0xffffffff.toInt()
        return if (ColorUtils.calculateContrast(black, band) >= ColorUtils.calculateContrast(white, band)) black else white
    }

    @JvmStatic
    fun getForCheckedVerse(checkedVerseBgColor: Int): Int {
        return if (ColorUtils.calculateLuminance(checkedVerseBgColor) > 0.4) {
            0xff000000.toInt()
        } else {
            0xffffffff.toInt()
        }
    }

    /**
     * Text color for a highlighted run inside a checked verse.
     *
     * The highlight band is drawn on top of the selection overlay. So the color from
     * [getForCheckedVerse] can be wrong there, for example white text on a yellow highlight.
     * This picks whichever color, the reading color or the checked-verse color, has better
     * contrast against what the band actually paints.
     */
    @JvmStatic
    fun getForCheckedVerseHighlight(fontColor: Int, checkedVerseBgColor: Int, pageBackgroundColor: Int, highlightBand: Int): Int {
        val checkedTextColor = getForCheckedVerse(checkedVerseBgColor)
        val overlay = ColorUtils.setAlphaComponent(checkedVerseBgColor, CHECKED_VERSE_OVERLAY_ALPHA)
        val selection = ColorUtils.compositeColors(overlay, pageBackgroundColor or 0xff000000.toInt())
        val painted = ColorUtils.compositeColors(highlightBand, selection)
        val readingColor = fontColor or 0xff000000.toInt()
        return if (ColorUtils.calculateContrast(checkedTextColor, painted) >= ColorUtils.calculateContrast(readingColor, painted)) {
            checkedTextColor
        } else {
            readingColor
        }
    }
}
