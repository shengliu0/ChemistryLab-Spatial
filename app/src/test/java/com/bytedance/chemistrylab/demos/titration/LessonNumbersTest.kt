package com.bytedance.chemistrylab.demos.titration

import com.bytedance.chemistrylab.core.electrolysis.Cell
import com.bytedance.chemistrylab.core.electrolysis.Electrolyte
import com.bytedance.chemistrylab.core.electrolysis.Voltameter
import com.bytedance.chemistrylab.core.galvanic.Galv
import com.bytedance.chemistrylab.core.galvanic.GalvanicCell
import com.bytedance.chemistrylab.core.galvanic.Metal
import com.bytedance.chemistrylab.core.titration.Acid
import com.bytedance.chemistrylab.core.titration.Indicator
import com.bytedance.chemistrylab.core.titration.IndicatorOptics
import com.bytedance.chemistrylab.core.titration.Titration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every number the guided tracks (导学) of electrolysis, daniell and titration quote, recomputed from the cores. */
class LessonNumbersTest {
    @Test
    fun titrationLesson() {
        val hcl = Titration(Acid.HCL)
        for (v in listOf(23.0, 24.0, 24.5)) assertTrue("pH($v) ≈ 2–3", hcl.pH(v) in 2.3..3.01)
        assertEquals(4.0, hcl.pH(24.95), 0.02)
        assertEquals(7.0, hcl.pH(25.00), 0.02)
        assertEquals(10.0, hcl.pH(25.05), 0.02)
        val ac = Titration(Acid.ACETIC)
        assertEquals(4.76, ac.pH(12.5), 0.01)
        assertEquals(8.73, ac.pH(25.0), 0.01)
        // phenolphthalein turns within one drop of 25.00 mL for both acids (the ±0.05 mL challenge is possible)
        for (t in listOf(hcl, ac)) {
            val pe = IndicatorOptics.endpointPH(Indicator.PHENOLPHTHALEIN)
            val d = (0..1000).first { t.pH(it * 0.05) >= pe }
            println("${t.acid}: phenolphthalein (pe %.2f) turns at drop $d = %.2f mL".format(pe, d * 0.05))
            assertTrue(d in 500..501)
        }
    }

    @Test
    fun electrolysisLesson() {
        val c = Voltameter()
        c.step(10.0, 2.0, Electrolyte.NA2SO4)
        assertEquals(0.0, c.current, 0.0)                         // below 2.2 V: nothing
        while (c.producedH < 10.0) c.step(1.0, 6.0, Electrolyte.NA2SO4)
        assertEquals(2.0, c.producedH / c.producedO, 1e-9)
        val ratio = Cell.resistance(Electrolyte.PURE) / Cell.resistance(Electrolyte.NA2SO4)
        println("R pure %.2f MΩ, R Na2SO4 %.2f Ω, ratio %.3g".format(Cell.resistance(Electrolyte.PURE) / 1e6, Cell.resistance(Electrolyte.NA2SO4), ratio))
        assertEquals(6.6, Cell.resistance(Electrolyte.PURE) / 1e6, 0.05)
        assertTrue(ratio in 0.5e6..2e6)                         // "a millionfold"
    }

    @Test
    fun daniellLesson() {
        val cell = GalvanicCell()
        assertEquals(0.61, cell.meterVolts(), 0.005)             // switch closed: E − Ir
        cell.switchClosed = false
        assertEquals(1.10, cell.meterVolts(), 0.005)             // open: the EMF
        cell.bridgeIn = false
        assertEquals(0.0, cell.meterVolts(), 0.0)
        assertEquals(1.56, Galv.standardEmf(Metal.ZN, Metal.AG), 0.005)
        // only Mg | Ag clears 3 V
        val over3 = Metal.entries.flatMap { a -> Metal.entries.map { b -> a to b } }.filter { (a, b) -> Galv.standardEmf(a, b) > 3.0 }
        assertEquals(listOf(Metal.MG to Metal.AG), over3)
    }
}
