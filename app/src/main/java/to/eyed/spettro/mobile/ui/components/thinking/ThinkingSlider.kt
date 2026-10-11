package to.eyed.spettro.mobile.ui.components.thinking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.model.Embers
import to.eyed.spettro.mobile.model.Meteor
import to.eyed.spettro.mobile.model.MeteorGeometry
import to.eyed.spettro.mobile.model.MeteorTrigger
import to.eyed.spettro.mobile.model.SliderKey
import to.eyed.spettro.mobile.model.SliderMoves
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.model.ThinkingStop
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.rememberReducedMotion

// The thinking slider: how hard the model thinks, from Off to Max, and one
// stop past Max — Ultra, where substantial tasks run as multi-agent
// workflows. Port of the desktop's ThinkingSlider.tsx and thinkingSlider.css.
// It replaces both the thinking menu and the Ultra switch: two controls for
// what users experience as one dial ("how much effort").
//
// Ultra is thinking *high* plus the CLI's ultracode switch (Thinking says why
// not max). Under the Ask first permission the CLI keeps Ultra saved but
// suspended, so the stop shows as Paused and offers the way out right there,
// rather than refusing the move or hiding the stop.
//
// Arriving at Ultra is the one moment that changes what a turn *is*, so it is
// marked: the thumb becomes a meteor and streaks into the stop along the bar
// (MeteorPainter), then stays lit. Only on the way in — never on opening,
// never on a redraw that finds it already there — and a plain glow under
// reduced motion. While it stays lit and the slider is open, the bar
// smoulders (EmberPainter); under reduced motion it is simply fire, still.
//
// A thick pill of a bar: a neutral groove, an accent fill, the stops as dots
// set into it, a white knob riding on it. The segment past Max is Ultra's
// fire, faint as a promise until Ultra is lit. The meteor and the smoulder
// are layers inside the bar, which clips them to its pill: nothing they
// throw can land on the labels.

/** The bar's height, and its end cap's radius: the stops' line runs from one cap's centre to the other's. */
private val BAR = 16.dp
private val CAP = 8.dp

/** Where the bar's centre line sits in the body, which is grown past the desktop's 32px to fit a finger. */
private val BAR_CENTER = 22.dp

/**
 * The labels' top: 18 under the bar's centre line, as on the desktop, whose
 * 32px hit area (the bar at its middle), 6px gap and -4px margin put them
 * there. The body ends with them; the caption's gap follows.
 */
private val LABELS_TOP = BAR_CENTER + 18.dp
private val LABELS_HEIGHT = 15.dp
private val BODY_HEIGHT = LABELS_TOP + LABELS_HEIGHT

/**
 * How far the thumb's layer reaches past the body on every side: lit Ultra's
 * widest glow (r + 4, blurred 14) at the drag's 1.08 scale, and the unset
 * thumb short of the first stop. Dimmed, the body is drawn through a layer, and a
 * layer only holds what lies inside it.
 */
private val THUMB_ROOM = 40.dp

/** A disabled slider's opacity: the desktop's 0.45 on the body and labels. */
private const val DISABLED_ALPHA = 0.45f

/**
 * Where the thumb rests for an unset level (`off` from a CLI that doesn't
 * list it): left of the first stop, just clear of the bar's end (its cap, 8,
 * plus the 12dp ring's radius).
 */
private const val OFF_THUMB = -14f

/** CSS `ease-out`, `ease-in-out`. */
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** The breath of lit Ultra's thumb: 2.8s each way. */
private const val BREATH_S = 2.8

/**
 * How often the smoulder (and the thumb's breath) is redrawn: its embers move
 * about a pixel a frame at this rate, and it costs a quarter of what every
 * frame would on a 120 Hz screen. Short of the interval by a few ms, so the
 * frame after the wait is the one that draws.
 */
private const val SMOULDER_WAIT_MS = 1000L / 30 - 4

/**
 * The slider over a chat's [options]. Draws nothing when the CLI offers no
 * thinking option.
 *
 * @param reasons whether the session's model reasons; false disables it.
 * @param onCommit sends a move (ThinkingCommitter); completes true for the
 *   move the queue drains on.
 * @param onPreview told the stop a move is previewing in the same frame, and
 *   null once the options have it or the slider goes away: the chip that
 *   opened it names what the slider names.
 * @param meteorProgress harness only: freeze a meteor at this point of its run (0…1).
 * @param meteorFrom harness only: the stop the frozen meteor set out from.
 * @param idleTime harness only: freeze lit Ultra's smoulder this many seconds in.
 */
