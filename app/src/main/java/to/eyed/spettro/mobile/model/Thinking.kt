package to.eyed.spettro.mobile.model

import kotlin.math.roundToInt
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpModelEntry

// What the thinking slider shows, and what moving it asks the CLI to do. Port
// of the desktop's views/chat/thinking.ts.
//
// The slider is one control over two of the CLI's options
// (internal/acp/config_options.go): the `thinking` select (off … max) and the
// `ultra` boolean (ultracode — substantial tasks run as multi-agent
// workflows). Ultra is the stop past Max, and it means thinking *high* plus
// ultracode, not max: workflows already spend tokens by the agent, and max
// thinking on top of every member of a fan-out would burn them for nothing.
//
// Pure on purpose: the position a set of options produces and the calls a
// move makes are the two things a screenshot draws perfectly while being
// wrong, so they are tested (ThinkingTest) without a screen.

object Thinking {
    /** The CLI's option ids. */
    const val THINKING_ID = "thinking"
    const val ULTRA_ID = "ultra"
    const val PERMISSION_ID = "permission"
    const val MODEL_ID = "model"

    /** The permission level that suspends ultracode. */
    const val ASK_FIRST = "ask-first"

    /** The level the Paused prompt offers instead. */
    const val RESTRICTED = "restricted"

    /** The thinking level Ultra runs at. */
    const val ULTRA_THINKING = "high"

    /** The id of the Ultra stop. Not a thinking level: no CLI level uses it. */
    const val ULTRA_STOP = "ultra"

    /**
     * A CLI level that turns thinking off. Not a stop: the user asked for
     * "low to max", so Off is only ever shown, when something else set it.
     */
    private const val OFF = "off"

    /** The Spettro Subscription's provider id (provider/manager.go). */
    private const val SUBSCRIPTION_PROVIDER = "spettro"

    /**
     * The levels in slider order, named in plain words. "X-High" is the
     * CLI's menu label; on a slider it reads as a typo.
     */
    val LEVELS: List<ThinkingStop> = listOf(
        ThinkingStop("low", "Low", "Answers quickly with only a brief think"),
        ThinkingStop("medium", "Medium", "Thinks for a moment before acting"),
        ThinkingStop("high", "High", "Thinks things through — a good default for real work"),
        ThinkingStop("x-high", "Extra high", "Thinks longer on hard problems (slower, more tokens)"),
        ThinkingStop("max", "Max", "As much as the model allows (slowest, most tokens)"),
    )

    /** The whole sentence: the Ultra caption's long form, and the lit chip's. */
    const val ULTRA_CAPTION =
        "High thinking + ultracode — substantial tasks run as multi-agent workflows (uses more tokens)"

    val ULTRA = ThinkingStop(
        id = ULTRA_STOP,
        label = "Ultra",
        // The one-line form; ULTRA_CAPTION is the whole sentence.
        caption = "High thinking + multi-agent workflows (more tokens)",
        ultra = true,
    )

    const val OFF_CAPTION = "Thinking is off — the model answers straight away"
    const val PAUSED_CAPTION = "Ultra is saved, but workflows don’t run under Ask first"
    const val PAUSED_VALUE_TEXT = "Ultra, paused — workflows need Restricted or Don’t ask permission"
    const val PAUSED_CHIP_TITLE = "Ultra is paused under Ask first — workflows need Restricted or Don’t ask permission"

    /**
     * How long a move's preview outlives its calls when the options never
     * come round to it (the CLI refused, and rolled the option back).
     */
    const val SETTLE_MS = 1_200L

    /**
     * How long a held key's repeats may pause before the stop they reached
     * is sent (repeats come every ~33 ms; the release sends it at once).
     */
    const val HELD_KEY_MS = 150L

    private fun choicesOf(option: AcpConfigOption?): List<AcpConfigOption.Opt> =
        (option?.kind as? AcpConfigOption.Kind.Select)?.groups?.flatMap { it.options }.orEmpty()

