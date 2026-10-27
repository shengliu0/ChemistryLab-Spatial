package com.bytedance.chemistrylab.core.galvanic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class GalvanicCoreTest {

    @Test
    fun standardEmfFromTheTable() {
        // CRC "Electrochemical Series": Cu²⁺/Cu +0.3419, Zn²⁺/Zn −0.7618 -> the Daniell cell 1.104 V
        val e = Galv.standardEmf(Metal.ZN, Metal.CU)
        println("GALV E°(Zn|Cu) = %.4f V, E°(Zn|Ag) = %.4f V, E°(Mg|Ag) = %.4f V, E°(Fe|Cu) = %.4f V, E°(Pb|Ag) = %.4f V".format(
            e, Galv.standardEmf(Metal.ZN, Metal.AG), Galv.standardEmf(Metal.MG, Metal.AG), Galv.standardEmf(Metal.FE, Metal.CU), Galv.standardEmf(Metal.PB, Metal.AG)))
        assertEquals(1.1037, e, 1e-9)
        assertEquals(1.104, e, 0.0005)
        assertEquals(1.5614, Galv.standardEmf(Metal.ZN, Metal.AG), 1e-9)
        assertEquals(3.1716, Galv.standardEmf(Metal.MG, Metal.AG), 1e-9)
        // the panel / pinch order is the activity series
        val e0 = Metal.entries.map { it.e0 }
        assertEquals(e0.sorted(), e0)
        assertEquals(Metal.MG, Metal.AG.next())
        // 2019 SI exact constants
        assertEquals(6.02214076e23 * 1.602176634e-19, Galv.F, 1e-5)
        assertEquals(6.02214076e23 * 1.380649e-23, Galv.R, 1e-9)
    }

    @Test
    fun nernstSlopeAndTheTextbookExample() {
        val s1 = Galv.slope(1) * 1000; val s2 = Galv.slope(2) * 1000
        println("GALV Nernst slope 25 °C: %.3f mV/decade (n=1), %.3f mV/decade (n=2)".format(s1, s2))
        assertEquals(59.16, s1, 0.005)
        assertEquals(29.58, s2, 0.005)
        // one decade of Cu²⁺ costs 29.58 mV, whatever the starting point
        val e1 = Galv.emf(Metal.ZN, 1.0, Metal.CU, 1.0); val e01 = Galv.emf(Metal.ZN, 1.0, Metal.CU, 0.1)
        assertEquals(Galv.slope(2), e1 - e01, 1e-12)
        // [Cu²⁺] = 0.01 M, [Zn²⁺] = 1 M: E = 1.1037 − 0.05916 = 1.0445 V
        val e = Galv.emf(Metal.ZN, 1.0, Metal.CU, 0.01)
        println("GALV E(Zn|Zn²⁺ 1 M || Cu²⁺ 0.01 M|Cu) = %.4f V; E([Zn²⁺] 0.01, [Cu²⁺] 1) = %.4f V".format(e, Galv.emf(Metal.ZN, 0.01, Metal.CU, 1.0)))
        assertEquals(1.0445, e, 0.0001)
        assertEquals(1.1629, Galv.emf(Metal.ZN, 0.01, Metal.CU, 1.0), 0.0001)
        // the cell form (E° − RT/NF ln Q) equals the difference of the two electrode potentials, also for n₋ ≠ n₊
        for ((a, c) in listOf(Metal.ZN to Metal.AG, Metal.MG to Metal.CU, Metal.PB to Metal.AG)) {
            val ca = 0.37; val cc = 0.021
            assertEquals(Galv.potential(c, cc) - Galv.potential(a, ca), Galv.emf(a, ca, c, cc), 1e-12)
        }
        assertEquals(2, Galv.electrons(Metal.ZN, Metal.AG))
        // Zn + 2Ag⁺ -> Zn²⁺ + 2Ag: Q = [Zn²⁺]/[Ag⁺]²
        assertEquals(0.5 / (0.1 * 0.1), Galv.quotient(Metal.ZN, 0.5, Metal.AG, 0.1), 1e-12)
    }

    @Test
    fun faradayMasses() {
        // 1 A·h = 3600 C: Zn loses 3600 · 65.38 / (2F) = 1.2197 g, Cu gains 3600 · 63.546 / (2F) = 1.1855 g
        val zn = 3600 * Galv.gramsPerCoulomb(Metal.ZN); val cu = 3600 * Galv.gramsPerCoulomb(Metal.CU)
        println("GALV 1 A·h: Zn −%.4f g, Cu +%.4f g, Ag +%.4f g".format(zn, cu, 3600 * Galv.gramsPerCoulomb(Metal.AG)))
        assertEquals(1.22, zn, 0.005); assertEquals(1.2197, zn, 0.0001)
        assertEquals(1.19, cu, 0.005); assertEquals(1.1855, cu, 0.0001)
        assertEquals(4.0247, 3600 * Galv.gramsPerCoulomb(Metal.AG), 0.0001)

        // the running cell keeps the same books: Δm = q M/(nF) at both strips, Δc = q/(nFV) in both beakers
        val cell = GalvanicCell()
        repeat(240) { cell.step(3600.0) }                       // 10 days
        val q = cell.q
        println("GALV 10 d: Q = %.1f C, Zn %.4f g, Cu %+.4f g, [Zn²⁺] %.4f M, [Cu²⁺] %.4f M, E = %.4f V".format(
            q, cell.left.dMass, cell.right.dMass, cell.left.c, cell.right.c, cell.emf()))
        assertTrue(q > 4000.0)
        assertEquals(-q * Galv.gramsPerCoulomb(Metal.ZN), cell.left.dMass, 1e-9)
        assertEquals(q * Galv.gramsPerCoulomb(Metal.CU), cell.right.dMass, 1e-9)
        assertEquals(1.0 + q / (2 * Galv.F * 0.1), cell.left.c, 1e-12)
        assertEquals(1.0 - q / (2 * Galv.F * 0.1), cell.right.c, 1e-12)
        assertEquals(Galv.emf(Metal.ZN, cell.left.c, Metal.CU, cell.right.c), cell.emf(), 1e-12)
    }

    @Test
    fun theCircuitModel() {
        val cell = GalvanicCell()
        val e = cell.emf()
        val iCell = cell.cellCurrent(); val iLoad = cell.loadCurrent(); val u = cell.meterVolts()
        println("GALV closed: E = %.4f V, U = %.4f V, I(load) = %.3f mA, I(cell) = %.4f mA, P = %.2f mW".format(e, u, iLoad * 1e3, iCell * 1e3, cell.loadPower() * 1e3))
        // I = E / (R_int + R_load) (the 10 MΩ meter in parallel changes it by 10 ppm)
        assertEquals(e / (80.0 + 100.0), iLoad, 1e-7)
        assertEquals(6.13, iLoad * 1e3, 0.01)
        assertEquals(e - iCell * cell.rInt, u, 1e-12)             // terminal voltage U = E − I r
        assertEquals(iLoad * 100.0, u, 1e-12)
        // switch open: the high-impedance voltmeter reads the EMF (to 9 µV), no load current
        cell.switchClosed = false
        println("GALV open: voltmeter %.6f V vs E %.6f V".format(cell.meterVolts(), e))
        assertEquals(e, cell.meterVolts(), 1e-5)
        assertEquals(0.0, cell.loadCurrent(), 0.0)
    }

    @Test
    fun noBridgeNoCurrent() {
        val cell = GalvanicCell()
        cell.bridgeIn = false
        assertEquals(0.0, cell.meterVolts(), 0.0)
        assertEquals(0.0, cell.cellCurrent(), 0.0)
        assertEquals(0.0, cell.loadCurrent(), 0.0)
        repeat(30) { cell.step(86400.0) }                       // a month on the bench: nothing happens
        assertEquals(0.0, cell.q, 0.0)
        assertEquals(1.0, cell.right.c, 0.0); assertEquals(0.0, cell.left.dMass, 0.0)
        assertEquals(1.1037, cell.emf(), 1e-9)                   // the EMF is still there, only the loop is open
        cell.bridgeIn = true
        assertTrue(cell.loadCurrent() > 0.006)
    }

    @Test
    fun bothBeakersStayNeutral() {
        val cell = GalvanicCell()
        cell.reset(Metal.ZN, 0.5, Metal.AG, 0.8)                  // Zn²⁺ sulfate vs Ag⁺ nitrate: n₋ ≠ n₊
        repeat(100) { cell.step(7200.0) }
        val l = cell.left; val r = cell.right
        val eWire = cell.q / Galv.F
        println("GALV Zn|Ag after %.0f h: Q = %.1f C (%.5f mol e⁻), net charge left %.2e, right %.2e mol; K⁺ into Ag beaker %.5f, NO₃⁻ into Zn beaker %.5f mol".format(
            cell.t / 3600, cell.q, eWire, l.netCharge, r.netCharge, r.kIn, l.no3In))
        assertTrue(cell.q > 1000.0)
        assertEquals(0.0, l.netCharge, 1e-12)
        assertEquals(0.0, r.netCharge, 1e-12)
        // one ion from the bridge per electron in the wire, on each side
        assertEquals(eWire, r.kIn, 1e-15); assertEquals(eWire, l.no3In, 1e-15)
        // stoichiometry: 2 Ag deposited per Zn dissolved
        assertEquals(2.0, (r.dMass / Metal.AG.molarMass) / (-l.dMass / Metal.ZN.molarMass), 1e-9)
        // fresh solution poured in: neutral again by construction
        r.fillSolution(0.01); assertEquals(0.0, r.netCharge, 1e-15)
    }

    @Test
    fun polarityFollowsThePotentials() {
        val cell = GalvanicCell()
        assertEquals(1, cell.polarity())                          // Zn | Cu: Cu is +, electrons left -> right
        cell.left.replace(Metal.AG, 1.0)                          // Ag | Cu: Ag is now +, electrons right -> left
        println("GALV Ag (left) | Cu (right): meter %.4f V, E° = %.4f V".format(cell.meterVolts(), cell.standardEmf()))
        assertEquals(-1, cell.polarity())
        assertTrue(cell.meterVolts() < 0.0)
        assertEquals(0.7996 - 0.3419, cell.standardEmf(), 1e-12)
        assertTrue(cell.anode() === cell.right)
        val cu0 = cell.right.mass
        repeat(24) { cell.step(3600.0) }
        assertTrue(cell.right.mass < cu0)                          // the copper strip dissolves now
        assertTrue(cell.left.dMass > 0.0)                          // silver plates out
        // a concentration cell: Cu | Cu²⁺ 0.01 || Cu²⁺ 1 | Cu — E = 2 × 29.58 mV, the dilute side is the anode
        cell.reset(Metal.CU, 0.01, Metal.CU, 1.0)
        println("GALV concentration cell Cu 0.01 M | Cu 1 M: E = %.4f V".format(cell.emf()))
        assertEquals(2 * Galv.slope(2), cell.emf(), 1e-12)
        assertEquals(1, cell.polarity())
        cell.reset(Metal.CU, 1.0, Metal.CU, 1.0)
        assertEquals(0, cell.polarity()); assertEquals(0.0, cell.loadCurrent(), 0.0)
    }

    @Test
    fun voltageHoldsThenCollapsesToEquilibrium() {
        val cell = GalvanicCell()
        val e0 = cell.emf()
        var prev = e0
        val trace = ArrayList<String>()
        var day = 0
        while (day < 60) {
            cell.step(86400.0); day++
            val e = cell.emf()
            assertTrue("EMF must fall monotonically (day $day)", e <= prev + 1e-12)
            prev = e
            if (day % 10 == 0) trace += "d%d %.4f V [Cu²⁺] %.3g".format(day, e, cell.right.c)
        }
        println("GALV discharge at 6 mA: " + trace.joinToString(" · "))
        // flat at first (Nernst is logarithmic): still > 1.07 V after a week
        val c7 = GalvanicCell().also { c -> repeat(7) { c.step(86400.0) } }
        assertTrue(c7.emf() > 1.07)
        // then it runs down to E = 0, i.e. Q = K = 10^(2FE°/RT ln10) = 10^37.3: no Cu²⁺ left to speak of
        repeat(4000) { cell.step(1e6) }
        val lgQ = log10(Galv.quotient(Metal.ZN, cell.left.c, Metal.CU, cell.right.c))
        val lgK = Galv.log10K(Metal.ZN, Metal.CU)
        println("GALV equilibrium: E = %.2e V, [Zn²⁺] %.6f M, [Cu²⁺] %.3e M, lg Q = %.4f, lg K = %.4f, Zn %.3f g, Cu %+.3f g".format(
            cell.emf(), cell.left.c, cell.right.c, lgQ, lgK, cell.left.dMass, cell.right.dMass))
        assertEquals(37.31, lgK, 0.01)
        assertTrue(abs(cell.emf()) < 1e-6)
        assertEquals(lgK, lgQ, 1e-3)
        assertEquals(2.0, cell.left.c, 1e-9)                       // every Cu²⁺ became a Zn²⁺
        // and the books still close: all 0.1 mol of Cu²⁺ plated = 6.355 g
        assertEquals(0.1 * Metal.CU.molarMass, cell.right.dMass, 1e-6)
        assertEquals(0.0, cell.left.netCharge, 1e-12); assertEquals(0.0, cell.right.netCharge, 1e-12)
    }

    @Test
    fun aStripCanDissolveThrough() {
        // Mg (2.43 g wet, 0.100 mol) against 3 M Ag⁺ (0.3 mol): the magnesium is used up first and the loop opens
        val cell = GalvanicCell()
        cell.reset(Metal.MG, 1.0, Metal.AG, 3.0)
        repeat(2000) { cell.step(86400.0) }
        println("GALV Mg|Ag run-out: Mg wet mass %.4f g, Q = %.0f C (Mg capacity %.0f C), I = %.3g A".format(
            cell.left.mass, cell.q, Galv.WET_CM3 * Metal.MG.density / Metal.MG.molarMass * 2 * Galv.F, cell.cellCurrent()))
        assertEquals(0.0, cell.left.mass, 1e-12)
        assertEquals(Galv.WET_CM3 * Metal.MG.density / Metal.MG.molarMass * 2 * Galv.F, cell.q, 1e-6)
        assertEquals(0.0, cell.cellCurrent(), 0.0)
        assertEquals(0.0, cell.meterVolts(), 0.0)
    }
}