@Composable
fun ThinkingSlider(
    options: List<AcpConfigOption>,
    reasons: Boolean?,
    onCommit: (ThinkingStop) -> Deferred<Boolean>,
    onSwitchToRestricted: () -> Unit,
    onTurnOffUltra: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onPreview: ((ThinkingStop?) -> Unit)? = null,
    meteorProgress: Double? = null,
    meteorFrom: Int? = null,
    idleTime: Double? = null,
) {
    val base = remember(options) { Thinking.state(options) } ?: return
    val colors = LocalSpettroColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val previewTo by rememberUpdatedState(onPreview)
    val commitTo by rememberUpdatedState(onCommit)

    val disabled = reasons == false
    // The moves, and the preview standing in for the options meanwhile.
    val moves = remember {
        SliderMoves(
            scope = scope,
            commit = { commitTo(it) },
            onPreview = { previewTo?.invoke(it) },
            base = base,
            disabled = disabled,
        )
    }
    SideEffect { moves.update(base, disabled) }
    // Closed mid-hold: where the key had got to still goes.
    DisposableEffect(moves) { onDispose { moves.dispose() } }
    val drag = moves.press
    var dismissed by remember { mutableStateOf(false) }

    val state = moves.state(base)
    val stops = state.stops
    val last = stops.size - 1
    val lit = state.lit
    val reduced = rememberReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val frozen = meteorProgress != null || idleTime != null

    // When the meteor flies is decided here, in the frame that lights Ultra,
    // so that very frame already has its flight (see MeteorTrigger).
    val meteorShown = drag?.rest ?: state.index
    var trigger by remember { mutableStateOf(MeteorTrigger(lit = lit, shown = meteorShown)) }
    val meteor = trigger.observe(lit, meteorShown, stops.size, reduced)
    if (meteor !== trigger) trigger = meteor

    // "Keep Ask first" dismisses the prompt until Ultra next pauses.
    LaunchedEffect(state.paused) { if (!state.paused) dismissed = false }

    val ultraIndex = state.ultraIndex
    // Where the thumb is drawn, and the stop the words name: apart only while
    // a press is over an Ultra that isn't on yet (see Press).
    val shown = drag?.rest ?: state.index
    val aim = drag?.at ?: state.index
    val shownStop = stops.getOrNull(shown)
    fun frac(i: Int): Float = if (last <= 0) 0f else max(0, i).toFloat() / last

    val flightFrom = if (meteorProgress != null) meteorFrom ?: max(0, ultraIndex - 1) else meteor.flight?.from
    val landed = if (meteorProgress != null) {
        meteorProgress >= Meteor.timing(frac(flightFrom!!).toDouble(), frac(ultraIndex).toDouble()).lands
    } else {
        meteor.landed
    }
    val inFlight = flightFrom != null
    val flying = inFlight && !landed
    // In flight the meteor's streak *is* the fill: the bar stays where the
    // eye last saw it and the streak burns it the rest of the way. The hidden
    // thumb waits there too, and is put down on Ultra at impact.
    val at = if (flying) flightFrom!! else shown
    // Off — the stop, or a level the CLI doesn't list — fills nothing, not
    // even the end cap. It follows the stop the bar is drawn at, so in
    // flight it is the stop the meteor left from.
    val atOff = at < 0 || stops.getOrNull(at)?.id == Thinking.OFF
    val fillFrac = frac(at)
    val showPaused = state.paused && shown == ultraIndex
    val litLook = shownStop?.ultra == true && !showPaused
    val pausedLook = shownStop?.ultra == true && showPaused
    // Lit and settled on Ultra, with nothing else moving: the bar smoulders —
    // for as long as someone could be looking at it.
    val smoulder = lit && !disabled && shown == ultraIndex && !inFlight &&
        (idleTime != null || (!reduced && visible))

    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    val latestMoves = rememberUpdatedState(moves)

    // The thumb and the fill slide 180ms to a new stop — or are simply there:
    // in a meteor's run, under a finger that has crossed a stop, under
    // reduced motion, and in a frozen harness frame.
    val snapMoves = inFlight || drag?.moved == true || reduced || frozen
    val targetFrac = fillFrac
    val targetOff = if (at < 0) OFF_THUMB else 0f
    // How far the fill's end cap has retracted: all the way at Off, sliding
    // with the thumb, as the desktop's translateX(-100%) on the fill does.
    val targetEmpty = if (atOff) 1f else 0f
    val fracAnim = remember { Animatable(targetFrac) }
    val offAnim = remember { Animatable(targetOff) }
    val emptyAnim = remember { Animatable(targetEmpty) }
    LaunchedEffect(targetFrac, targetOff, targetEmpty, snapMoves) {
        if (snapMoves) {
            fracAnim.snapTo(targetFrac)
            offAnim.snapTo(targetOff)
            emptyAnim.snapTo(targetEmpty)
        } else {
            launch { fracAnim.animateTo(targetFrac, SpettroMotion.slider()) }
            launch { offAnim.animateTo(targetOff, SpettroMotion.slider()) }
            launch { emptyAnim.animateTo(targetEmpty, SpettroMotion.slider()) }
        }
    }

    // Past Max: faint until it is reached, lit with Ultra, fainter paused.
    val fireTarget = when {
        flying -> 0.32f
        litLook -> 1f
        pausedLook -> 0.12f
        else -> 0.3f
    }
    // These fades, unlike the moves, are kept under reduced motion, as the
    // desktop keeps them — and Android's reduced motion (an animator scale
    // of 0) would otherwise finish every one of them in a frame.
    val fireAlpha = remember { Animatable(fireTarget) }
    LaunchedEffect(fireTarget, frozen) {
        if (frozen) {
            fireAlpha.snapTo(fireTarget)
        } else {
            keepPace(reduced) { fireAlpha.animateTo(fireTarget, tween(300, easing = EaseOut)) }
        }
    }
    // In flight the meteor is the thumb; it comes back as the head cools.
    val thumbAlpha = remember { Animatable(if (flying) 0f else 1f) }
    LaunchedEffect(flying, frozen) {
        if (flying || frozen) {
            thumbAlpha.snapTo(if (flying) 0f else 1f)
        } else {
            keepPace(reduced) { thumbAlpha.animateTo(1f, tween(260, easing = EaseOut)) }
        }
    }
    // Reduced motion gets no meteor: the lit thumb simply glows up.
    val glowIn = remember { Animatable(1f) }
    LaunchedEffect(meteor.glowIn) {
        if (meteor.glowIn) {
            glowIn.snapTo(0f)
            keepPace(reduced) { glowIn.animateTo(1f, tween(500, easing = EaseOut)) }
        }
    }

    // One slow clock for lit Ultra: the thumb's breath and the smoulder.
    val clock = remember { mutableDoubleStateOf(0.0) }
    val ambient = lit && !reduced && visible && !frozen
    LaunchedEffect(ambient) {
        if (!ambient) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.doubleValue = (now - start) / 1e9
            delay(SMOULDER_WAIT_MS)
        }
    }
    val smoulderStart = remember { mutableDoubleStateOf(0.0) }
    val embersAlpha = remember { Animatable(0f) }
    LaunchedEffect(smoulder, frozen) {
        if (!smoulder) return@LaunchedEffect
        smoulderStart.doubleValue = clock.doubleValue
        if (frozen) {
            embersAlpha.snapTo(1f)
        } else {
            // The smoulder comes up gently, whether after the meteor or on opening.
            embersAlpha.snapTo(0f)
            embersAlpha.animateTo(1f, tween(700, easing = EaseOut))
        }
    }

    // The meteor's own clock: linear wall time over the run, each frame's
    // landing and burn-out committed before that frame is drawn.
    val meteorP = remember(meteor.key) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(meteor.key, meteor.flight != null, meteorProgress == null) {
        val from = meteor.flight?.from ?: return@LaunchedEffect
        if (meteorProgress != null) return@LaunchedEffect
        val timing = Meteor.timing(frac(from).toDouble(), frac(ultraIndex).toDouble())
        val start = withFrameNanos { it }
        var hasLanded = false
        while (true) {
            val finished = withFrameNanos { now ->
                val p = (now - start) / 1e6 / timing.totalMs
                // Impact gets a frame of its own even when a slow frame
                // carries the clock past the end: the thumb and the lit fill
                // are put down on Ultra there, with no slide, before the
                // flight that holds the moves still goes away.
                if (!hasLanded && p >= timing.lands) {
                    hasLanded = true
                    trigger = trigger.land()
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    meteorP.doubleValue = p.coerceAtMost(1.0)
                    false
                } else if (p >= 1) {
                    trigger = trigger.done()
                    true
                } else {
                    meteorP.doubleValue = p
                    false
                }
            }
            if (finished) break
        }
    }

    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardFocus = focused && LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(autoFocus) {
        if (autoFocus && !disabled) runCatching { focus.requestFocus() }
    }
    fun refocus() {
        runCatching { focus.requestFocus() }
    }

    val valueLabel = if (showPaused) "Ultra · Paused" else stops.getOrNull(aim)?.label ?: "Off"
    val caption = Thinking.caption(state, aim, showPaused, disabled, Thinking.modelName(options))
    val fire = Brush.horizontalGradient(listOf(colors.ultraTextEmber, colors.ultraTextFlame))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Thinking",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = valueLabel,
                style = if (litLook) {
                    TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, brush = fire)
                } else {
                    TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = colors.textSecondary)
                },
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(BODY_HEIGHT)
                .clearAndSetSemantics {
                    contentDescription = "Thinking"
                    stateDescription = Thinking.valueText(state)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = max(0, state.index).toFloat(),
                        range = 0f..max(0, last).toFloat(),
                        steps = max(0, last - 1),
                    )
                    if (disabled) {
                        disabled()
                    } else {
                        setProgress { target ->
                            val moved = moves.setProgress(target)
                            if (moved) tick()
                            moved
                        }
                        customActions = stops.mapIndexed { i, stop ->
                            CustomAccessibilityAction(stop.label) {
                                if (moves.select(i)) tick()
                                true
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (disabled) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val key = when (event.key) {
                                Key.DirectionRight, Key.DirectionUp -> SliderKey.Forward
                                Key.DirectionLeft, Key.DirectionDown -> SliderKey.Back
                                Key.MoveHome -> SliderKey.Home
                                Key.MoveEnd -> SliderKey.End
                                else -> return@onPreviewKeyEvent false
                            }
                            if (moves.key(key, repeat = event.nativeKeyEvent.repeatCount > 0)) tick()
                            true
                        }
                        KeyEventType.KeyUp -> {
                            moves.keyUp()
                            false
                        }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .focusable(enabled = !disabled)
                // The bar runs to within a thumb of the screen's edges, where
                // a drag would otherwise be the system's back gesture.
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val moves = latestMoves.value
                        // The rail, measured once per press.
                        val railLeft = CAP.toPx()
                        val railWidth = size.width - 2 * railLeft
                        fun indexAt(x: Float): Int? {
                            val last = moves.state().stops.lastIndex
                            return if (railWidth <= 0) null else (((x - railLeft) / railWidth).coerceIn(0f, 1f) * last).roundToInt()
                        }
                        if (moves.pressAt(indexAt(down.position.x))) tick()
                        if (moves.press == null) return@awaitEachGesture
                        // Every change is consumed, so the sheet under the
                        // slider never takes the drag for its own.
                        down.consume()
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUp()) {
                                    change.consume()
                                    released = true
                                    break
                                }
                                change.consume()
                                // A new frame only when the finger crosses into another stop.
                                if (moves.dragTo(indexAt(change.position.x))) tick()
                            }
                        } finally {
                            if (released) moves.release() else moves.cancelPress()
                        }
                    }
                },
        ) {
            val width = maxWidth
            val span = (width - CAP * 2).value.toDouble()
            val barWidth = width.value.toDouble()
            val dragging = drag != null
            // Dimmed as one, as the desktop's body is (the bar doesn't show
            // through the knob), in a layer grown past the body so it holds
            // the thumb and its glow where they reach beyond it.
            val dim = if (disabled) DISABLED_ALPHA else 1f
            Box(
                Modifier
                    .fillMaxSize()
                    .bleed(THUMB_ROOM)
                    .graphicsLayer { alpha = dim }
                    .padding(THUMB_ROOM),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    inDp { w, _ ->
                        val f = fracAnimValue(snapMoves, targetFrac, fracAnim.value)
                        val e = fracAnimValue(snapMoves, targetEmpty, emptyAnim.value)
                        drawBar(
                            colors = colors,
                            width = w,
                            stops = stops.size,
                            ultraIndex = ultraIndex,
                            fillEnd = CAP.value * (1 - e) + (w - 2 * CAP.value) * f,
                            fillFrac = fillFrac,
                            shown = shown,
                            empty = atOff,
                            fireAlpha = fireAlpha.value,
                            litLook = litLook && !flying,
                            pausedLook = pausedLook,
                            flying = flying,
                            shadeTicks = litLook || pausedLook,
                        )
                    }
                }
                if (smoulder) {
                    val field = remember(barWidth) { Embers.plan(fillEnd = CAP.value + span, height = BAR.value.toDouble()) }
                    val palette = colors.ultraPalette()
                    Canvas(
                        Modifier
                            .padding(top = BAR_CENTER - BAR / 2)
                            .fillMaxWidth()
                            .height(BAR)
                            .graphicsLayer {
                                shape = RoundedCornerShape(CAP)
                                clip = true
                                compositingStrategy = CompositingStrategy.Offscreen
                                alpha = embersAlpha.value
                            },
                    ) {
                        val t = idleTime ?: max(0.0, clock.doubleValue - smoulderStart.doubleValue)
                        inDp { _, _ -> drawEmbers(field, t, palette) }
                    }
                }
                if (inFlight) {
                    val seed = if (frozen) 7 else meteor.key
                    val from = flightFrom!!
                    val run = remember(seed, from, barWidth, ultraIndex, last) {
                        Meteor.plan(
                            MeteorGeometry(
                                railLeft = CAP.value.toDouble(),
                                railWidth = span,
                                y = BAR.value / 2.0,
                                height = BAR.value.toDouble(),
                                fromFrac = frac(from).toDouble(),
                                toFrac = frac(ultraIndex).toDouble(),
                            ),
                            seed,
                        )
                    }
                    val palette = colors.ultraPalette()
                    Canvas(
                        Modifier
                            .padding(top = BAR_CENTER - BAR / 2)
                            .fillMaxWidth()
                            .height(BAR)
                            .graphicsLayer {
                                shape = RoundedCornerShape(CAP)
                                clip = true
                                compositingStrategy = CompositingStrategy.Offscreen
                            },
                    ) {
                        inDp { _, _ -> drawMeteor(run, meteorProgress ?: meteorP.doubleValue, palette) }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    inDp { w, _ ->
                        val f = fracAnimValue(snapMoves, targetFrac, fracAnim.value)
                        val off = if (snapMoves) targetOff else offAnim.value
                        val breath = if (ambient) breathAt(clock.doubleValue) else 0f
                        drawThumb(
                            colors = colors,
                            center = Offset(CAP.value + (w - 2 * CAP.value) * f + off, BAR_CENTER.value),
                            look = when {
                                at < 0 -> ThumbLook.Unset
                                pausedLook -> ThumbLook.Paused
                                litLook -> ThumbLook.Lit
                                else -> ThumbLook.Normal
                            },
                            alpha = if (flying) 0f else thumbAlpha.value,
                            scale = if (dragging) 1.08f else 1f,
                            breath = breath,
                            glowIn = glowIn.value,
                            focusRing = keyboardFocus,
                        )
                    }
                }
            }
            Labels(
                stops = stops,
                aim = aim,
                litLook = litLook,
                fire = fire,
                modifier = Modifier
                    .padding(top = LABELS_TOP)
                    .fillMaxWidth()
                    .height(LABELS_HEIGHT)
                    .graphicsLayer { alpha = dim },
            )
        }

        Text(
            text = caption,
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
            modifier = Modifier
                .padding(top = 4.dp)
                .heightIn(min = 16.dp),
        )

        if (disabled && state.ultraOn) {
            PromptBox(
                icon = false,
                text = "Ultra is still on for this model.",
                status = false,
            ) {
                PromptButton("Turn off Ultra", primary = false) {
                    refocus()
                    onTurnOffUltra()
                }
            }
        }
        if (!disabled && state.paused && !dismissed) {
            PromptBox(
                icon = true,
                text = "Workflows need Restricted or Don’t ask permission." +
                    if (state.canRestrict) " Switch to Restricted?" else "",
                status = true,
            ) {
                if (state.canRestrict) {
                    PromptButton("Switch", primary = true) {
                        refocus()
                        onSwitchToRestricted()
                    }
                }
                PromptButton("Keep Ask first", primary = false) {
                    refocus()
                    dismissed = true
                }
            }
        }
    }
}

