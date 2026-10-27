// MicroScale — how many of each particle the molecule window shows, from the flask's exact state.
//
// TWO SCALES, both stated on screen:
//   * H₃O⁺ and OH⁻ on a LOG scale: one icon per half decade above 10⁻⁸ mol/L,
//         n = round(2·(log10 c + 8)),  i.e. n(H₃O⁺) = round(2·(8 − pH)), n(OH⁻) = round(2·(pH − 6)).
//     A linear scale cannot show a titration: from pH 1 to pH 13 [H⁺] changes 10¹² times. On the log
//     scale the H₃O⁺ count IS the pH curve turned upside down (14 at pH 1, 2 + 2 at pH 7, 0 by pH 8.5):
//     it falls slowly while acid is in excess and collapses at the equivalence point.
//   * The spectators and the weak-acid family LINEARLY (real ratios): 8 icons = the acid taken,
//     Cl⁻ = 8 (HCl), Na⁺ = 8·n(NaOH)/n(acid) (8 at the equivalence point), and for acetic acid
//     CH₃COOH + CH₃COO⁻ = 8 split by the exact degree of dissociation — 8 undissociated molecules at the
//     start (1.3 % dissociated: a weak acid), 4 + 4 at half-equivalence (the buffer, pH = pKa).

package com.bytedance.chemistrylab.core.titration

import kotlin.math.log10
import kotlin.math.roundToInt

data class MicroTargets(val h3o: Int, val oh: Int, val na: Int, val cl: Int, val ha: Int, val a: Int)

object MicroScale {
    /** Icons per decade of concentration on the log scale. */
    const val PER_DECADE = 2
    /** Concentration (log10 mol/L) that maps to zero icons. */
    const val FLOOR_LOG = -8.0
    const val ION_MAX = 14
    /** Icons for "all the acid". */
    const val ACID_ICONS = 8
    const val NA_MAX = 16

    fun logIcons(conc: Double): Int = if (conc <= 0.0) 0 else (PER_DECADE * (log10(conc) - FLOOR_LOG)).roundToInt().coerceIn(0, ION_MAX)

    fun targets(t: Titration, vbMl: Double): MicroTargets {
        val h = t.h(vbMl)
        val nAcid = t.ca * t.vaMl; val nNa = t.cb * vbMl
        val na = (ACID_ICONS * nNa / nAcid).roundToInt().coerceIn(0, NA_MAX)
        val cl = if (t.acid.strong) ACID_ICONS else 0
        val ha = if (t.acid.strong) 0 else (ACID_ICONS * (1 - AcidBase.alphaA(t.acid.ka, h))).roundToInt().coerceIn(0, ACID_ICONS)
        val a = if (t.acid.strong) 0 else ACID_ICONS - ha
        return MicroTargets(logIcons(h), logIcons(AcidBase.KW / h), na, cl, ha, a)
    }
}
