package to.eyed.spettro.mobile.ui.components.thinking

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which of the slider's stop names fit under it, apart from the layout so
 * the rule can be tested. A narrow screen or large text can leave no room
 * for every name; the type is never shrunk below the user's font scale, so
 * names are left out instead.
 */
internal object SliderLabels {

    /**
     * Where label [i] of [widths] sits: centred on [centres]`[i]` and kept on
     * screen within [width], exactly as the slider places it. Left and right
     * edges, in pixels.
     */
    fun span(centre: Float, labelWidth: Int, width: Int): IntRange {
        val left = (centre - labelWidth / 2f).roundToInt().coerceIn(0, (width - labelWidth).coerceAtLeast(0))
        return left..(left + labelWidth)
    }

    /**
     * The labels to show, each at least [gapPx] clear of every other shown.
     * The stop aimed at ([aim], the current one or the one under the
     * finger) always shows; then the two ends, unless one collides with the
     * aimed stop's, which wins; then the others nearest the aimed stop first
     * (the left one on a tie), each only if it fits. With no stop aimed at
     * (-1), the others go left to right. Never empty when there are stops.
     */
    fun visible(centres: List<Float>, widths: List<Int>, width: Int, aim: Int, gapPx: Int): Set<Int> {
        val count = minOf(centres.size, widths.size)
        if (count == 0) return emptySet()
        val spans = List(count) { span(centres[it], widths[it], width) }
        val shown = LinkedHashSet<Int>()
        fun clear(i: Int): Boolean = shown.all { j ->
            spans[i].last + gapPx <= spans[j].first || spans[j].last + gapPx <= spans[i].first
        }
        fun offer(i: Int) {
            if (i !in shown && clear(i)) shown += i
        }
        val aimed = aim in 0 until count
        if (aimed) shown += aim
        offer(0)
        offer(count - 1)
        val rest = (0 until count).sortedWith(
            if (aimed) compareBy<Int>({ abs(it - aim) }, { it }) else compareBy { it },
        )
        rest.forEach(::offer)
        return shown
    }
}
