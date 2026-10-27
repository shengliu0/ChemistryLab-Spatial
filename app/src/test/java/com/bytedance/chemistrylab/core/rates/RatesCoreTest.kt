package com.bytedance.chemistrylab.core.rates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real numbers for the disappearing cross and collision theory (see RatesCore header for sources):
 * Arrhenius +10 K ratio, catalyst factor, first-order concentration scaling, exact run integration,
 * the collision-theory fraction exp(−Ea/RT) from sampled Maxwell–Boltzmann velocities, and the molecule
 * window (CollisionBox) reproducing that fraction and its temperature dependence.
 */
class RatesCoreTest {
    private val R = Arrhenius.R

    @Test
    fun arrheniusTenKelvinNearRoomTemperatureDoublesTheRate() {
        val r = Thiosulfate.k(308.15) / Thiosulfate.k(298.15)
        val closed = exp(Thiosulfate.EA / R * (1 / 298.15 - 1 / 308.15))
        println("ARRHENIUS Ea = 53 kJ/mol: k(35 °C)/k(25 °C) = %.4f (closed form %.4f); A = %.3e s⁻¹, k25 = %.4e s⁻¹".format(r, closed, Thiosulfate.A, Thiosulfate.K25))
        assertEquals(closed, r, 1e-12)
        assertEquals(2.0, r, 0.02)
        // and the mean molecular speed only rises by √(308.15/298.15) = 1.7 %
        val sp = Arrhenius.speedRatio(298.15, 308.15)
        println("SPEED +10 K: ×%.4f (+%.1f %%) while the fraction above Ea rises ×%.3f".format(sp, (sp - 1) * 100,
            Arrhenius.fractionAbove(Thiosulfate.EA, 308.15) / Arrhenius.fractionAbove(Thiosulfate.EA, 298.15)))
        assertEquals(1.0166, sp, 1e-4)
    }

    @Test
    fun catalystLowersTheBarrierRateFactorExp() {
        val dEa = Thiosulfate.EA - Thiosulfate.EA_CAT
        val f = Thiosulfate.k(298.15, Thiosulfate.EA_CAT) / Thiosulfate.k(298.15)
        println("CATALYST ΔEa = %.0f kJ/mol at 25 °C: k ×%.1f (exp(ΔEa/RT) = %.1f); t(X) %.2f s -> %.3f s".format(dEa / 1e3, f,
            Arrhenius.catalystFactor(dEa, 298.15), Thiosulfate.tObscure(0.15, 298.15), Thiosulfate.tObscure(0.15, 298.15, Thiosulfate.EA_CAT)))
        assertEquals(exp(dEa / (R * 298.15)), f, 1e-9 * f)
        assertEquals(189.5, f, 0.5)
    }

    @Test
    fun halvingTheConcentrationDoublesTheTime() {
        val t1 = Thiosulfate.tObscure(0.15, 298.15); val t2 = Thiosulfate.tObscure(0.075, 298.15)
        val i1 = Thiosulfate.tObscureInitialRate(0.15, 298.15); val i2 = Thiosulfate.tObscureInitialRate(0.075, 298.15)
        println("FIRST ORDER: t(0.150 M) = %.2f s, t(0.075 M) = %.2f s, ratio %.4f exact (%.4f initial-rate form)".format(t1, t2, t2 / t1, i2 / i1))
        assertEquals(40.0, t1, 1e-9)
        assertEquals(2.0, i2 / i1, 1e-12)
        assertEquals(2.0, t2 / t1, 0.02)
        // and the temperature series the demo opens with (0.15 M): 15 / 25 / 35 / 45 °C
        val ts = listOf(15.0, 25.0, 35.0, 45.0).map { Thiosulfate.tObscure(0.15, it + 273.15) }
        println("SERIES 0.15 M: " + ts.joinToString(" / ") { "%.1f s".format(it) })
        assertTrue(ts.zipWithNext().all { (a, b) -> a / b in 1.8..2.3 })
        // under 2 % of the thiosulfate has reacted when the X vanishes: the initial-rate picture holds
        assertTrue(Thiosulfate.S_STAR / 0.15 < 0.02)
        // threshold: 2 % of the X's light gets through at S*
        assertEquals(0.02, Thiosulfate.transmittance(Thiosulfate.S_STAR), 1e-12)
    }