private fun fracAnimValue(snap: Boolean, target: Float, animated: Float): Float = if (snap) target else animated

/** Draws in the desktop's CSS px (dp), as its canvases set their transform to the pixel ratio. */
private inline fun DrawScope.inDp(crossinline block: DrawScope.(width: Float, height: Float) -> Unit) {
    val w = size.width / density
    val h = size.height / density
    scale(density, density, pivot = Offset.Zero) { block(w, h) }
}

/**
 * Lays its content out [room] larger than its slot on every side, centred on
 * it, so a layer on that content holds what it draws past the slot's edges.
 */
private fun Modifier.bleed(room: Dp): Modifier = layout { measurable, constraints ->
    val r = room.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(width + 2 * r, height + 2 * r))
    layout(width, height) { placeable.place(-r, -r) }
}

/** An animator scale that plays a fade at its own pace. */
private object FullPace : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** Runs [block]'s animations at their own pace under reduced motion, when the system's scale is 0. */
private suspend fun keepPace(reduced: Boolean, block: suspend () -> Unit) {
    if (reduced) withContext(FullPace) { block() } else block()
}

/** The breath's drawn-in share at [t] seconds: 2.8s in, 2.8s out, eased. */
private fun breathAt(t: Double): Float {
    val phase = (t / BREATH_S) % 2.0
    val u = if (phase < 1) phase else 2 - phase
    return EaseInOut.transform(u.toFloat())
}

