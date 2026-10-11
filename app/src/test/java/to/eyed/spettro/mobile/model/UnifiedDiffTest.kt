package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the desktop's unifiedDiff.test.ts. */
class UnifiedDiffTest {

    /** Lines as `diff -u` would print them, plus each line's numbers. */
    private fun render(lines: List<UnifiedDiff.Line>): List<String> = lines.map { l ->
        val sign = when (l.kind) {
            UnifiedDiff.Kind.ADDED -> "+"
            UnifiedDiff.Kind.REMOVED -> "-"
            UnifiedDiff.Kind.CONTEXT -> " "
        }
        "${l.oldNo ?: "_"}/${l.newNo ?: "_"} $sign${l.text}"
    }

    private fun file(n: Int, change: Map<Int, String> = emptyMap()): String =
        (1..n).joinToString("\n") { change[it] ?: "line $it" } + "\n"

    private fun UnifiedDiff.Hunk.span() = listOf(oldStart, oldLines, newStart, newLines)

    @Test
    fun marksAnInsertedLineWithContextAndBothNumberings() {
        val d = UnifiedDiff.of("a\nb\nc\nd\ne\nf\n", "a\nb\nc\nNEW\nd\ne\nf\n")
        assertEquals(1, d.added)
        assertEquals(0, d.removed)
        assertEquals(1, d.hunks.size)
        assertEquals(
            listOf("1/1  a", "2/2  b", "3/3  c", "_/4 +NEW", "4/5  d", "5/6  e", "6/7  f"),
            render(d.hunks[0].lines),
        )
        assertEquals(listOf(1, 6, 1, 7), d.hunks[0].span())
    }

    @Test
    fun marksADeletedLine() {
        val d = UnifiedDiff.of(file(10), file(10).replace("line 5\n", ""))
        assertEquals(1, d.removed)
        assertEquals(0, d.added)
        assertEquals(
            listOf("2/2  line 2", "3/3  line 3", "4/4  line 4", "5/_ -line 5", "6/5  line 6", "7/6  line 7", "8/7  line 8"),
            render(d.hunks[0].lines),
        )
        assertEquals(listOf(2, 7, 2, 6), d.hunks[0].span())
    }

    @Test
    fun showsAReplacedBlockAsRemovalsThenAdditions() {
        val d = UnifiedDiff.of("keep\nold 1\nold 2\nkeep\n", "keep\nnew 1\nnew 2\nnew 3\nkeep\n")
        assertEquals(
            listOf("1/1  keep", "2/_ -old 1", "3/_ -old 2", "_/2 +new 1", "_/3 +new 2", "_/4 +new 3", "4/5  keep"),
            render(d.hunks[0].lines),
        )
    }

    @Test
    fun pairsUnchangedLinesInsideAChangedRegion() {
        val d = UnifiedDiff.of("x\nshared\ny\n", "X\nshared\nY\n")
        assertEquals(2, d.added)
        assertEquals(2, d.removed)
        assertTrue("2/2  shared" in render(d.hunks[0].lines))
    }

    @Test
    fun mergesChangesWhoseContextOverlaps() {
        val d = UnifiedDiff.of(file(20), file(20, mapOf(5 to "five", 12 to "twelve")))
        assertEquals(1, d.hunks.size)
        assertEquals(listOf(2, 14, 2, 14), d.hunks[0].span())
    }

    @Test
    fun splitsChangesFarApart() {
        val d = UnifiedDiff.of(file(40), file(40, mapOf(5 to "five", 30 to "thirty")))
        assertEquals(2, d.hunks.size)
        assertEquals(listOf(2, 7, 2, 7), d.hunks[0].span())
        assertEquals(listOf(27, 7, 27, 7), d.hunks[1].span())
        assertTrue("_/30 +thirty" in render(d.hunks[1].lines))
    }

    @Test
    fun aMissingFinalNewlineChangesTheLastLine() {
        val d = UnifiedDiff.of("a\nb\n", "a\nb")
        assertEquals(1, d.removed)
        assertEquals(1, d.added)
        val added = d.hunks[0].lines.first { it.kind == UnifiedDiff.Kind.ADDED }
        assertEquals("b", added.text)
        assertTrue(added.noNewline)
        assertEquals(false, d.hunks[0].lines.first { it.kind == UnifiedDiff.Kind.REMOVED }.noNewline)
    }

    @Test
    fun twoFilesWithoutFinalNewlines() {
        assertEquals(listOf("1/1  a", "2/_ -b", "_/2 +c"), render(UnifiedDiff.of("a\nb", "a\nc").hunks[0].lines))
    }

    @Test
    fun aCreatedFileIsAllAdditionsFromZero() {
        val d = UnifiedDiff.of(null, "one\ntwo\n")
        assertEquals(2, d.added)
        assertEquals(listOf(0, 0, 1, 2), d.hunks[0].span())
    }

    @Test
    fun identicalTextHasNoHunks() {
        assertEquals(0, UnifiedDiff.of("same\n", "same\n").hunks.size)
    }

    @Test
    fun aLargeRewriteFallsBackToAWholesaleReplace() {
        val before = (0 until 3000).joinToString("\n") { "a$it" }
        val after = (0 until 3000).joinToString("\n") { "b$it" }
        val started = System.currentTimeMillis()
        val d = UnifiedDiff.of(before, after)
        assertTrue(System.currentTimeMillis() - started < 2000)
        assertEquals(3000, d.removed)
        assertEquals(3000, d.added)
    }

    @Test
    fun crlfLinesCompareByTheirText() {
        val d = UnifiedDiff.of("a\r\nb\r\n", "a\r\nc\r\n")
        assertEquals(listOf("1/1  a", "2/_ -b", "_/2 +c"), render(d.hunks[0].lines))
    }
}
