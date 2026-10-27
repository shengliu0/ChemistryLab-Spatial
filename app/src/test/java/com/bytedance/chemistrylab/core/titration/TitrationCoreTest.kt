package com.bytedance.chemistrylab.core.titration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class TitrationCoreTest {
    private val hcl = Titration(Acid.HCL)
    private val hac = Titration(Acid.ACETIC)

    @Test
    fun strongAcidCurveMatchesClosedForms() {
        // 0.100 M HCl: [H⁺] = 0.1 (+ Kw/0.1) -> pH 1.000
        println("HCl start pH = %.6f".format(hcl.pH(0.0)))
        assertEquals(1.0, hcl.pH(0.0), 1e-9)
        // before equivalence, excess acid: [H⁺] = (CaVa − CbVb)/(Va + Vb)  (water's own H⁺ negligible)
        for (v in doubleArrayOf(5.0, 10.0, 20.0, 24.0, 24.9)) {
            val closed = -log10((0.1 * 25 - 0.1 * v) / (25 + v))
            println("HCl V = %5.2f mL  pH = %.5f  (excess-acid closed form %.5f)".format(v, hcl.pH(v), closed))
            assertEquals(closed, hcl.pH(v), 1e-6)
        }
        // equivalence: NaCl solution, pH = ½pKw = 7.00 exactly
        println("HCl equivalence V = %.2f mL  pH = %.6f".format(hcl.veqMl, hcl.pH(hcl.veqMl)))
        assertEquals(25.00, hcl.veqMl, 1e-12)
        assertEquals(7.0, hcl.pH(25.0), 1e-9)
        // past it, excess base: pOH = −log((CbVb − CaVa)/(Va + Vb))
        for (v in doubleArrayOf(25.1, 26.0, 30.0, 50.0)) {
            val closed = 14 + log10((0.1 * v - 0.1 * 25) / (25 + v))
            assertEquals(closed, hcl.pH(v), 1e-6)
        }
        // the famous jump: ±1 drop (0.05 mL) around equivalence spans 4.00 -> 10.00
        println("HCl jump: pH(24.95) = %.3f, pH(25.00) = %.3f, pH(25.05) = %.3f".format(hcl.pH(24.95), hcl.pH(25.0), hcl.pH(25.05)))
        assertEquals(4.00, hcl.pH(24.95), 0.005); assertEquals(10.00, hcl.pH(25.05), 0.005)
    }

    @Test
    fun weakAcidStartBufferAndEquivalence() {
        val ka = AcidBase.KA_ACETIC; val pKa = -log10(ka)
        // start: exact quadratic h² + Ka h − Ka C = 0  (water negligible) -> pH 2.88
        val hq = (-ka + sqrt(ka * ka + 4 * ka * 0.1)) / 2
        println("HAc start pH = %.5f (quadratic %.5f; √(KaC) estimate %.5f)".format(hac.pH(0.0), -log10(hq), -log10(sqrt(ka * 0.1))))
        assertEquals(-log10(hq), hac.pH(0.0), 1e-6)
        assertEquals(2.88, hac.pH(0.0), 0.005)
        // half-equivalence: pH = pKa (Henderson–Hasselbalch with equal amounts; exact result within 0.001)
        println("HAc half-equivalence 12.50 mL: pH = %.5f, pKa = %.5f".format(hac.pH(12.5), pKa))
        assertEquals(pKa, hac.pH(12.5), 0.002)
        assertEquals(4.76, hac.pH(12.5), 0.005)
        // buffer region obeys Henderson–Hasselbalch to < 0.01
        for (v in doubleArrayOf(5.0, 10.0, 15.0, 20.0)) assertEquals(pKa + log10(v / (25 - v)), hac.pH(v), 0.01)
        // equivalence: acetate hydrolysis, [OH⁻] = √(Kb·C), C = 0.0500 M  -> pH 8.73
        val ohEq = sqrt(AcidBase.KW / ka * 0.05)
        println("HAc equivalence pH = %.5f (√(Kb C) estimate %.5f)".format(hac.pH(25.0), 14 + log10(ohEq)))
        assertEquals(14 + log10(ohEq), hac.pH(25.0), 0.002)
        assertEquals(8.72, hac.pH(25.0), 0.015)
    }

    @Test
    fun chargeBalanceClosesEverywhere() {
        var worst = 0.0
        for (acid in Acid.values()) {
            val t = Titration(acid)
            var v = 0.0
            while (v <= 50.0001) { worst = maxOf(worst, abs(t.residual(v))); v += 0.05 }
        }
        println("worst charge-balance residual over 0..50 mL (both acids, every drop) = %.2e mol/L".format(worst))
        assertTrue(worst < 1e-12)
        // pure water and edge cases: no acid, no base -> pH 7; huge excess base stays finite
        assertEquals(7.0, AcidBase.pH(0.0, 0.0, Double.POSITIVE_INFINITY), 1e-9)
        assertEquals(7.0, AcidBase.pH(0.0, 0.0, 1.75e-5), 1e-9)
        assertTrue(AcidBase.pH(0.0, 5.0, 1.0).isFinite())
        // pH(V) is monotone (bisection on V relies on it)
        for (acid in Acid.values()) { val t = Titration(acid); var p = t.pH(0.0); var v = 0.05
            while (v <= 50.0) { val q = t.pH(v); assertTrue("$acid not monotone at $v", q > p); p = q; v += 0.05 } }
    }

    @Test
    fun badInputIsRejected() {
        var threw = 0
        for (args in listOf(doubleArrayOf(-1.0, 0.0, 1e-5), doubleArrayOf(0.1, Double.NaN, 1e-5), doubleArrayOf(0.1, 0.0, 0.0)))
            try { AcidBase.solveH(args[0], args[1], args[2]) } catch (e: IllegalArgumentException) { threw++ }
        assertEquals(3, threw)
    }

    @Test
    fun indicatorRangesComeOutOfTheSpectra() {
        for (ind in Indicator.values()) {
            val (lo, hi) = IndicatorOptics.transitionRange(ind)
            val end = IndicatorOptics.endpointPH(ind)
            val a = IndicatorOptics.acidColour(ind); val b = IndicatorOptics.baseColour(ind)
            println("%-17s pKa %.2f  computed range %.2f–%.2f (table %.1f–%.1f)  endpoint pH %.2f  acid sRGB-lin (%.2f %.2f %.2f) base (%.2f %.2f %.2f)".format(
                ind, ind.pKa, lo, hi, ind.tabLo, ind.tabHi, end, a[0], a[1], a[2], b[0], b[1], b[2]))
            assertEquals("$ind low end", ind.tabLo, lo, 0.25)
            assertEquals("$ind high end", ind.tabHi, hi, 0.25)
            assertTrue(end in lo..hi)
        }
        // the colours are the right colours: phenolphthalein colourless -> pink (red + blue > green),
        // methyl orange red -> yellow-orange, bromothymol blue yellow -> blue
        val pp = IndicatorOptics.baseColour(Indicator.PHENOLPHTHALEIN); val pp0 = IndicatorOptics.acidColour(Indicator.PHENOLPHTHALEIN)
        assertTrue(pp0.all { it > 0.99 }); assertTrue(pp[0] > 2 * pp[1] && pp[2] > 2 * pp[1])
        val mo0 = IndicatorOptics.acidColour(Indicator.METHYL_ORANGE); val mo = IndicatorOptics.baseColour(Indicator.METHYL_ORANGE)
        assertTrue(mo0[0] > 0.8 && mo0[1] < 0.1); assertTrue(mo[0] > 0.9 && mo[1] > 0.2 && mo[2] < 0.05)
        val bt0 = IndicatorOptics.acidColour(Indicator.BROMOTHYMOL_BLUE); val bt = IndicatorOptics.baseColour(Indicator.BROMOTHYMOL_BLUE)
        assertTrue(bt0[0] > 0.9 && bt0[1] > 0.9 && bt0[2] < 0.6); assertTrue(bt[2] > 0.9 && bt[0] < 0.1)
        // stage classification follows pH
        assertEquals(0, IndicatorOptics.stage(Indicator.PHENOLPHTHALEIN, 7.0)); assertEquals(3, IndicatorOptics.stage(Indicator.PHENOLPHTHALEIN, 12.0))
        assertEquals(0, IndicatorOptics.stage(Indicator.METHYL_ORANGE, 2.0)); assertEquals(3, IndicatorOptics.stage(Indicator.METHYL_ORANGE, 6.0))
    }

    @Test
    fun endpointErrorPerIndicator() {
        // endpoint = burette volume where the flask reaches the indicator's computed endpoint pH
        val res = HashMap<String, Double>()
        for (t in listOf(hcl, hac)) for (ind in Indicator.values()) {
            val vEnd = t.volumeAt(IndicatorOptics.endpointPH(ind))
            val err = vEnd - t.veqMl
            res["${t.acid}/$ind"] = err
            println("%-6s + %-17s endpoint pH %.2f -> V = %6.3f mL, error %+7.3f mL (%+.2f %%)".format(t.acid, ind, IndicatorOptics.endpointPH(ind), vEnd, err, 100 * err / t.veqMl))
        }
        // HCl: all three indicators sit inside the vertical jump (4.0 -> 10.0 within ±1 drop): error ≤ 2 drops (0.4 %)
        for (ind in Indicator.values()) assertTrue("HCL/$ind", abs(res["HCL/$ind"]!!) < 2 * Titration.DROP_ML)
        // acetic acid: the jump is 7.5 -> 10.0 only; phenolphthalein (8.2) lands inside it, < 1 drop off
        assertTrue(abs(res["ACETIC/PHENOLPHTHALEIN"]!!) < Titration.DROP_ML)
        // bromothymol blue (green ~6.9) turns several drops early
        assertTrue(res["ACETIC/BROMOTHYMOL_BLUE"]!! < -0.1)
        // methyl orange turns while 90 % of the acid is still there: the buffer region starts at its range
        assertTrue(res["ACETIC/METHYL_ORANGE"]!! < -20.0)
    }

    @Test
    fun theFlashLastsLongerNearTheEnd() {
        val pe = IndicatorOptics.endpointPH(Indicator.PHENOLPHTHALEIN)
        var last = -1.0
        for (v in doubleArrayOf(5.0, 15.0, 20.0, 24.0, 24.5, 24.9)) {
            val life = hcl.flashLife(v, pe, 0.35)
            println("HCl + phenolphthalein: pink flash of the drop at %5.2f mL lasts %.2f s".format(v, life))
            assertTrue(life > last); last = life
        }
        // at the equivalence point the bulk itself turns (strong acid: the drop after 25.00 mL)
        assertTrue(hcl.flashLife(25.0, pe, 0.35).isInfinite())
        // a parcel ends at the bulk composition after the drop
        assertEquals(hcl.pH(20.05), hcl.parcelPH(20.0, 1, 100.0, 0.35), 1e-9)
        // and starts as the drop itself (0.100 M NaOH, pH 13)
        assertEquals(13.0, hcl.parcelPH(20.0, 1, 0.0, 0.35), 1e-6)
    }

    @Test
    fun microCountsFollowTheLogScale() {
        val s = MicroScale.targets(hcl, 0.0)
        println("micro HCl start $s, at 25.00 ${MicroScale.targets(hcl, 25.0)}, at 30 ${MicroScale.targets(hcl, 30.0)}")
        assertEquals(14, s.h3o); assertEquals(0, s.oh); assertEquals(8, s.cl); assertEquals(0, s.na)
        val eq = MicroScale.targets(hcl, 25.0)
        assertEquals(2, eq.h3o); assertEquals(2, eq.oh); assertEquals(8, eq.na)          // neutral: equal, few
        val w0 = MicroScale.targets(hac, 0.0); val wh = MicroScale.targets(hac, 12.5); val we = MicroScale.targets(hac, 25.0)
        println("micro HAc start $w0, half $wh, equivalence $we")
        assertEquals(8, w0.ha); assertEquals(0, w0.a)                                       // mostly undissociated
        assertEquals(4, wh.ha); assertEquals(4, wh.a)                                       // the buffer
        assertEquals(0, we.h3o); assertTrue(we.oh >= 5)                                     // hydrolysis: basic
        // the H₃O⁺ count never rises while base is added
        var prev = 99; var v = 0.0
        while (v <= 50.0) { val n = MicroScale.targets(hac, v).h3o; assertTrue(n <= prev); prev = n; v += 0.5 }
    }
}
