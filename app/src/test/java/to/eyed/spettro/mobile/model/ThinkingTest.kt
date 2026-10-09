package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpModelEntry

/**
 * The thinking slider's arithmetic, without a screen: which stop a set of
 * options puts the thumb on, and which calls a move sends. Both draw
 * perfectly in a screenshot while being wrong — a thumb on "High" for a CLI
 * running Ultra looks exactly as tidy as a correct one.
 *
 * Ported case for case from the desktop's tests/thinking.test.ts; options are
 * shaped like internal/acp/config_options.go builds them.
 */
class ThinkingTest {

    private fun select(id: String, value: String, choices: List<Pair<String, String>>, description: String? = null) =
        AcpConfigOption(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            description = description,
            kind = AcpConfigOption.Kind.Select(
                current = value,
                groups = listOf(
                    AcpConfigOption.OptionGroup(null, choices.map { (v, n) -> AcpConfigOption.Opt(n, v) }),
                ),
            ),
        )

    /** thinkingConfigOption: Off, Low, Medium, High, X-High, Max. */
    private fun thinking(value: String, values: List<String> = listOf("off", "low", "medium", "high", "x-high", "max")) =
        select("thinking", value, values.map { it to if (it == "x-high") "X-High" else it }, "Extended-thinking effort")

    /** ultraConfigOption, with the note it carries when Ask first suspends it. */
    private fun ultra(on: Boolean, suspended: Boolean = false) = AcpConfigOption(
        id = "ultra",
        name = "Ultra",
        description = if (suspended) {
            "Ultracode: substantive tasks run as dynamic workflows (suspended under Ask first — workflows need Restricted or YOLO)"
        } else {
            "Ultracode: substantive tasks run as dynamic workflows"
        },
        kind = AcpConfigOption.Kind.Bool(on),
    )

    private fun permission(value: String) = select(
        "permission",
        value,
        listOf("ask-first" to "Ask first", "restricted" to "Restricted", "yolo" to "YOLO"),
    )

    // MARK: Where the thumb sits

    @Test
    fun `is Low to Max, then Ultra, with no Off stop`() {
        val state = Thinking.state(listOf(thinking("high"), ultra(false)))
        assertEquals(listOf("Low", "Medium", "High", "Extra high", "Max", "Ultra"), state?.stops?.map { it.label })
    }

    @Test
    fun `is Ultra whenever ultracode is on, whatever the level`() {
        // Ultra is high + ultracode; a level set elsewhere (/thinking max)
        // while ultracode is on still reads as Ultra — that is what will run.
        for (level in listOf("high", "max", "low")) {
            val state = Thinking.state(listOf(thinking(level), ultra(true)))
            assertEquals("Ultra", state?.label)
            assertEquals(5, state?.index)
        }
    }

    @Test
    fun `is the matching level otherwise, in plain words`() {
        val state = Thinking.state(listOf(thinking("x-high"), ultra(false)))
        assertEquals("Extra high", state?.label)
        assertEquals(3, state?.index)
    }

    @Test
    fun `rests at the far left, labelled Off, when thinking is off`() {
        val state = Thinking.state(listOf(thinking("off"), ultra(false)))
        assertEquals(-1, state?.index)
        assertEquals("Off", state?.label)
        assertEquals("Thinking off", Thinking.chipLabel(state!!))
    }

    @Test
    fun `keeps Low to Max order whatever order the CLI lists them in`() {
        val state = Thinking.state(listOf(thinking("low", listOf("max", "off", "low", "high"))))
        assertEquals(listOf("low", "high", "max"), state?.stops?.map { it.id })
    }

    @Test
    fun `puts a level it doesn't know after Max, under the CLI's name`() {
        val state = Thinking.state(listOf(thinking("turbo", listOf("low", "max", "turbo")), ultra(false)))
        assertEquals(listOf("Low", "Max", "turbo", "Ultra"), state?.stops?.map { it.label })
        assertEquals("turbo", state?.label)
    }

    @Test
    fun `offers every level when the CLI lists none`() {
        val state = Thinking.state(listOf(thinking("high", emptyList())))
        assertEquals(Thinking.LEVELS.map { it.id }, state?.stops?.map { it.id })
    }

    @Test
    fun `offers no Ultra stop without an ultra option, and no slider without thinking`() {
        assertFalse(Thinking.state(listOf(thinking("high")))!!.stops.any { it.ultra })
        assertNull(Thinking.state(listOf(ultra(true))))
    }

    @Test
    fun `is paused when the CLI says ultracode is suspended, or Ask first is set`() {
        assertTrue(Thinking.state(listOf(thinking("high"), ultra(true, suspended = true)))!!.paused)
        assertTrue(Thinking.state(listOf(permission("ask-first"), thinking("high"), ultra(true)))!!.paused)
        assertFalse(Thinking.state(listOf(permission("restricted"), thinking("high"), ultra(true)))!!.paused)
        // Off is never paused: there is nothing to hold back.
        assertFalse(Thinking.state(listOf(permission("ask-first"), thinking("high"), ultra(false)))!!.paused)
    }

    @Test
    fun `knows whether the permission option offers Restricted`() {
        assertTrue(Thinking.state(listOf(permission("ask-first"), thinking("high"), ultra(true)))!!.canRestrict)
        assertFalse(Thinking.state(listOf(thinking("high"), ultra(true)))!!.canRestrict)
    }

