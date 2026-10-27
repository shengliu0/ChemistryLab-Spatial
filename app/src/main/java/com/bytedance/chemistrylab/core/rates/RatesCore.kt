// RatesCore — reaction rates and collision theory for the "disappearing cross". Pure Kotlin.
//
//     Na₂S₂O₃ + 2 HCl → 2 NaCl + SO₂ + S↓ + H₂O        (S₂O₃²⁻ + 2 H⁺ → S↓ + SO₂ + H₂O)
//
// ARRHENIUS. k = A·exp(−Ea/RT). For this reaction school data sets give rate ∝ [S₂O₃²⁻] (first order)
// and ~zero order in H⁺ while the acid is in excess (e.g. OCR A-level PAG 10.2 "thiosulfate & acid";
// the Boston University "colloidal sulfur" lecture demonstration), and Arrhenius analyses of the
// disappearing-cross timings land at Ea ≈ 45–60 kJ/mol (student values scatter widely, some as low as
// ~37). We use Ea = 53 kJ/mol: a representative value inside that range, chosen because it gives the
// textbook "×2 per +10 K near room temperature" (k(308.15)/k(298.15) = 2.001, RatesCoreTest).
// It is a representative school value, not a precision literature constant.
//
// RATE LAW AND THE X. Sulfur forms at r = k[S₂O₃²⁻] (pseudo-first order, acid in excess), so with a
// thiosulfate start concentration c0 the colloidal sulfur is EXACTLY [S](t) = c0(1 − e^(−∫k dt))
// (T may vary during a run: the integral is accumulated step by step, exact for piecewise-constant T).
// THRESHOLD MODEL: the cross vanishes when the light coming straight through the liquid from the X is
// down to T* = 2 % (below the eye's contrast threshold against the milky veil). Turbidity is
// Beer–Lambert-like in the amount of sulfur: τ = τ*·[S]/S*, τ* = ln(1/T*) = 3.91, so for a fixed
// flask, fill and sight path the X is gone at a fixed amount of sulfur S* — this is why 1/t measures
// the rate. S* = 2.0 mmol/L (an assumption for the drawn ~2 cm path) and k(25 °C) is CALIBRATED so
// that 0.15 mol/L at 25 °C takes 40 s, a typical classroom time; only the ratio S*/k matters for t.
//     t = −ln(1 − S*/c0)/k   (exact)       ≈ S*/(k c0) = S*/rate₀   (S* ≪ c0: under 2 % has reacted)
// so halving c0 doubles t (2.014× exact, 2× in the initial-rate form) and 1/t ∝ k.
//
// COLLISION THEORY. Hard spheres in a Maxwell–Boltzmann gas: weight each pair by its collision flux
// (∝ relative speed g), impact parameter b with b² uniform on [0, d²]; the energy along the line of
// centres is E = ½μg²(1 − b²/d²). Among COLLISIONS (not pairs) E is exactly exponential,
//     P(E ≥ Ea) = exp(−Ea/RT)                     (Atkins' Physical Chemistry, "collision theory";
//                                                   Laidler, Chemical Kinetics: line-of-centres model)
// [CollisionTheory.monteCarlo] checks this against sampled MB velocities. With a steric factor p:
// rate = p·Z·exp(−Ea/RT). At 25 °C and 53 kJ/mol the fraction is 5×10⁻¹⁰, so the molecule window
// (CollisionBox) exaggerates molecular energies ×5: its fraction is exp(−Ea/5RT), the fifth root of
// reality (25 °C: 1.4 %), and every ratio it shows is the fifth root of the real one.

package com.bytedance.chemistrylab.core.rates

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

object Arrhenius {
    /** Molar gas constant, J/(mol·K) (exact since the 2019 SI). */
    const val R = 8.314462618
    const val KELVIN = 273.15
    const val T25 = 298.15

    fun k(a: Double, ea: Double, tK: Double) = a * exp(-ea / (R * tK))

    /** k(t2)/k(t1) for the same A and Ea. */
    fun ratio(ea: Double, t1: Double, t2: Double) = exp(ea / R * (1 / t1 - 1 / t2))

    /** Rate factor from lowering the barrier by [dEa] J/mol at [tK] (same A): exp(ΔEa/RT). */
    fun catalystFactor(dEa: Double, tK: Double) = exp(dEa / (R * tK))

    /** Fraction of collisions whose line-of-centres energy is ≥ [ea] J/mol at [tK]: exp(−Ea/RT). */
    fun fractionAbove(ea: Double, tK: Double) = exp(-ea / (R * tK))

    /** Ratio of mean (or rms) molecular speeds at t2 vs t1: √(t2/t1). */
    fun speedRatio(t1: Double, t2: Double) = sqrt(t2 / t1)
}

object Thiosulfate {
    /** Activation energy, J/mol (representative school value, see header). */
    const val EA = 53.0e3
    /** The molecule window's illustrative catalyst: the barrier lowered to 40 kJ/mol (hypothetical). */
    const val EA_CAT = 40.0e3
    /** Calibration point: 0.15 mol/L thiosulfate at 25 °C hides the X in 40 s. */
    const val C_REF = 0.15
    const val T_REF_S = 40.0
    /** Colloidal sulfur (mol/L) at which the X is lost, and the direct-light threshold that defines it. */
    const val S_STAR = 2.0e-3
    const val T_STAR = 0.02
    val TAU_STAR = ln(1 / T_STAR)

