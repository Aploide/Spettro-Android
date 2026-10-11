package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.ui.text.AnnotatedString
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * The markdown renderer's annotator hook, so an emphasis character that
 * isn't emphasis stays the character that was typed.
 *
 * Inside inline code the parser still marks each `*` and `_` as an
 * emphasis token, and the renderer writes every such token back as `*`:
 * `` `if __name__ == "__main__"` `` read as `if **name** == "**main**"`.
 * This writes the source character instead, as the desktop shows it.
 */
internal val keepEmphasisCharacters: AnnotatedString.Builder.(content: String, child: ASTNode) -> Boolean =
    { content, child ->
        val parent = child.parent?.type
        if (child.type == MarkdownTokenTypes.EMPH &&
            parent != MarkdownElementTypes.EMPH &&
            parent != MarkdownElementTypes.STRONG
        ) {
            append(child.getTextInNode(content).toString())
            true
        } else {
            false
        }
    }
