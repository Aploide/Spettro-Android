package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The meteor's and the smoulder's plans: the describes in the desktop's
 * tests/thinking.test.ts, plus golden values taken from the desktop's own
 * meteor.ts and embers.ts, so the port's random stream and the order it is
 * drawn in can't drift from the original unnoticed.
 */
class MeteorTest {

    // The bar the numbers were tuned in: 16px tall, its rail an end cap (8px)
    // in from each end.
    private fun body(fromFrac: Double, y: Double = 8.0, height: Double = 16.0) =
        MeteorGeometry(railLeft = 8.0, railWidth = 318.0, y = y, height = height, fromFrac = fromFrac, toFrac = 1.0)

    private fun close(expected: Double, actual: Double, what: String) =
        assertTrue("$what: expected $expected, got $actual", abs(expected - actual) < 1e-9)

    @Test
    fun `is the same run for the same seed, so a frozen frame is reproducible`() {
        assertEquals(Meteor.plan(body(0.0), 7), Meteor.plan(body(0.0), 7))
        assertNotEquals(Meteor.plan(body(0.0), 7), Meteor.plan(body(0.0), 8))
    }

    @Test
    fun `is quick, well under a second from Low and shorter from Max, and lands before it cools`() {
        val low = Meteor.timing(0.0, 1.0)
        val max = Meteor.timing(0.8, 1.0)
        assertTrue(low.totalMs >= 600)
        assertTrue(low.totalMs <= 850)
        assertTrue(max.totalMs <= 600)
        assertTrue(max.totalMs < low.totalMs)
        for (t in listOf(low, max)) {
            assertTrue(t.lands > 0.3)
            assertTrue(t.lands < 0.75)
        }
    }

    @Test
    fun `sets out from the stop the thumb was on, even for a one-step run`() {
        // The thumb is hidden as the head appears: a head set out from
        // anywhere else is the thumb jumping, with the fill's end showing
        // ahead of the head.
        for (fromFrac in listOf(0.0, 0.2, 0.6, 0.8)) {
            assertEquals(fromFrac, Meteor.timing(fromFrac, 1.0).startFrac, 0.0)
            val run = Meteor.plan(body(fromFrac), 7)
            assertEquals(run.fillX, run.fromX, 0.0)
            assertEquals(8 + fromFrac * 318, run.fillX, 0.0)
        }
        // A short run comes in streaking: it sets out faster than a long one.
        assertTrue(Meteor.plan(body(0.8), 7).launch > Meteor.plan(body(0.0), 7).launch)
    }

    @Test
    fun `throws 40 to 120 sparks, each out before the run ends`() {
        for (fromFrac in listOf(0.0, 0.8)) {
            val run = Meteor.plan(body(fromFrac), 7)
            assertTrue(run.sparks.size in 40..120)
            for (s in run.sparks) assertTrue(s.born + s.life <= run.total + 1e-9)
        }
    }

    @Test
    fun `scatters its sparks to fit the bar, and pulls them no harder than it can hold`() {
        val run = Meteor.plan(body(0.0), 7)
        for (s in run.sparks) assertTrue(abs(s.y - run.y) < 16.0 / 2)
        // A taller bar gives them more room, in proportion.
        val tall = Meteor.plan(body(0.0, y = 16.0, height = 32.0), 7)
        assertEquals(run.gravity * 2, tall.gravity, 1e-9)
    }

    @Test
    fun `draws the desktop's random stream`() {
        val rand = Meteor.random(7)
        val expected = listOf(
            0.011704753153026104,
            0.06195825757458806,
            0.97690763277933,
            0.6990287057124078,
            0.5214452685322613,
        )
        for ((i, value) in expected.withIndex()) close(value, rand(), "draw $i")
        val eleven = Meteor.random(11)
        close(0.5115870486479253, eleven(), "seed 11")
        close(0.5299464082345366, eleven(), "seed 11, second draw")
    }