/**
 * The bar, bottom to top: the groove, Ultra's segment, the fill, the stops.
 * Everything is clipped to the pill.
 */
private fun DrawScope.drawBar(
    colors: SpettroColors,
    width: Float,
    stops: Int,
    ultraIndex: Int,
    fillEnd: Float,
    fillFrac: Float,
    shown: Int,
    /** At Off: nothing is filled, so no stop is passed. */
    empty: Boolean,
    fireAlpha: Float,
    litLook: Boolean,
    pausedLook: Boolean,
    flying: Boolean,
    shadeTicks: Boolean,
) {
    val cap = CAP.value
    val top = BAR_CENTER.value - cap
    val height = BAR.value
    val span = width - 2 * cap
    val last = stops - 1
    fun frac(i: Int): Float = if (last <= 0) 0f else max(0, i).toFloat() / last
    val pill = Path().apply {
        addRoundRect(RoundRect(0f, top, width, top + height, CornerRadius(cap)))
    }
    clipPath(pill) {
        // The groove: the same in either accent, with an inset shadow along its top.
        drawRect(colors.textPrimary.copy(alpha = 0.11f), topLeft = Offset(0f, top), size = Size(width, height))
        val shade = colors.scrim.copy(alpha = colors.scrim.alpha * 0.5f)
        drawRect(
            Brush.verticalGradient(
                0f to shade.copy(alpha = shade.alpha * 0.84f),
                1f / 3 to shade.copy(alpha = shade.alpha * 0.5f),
                2f / 3 to shade.copy(alpha = shade.alpha * 0.16f),
                1f to shade.copy(alpha = 0f),
                startY = top,
                endY = top + 3f,
            ),
            topLeft = Offset(0f, top),
            size = Size(width, 3f),
        )

        // Past Max: the Ultra segment, a faint ember until it is reached. It
        // warms in over most of the step before Max rather than starting at
        // a hard edge. The transparent end is the flame at no alpha, not
        // transparent black, which would leave a dark fringe.
        if (ultraIndex > 0) {
            val maxAt = cap + span * frac(ultraIndex - 1)
            val startX = maxAt - span * 0.17f
            drawRect(
                Brush.horizontalGradient(
                    0f to colors.ultraFlame.copy(alpha = 0f),
                    ((maxAt - startX) / (width - startX)) to colors.ultraFlame,
                    1f to colors.ultraEmber,
                    startX = startX,
                    endX = width,
                ),
                topLeft = Offset(0f, top),
                size = Size(width, height),
                alpha = fireAlpha,
            )
        }

        // The fill, from the bar's left end to the thumb's centre.
        // Square-ended: the thumb covers its end. In flight the thumb is gone,
        // so it is rounded like the bar's own ends.
        if (fillEnd > 0f) {
            when {
                flying -> drawPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = top,
                                right = fillEnd,
                                bottom = top + height,
                                topLeftCornerRadius = CornerRadius.Zero,
                                topRightCornerRadius = CornerRadius(cap),
                                bottomRightCornerRadius = CornerRadius(cap),
                                bottomLeftCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    },
                    colors.accent,
                )
                litLook -> {
                    // Lit, the whole fill is Ultra's fire, heating from ember
                    // at the bar's end, through flame at Max, to white-hot at
                    // the Ultra stop — its own palette in either accent.
                    val fireStart = frac(ultraIndex - 1).takeIf { ultraIndex > 0 } ?: 0.8f
                    val flameAt = cap + (fillEnd - cap) * fireStart
                    drawRect(
                        Brush.horizontalGradient(
                            0f to colors.ultraEmber,
                            (flameAt / fillEnd).coerceIn(0f, 1f) to colors.ultraFlame,
                            1f to colors.ultraSpark,
                            startX = 0f,
                            endX = fillEnd,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(fillEnd, height),
                    )
                }
                pausedLook -> drawRect(
                    colors.textSecondary.copy(alpha = colors.textSecondary.alpha * 0.5f),
                    topLeft = Offset(0f, top),
                    size = Size(fillEnd, height),
                )
                else -> drawRect(colors.accent, topLeft = Offset(0f, top), size = Size(fillEnd, height))
            }
        }

        // The stops: small dots set into the bar, lit only where the fill is.
        for (i in 0 until stops) {
            val passed = i <= shown && frac(i) <= fillFrac && !empty
            val color = when {
                !passed -> colors.textPrimary.copy(alpha = 0.26f)
                // On the fire and on the paused grey a notch of shade reads on both.
                shadeTicks -> colors.scrim.copy(alpha = colors.scrim.alpha * 0.7f)
                else -> colors.accentContrast.copy(alpha = 0.5f)
            }
            drawCircle(color, radius = 2f, center = Offset(cap + span * frac(i), BAR_CENTER.value))
        }
    }
}