    @Test
    fun `says paused Ultra the desktop's way to a screen reader and on the chip`() {
        val paused = Thinking.state(listOf(permission("ask-first"), thinking("high"), ultra(true)))!!
        assertEquals("Ultra, paused — workflows need Restricted or Don’t ask permission", Thinking.valueText(paused))
        assertEquals(Thinking.PAUSED_CHIP_TITLE, Thinking.chipTitle(paused))
        val lit = Thinking.state(listOf(permission("restricted"), thinking("high"), ultra(true)))!!
        assertEquals("Ultra", Thinking.valueText(lit))
        assertEquals(Thinking.ULTRA_CAPTION, Thinking.chipTitle(lit))
        val high = Thinking.state(listOf(thinking("high"), ultra(false)))!!
        assertEquals("Thinking: High", Thinking.chipTitle(high))
    }

    @Test
    fun `captions the stop the words name, Off, a pause, and a model that can't think`() {
        val state = Thinking.state(listOf(thinking("off"), ultra(false)))!!
        assertEquals(Thinking.OFF_CAPTION, Thinking.caption(state, -1, showPaused = false, disabled = false, modelName = null))
        assertEquals(
            "Thinks for a moment before acting",
            Thinking.caption(state, 1, showPaused = false, disabled = false, modelName = null),
        )
        assertEquals(Thinking.PAUSED_CAPTION, Thinking.caption(state, 5, showPaused = true, disabled = false, modelName = null))
        assertEquals(
            "GPT mini doesn’t think before answering, so this has no effect",
            Thinking.caption(state, 1, showPaused = false, disabled = true, modelName = "GPT mini"),
        )
        assertEquals(
            "This model doesn’t think before answering, so this has no effect",
            Thinking.caption(state, 1, showPaused = false, disabled = true, modelName = null),
        )
    }

    // MARK: What a move sends

    private fun stop(id: String) = Thinking.state(listOf(thinking("high"), ultra(false)))!!.stops.first { it.id == id }

    @Test
    fun `to Ultra sends thinking high, then ultracode on, not max`() {
        assertEquals(
            listOf(
                ConfigCall("thinking", ConfigValue.Str("high")),
                ConfigCall("ultra", ConfigValue.Bool(true)),
            ),
            Thinking.callsFor(false, Thinking.ULTRA),
        )
    }

    @Test
    fun `from Ultra sends ultracode off first, then the level`() {
        assertEquals(
            listOf(
                ConfigCall("ultra", ConfigValue.Bool(false)),
                ConfigCall("thinking", ConfigValue.Str("max")),
            ),
            Thinking.callsFor(true, stop("max")),
        )
    }

    @Test
    fun `between levels sends the level alone`() {
        assertEquals(listOf(ConfigCall("thinking", ConfigValue.Str("low"))), Thinking.callsFor(false, stop("low")))
    }

    @Test
    fun `previews the move, paused at once under Ask first`() {
        val base = Thinking.state(listOf(permission("ask-first"), thinking("max"), ultra(false)))!!
        val preview = Thinking.previewState(base, Thinking.ULTRA)
        assertEquals(5, preview.index)
        assertTrue(preview.ultraOn)
        assertTrue(preview.paused)
        assertEquals("high", preview.thinking)
    }

    @Test
    fun `catches up only once the options say what the move said`() {
        val maxed = Thinking.state(listOf(thinking("max"), ultra(false)))!!
        val halfway = Thinking.state(listOf(thinking("high"), ultra(false)))!!
        val there = Thinking.state(listOf(thinking("high"), ultra(true)))!!
        assertFalse(Thinking.caughtUp(maxed, Thinking.ULTRA))
        // Thinking high has landed, ultracode not yet: not there.
        assertFalse(Thinking.caughtUp(halfway, Thinking.ULTRA))
        assertTrue(Thinking.caughtUp(there, Thinking.ULTRA))
        assertTrue(Thinking.caughtUp(maxed, stop("max")))
        assertFalse(Thinking.caughtUp(there, stop("high")))
        assertFalse(Thinking.caughtUp(null, stop("high")))
    }

    // MARK: Whether the model reasons

    private val model = select("model", "openai:gpt-mini", listOf("openai:gpt-mini" to "GPT mini"))

    private fun entry(provider: String, name: String, reasoning: Boolean = false, local: Boolean = false) =
        AcpModelEntry(
            provider = provider,
            providerName = provider,
            name = name,
            displayName = name,
            reasoning = reasoning,
            local = local,
        )

    @Test
    fun `is read from the models catalog's reasoning capability`() {
        assertEquals(false, Thinking.modelReasons(listOf(model), listOf(entry("openai", "gpt-mini"))))
        assertEquals(true, Thinking.modelReasons(listOf(model), listOf(entry("openai", "gpt-mini", reasoning = true))))
        assertEquals("GPT mini", Thinking.modelName(listOf(model)))
    }

    @Test
    fun `counts local and Subscription models as thinking, as the CLI does`() {
        // provider/manager.go SupportsReasoning: their entries carry no
        // reasoning flag, but the CLI sends them the level anyway.
        fun pick(value: String) = select("model", value, emptyList())
        assertEquals(
            true,
            Thinking.modelReasons(
                listOf(pick("http://localhost:1234:qwen3")),
                listOf(entry("http://localhost:1234", "qwen3", local = true)),
            ),
        )
        assertEquals(true, Thinking.modelReasons(listOf(pick("spettro:fast")), listOf(entry("spettro", "fast"))))
    }

    @Test
    fun `is unknown, so the slider stays usable, for a model the catalog lacks`() {
        assertNull(Thinking.modelReasons(listOf(model), emptyList()))
        assertNull(Thinking.modelReasons(emptyList(), listOf(entry("openai", "gpt-mini"))))
    }

    @Test
    fun `each state from the same options is equal, and moves change it`() {
        val options = listOf(permission("restricted"), thinking("high"), ultra(false))
        assertEquals(Thinking.state(options), Thinking.state(options))
        assertNotEquals(Thinking.state(options), Thinking.previewState(Thinking.state(options)!!, Thinking.ULTRA))
    }
}
