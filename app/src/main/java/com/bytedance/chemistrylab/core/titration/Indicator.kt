// Indicator — the colour of the flask, COMPUTED from the indicator's two forms, pure Kotlin.
//
// An indicator is itself a weak acid HIn ⇌ H⁺ + In⁻ whose two forms absorb differently. At a given pH
// the base form is the fraction f = 1/(1 + 10^(pKa − pH)) of it (the same Henderson relation as any weak
// acid), and the solution's absorbance is the Beer–Lambert sum of both forms:
//     A(λ) = [ (1 − f)·ε_HIn(λ) + f·ε_In(λ) ] · c·ℓ,    T(λ) = 10^(−A(λ))
// T(λ) is turned into a colour with the CIE 1931 observer under equal-energy white (core/optics/Spectrum,
// white-balanced: T ≡ 1 gives exactly sRGB (1, 1, 1)), i.e. the colour seen against the white tile.
//
// Absorption bands are modelled as Gaussians at the published band maxima with published molar
// absorptivities (rounded; e.g. Sabnis, Handbook of Acid-Base Indicators, CRC 2008; pKa within the
// literature spread): phenolphthalein In²⁻ 553 nm (ε ≈ 3.0×10⁴ L/(mol·cm)) + its near-UV band at 374 nm, HIn colourless, pKa 9.5;
// methyl orange HIn⁺ 507 nm (4.5×10⁴, red) / In⁻ 464 nm (2.7×10⁴, yellow), pKa 3.46; bromothymol blue
// HIn⁻ 432 nm (1.6×10⁴, yellow) / In²⁻ 617 nm (3.8×10⁴, blue), pKa 7.10. Amounts = the usual drops of a
// 0.1 % solution into 25 mL (1.25 / 2.5 / 0.64 ×10⁻⁵ mol/L). Seen from above against the white tile the
// colour depends on moles per unit AREA (c·ℓ), which dilution by the titrant does not change; the display
// column is that of the starting solution through ℓ = 5 cm.
//
// TRANSITION RANGE, computed: the colour is "visibly different" from a reference when their OKLab
// distance exceeds ΔE = 0.05 (≈ 2–3 just-noticeable differences — ONE threshold for all three). The
// range runs from the first pH whose colour differs from the pure acid form to the last that still
// differs from the pure base form. Out of the model come 8.3–10.0 (phenolphthalein), 3.0–4.3 (methyl
// orange) and 6.2–7.6 (bromothymol blue) — the tabulated 8.2–10.0, 3.1–4.4, 6.0–7.6 (IndicatorTest).
// The asymmetries (methyl orange starts changing only 0.4 below its pKa) are not put in by hand: they
// come from the forms' different intensities.
//
// ENDPOINT (what a student calls "it turned"): phenolphthalein, a one-colour indicator, at the first
// visible pink (the start of its range); the two-colour indicators at their intermediate colour (orange,
// green), where the colour is half way (in OKLab distance) between the two forms.

package com.bytedance.chemistrylab.core.titration

import com.bytedance.chemistrylab.core.optics.Spectrum
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** One Gaussian absorption band: centre (nm), peak molar absorptivity (L mol⁻¹ cm⁻¹), σ (nm). */
class Band(val nm: Double, val eps: Double, val sigma: Double) {
    fun at(l: Double) = eps * exp(-0.5 * ((l - nm) / sigma).pow(2))
}

enum class Indicator(
    val pKa: Double,
    val acidBands: List<Band>,
    val baseBands: List<Band>,
    /** mol/L in the starting 25 mL. */
    val conc: Double,
    /** Textbook transition range (for the tests and the readout's comparison only). */
    val tabLo: Double, val tabHi: Double,
    /** One-colour indicator (colourless acid form). */
    val oneColour: Boolean,
) {
    PHENOLPHTHALEIN(9.50, emptyList(), listOf(Band(553.0, 3.0e4, 28.0), Band(374.0, 1.5e4, 24.0)), 1.25e-5, 8.2, 10.0, true),
    METHYL_ORANGE(3.46, listOf(Band(507.0, 4.5e4, 38.0)), listOf(Band(464.0, 2.7e4, 47.0)), 2.5e-5, 3.1, 4.4, false),
    BROMOTHYMOL_BLUE(7.10, listOf(Band(432.0, 1.6e4, 34.0)), listOf(Band(617.0, 3.8e4, 38.0)), 0.64e-5, 6.0, 7.6, false);
}

object IndicatorOptics {
    /** Display column through the solution (cm). */
    const val COLUMN_CM = 5.0
    /** "Visibly different" in OKLab. */
    const val DELTA_E = 0.05

    /** Fraction of the indicator in its base form at [pH]. */
    fun baseFraction(ind: Indicator, pH: Double) = 1.0 / (1.0 + 10.0.pow(ind.pKa - pH))