/** Unset: a level that is none of the stops; the Off stop is Normal. */
private enum class ThumbLook { Normal, Unset, Lit, Paused }

/** The knob, riding on the bar, unclipped by it. */
private fun DrawScope.drawThumb(
    colors: SpettroColors,
    center: Offset,
    look: ThumbLook,
    alpha: Float,
    scale: Float,
    breath: Float,
    glowIn: Float,
    focusRing: Boolean,
) {
    if (alpha <= 0f) return
    val dark = colors.isDark
    withTransform({ scale(scale, scale, pivot = center) }) {
        val r = if (look == ThumbLook.Unset) 6f else 11f
        if (focusRing) {
            drawCircle(colors.focusRing.copy(alpha = colors.focusRing.alpha * alpha), radius = r + 3f, center = center, style = Stroke(2f))
        }
        when (look) {
            ThumbLook.Unset -> {
                // An unset level (`off` from a CLI that doesn't list it, so
                // not a stop): a smaller hollow thumb short of the first
                // stop, so position and label agree.
                drawCircle(colors.surfaceRaised, radius = r, center = center, alpha = alpha)
                drawCircle(colors.textTertiary, radius = r - 0.75f, center = center, style = Stroke(1.5f), alpha = alpha)
            }
            ThumbLook.Paused -> {
                // Paused Ultra: saved, not running. Muted, with a pause mark.
                softDisc(Color.Black.copy(alpha = (if (dark) 0.3f else 0.06f) * alpha), center + Offset(0f, 1f), r, 2f)
                drawCircle(colors.surfaceRaised, radius = r, center = center, alpha = alpha)
                drawCircle(colors.textTertiary, radius = r - 0.75f, center = center, style = Stroke(1.5f), alpha = alpha)
                val box = center - Offset(4f, 4f)
                for (x in listOf(2.6f, 5.4f)) {
                    drawLine(
                        colors.textSecondary,
                        start = box + Offset(x, 1.6f),
                        end = box + Offset(x, 6.4f),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round,
                        alpha = alpha,
                    )
                }
            }
            ThumbLook.Lit -> {
                // Lit Ultra: a small sun, softly breathing — the glow at
                // rest and the glow drawn in, cross-fading. Under reduced
                // motion it comes up once instead, from a dull, unlit knob.
                val sat = 0.4f + 0.6f * glowIn
                val rest = (1f - breath) * glowIn * alpha
                val drawnIn = breath * alpha
                softDisc(colors.ultraFlame.saturate(sat).copy(alpha = 0.5f * rest), center, r + 2f, 10f)
                softDisc(colors.ultraSpark.saturate(sat).copy(alpha = 0.8f * rest), center, r, 4f)
                softDisc(colors.ultraFlame.copy(alpha = 0.38f * drawnIn), center, r + 4f, 14f)
                softDisc(colors.ultraSpark.copy(alpha = 0.9f * drawnIn), center, r, 5f)
                drawCircle(colors.ultraFlame.saturate(sat).copy(alpha = 0.7f * glowIn * alpha), radius = r + 1f, center = center)
                // radial-gradient(circle at 50% 45%, core 0–28%, spark 58%,
                // flame 100%), sized to the knob's farthest corner.
                val focal = center + Offset(0f, -0.1f * r)
                val reach = sqrt(r * r + (1.1f * r) * (1.1f * r))
                drawCircle(
                    Brush.radialGradient(
                        0f to colors.ultraCore.saturate(sat),
                        0.28f to colors.ultraCore.saturate(sat),
                        0.58f to colors.ultraSpark.saturate(sat),
                        1f to colors.ultraFlame.saturate(sat),
                        center = focal,
                        radius = reach,
                    ),
                    radius = r,
                    center = center,
                    alpha = alpha,
                )
            }
            ThumbLook.Normal -> {
                // A whole dp of rim: Monochrome's dark fill is near-white,
                // the knob white, and a hairline alone left it melting in.
                val scrim = colors.scrim.alpha
                softDisc(Color.Black.copy(alpha = scrim * 0.64f * alpha), center + Offset(0f, 2f), r, 6f)
                softDisc(Color.Black.copy(alpha = scrim * 0.72f * alpha), center + Offset(0f, 1f), r, 2f)
                drawCircle(Color.Black.copy(alpha = scrim * 0.72f), radius = r + 1f, center = center, alpha = alpha)
                drawCircle(colors.paper, radius = r, center = center, alpha = alpha)
            }
        }
    }
}

