package yuku.alkitab.base.util

import androidx.annotation.Keep
import com.google.gson.JsonSyntaxException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import yuku.alkitab.base.App

object Highlights {
    private val TAG: String = Highlights::class.java.simpleName

    /** Oklab lightness of a band on a light page, capped [LIGHT_PAGE_BAND_GAP] below the page itself. */
    private const val LIGHT_PAGE_BAND_LIGHTNESS = 0.88f
    private const val LIGHT_PAGE_BAND_GAP = 0.06f
    private const val LIGHT_PAGE_BAND_CHROMA = 0.12f

    /** Oklab lightness of a band on a dark page, bright enough that the text on it switches to dark. */
    private const val DARK_PAGE_BAND_LIGHTNESS = 0.66f
    private const val DARK_PAGE_BAND_CHROMA = 0.13f

    /** Below this Oklab chroma a stored color has no meaningful hue and draws as a neutral band. */
    private const val NEUTRAL_CHROMA = 0.02f

    private val bandCache = ConcurrentHashMap<Long, Int>()

    private val tmpInfo: ThreadLocal<Info> = ThreadLocal.withInitial { Info() }

    private val tmpPartial: ThreadLocal<Info.Partial> = ThreadLocal.withInitial { Info.Partial() }

    @Keep
    class Info {
        /** rgb only, without the alpha */
        @JvmField var colorRgb: Int = 0
        @JvmField var partial: Partial? = null

        @Keep
        class Partial {
            /** hashCode of the plain text (not with formatting) */
            @JvmField var hashCode: Int = 0
            @JvmField var startOffset: Int = 0
            @JvmField var endOffset: Int = 0
        }

        fun shouldRenderAsPartialForVerseText(verseText: CharSequence): Boolean {
            val partial = partial
            return partial != null && partial.hashCode == Highlights.hashCode(verseText.toString()) && partial.startOffset <= verseText.length && partial.endOffset <= verseText.length
        }
    }


    /** Encode a full-verse highlight */
    @JvmStatic
    fun encode(colorRgb: Int): String {
        val info = tmpInfo.get()!!

        info.colorRgb = colorRgb
        info.partial = null

        return App.getDefaultGson().toJson(info)
    }

    /** Encode a partial highlight */
    @JvmStatic
    fun encode(colorRgb: Int, hashCode: Int, startOffset: Int, endOffset: Int): String {
        val info = tmpInfo.get()!!

        info.colorRgb = colorRgb

        val partial = tmpPartial.get()!!
        info.partial = partial
        partial.hashCode = hashCode
        partial.startOffset = startOffset
        partial.endOffset = endOffset

        return App.getDefaultGson().toJson(info)
    }

    /**
     * Decodes color code for highlight. It starts with the string "c" then 6 hex digits rrggbb.
     * @return highlight info, or null if cannot decode
     */
    @JvmStatic
    fun decode(text: String?): Info? {
        if (text == null) return null
        if (text.length >= 7 && text[0] == 'c') { // legacy compat
            return try {
                val colorRgb = text.substring(1, 7).toInt(16)
                val res = Info()
                res.colorRgb = colorRgb
                res.partial = null
                res
            } catch (e: NumberFormatException) {
                AppLog.e(TAG, "@@decode", e)
                null
            }
        } else if (text.startsWith("{")) { // json format
            return try {
                App.getDefaultGson().fromJson(text, Info::class.java)
            } catch (e: JsonSyntaxException) {
                AppLog.e(TAG, "@@decode", e)
                null
            }
        } else {
            return null
        }
    }

    /**
     * Opaque color of the band drawn behind highlighted text on [backgroundColor].
     *
     * Only the hue of [colorRgb] is kept. Lightness and chroma come from the page: soft pastels on
     * a light page, and mid-tone bands on a dark page, where they need dark text (see
     * [TextColorUtil.getForHighlight]). Saturated colors of different hues blended at the
     * strengths a page allows otherwise end up looking alike, and on a black page nearly invisible.
     *
     * The stored color is never changed, so other installs syncing the same highlight are free to
     * draw it their own way.
     */
    @JvmStatic
    fun bandColor(colorRgb: Int, backgroundColor: Int): Int {
        val rgb = colorRgb and 0xffffff
        val bg = backgroundColor and 0xffffff
        if (bandCache.size > 512) bandCache.clear()
        return bandCache.getOrPut((rgb.toLong() shl 32) or bg.toLong()) { solveBand(rgb, bg) }
    }

