package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.rememberMarkdownState
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoBody
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Transcript markdown, rendered through mikepenz's markdown-renderer-m3 and
 * styled to the desktop's transcript: 15sp prose at 1.6, monospaced code on
 * the sunken surface under a hairline, inline code on a 7% ink wash, links in
 * the accent's text colour.
 *
 * Streaming strategy (port of MarkdownText.swift, docs/31-markdown-rendering.md):
 * the source is split into stable block-level chunks — on blank lines, but
 * never inside a fenced code block and never inside a list run. Each chunk is
 * its own `key`ed skippable composable whose only parameter is the chunk
 * text, so while an answer streams only the growing last chunk re-parses;
 * finished chunks compare equal and are skipped entirely.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val chunks = remember(text) { MarkdownChunker.chunks(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        chunks.forEachIndexed { index, chunk ->
            key(index) {
                MarkdownChunkView(chunk)
            }
        }
    }
}

/**
 * One markdown chunk. A separate skippable composable so Compose can skip
 * every chunk whose text didn't change this update — the renderer then never
 * re-parses or re-lays-out finished chunks.
 *
 * The renderer parses off the main thread by default and draws nothing
 * until it's done, so a row coming back into view (a scroll, the keyboard
 * going away) measured zero high for a frame and then grew — pushing the
 * transcript's end out of view — and the streaming chunk blinked out on
 * every token. A chunk is a paragraph or so: it is parsed in place the first
 * time, and while it grows the last parse stays up until the next is ready.
 */
@Composable
private fun MarkdownChunkView(chunk: String) {
    val colors = LocalSpettroColors.current
    val body = MaterialTheme.typography.bodyLarge
    val state = rememberMarkdownState(chunk, retainState = true, immediate = true)
    Markdown(
        markdownState = state,
        colors = markdownColor(
            text = colors.textPrimary,
            codeBackground = colors.surfaceSunken,
            inlineCodeBackground = colors.textPrimary.copy(alpha = 0.07f),
            dividerColor = colors.hairline,
            tableBackground = colors.surfaceRaised,
        ),
        typography = markdownTypography(
            h1 = body.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            h2 = body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            h3 = body.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontWeight = FontWeight.Medium),
            text = body,
            code = MonoBody,
            inlineCode = MonoBody.copy(fontSize = 13.sp),
            quote = body.copy(color = colors.textSecondary),
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            textLink = TextLinkStyles(
                style = SpanStyle(color = colors.accentText),
                pressedStyle = SpanStyle(
                    color = colors.accentText,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            table = body,
        ),
        dimens = markdownDimens(codeBackgroundCornerSize = Dimens.radiusSm),
        components = markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node) { code, language, _ ->
                    SpettroCodeBlock(code = code, language = language)
                }
            },
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node) { code, language, _ ->
                    SpettroCodeBlock(code = code, language = language)
                }
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The Spettro code block: language tag over a hairline seam, monospaced code
 * that scrolls horizontally, all on the sunken surface with a hairline border
 * rounded to the small radius.
 */
@Composable
private fun SpettroCodeBlock(code: String, language: String?) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(Dimens.hairlineWidth, colors.hairline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.spacingSm, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // The fence's first word, as the desktop labels it.
                text = language?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            CopyButton(text = code.trimEnd('\n'))
        }
        HairlineDivider()
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = code.trimEnd('\n'),
                style = MonoBody,
                color = colors.textPrimary,
                softWrap = false,
                modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
            )
        }
    }
}

/** How long "Copied" stays before the button says Copy again. */
private const val COPIED_MS = 1200L

/** A code block's Copy, which says "Copied" for a moment once it has (the desktop's CopyButton). */
@Composable
private fun CopyButton(text: String) {
    val colors = LocalSpettroColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusXs))
            .clickable {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }
            // 40dp: a thumb's target, not the desktop's 24px.
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * Splits markdown into independently renderable chunks on blank lines,
 * keeping fenced code blocks (even unterminated ones, mid-stream) and list
 * runs intact so numbering and fences never break across chunks.
 * Port of `MarkdownChunker` in MarkdownText.swift.
 */
internal object MarkdownChunker {
    fun chunks(source: String): List<String> {
        val chunks = mutableListOf<String>()
        val current = mutableListOf<String>()
        var inFence = false
        val lines = source.split("\n")

        fun flush() {
            // Preserve interior blank lines but drop pure-whitespace chunks.
            if (current.any { it.isNotBlank() }) {
                chunks.add(current.joinToString("\n"))
            }
            current.clear()
        }

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                current.add(line)
                continue
            }
            if (trimmed.isEmpty() && !inFence) {
                // A blank line splits chunks unless it sits inside a list run
                // (loose lists restart numbering if split apart).
                val prevIsItem = current.lastOrNull()?.let(::isListItem) ?: false
                var nextIsItem = false
                for (i in (index + 1) until lines.size) {
                    if (lines[i].isNotBlank()) {
                        nextIsItem = isListItem(lines[i])
                        break
                    }
                }
                if (prevIsItem && nextIsItem) current.add(line) else flush()
                continue
            }
            current.add(line)
        }
        flush()
        return chunks
    }

    private fun isListItem(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") || t.startsWith("> ")) {
            return true
        }
        val mark = t.indexOfFirst { it == '.' || it == ')' }
        if (mark <= 0 || mark > 3) return false
        return t.substring(0, mark).all { it.isDigit() }
    }
}

@Preview(name = "Markdown dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun MarkdownTextPreviewDark() {
    SpettroTheme(darkTheme = true) {
        MarkdownText(
            text = SAMPLE_MARKDOWN,
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}

@Preview(name = "Markdown light", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun MarkdownTextPreviewLight() {
    SpettroTheme(darkTheme = false) {
        MarkdownText(
            text = SAMPLE_MARKDOWN,
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}

private val SAMPLE_MARKDOWN = """
    # Heading

    Some **bold**, some *italic*, some `inline code`, and a [link](https://example.com).

    - First item
    - Second item

    > A blockquote worth reading.

    ```kotlin
    fun greet(name: String): String {
        return "Hello, ${'$'}name!"
    }
    ```
""".trimIndent()
