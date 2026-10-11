package to.eyed.spettro.mobile.ui.components.thinking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.model.ThinkingStop
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * What TalkBack reads and does on the thinking slider: one "Thinking" node
 * with the stop's name as its state, a stepped range from Off to Ultra,
 * a volume-key / swipe step that commits, and one custom action per stop.
 */
@RunWith(AndroidJUnit4::class)
class ThinkingSliderSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun select(id: String, value: String, choices: List<Pair<String, String>>) = AcpConfigOption(
        id = id,
        name = id,
        description = null,
        kind = AcpConfigOption.Kind.Select(
            current = value,
            groups = listOf(AcpConfigOption.OptionGroup(null, choices.map { (v, n) -> AcpConfigOption.Opt(n, v) })),
        ),
    )

    private fun options(thinking: String, ultra: Boolean) = listOf(
        select(
            "thinking",
            thinking,
            listOf("off" to "Off", "low" to "Low", "medium" to "Medium", "high" to "High", "x-high" to "X-High", "max" to "Max"),
        ),
        AcpConfigOption(id = "ultra", name = "Ultra", description = null, kind = AcpConfigOption.Kind.Bool(ultra)),
        select("permission", "restricted", listOf("ask-first" to "Ask first", "restricted" to "Restricted", "yolo" to "YOLO")),
    )

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun readsTheStopAndStepsThroughThemToUltraAndBack() {
        var opts by mutableStateOf(options("high", ultra = false))
        val commits = mutableListOf<ThinkingStop>()
        rule.setContent {
            SpettroTheme(darkTheme = true) {
                ThinkingSlider(
                    options = opts,
                    reasons = true,
                    onCommit = { stop ->
                        commits += stop
                        // The CLI answers by moving the options, as the host's broadcast does.
                        opts = options(if (stop.ultra) Thinking.ULTRA_THINKING else stop.id, ultra = stop.ultra)
                        CompletableDeferred(true)
                    },
                    onSwitchToRestricted = {},
                    onTurnOffUltra = {},
                )
            }
        }
        val slider = rule.onNodeWithContentDescription("Thinking")
        slider.assert(state("High"))
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        // Off, Low, Medium, High, X-High, Max, Ultra: seven stops, five
        // between the ends.
        assertEquals(3f, range.current)
        assertEquals(0f..6f, range.range)
        assertEquals(5, range.steps)

        // TalkBack's swipe up / volume up is a SetProgress one step on.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        rule.waitUntil(3_000) { commits.isNotEmpty() }
        rule.waitForIdle()
        slider.assert(state("Extra high"))

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        rule.waitUntil(3_000) { commits.lastOrNull()?.ultra == true }
        rule.waitForIdle()
        slider.assert(state("Ultra"))

        // Each stop is also a custom action, named as the stop is.
        assertEquals(listOf("Thinking"), slider.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        val custom = slider.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Off", "Low", "Medium", "High", "Extra high", "Max", "Ultra"), custom)

        // And from Ultra straight back down to Low in one action…
        fun action(label: String) =
            slider.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        val toLow = action("Low")
        rule.runOnIdle { toLow.action() }
        rule.waitUntil(3_000) { commits.lastOrNull()?.id == "low" }
        rule.waitForIdle()
        slider.assert(state("Low"))

        // …and on to Off, a stop like the others.
        val toOff = action("Off")
        rule.runOnIdle { toOff.action() }
        rule.waitUntil(3_000) { commits.lastOrNull()?.id == "off" }
        rule.waitForIdle()
        slider.assert(state("Off"))
    }

    @Test
    fun offReadsAsOffAndAStepUpIsLow() {
        var opts by mutableStateOf(options("off", ultra = false))
        val commits = mutableListOf<ThinkingStop>()
        rule.setContent {
            SpettroTheme(darkTheme = false) {
                ThinkingSlider(
                    options = opts,
                    reasons = true,
                    onCommit = { stop ->
                        commits += stop
                        opts = options(stop.id, ultra = false)
                        CompletableDeferred(true)
                    },
                    onSwitchToRestricted = {},
                    onTurnOffUltra = {},
                )
            }
        }
        val slider = rule.onNodeWithContentDescription("Thinking")
        slider.assert(state("Off"))
        // Off is the stop at 0.
        assertEquals(0f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        // TalkBack's step up from Off asks for 1; it means Low.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        rule.waitUntil(3_000) { commits.isNotEmpty() }
        rule.waitForIdle()
        assertEquals("low", commits.single().id)
        slider.assert(state("Low"))
        // And a step down from Low is Off again.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        rule.waitUntil(3_000) { commits.size == 2 }
        rule.waitForIdle()
        assertEquals("off", commits.last().id)
        slider.assert(state("Off"))
    }
}