    /**
     * The slider's stops and position, from the session's options. Null when
     * the CLI advertises no thinking option: then there is nothing to slide.
     *
     * The stops follow what the CLI offers — a level it drops disappears, one
     * it adds lands after Max under its own name — but always in Low → Max
     * order, whatever order the menu came in. Ultra is offered only beside an
     * `ultra` boolean.
     */
    fun state(options: List<AcpConfigOption>): ThinkingState? {
        val thinkingOption = options.firstOrNull { it.id == THINKING_ID }
        val thinkingKind = thinkingOption?.kind as? AcpConfigOption.Kind.Select ?: return null
        val ultraOption = options.firstOrNull { it.id == ULTRA_ID }
        val ultraKind = ultraOption?.kind as? AcpConfigOption.Kind.Bool
        val permission = options.firstOrNull { it.id == PERMISSION_ID }

        val offered = choicesOf(thinkingOption).filter { it.value != OFF }
        val stops = if (offered.isEmpty()) {
            LEVELS.toMutableList()
        } else {
            (
                LEVELS.filter { level -> offered.any { it.value == level.id } } +
                    offered
                        .filter { choice -> LEVELS.none { it.id == choice.value } }
                        .map { ThinkingStop(it.value, it.name, it.description ?: "") }
                ).toMutableList()
        }
        if (ultraKind != null) stops += ULTRA

        // The Android parser keeps a missing value as "", the desktop's null.
        val thinking = thinkingKind.current.ifEmpty { null }
        val ultraOn = ultraKind?.current == true
        val askFirst = (permission?.kind as? AcpConfigOption.Kind.Select)?.current == ASK_FIRST
        // The CLI says so in the description when Ask first suspends a saved
        // Ultra (ultraConfigOption); the permission is read too, for the
        // moment between a local change and the CLI's reply.
        val paused = ultraOn && ((ultraOption?.description ?: "").contains("suspended") || askFirst)

        val index = if (ultraOn) stops.size - 1 else stops.indexOfFirst { !it.ultra && it.id == thinking }
        return ThinkingState(
            stops = stops,
            index = index,
            label = if (index < 0) "Off" else stops[index].label,
            thinking = thinking,
            ultraOn = ultraOn,
            paused = paused,
            askFirst = askFirst,
            canRestrict = choicesOf(permission).any { it.value == RESTRICTED },
        )
    }

    /**
     * The calls that move the CLI from [ultraOn] to [target], in order. Sent
     * one after the other, never together: two replies racing each other
     * would each carry the other's option at its old value and walk the thumb
     * back.
     *
     *  - To Ultra: thinking high, then ultracode on.
     *  - From Ultra: ultracode off first (so nothing runs as a workflow at the
     *    new level even for a moment), then the level.
     *  - Elsewhere: the level alone.
     */
    fun callsFor(ultraOn: Boolean, target: ThinkingStop): List<ConfigCall> = when {
        target.ultra -> listOf(
            ConfigCall(THINKING_ID, ConfigValue.Str(ULTRA_THINKING)),
            ConfigCall(ULTRA_ID, ConfigValue.Bool(true)),
        )
        ultraOn -> listOf(
            ConfigCall(ULTRA_ID, ConfigValue.Bool(false)),
            ConfigCall(THINKING_ID, ConfigValue.Str(target.id)),
        )
        else -> listOf(ConfigCall(THINKING_ID, ConfigValue.Str(target.id)))
    }

    /**
     * The state a pending move will produce, so the thumb goes where it was
     * put at once instead of waiting a round trip to the CLI (and stepping
     * through "High" on its way to Ultra).
     */
    fun previewState(state: ThinkingState, target: ThinkingStop): ThinkingState = state.copy(
        index = state.stops.indexOfFirst { it.id == target.id },
        label = target.label,
        thinking = if (target.ultra) ULTRA_THINKING else target.id,
        ultraOn = target.ultra,
        paused = target.ultra && state.askFirst,
    )

    /**
     * Whether the options now say what the pending move said. The preview
     * gives way only then (or [SETTLE_MS] after its calls, for a refusal):
     * a call's reply and the option update it causes arrive separately, and
     * letting go on the reply showed the old value for a frame — Ultra going
     * dark between "thinking high" and "ultra on", relaunching the meteor.
     */
    fun caughtUp(base: ThinkingState?, pending: ThinkingStop?): Boolean =
        base != null &&
            pending != null &&
            base.ultraOn == pending.ultra &&
            (pending.ultra || base.thinking == pending.id)