    @Test
    fun runIntegrationMatchesTheClosedFormAtAnyFrameRate() {
        for (dt in listOf(1.0 / 90, 1.0 / 60, 0.25)) {
            val run = XRun(0.12)
            while (!run.obscured) run.step(dt, 308.15)
            val exact = Thiosulfate.tObscure(0.12, 308.15)
            println("RUN dt = %.4f s: X gone at %.6f s (closed form %.6f), mean T %.2f K".format(dt, run.tObscured, exact, run.tMeanK))
            assertEquals(exact, run.tObscured, 1e-9)
            assertEquals(308.15, run.tMeanK, 1e-9)
        }
        // changing temperature mid-run: still exact (piecewise-constant k), mean T = time average
        val run = XRun(0.15)
        repeat(600) { run.step(0.01, 298.15) }                     // 6 s at 25 °C
        while (!run.obscured) run.step(0.01, 318.15)               // then 45 °C
        val k1 = Thiosulfate.k(298.15); val k2 = Thiosulfate.k(318.15)
        val exact = 6.0 + (Thiosulfate.kStar(0.15) - 6.0 * k1) / k2
        println("RUN 25 °C for 6 s then 45 °C: X gone at %.4f s (closed form %.4f), mean T %.2f °C".format(run.tObscured, exact, run.tMeanK - 273.15))
        assertEquals(exact, run.tObscured, 1e-6)
    }

    @Test
    fun monteCarloOfMaxwellBoltzmannCollisionsGivesExpMinusEaOverRT() {
        for ((e0, mb) in listOf(1.0 to 1.0, 2.0 to 2.0, 4.28 to 2.0, 6.0 to 0.5)) {
            val mc = CollisionTheory.monteCarlo(e0, 1.0, mb, 1_000_000, 42L + e0.toLong())
            val th = exp(-e0)
            val z = (mc.weighted - th) / mc.stdErr
            println("MC Ea/RT = %.2f, mB/mA = %.1f: flux-weighted %.5f ± %.5f vs exp(−Ea/RT) %.5f (z = %+.2f); unweighted pairs %.5f".format(
                e0, mb, mc.weighted, mc.stdErr, th, z, mc.unweighted))
            assertTrue("MC off by $z sigma", abs(z) < 4.0)
            assertTrue("pairs must under-count the tail", mc.unweighted < th)
        }
        // the molecule window's ×5 energy scale: 25 °C, 53 kJ/mol
        val f = exp(-Thiosulfate.EA / (5 * R * 298.15))
        println("WINDOW fraction at 25 °C: %.4f (real %.2e); +10 K ratio window %.4f, ^5 = %.4f = real %.4f".format(
            f, Arrhenius.fractionAbove(Thiosulfate.EA, 298.15),
            exp(-Thiosulfate.EA / (5 * R * 308.15)) / f, (exp(-Thiosulfate.EA / (5 * R * 308.15)) / f).pow(5),
            Arrhenius.fractionAbove(Thiosulfate.EA, 308.15) / Arrhenius.fractionAbove(Thiosulfate.EA, 298.15)))
        assertEquals(0.0139, f, 0.0005)
    }

    @Test
    fun hardSphereBoxConservesEnergyAndMomentumInCollisions() {
        val box = CollisionBox(seed = 7L)
        box.thermostatRate = 0.0; box.barrier = Double.MAX_VALUE
        box.reset(298.15)
        val e0 = box.kineticEnergy()
        repeat(60 * 60) { box.step(1.0 / 60) }
        val e1 = box.kineticEnergy()
        println("BOX 60 s without thermostat: KE %.9f -> %.9f (rel %.2e), %d collisions".format(e0, e1, (e1 - e0) / e0, box.collisionsAll))
        assertEquals(0.0, (e1 - e0) / e0, 1e-9)
        assertTrue(box.collisionsAll > 5000)
        // equipartition check: <½mv²> per particle ≈ 3/2 kT (time-averaged would be tighter; one snapshot)
        val per = e1 / box.n / (1.5 * box.kTd())
        println("BOX <KE>/(3/2 kT) = %.3f".format(per))
        assertEquals(1.0, per, 0.25)
    }

    /** Independent runs of the window at [tK] (barrier [ea]), one per seed. */
    private class Res(val coll: Long, val above: Long, val react: Long, val pairT: Double, val time: Double, val fTh: Double) {
        val f get() = above.toDouble() / coll
        val ratePerPair get() = react / pairT
    }

