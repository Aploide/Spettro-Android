package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.components.activationAnnotated
import to.eyed.spettro.mobile.ui.components.Shimmer
import to.eyed.spettro.mobile.ui.components.CardVariant
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.animation.core.animateFloatAsState
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.core.B64
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.TranscriptItem
import to.eyed.spettro.mobile.ui.components.GlareText
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Dispatches a transcript entry to the right renderer: a right-aligned
 * neutral bubble for the user, full-width markdown prose for the assistant, a
 * one-line disclosure for streamed reasoning, a muted line for notices (an
 * error card for errors), and [ToolCallRow] for tool calls — the desktop's
 * transcript.
 */
@Composable
fun TranscriptItemView(
    item: TranscriptItem,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is TranscriptItem.Message -> when (val role = item.message.role) {
            ChatMessage.Role.User -> UserBubble(item.message, modifier)
            ChatMessage.Role.Assistant -> AssistantProse(item.message, modifier)
            ChatMessage.Role.Reasoning -> ReasoningPanel(item.message, modifier)
            is ChatMessage.Role.Notice -> NoticeCard(item.message, role.isError, modifier)
        }
        is TranscriptItem.Tool -> ToolCallRow(tool = item.tool, modifier = modifier)
    }
}

// MARK: User

@Composable
private fun UserBubble(message: ChatMessage, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        if (message.attachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                message.attachments.forEach { attachment ->
                    AttachmentThumbnail(attachment = attachment, size = 120.dp)
                }
            }
        }
        if (message.text.isNotEmpty()) {
            // The bubble caps at 85% of the transcript width and hugs the
            // trailing edge; only the user keeps chat-bubble framing. It is
            // neutral, as on the desktop — which also leaves room for the
            // activating phrase to stay lit: what armed the turn should still
            // be visible in the turn it armed.
            Box(
                modifier = Modifier.fillMaxWidth(0.85f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = activationAnnotated(message.text),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusBubble))
                        .background(colors.userBubble)
                        .padding(horizontal = Dimens.spacingLg, vertical = 10.dp),
                )
            }
        }
        SteeringCaption(message.steering)
    }
}

/** How long "Delivered" stays under a steer the agent has just read. */
private const val DELIVERED_MS = 2400L

/**
 * Where a message sent mid-turn stands. Waiting, it says so plainly — the
 * agent reads it at its next step, not now. Once read it says "Delivered"
 * for a moment and then gets out of the way; a steer that was already
 * delivered when the chat was opened is history and says nothing.
 * Port of the desktop's SteeringCaption.
 */
@Composable
private fun SteeringCaption(state: ChatMessage.Steering?) {
    val colors = LocalSpettroColors.current
    // Seeded with the state first seen, so a steer delivered before this
    // row was composed (a reopened chat, a scrolled-back row) stays quiet.
    var previous by remember { mutableStateOf(state) }
    var showDelivered by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        val was = previous
        previous = state
        if (state != ChatMessage.Steering.DELIVERED || was == ChatMessage.Steering.DELIVERED) return@LaunchedEffect
        showDelivered = true
        delay(DELIVERED_MS)
        showDelivered = false
    }
    when {
        state == ChatMessage.Steering.SENDING || state == ChatMessage.Steering.QUEUED ->
            SteeringLine(
                icon = Icons.Outlined.Schedule,
                text = "Queued · will be seen at the next step",
                color = colors.textTertiary,
            )
        else -> AnimatedVisibility(
            visible = state == ChatMessage.Steering.DELIVERED && showDelivered,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            SteeringLine(icon = Icons.Outlined.Check, text = "Delivered", color = colors.success)
        }
    }
}

@Composable
private fun SteeringLine(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier.padding(end = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun AttachmentThumbnail(
    attachment: ImageAttachment,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberAttachmentBitmap(attachment)
    val shape = RoundedCornerShape(Dimens.radiusSm)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(LocalSpettroColors.current.surfaceRaised),
        )
    }
}

@Composable
private fun rememberAttachmentBitmap(attachment: ImageAttachment): ImageBitmap? =
    remember(attachment.id) {
        B64.decodeStd(attachment.base64Data)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

// MARK: Assistant

// Assistant answers render as plain full-width prose (synara-style): no
// bubble chrome and no avatar — the agent's output owns the whole column.
@Composable
private fun AssistantProse(message: ChatMessage, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        MarkdownText(text = message.text)
        if (message.isStreaming) {
            TypingDots()
        }
    }
}

/** Three softly pulsing dots marking a still-streaming answer. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "typingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(alpha)
                    .background(
                        LocalSpettroColors.current.textTertiary,
                        RoundedCornerShape(percent = 50),
                    ),
            )
        }
    }
}

// MARK: Reasoning

/** The reasoning and tool-header size on the desktop (13.5px). */
private val ReasoningText = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp)

/**
 * Streamed reasoning as one line — "Thinking…" shimmering while it streams,
 * then "Thought for 12s" — that opens to the text behind a 2dp hairline rule,
 * as the desktop draws it. No card: thinking is not a result.
 */
@Composable
private fun ReasoningPanel(message: ChatMessage, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, SpettroMotion.chevron(), label = "reasoningChevron")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(transcriptSpring()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusXs))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val duration = message.thoughtDuration
            Shimmer(
                text = when {
                    message.isStreaming -> "Thinking…"
                    duration != null -> "Thought for $duration"
                    else -> "Thought"
                },
                style = ReasoningText,
                isActive = message.isStreaming,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(turn),
            )
        }
        if (expanded) {
            Text(
                text = message.text,
                style = ReasoningText.copy(lineHeight = 21.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .drawBehind {
                        drawRect(colors.hairline, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                    }
                    .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

// MARK: Notice

/**
 * A notice. Ordinary ones are not cards on the desktop but a muted line with
 * an icon; an error is the error card — a danger-tinted ground and border,
 * the icon in danger, the sentence itself in the primary ink (it is meant to
 * be read, not to alarm).
 */
@Composable
private fun NoticeCard(message: ChatMessage, isError: Boolean, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    if (isError) {
        SpettroCard(variant = CardVariant.Error, modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Text(
                    text = message.text,
                    style = TextStyle(fontSize = 13.5.sp, lineHeight = 19.5.sp),
                    color = colors.textPrimary,
                )
            }
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    }
}

// MARK: Previews

@Preview(name = "Transcript dark", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun TranscriptPreviewDark() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            ChatPreviewData.transcript.forEach { TranscriptItemView(item = it) }
            TranscriptItemView(item = ChatPreviewData.errorNotice)
        }
    }
}

@Preview(name = "Transcript light", showBackground = true, backgroundColor = 0xFFFAFAFA, widthDp = 380)
@Composable
private fun TranscriptPreviewLight() {
    SpettroTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            TranscriptItemView(item = ChatPreviewData.userMessage)
            TranscriptItemView(item = ChatPreviewData.reasoning)
            TranscriptItemView(item = ChatPreviewData.assistantMessage)
            TranscriptItemView(item = ChatPreviewData.infoNotice)
        }
    }
}

@Preview(name = "Streaming", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun TranscriptPreviewStreaming() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            ChatPreviewData.streamingTranscript.forEach { TranscriptItemView(item = it) }
        }
    }
}