/**
 * A disc blurred like a CSS box-shadow: a Gaussian of σ = blur / 2 over its
 * edge, as a radial gradient (shadows on a hardware canvas are not to be
 * relied on for every shape).
 */
private fun DrawScope.softDisc(color: Color, center: Offset, radius: Float, blur: Float) {
    if (color.alpha <= 0.001f) return
    val sigma = blur / 2f
    if (sigma < 0.05f) {
        drawCircle(color, radius = radius, center = center)
        return
    }
    val inner = max(0f, radius - 3 * sigma)
    val outer = radius + 3 * sigma
    val samples = 12
    val colorStops = ArrayList<Pair<Float, Color>>(samples + 2)
    colorStops += 0f to color.copy(alpha = color.alpha * phi(radius / sigma))
    for (i in 0..samples) {
        val d = inner + (outer - inner) * i / samples
        colorStops += (d / outer) to color.copy(alpha = color.alpha * phi((radius - d) / sigma))
    }
    drawCircle(
        Brush.radialGradient(*colorStops.toTypedArray(), center = center, radius = outer),
        radius = outer,
        center = center,
    )
}

/** The standard normal's cumulative distribution (Abramowitz–Stegun 7.1.26 for erf). */
private fun phi(z: Float): Float {
    val x = z / sqrt(2f)
    val t = 1f / (1f + 0.3275911f * kotlin.math.abs(x))
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) *
        t * exp(-x * x)
    val erf = if (x >= 0) y else -y
    return 0.5f * (1f + erf)
}