    private fun runBox(tK: Double, ea: Double, seconds: Int, seed: Long, reactive: Boolean): Res {
        val box = CollisionBox(seed = seed)
        box.barrier = ea; box.reactive = reactive
        box.reset(tK)
        repeat(seconds * 60) { box.step(1.0 / 60) }
        return Res(box.collisionsAB, box.aboveAB, box.reactions, box.pairTimeIntegral, box.simTime, box.fractionTheory())
    }

    /** Mean and standard error over seeds. Collision energies are correlated in time (a fast molecule makes
     *  several energetic hits), so the spread BETWEEN independent runs is the honest error, not √N counting. */
    private fun stats(xs: List<Double>): Pair<Double, Double> {
        val m = xs.average()
        return m to sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1) / xs.size)
    }

    private val cases = listOf(278.15 to Thiosulfate.EA, 298.15 to Thiosulfate.EA, 338.15 to Thiosulfate.EA, 298.15 to Thiosulfate.EA_CAT)
    private val seeds = (1L..8L).map { it * 7919L }

    @Test
    fun moleculeWindowCollisionsFollowExpMinusEaOverRT() {
        // bare collision statistics (reactions off): the box's own hard-sphere dynamics must give exp(−Ea/(5RT))
        for (c in cases) {
            val runs = seeds.map { runBox(c.first, c.second, 90, it, reactive = false) }
            val (m, se) = stats(runs.map { it.f / it.fTh })
            println("WINDOW %s: %d A–B collisions in %d runs (%.1f /s), fraction ≥ Ea / exp(−Ea/5RT) = %.3f ± %.3f (theory %.4f)".format(
                label(c), runs.sumOf { it.coll }, runs.size, runs.sumOf { it.coll } / runs.sumOf { it.time }, m, se, runs[0].fTh))
            assertTrue("collision-energy fraction off: $m ± $se", abs(m - 1) < 4 * se + 0.03)
        }
    }

    @Test
    fun moleculeWindowReactionRateScalesLikeTheAnalyticFraction() {
        val res = cases.map { c -> seeds.map { runBox(c.first, c.second, 120, it + 1, reactive = true) } }
        for ((k, runs) in res.withIndex()) {
            val (m, se) = stats(runs.map { it.f / it.fTh })
            val above = runs.sumOf { it.above }; val react = runs.sumOf { it.react }
            val p = react.toDouble() / above
            val seP = sqrt(0.25 / above)
            // reacting removes the FASTEST reactants, so the reactants' energetic tail runs a little short of
            // Maxwell–Boltzmann (the Prigogine–Xhrouet effect): printed, not hidden; the "solvent" thermostat keeps it small
            println("WINDOW-REACTING %s: %.2f reactions/s, ≥Ea fraction / theory %.3f ± %.3f (tail depletion), reacting/≥Ea %.3f (steric p = 0.5, z = %+.2f)".format(
                label(cases[k]), react / runs.sumOf { it.time }, m, se, p, (p - 0.5) / seP))
            assertTrue("steric factor off", abs(p - 0.5) < 4 * seP)
            assertTrue("tail depletion too large: $m", m > 0.85 && m < 1 + 4 * se + 0.03)
        }
        // rate per reactant pair ∝ √T · exp(−Ea/(5RT)): 5 °C and 65 °C against 25 °C, and the catalyst
        for ((a, b) in listOf(0 to 1, 2 to 1, 2 to 0, 3 to 1)) {
            val (ma, sa) = stats(res[a].map { it.ratePerPair }); val (mb, sb) = stats(res[b].map { it.ratePerPair })
            val meas = ma / mb
            val rel = sqrt((sa / ma) * (sa / ma) + (sb / mb) * (sb / mb))
            val th = sqrt(cases[a].first / cases[b].first) * res[a][0].fTh / res[b][0].fTh
            println("WINDOW RATE %s vs %s: measured ×%.3f ± %.0f %%, theory √T·exp(−Ea/5RT) ratio ×%.3f".format(
                label(cases[a]), label(cases[b]), meas, rel * 100, th))
            assertTrue("rate ratio off: $meas vs $th", abs(meas / th - 1) < 4 * rel + 0.06)
        }
    }

    private fun label(c: Pair<Double, Double>) = "%.0f °C/%.0f kJ".format(c.first - 273.15, c.second / 1e3)
}
