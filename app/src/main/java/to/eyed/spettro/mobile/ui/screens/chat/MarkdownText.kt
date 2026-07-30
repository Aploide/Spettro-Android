package to.eyed.spettro.mobile.ui.screens.chat

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
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoBody
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Transcript markdown, rendered through mikepenz's markdown-renderer-m3 and
 * styled to the Spettro transcript conventions: 14sp prose, monospaced code
 * on the raised surface with a hairline border, links in the accent.
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
 */
@Composable
private fun MarkdownChunkView(chunk: String) {
    val colors = LocalSpettroColors.current
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium
    Markdown(
        content = chunk,
        colors = markdownColor(
            text = scheme.onSurface,
            codeBackground = colors.surfaceRaised,
            inlineCodeBackground = colors.surfaceRaised,
            dividerColor = colors.hairline,
            tableBackground = colors.surfaceRaised,
        ),
        typography = markdownTypography(
            h1 = body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            h2 = body.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
            h3 = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            h4 = body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
            h5 = body.copy(fontWeight = FontWeight.SemiBold),
            h6 = body.copy(fontWeight = FontWeight.Medium),
            text = body,
            code = MonoBody,
            inlineCode = MonoBody,
            quote = body.copy(color = scheme.onSurfaceVariant),
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            textLink = TextLinkStyles(
                style = SpanStyle(color = colors.accent),
                pressedStyle = SpanStyle(
                    color = colors.accent,
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
 * that scrolls horizontally, all on the raised surface with a hairline border
 * rounded to the small radius — the Compose port of `SpettroCodeBlockStyle`.
 */
@Composable
private fun SpettroCodeBlock(code: String, language: String?) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, colors.hairline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs + 1.dp),
        ) {
            Text(
                text = language?.takeIf { it.isNotBlank() } ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HairlineDivider()
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = code.trimEnd('\n'),
                style = MonoBody,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier.padding(Dimens.spacingSm),
            )
        }
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

@Preview(name = "Markdown dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun MarkdownTextPreviewDark() {
    SpettroTheme(darkTheme = true) {
        MarkdownText(
            text = SAMPLE_MARKDOWN,
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}

@Preview(name = "Markdown light", showBackground = true, backgroundColor = 0xFFF9F9F7)
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