    /** k at 25 °C (1/s), from the calibration; A follows from Ea. */
    val K25 = -ln(1 - S_STAR / C_REF) / T_REF_S
    val A = K25 * exp(EA / (Arrhenius.R * Arrhenius.T25))

    /** Pseudo-first-order rate constant (1/s) at [tK]; [ea] lets a catalyst lower the barrier. */
    fun k(tK: Double, ea: Double = EA) = Arrhenius.k(A, ea, tK)

    /** ∫k dt at which [S] reaches S* for start concentration [c0] (∞ if c0 ≤ S*). */
    fun kStar(c0: Double) = if (c0 <= S_STAR) Double.POSITIVE_INFINITY else -ln(1 - S_STAR / c0)

    /** Exact time for the X to vanish at constant [tK]. */
    fun tObscure(c0: Double, tK: Double, ea: Double = EA) = kStar(c0) / k(tK, ea)

    /** The initial-rate form t = S* ÷ rate₀ (what "1/t ∝ rate" assumes). */
    fun tObscureInitialRate(c0: Double, tK: Double, ea: Double = EA) = S_STAR / (k(tK, ea) * c0)

    /** Colloidal sulfur (mol/L) after accumulated ∫k dt = [kInt]. */
    fun sulfur(c0: Double, kInt: Double) = c0 * (1 - exp(-kInt))

    /** Turbidity optical depth of the sight path for [s] mol/L of sulfur (τ* at S*). */
    fun opticalDepth(s: Double) = TAU_STAR * s / S_STAR

    /** Fraction of the X's light that still comes straight through. */
    fun transmittance(s: Double) = exp(-opticalDepth(s))
}

/**
 * One disappearing-cross run: thiosulfate at [c0] mol/L, acid in excess, from the moment of mixing.
 * [step] integrates ∫k dt exactly for a temperature held over the step and finds the crossing of S*
 * inside the step (no frame-rate dependence).
 */
class XRun(val c0: Double) {
    var t = 0.0; private set
    var kInt = 0.0; private set
    private var tempInt = 0.0
    private val kStar = Thiosulfate.kStar(c0)
    /** Time (s) at which the X vanished, −1 while still visible. */
    var tObscured = -1.0; private set
    /** Mean liquid temperature (K) over the run up to the moment the X vanished. */
    var tMeanK = 0.0; private set

    val sulfur get() = Thiosulfate.sulfur(c0, kInt)
    val transmittance get() = Thiosulfate.transmittance(sulfur)
    val obscured get() = tObscured >= 0.0
    /** Progress toward the threshold, S/S* (1 = the X has just vanished). */
    val progress get() = sulfur / Thiosulfate.S_STAR

    fun step(dt: Double, tK: Double, ea: Double = Thiosulfate.EA) {
        if (dt <= 0.0) return
        val k = Thiosulfate.k(tK, ea)
        val next = kInt + k * dt
        if (tObscured < 0.0 && next >= kStar) {
            val f = (kStar - kInt) / k
            tObscured = t + f
            tMeanK = (tempInt + tK * f) / tObscured
        }
        kInt = next; tempInt += tK * dt; t += dt
    }
}

/** Monte-Carlo check of the collision-theory fraction (pure statistics, no geometry). */
object CollisionTheory {
    class Mc(val weighted: Double, val stdErr: Double, val unweighted: Double, val samples: Int)

    /**
     * Sample [n] pairs with Maxwell–Boltzmann velocities (masses [mA], [mB], kT = 1), weight each by its
     * relative speed g (the collision flux), draw b²/d² uniform and compute E = ½μg²(1 − b²/d²).
     * Returns the flux-weighted fraction with E ≥ [e0] (in units of kT; theory: e^(−e0)), its standard
     * error, and the UNWEIGHTED fraction (pairs, not collisions — the classic mistake, larger tail loss).
     */
    fun monteCarlo(e0: Double, mA: Double, mB: Double, n: Int, seed: Long): Mc {
        val rng = Random(seed)
        val mu = mA * mB / (mA + mB)
        val sa = sqrt(1 / mA); val sb = sqrt(1 / mB)
        var sw = 0.0; var swy = 0.0; var sw2 = 0.0; var sw2y = 0.0; var cnt = 0
        for (i in 0 until n) {
            val gx = rng.nextGaussian() * sb - rng.nextGaussian() * sa
            val gy = rng.nextGaussian() * sb - rng.nextGaussian() * sa
            val gz = rng.nextGaussian() * sb - rng.nextGaussian() * sa
            val g2 = gx * gx + gy * gy + gz * gz
            val g = sqrt(g2)
            val e = 0.5 * mu * g2 * (1 - rng.nextDouble())
            val y = if (e >= e0) 1.0 else 0.0
            sw += g; swy += g * y; sw2 += g * g; sw2y += g * g * y
            if (y > 0) cnt++
        }
        val f = swy / sw
        // variance of a ratio estimator: Σ w²(y − f)² / (Σw)²
        val v = (sw2y * (1 - f) * (1 - f) + (sw2 - sw2y) * f * f) / (sw * sw)
        return Mc(f, sqrt(v), cnt.toDouble() / n, n)
    }
}
