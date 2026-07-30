package to.eyed.spettro.mobile.ui.screens.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Dispatches a transcript entry to the right renderer: a right-aligned accent
 * bubble for the user, full-width markdown prose for the assistant, a
 * collapsible panel for streamed reasoning, a quiet centered card for
 * notices, and [ToolCallRow] for tool calls.
 * Port of TranscriptItemView.swift.
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
            // The bubble caps at ~80% of the transcript width and hugs the
            // trailing edge; only the user keeps chat-bubble framing.
            Box(
                modifier = Modifier.fillMaxWidth(0.8f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusBubble))
                        .background(colors.userBubble)
                        .padding(
                            horizontal = Dimens.spacingMd,
                            vertical = Dimens.spacingSm + 2.dp,
                        ),
                )
            }
        }
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
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(percent = 50),
                    ),
            )
        }
    }
}

// MARK: Reasoning

@Composable
private fun ReasoningPanel(message: ChatMessage, modifier: Modifier) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(transcriptSpring()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .clickable { expanded = !expanded }
                .padding(horizontal = Dimens.spacingXs, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(14.dp),
            )
            GlareText(
                text = if (message.isStreaming) "Thinking…" else "Reasoning",
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
                isActive = message.isStreaming,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = secondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(12.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            SpettroCard(radius = Dimens.radiusSm, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = secondary,
                    modifier = Modifier.padding(Dimens.spacingSm),
                )
            }
        }
    }
}

// MARK: Notice

@Composable
private fun NoticeCard(message: ChatMessage, isError: Boolean, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    val tint = if (isError) colors.diffRemoved else MaterialTheme.colorScheme.onSurfaceVariant
    SpettroCard(radius = Dimens.radiusSm, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = tint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// MARK: Previews

@Preview(name = "Transcript dark", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
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

@Preview(name = "Transcript light", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 380)
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

@Preview(name = "Streaming", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
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