    /**
     * The stop TalkBack's set-progress [target] means, from the thumb at
     * [index] with [last] the last stop. The slider reports Off as 0 — it is
     * no stop of its own — so TalkBack's step up from Off asks for 1, which
     * would skip Low. From Off any step up is Low, as the desktop's arrow is,
     * and a step down is nothing further left (null).
     */
    fun a11yTarget(index: Int, target: Float, last: Int): Int? {
        if (last < 0 || target.isNaN()) return null
        if (index < 0) return if (target > 0f) 0 else null
        return target.roundToInt().coerceIn(0, last)
    }

    /**
     * Whether the session's model can think at all, from the
     * `_spettro/models/list` catalog. Null when the catalog doesn't know the
     * model — then the slider stays usable rather than guess.
     *
     * The same rule the CLI applies before it sends a thinking level
     * (provider/manager.go SupportsReasoning), not the bare `reasoning` flag:
     * a local server's models and the Subscription's carry no flag, yet the
     * CLI sends them the level all the same.
     */
    fun modelReasons(options: List<AcpConfigOption>, models: List<AcpModelEntry>): Boolean? {
        val value = (options.firstOrNull { it.id == MODEL_ID }?.kind as? AcpConfigOption.Kind.Select)
            ?.current?.ifEmpty { null } ?: return null
        val entry = models.firstOrNull { "${it.provider}:${it.name}" == value } ?: return null
        return entry.reasoning || entry.local || entry.provider == SUBSCRIPTION_PROVIDER
    }

    /** The session's model, by the name its menu shows. */
    fun modelName(options: List<AcpConfigOption>): String? {
        val model = options.firstOrNull { it.id == MODEL_ID }
        val value = (model?.kind as? AcpConfigOption.Kind.Select)?.current?.ifEmpty { null } ?: return null
        return choicesOf(model).firstOrNull { it.value == value }?.name
    }

    /** What a screen reader says the slider is set to (the desktop's aria-valuetext). */
    fun valueText(state: ThinkingState): String = if (state.paused) PAUSED_VALUE_TEXT else state.label

    /** The chip's words: a bare "Off" in a toolbar reads as something broken. */
    fun chipLabel(state: ThinkingState): String = if (state.index < 0) "Thinking off" else state.label

    /** The chip's long-press title. */
    fun chipTitle(state: ThinkingState): String = when {
        state.ultraOn && !state.paused -> ULTRA_CAPTION
        state.paused -> PAUSED_CHIP_TITLE
        else -> "Thinking: ${state.label}"
    }

    /** The line under the slider for the stop the words name ([aim], -1 for Off). */
    fun caption(state: ThinkingState, aim: Int, showPaused: Boolean, disabled: Boolean, modelName: String?): String {
        val aimStop = state.stops.getOrNull(aim)
        return when {
            disabled -> "${modelName ?: "This model"} doesn’t think before answering, so this has no effect"
            aimStop == null -> OFF_CAPTION
            showPaused -> PAUSED_CAPTION
            else -> aimStop.caption
        }
    }
}

data class ThinkingStop(
    /** The thinking level, or [Thinking.ULTRA_STOP]. */
    val id: String,
    val label: String,
    /** One line saying what the stop buys you, under the slider. */
    val caption: String,
    val ultra: Boolean = false,
)

data class ThinkingState(
    val stops: List<ThinkingStop>,
    /** The stop the thumb sits on; -1 is Off (the thumb rests short of Low). */
    val index: Int,
    /** The value's name: a stop's label, or "Off". */
    val label: String,
    val thinking: String?,
    val ultraOn: Boolean,
    /** Ultra is saved but the permission level holds every workflow back. */
    val paused: Boolean,
    val askFirst: Boolean,
    /** Whether the permission option offers Restricted, the level that lifts the pause. */
    val canRestrict: Boolean,
) {
    /** Ultra's index, or -1 when the CLI offers no ultra option. */
    val ultraIndex: Int get() = stops.indexOfFirst { it.ultra }

    /** Lit: Ultra is on and running, not held back by Ask first. */
    val lit: Boolean get() = ultraOn && !paused
}

/** One `session/set_config_option` call. */
data class ConfigCall(val configId: String, val value: ConfigValue)