    private fun solveBand(rgb: Int, bg: Int): Int {
        val pageLightness = oklab(bg).l
        val isDarkPage = pageLightness < 0.5f
        val lightness = if (isDarkPage) DARK_PAGE_BAND_LIGHTNESS else minOf(LIGHT_PAGE_BAND_LIGHTNESS, pageLightness - LIGHT_PAGE_BAND_GAP)

        val color = oklab(rgb)
        val colorChroma = sqrt(color.a * color.a + color.b * color.b)
        val hue = atan2(color.b, color.a)
        val targetChroma = when {
            colorChroma < NEUTRAL_CHROMA -> 0f
            isDarkPage -> DARK_PAGE_BAND_CHROMA
            else -> LIGHT_PAGE_BAND_CHROMA
        }

        // The most saturated color of this hue and lightness that sRGB can show, up to the target.
        // Chroma 0 is always in gamut, so the search always lands on a displayable color.
        var chroma = targetChroma
        if (oklabToLinearRgb(lightness, chroma * cos(hue), chroma * sin(hue)) == null) {
            var lo = 0f
            var hi = chroma
            repeat(16) {
                val mid = (lo + hi) / 2f
                if (oklabToLinearRgb(lightness, mid * cos(hue), mid * sin(hue)) != null) lo = mid else hi = mid
            }
            chroma = lo
        }
        val linear = oklabToLinearRgb(lightness, chroma * cos(hue), chroma * sin(hue))!!
        return (0xff shl 24) or (linearToSrgb(linear[0]) shl 16) or (linearToSrgb(linear[1]) shl 8) or linearToSrgb(linear[2])
    }

    /** Linear sRGB for an Oklab color, or null if it falls outside the sRGB gamut. */
    private fun oklabToLinearRgb(l: Float, a: Float, b: Float): FloatArray? {
        val lCone = l + 0.39633778f * a + 0.21580376f * b
        val mCone = l - 0.105561346f * a - 0.06385417f * b
        val sCone = l - 0.08948418f * a - 1.2914855f * b
        val lc = lCone * lCone * lCone
        val mc = mCone * mCone * mCone
        val sc = sCone * sCone * sCone
        val res = floatArrayOf(
            4.0767417f * lc - 3.3077116f * mc + 0.23096994f * sc,
            -1.268438f * lc + 2.6097574f * mc - 0.34131938f * sc,
            -0.0041960864f * lc - 0.7034186f * mc + 1.7076147f * sc,
        )
        return if (res.all { it in -1e-4f..1.0001f }) res else null
    }

    private fun linearToSrgb(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (s * 255f + 0.5f).toInt().coerceIn(0, 0xff)
    }

    private class Oklab(val l: Float, val a: Float, val b: Float)

    private fun oklab(rgb: Int): Oklab {
        val r = srgbToLinear((rgb shr 16) and 0xff)
        val g = srgbToLinear((rgb shr 8) and 0xff)
        val b = srgbToLinear(rgb and 0xff)

        val lCone = cbrt(0.41222146f * r + 0.53633255f * g + 0.051445995f * b)
        val mCone = cbrt(0.2119035f * r + 0.6806995f * g + 0.10739696f * b)
        val sCone = cbrt(0.08830246f * r + 0.28171885f * g + 0.6299787f * b)

        return Oklab(
            0.21045426f * lCone + 0.7936178f * mCone - 0.004072047f * sCone,
            1.9779985f * lCone - 2.4285922f * mCone + 0.4505937f * sCone,
            0.025904037f * lCone + 0.78277177f * mCone - 0.80867577f * sCone,
        )
    }

    private fun srgbToLinear(component: Int): Float {
        val c = component / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    @JvmStatic
    fun hashCode(verseText: String): Int {
        return verseText.hashCode()
    }
}
