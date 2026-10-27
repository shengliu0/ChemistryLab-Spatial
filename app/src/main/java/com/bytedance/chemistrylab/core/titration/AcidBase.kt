// AcidBase — exact pH of an acid being titrated with NaOH, pure Kotlin.
//
// THE MODEL. A monoprotic acid HA (total C_A, dissociation constant Ka; HCl is the limit Ka → ∞) and
// sodium (C_Na, from the NaOH added) in water at 25 °C. The full charge balance, nothing dropped:
//     [H⁺] + [Na⁺] = [OH⁻] + [A⁻],   [OH⁻] = Kw/[H⁺],   [A⁻] = C_A·Ka/(Ka + [H⁺])
// The residual f(h) = h + C_Na − Kw/h − C_A·Ka/(Ka + h) is STRICTLY increasing in h (every term
// grows with h), so it has exactly one positive root. It is found by bisection on log10[H⁺] over
// [10⁻¹⁶, 10] — always bracketed, no starting guess, no Newton overshoot near the equivalence point
// where the curve is vertical — down to adjacent doubles, so the charge balance closes to ~1e-17 M.
// No approximation (no "[H⁺] ≈ √(KaC)", no Henderson–Hasselbalch): those are what the TESTS compare to.
//
// VOLUMES ARE EXACT: C_A = Ca·Va/(Va + Vb), C_Na = Cb·Vb/(Va + Vb) — the flask is diluted by every drop.
// Ideal solution (activity coefficients 1), no CO₂, the indicator's own acid ignored (2 drops of a 0.1 %
// solution ≈ 0.3 µmol, i.e. 0.003 mL of titrant: below the drop size).
//
// DATA. Kw = 1.0×10⁻¹⁴ at 25 °C (CRC Handbook: pKw = 13.995). Acetic acid Ka = 1.75×10⁻⁵ at 25 °C
// (Harned & Ehlers, J. Am. Chem. Soc. 55, 652 (1933): 1.754×10⁻⁵; pKa = 4.757).
//
// THE DROP (plume). A drop of NaOH does not mix at once: it lands as a small parcel that entrains the
// stirred bulk exponentially, v(t) = v_drop·e^{t/τ}, until it fills the flask. The parcel's own pH is
// solved with the same charge balance on (bulk before the drop, entrained) + (the drop). Far from the
// end the bulk acid neutralises the parcel within ~0.3 s; near the end there is so little acid left
// that the parcel stays basic for seconds — the indicator's "flash that fades more and more slowly".

package com.bytedance.chemistrylab.core.titration

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

enum class Acid(val ka: Double) {
    /** Hydrochloric acid: fully dissociated (Ka → ∞). */
    HCL(Double.POSITIVE_INFINITY),
    /** Acetic acid (Harned & Ehlers 1933). */
    ACETIC(AcidBase.KA_ACETIC);

    val strong get() = ka.isInfinite()
    val pKa get() = if (strong) Double.NEGATIVE_INFINITY else -log10(ka)
}

object AcidBase {
    /** Ion product of water at 25 °C, (mol/L)². */
    const val KW = 1.0e-14
    /** Acetic acid at 25 °C. */
    const val KA_ACETIC = 1.75e-5

    /** Fraction of the acid present as A⁻ at [h] mol/L of H⁺ (1 for a strong acid). */
    fun alphaA(ka: Double, h: Double) = if (ka.isInfinite()) 1.0 else ka / (ka + h)

    /** Charge-balance residual [H⁺] + [Na⁺] − [OH⁻] − [A⁻] (mol/L). Zero at the solution. */
    fun residual(h: Double, cAcid: Double, cNa: Double, ka: Double) = h + cNa - KW / h - cAcid * alphaA(ka, h)

    /** Exact [H⁺] (mol/L) for total acid [cAcid], sodium [cNa] (mol/L): bisection on log10[H⁺]. */
    fun solveH(cAcid: Double, cNa: Double, ka: Double): Double {
        require(cAcid >= 0.0 && cNa >= 0.0 && cAcid.isFinite() && cNa.isFinite()) { "concentrations must be finite and ≥ 0" }
        require(ka > 0.0) { "Ka must be > 0" }
        var lo = -16.0; var hi = 1.0                        // f(10⁻¹⁶) < 0 < f(10) for any C ≤ 10 M
        repeat(200) {
            val mid = 0.5 * (lo + hi)
            if (mid <= lo || mid >= hi) return 10.0.pow(mid)
            if (residual(10.0.pow(mid), cAcid, cNa, ka) > 0.0) hi = mid else lo = mid
        }
        return 10.0.pow(0.5 * (lo + hi))
    }