    @Test
    fun `plans the desktop's run, spark for spark`() {
        val run = Meteor.plan(body(0.0), 7)
        assertEquals(120, run.sparks.size)
        close(0.25, run.launch, "launch")
        close(0.42, run.flight, "flight")
        close(0.74, run.total, "total")
        val first = run.sparks[0]
        close(0.005080253999793964, first.born, "born")
        close(0.2101837769455127, first.life, "life")
        close(4.111976138544666, first.x, "x")
        close(8.796114822849631, first.y, "y")
        close(21.73227784524402, first.vx, "vx")
        close(-9.620663104578853, first.vy, "vy")
        close(4.5894662696868185, first.drag, "drag")
        close(0.9997032952595449, first.size, "size")
        close(0.7636655419040471, first.heat, "heat")
        close(4.800918348628225, first.phase, "phase")
        close(58.51214001420885, first.freq, "freq")
        val wake = run.sparks[40]
        close(0.2977013938043102, wake.born, "wake born")
        close(183.8150215416674, wake.x, "wake x")
        close(135.67878194393674, wake.vx, "wake vx")
        val last = run.sparks.last()
        close(0.47209343817085037, last.born, "ember born")
        close(320.8923184014857, last.x, "ember x")
        close(-24.88705620355904, last.vx, "ember vx")
        close(25.823709460906684, last.freq, "ember freq")

        val fromMax = Meteor.plan(body(0.8), 3)
        assertEquals(85, fromMax.sparks.size)
        close(0.61, fromMax.launch, "launch from Max")
        close(265.42414979557003, fromMax.sparks[0].x, "x from Max")
        close(-23.52854361757636, fromMax.sparks[0].vy, "vy from Max")

        val medium = Meteor.timing(0.2, 1.0)
        close(368.0, medium.landsMs, "lands from Medium")
        close(688.0, medium.totalMs, "total from Medium")
    }

    @Test
    fun `accelerates the head into the stop and swallows its tail after impact`() {
        val run = Meteor.plan(body(0.0), 7)
        val start = Meteor.head(run, 0.0)
        assertEquals(run.fromX, start.x, 1e-9)
        assertTrue(Meteor.head(run, run.flight * 0.9).v > start.v)
        assertEquals(run.toX, Meteor.head(run, run.flight).x, 1e-9)
        assertEquals(0.0, Meteor.head(run, run.flight).v, 0.0)
        assertEquals(0.0, Meteor.tailLength(run, 0.0), 0.0)
        assertTrue(Meteor.tailLength(run, run.flight * 0.5) >= run.track * 0.14)
        assertEquals(0.0, Meteor.tailLength(run, run.flight + 0.13), 1e-9)
    }

    // MARK: Lit Ultra's smoulder

    @Test
    fun `is the same field for the same seed`() {
        assertEquals(Embers.plan(326.0, 16.0, 11), Embers.plan(326.0, 16.0, 11))
    }

    @Test
    fun `keeps its embers on the bar, a few of them, bobbing inside its height`() {
        val field = Embers.plan(326.0, 16.0, 11)
        assertTrue(field.motes.size in 5..12)
        for (m in field.motes) {
            assertTrue(m.amp + m.size < 16.0 / 2)
            // Slow: the length of the bar in no less than ten seconds.
            assertTrue(m.speed <= 326.0 / 10)
        }
    }

    @Test
    fun `plans the desktop's embers`() {
        val field = Embers.plan(326.0, 16.0, 11)
        assertEquals(8, field.motes.size)
        val first = field.motes[0]
        close(22.231740972958505, first.speed, "speed")
        close(0.04637031072052195, first.offset, "offset")
        close(2.902731979545206, first.amp, "amp")
        close(1.6262206903658807, first.bob, "bob")
        close(5.345587636701754, first.phase, "phase")
        close(1.126615608204156, first.size, "size")
        close(2.240078484173864, first.freq, "freq")
        close(0.8986626938014524, field.motes[7].offset, "last offset")
    }
}