/** The CSS `saturate()` filter on one colour. */
private fun Color.saturate(s: Float): Color {
    if (s >= 1f) return this
    val r = (0.213f + 0.787f * s) * red + (0.715f - 0.715f * s) * green + (0.072f - 0.072f * s) * blue
    val g = (0.213f - 0.213f * s) * red + (0.715f + 0.285f * s) * green + (0.072f - 0.072f * s) * blue
    val b = (0.213f - 0.213f * s) * red + (0.715f - 0.715f * s) * green + (0.072f + 0.928f * s) * blue
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), alpha)
}

/**
 * The stops' names, each centred under its stop (and kept on screen at the
 * ends). Taps land on the body round them, which selects the stop under the
 * finger, as tapping a label does on the desktop.
 */
@Composable
private fun Labels(
    stops: List<ThinkingStop>,
    aim: Int,
    litLook: Boolean,
    fire: Brush,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val last = stops.size - 1
    val ultraInk = lerp(colors.textSecondary, colors.ultraTextFlame, 0.75f)
    Layout(
        modifier = modifier,
        content = {
            stops.forEachIndexed { i, stop ->
                val current = i == aim
                val weight = if (current) FontWeight.SemiBold else FontWeight.Normal
                val style = when {
                    stop.ultra && litLook -> TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, brush = fire)
                    stop.ultra -> TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = weight, color = ultraInk)
                    current -> TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = weight, color = colors.textPrimary)
                    else -> TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = colors.textSecondary)
                }
                Text(text = stop.label, style = style, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val cap = CAP.roundToPx()
        val span = width - 2 * cap
        layout(width, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val f = if (last <= 0) 0f else i.toFloat() / last
                val centre = cap + span * f
                val x = (centre - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.place(x, 0)
            }
        }
    }
}

/** The Paused and "still on" prompts under the slider: a sunken box, the words, its buttons. */
@Composable
private fun PromptBox(
    icon: Boolean,
    text: String,
    status: Boolean,
    actions: @Composable () -> Unit,
) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceSunken)
            .then(if (status) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            if (icon) {
                Icon(
                    imageVector = Icons.Filled.PauseCircle,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.padding(top = 1.5.dp).size(13.dp),
                )
            }
            Text(
                text = text,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) { actions() }
    }
}

@Composable
private fun PromptButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(colors.textPrimary)
                } else {
                    Modifier
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.hairline, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = if (primary) colors.canvas else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