    fun pH(cAcid: Double, cNa: Double, ka: Double) = -log10(solveH(cAcid, cNa, ka))
}

/**
 * [acid] at [ca] mol/L, [vaMl] mL in the flask, titrated with NaOH at [cb] mol/L. Volumes in mL.
 * The defaults are the school experiment: 25.00 mL of 0.100 mol/L acid, 0.100 mol/L NaOH.
 */
class Titration(val acid: Acid, val ca: Double = 0.100, val vaMl: Double = 25.00, val cb: Double = 0.100) {
    /** Equivalence volume: n(NaOH) = n(acid). */
    val veqMl = ca * vaMl / cb

    fun cAcid(vbMl: Double) = ca * vaMl / (vaMl + vbMl)
    fun cNa(vbMl: Double) = cb * vbMl / (vaMl + vbMl)
    fun h(vbMl: Double) = AcidBase.solveH(cAcid(vbMl), cNa(vbMl), acid.ka)
    fun pH(vbMl: Double) = -log10(h(vbMl))
    fun residual(vbMl: Double) = AcidBase.residual(h(vbMl), cAcid(vbMl), cNa(vbMl), acid.ka)
    /** Undissociated HA as a fraction of all the acid (0 for HCl). */
    fun fractionHA(vbMl: Double) = 1.0 - AcidBase.alphaA(acid.ka, h(vbMl))

    /** Burette volume at which the flask reaches [pHTarget] (pH(V) is increasing): bisection on V. */
    fun volumeAt(pHTarget: Double, vMaxMl: Double = 50.0): Double {
        var lo = 0.0; var hi = vMaxMl
        if (pH(lo) >= pHTarget) return 0.0
        if (pH(hi) < pHTarget) return vMaxMl
        repeat(80) { val m = 0.5 * (lo + hi); if (pH(m) >= pHTarget) hi = m else lo = m }
        return 0.5 * (lo + hi)
    }

    /**
     * pH of a parcel of [nDrops] drops (each [dropMl]) that landed when the flask held [vbBeforeMl] of
     * titrant, [ageS] seconds later, mixing with e-folding time [tauS]: the parcel has entrained bulk
     * (composition before the drop) up to v = v_drop·e^{age/τ}, at most the whole flask.
     */
    fun parcelPH(vbBeforeMl: Double, nDrops: Int, ageS: Double, tauS: Double, dropMl: Double = DROP_ML): Double {
        val vd = nDrops * dropMl
        val vTot = vaMl + vbBeforeMl + vd
        val vp = minOf(vTot, vd * exp(maxOf(0.0, ageS) / tauS))
        val ve = vp - vd
        val acidMol = cAcid(vbBeforeMl) * ve
        val naMol = cNa(vbBeforeMl) * ve + cb * vd
        return AcidBase.pH(acidMol / vp, naMol / vp, acid.ka)
    }

    /** How long a fresh drop's parcel stays at or above [pHShow] (s): the indicator "flash" life. */
    fun flashLife(vbBeforeMl: Double, pHShow: Double, tauS: Double, nDrops: Int = 1): Double {
        val vd = nDrops * DROP_ML
        val tFull = tauS * kotlin.math.ln((vaMl + vbBeforeMl + vd) / vd)
        if (parcelPH(vbBeforeMl, nDrops, tFull, tauS) >= pHShow) return Double.POSITIVE_INFINITY   // the bulk itself has turned
        if (parcelPH(vbBeforeMl, nDrops, 0.0, tauS) < pHShow) return 0.0
        var lo = 0.0; var hi = tFull
        repeat(60) { val m = 0.5 * (lo + hi); if (parcelPH(vbBeforeMl, nDrops, m, tauS) >= pHShow) lo = m else hi = m }
        return 0.5 * (lo + hi)
    }

    companion object {
        /** One drop from a burette tip: 0.05 mL (20 drops per mL, the usual school figure). */
        const val DROP_ML = 0.05
        /** Burette capacity, mL. */
        const val CAPACITY_ML = 50.0
    }
}