    // per indicator: absorbance spectra of the pure forms on Spectrum.GRID (per unit c·ℓ)
    private val epsAcid = HashMap<Indicator, DoubleArray>()
    private val epsBase = HashMap<Indicator, DoubleArray>()
    private fun spectra(ind: Indicator): Pair<DoubleArray, DoubleArray> = synchronized(this) {
        val a = epsAcid.getOrPut(ind) { DoubleArray(Spectrum.GRID.size) { k -> ind.acidBands.sumOf { it.at(Spectrum.GRID[k]) } } }
        val b = epsBase.getOrPut(ind) { DoubleArray(Spectrum.GRID.size) { k -> ind.baseBands.sumOf { it.at(Spectrum.GRID[k]) } } }
        a to b
    }
    private val W: Array<DoubleArray> by lazy { Array(Spectrum.GRID.size) { Spectrum.weights(Spectrum.GRID[it]) } }

    /**
     * Linear-sRGB colour (0..1, white = 1) of the solution against white light, with base fraction [f]
     * and the column scaled by [column] (1 = the flask; a thin wisp of a drop is less).
     */
    fun colour(ind: Indicator, f: Double, column: Double = 1.0, out: DoubleArray = DoubleArray(3)): DoubleArray {
        val (ea, eb) = spectra(ind)
        val cl = ind.conc * COLUMN_CM * column
        out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
        for (k in Spectrum.GRID.indices) {
            val t = 10.0.pow(-((1 - f) * ea[k] + f * eb[k]) * cl)
            val w = W[k]
            out[0] += w[0] * t; out[1] += w[1] * t; out[2] += w[2] * t
        }
        for (c in 0..2) out[c] = out[c].coerceIn(0.0, 1.0)
        return out
    }

    fun colourAtPH(ind: Indicator, pH: Double, column: Double = 1.0) = colour(ind, baseFraction(ind, pH), column)

    /** OKLab (Ottosson 2020) of a linear-sRGB colour. */
    fun oklab(c: DoubleArray): DoubleArray {
        val l = cbrt(0.4122214708 * c[0] + 0.5363325363 * c[1] + 0.0514459929 * c[2])
        val m = cbrt(0.2119034982 * c[0] + 0.6806995451 * c[1] + 0.1073969566 * c[2])
        val s = cbrt(0.0883024619 * c[0] + 0.2817188376 * c[1] + 0.6299787005 * c[2])
        return doubleArrayOf(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
    }

    fun deltaE(a: DoubleArray, b: DoubleArray): Double {
        val p = oklab(a); val q = oklab(b)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    /** Pure acid-form and base-form colours. */
    fun acidColour(ind: Indicator) = colour(ind, 0.0)
    fun baseColour(ind: Indicator) = colour(ind, 1.0)

    /** How far (0..1, OKLab distance) the colour at [pH] has moved from the acid form toward the base form. */
    fun progress(ind: Indicator, pH: Double): Double {
        val a = acidColour(ind); val b = baseColour(ind)
        return (deltaE(colourAtPH(ind, pH), a) / deltaE(a, b)).coerceIn(0.0, 1.0)
    }

    private val rangeCache = HashMap<Indicator, DoubleArray>()

    /** Computed visual transition range [lo, hi] (pH), and the endpoint pH, cached: (lo, hi, end). */
    fun ranges(ind: Indicator): DoubleArray = synchronized(rangeCache) {
        rangeCache.getOrPut(ind) {
            val a = acidColour(ind); val b = baseColour(ind); val total = deltaE(a, b)
            // first pH visibly different from the acid form / last visibly different from the base form
            val lo = bisectPH(ind) { deltaE(colourAtPH(ind, it), a) >= DELTA_E }
            val hi = bisectPH(ind) { deltaE(colourAtPH(ind, it), b) < DELTA_E }
            val end = if (ind.oneColour) lo else bisectPH(ind) { deltaE(colourAtPH(ind, it), a) >= 0.5 * total }
            doubleArrayOf(lo, hi, end)
        }
    }
    fun transitionRange(ind: Indicator) = ranges(ind).let { it[0] to it[1] }
    fun endpointPH(ind: Indicator) = ranges(ind)[2]

    /** Smallest pH in [pKa − 4, pKa + 4] where [test] becomes true (it is monotone in pH). */
    private fun bisectPH(ind: Indicator, test: (Double) -> Boolean): Double {
        var lo = ind.pKa - 4; var hi = ind.pKa + 4
        repeat(50) { val m = 0.5 * (lo + hi); if (test(m)) hi = m else lo = m }
        return 0.5 * (lo + hi)
    }

    /** 0 = acid colour, 1 = in transition (first half), 2 = in transition (second half), 3 = base colour. */
    fun stage(ind: Indicator, pH: Double): Int {
        val r = ranges(ind)
        return when {
            pH < r[0] -> 0
            pH >= r[1] -> 3
            ind.oneColour -> if (progress(ind, pH) < 0.35) 1 else 2
            pH < r[2] -> 1
            else -> 2
        }
    }
}
