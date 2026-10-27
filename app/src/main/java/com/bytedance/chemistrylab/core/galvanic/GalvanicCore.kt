// GalvanicCore — a two-beaker galvanic cell (Daniell, 1836) joined by a KNO₃ salt bridge, pure Kotlin.
//
// HALF CELLS. Each beaker is a metal strip M in a solution of its own ions M^{n+} (sulfate, or nitrate
// where the sulfate is insoluble/unusual: Pb(NO₃)₂, AgNO₃). The electrode potential is Nernst's
//     φ = E°(M^{n+}/M) + (RT / nF) ln a(M^{n+}),        a ≈ c / (1 mol/L)   (ideal solution, metal a = 1)
// with the standard reduction potentials of the CRC Handbook of Chemistry and Physics, "Electrochemical
// Series" (97th ed., 2016; the same values as Bard, Parsons & Jordan, Standard Potentials in Aqueous
// Solution, 1985): Zn −0.7618, Cu +0.3419, Fe −0.447, Pb −0.1262, Ag +0.7996, Mg −2.372 V vs SHE.
// The electrode with the HIGHER φ is the cathode (+, reduction), the other the anode (−, oxidation);
// the cell EMF is E = φ(+) − φ(−) = E° − (RT/NF) ln Q for the overall reaction with N = lcm(n₋, n₊)
// electrons, Q = a(anode ion)^{N/n₋} / a(cathode ion)^{N/n₊}. Zn|Cu: E° = 0.3419 + 0.7618 = 1.1037 V.
// Mg: −2.372 V is the thermodynamic value; a real Mg strip in water is filmed and corrodes and gives
// about 1 V less. Activities are taken equal to molarities (a real 1 M CuSO₄ has γ± ≈ 0.04; the
// textbook Daniell cell still reads ≈ 1.10 V because the two γ largely cancel).
//
// CIRCUIT (stated model values): EMF E with internal resistance r = 80 Ω (mostly the salt bridge:
// a 20 cm U-tube of 1 M KNO₃, κ ≈ 10 S/m, 2 cm bore → 64 Ω, plus cotton plugs and the two solutions),
// a high-impedance voltmeter R_V = 10 MΩ across the terminals, and — through the knife switch — a
// small fan (micro motor, taken as a 100 Ω load) in series with the ammeter (0 Ω). Then
//     R_ext = R_V ∥ R_load (switch closed) or R_V (open),   I_cell = E / (r + R_ext),
//     U = E · R_ext / (r + R_ext) (the voltmeter: the EMF when open, E − I·r under load).
// No salt bridge (or a strip dissolved through) = no closed loop: I = 0 and the voltmeter reads 0.
//
// CHEMISTRY OF THE CHARGE (Faraday). A charge dq through the cell oxidises dq/(n₋F) mol of the anode
// metal into its beaker and reduces dq/(n₊F) mol of cathode ions onto the cathode:
//     Δm = q · M / (nF)   (1 A·h: Zn −1.220 g, Cu +1.186 g).
// The salt bridge closes the loop: per mole of electrons in the wire one mole of NO₃⁻ enters the anode
// beaker and one mole of K⁺ the cathode beaker, so both stay neutral (textbook idealisation of the
// liquid junctions; in reality the split follows the transference numbers, t(K⁺) ≈ t(NO₃⁻) ≈ 0.5 —
// why KNO₃ is the bridge salt — and KNO₃, not KCl, because Cl⁻ would precipitate AgCl and PbCl₂).
// Each beaker holds 100 mL; each strip is 2 cm × 7 cm wet × 1 mm (1.4 cm³ of metal below the
// waterline, densities CRC). The cell is integrated with backward Euler in ln c (exactly stable, it
// relaxes to E = 0, i.e. Q = K, and resolves [Cu²⁺] down to 10⁻³⁷ M).

package com.bytedance.chemistrylab.core.galvanic

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

// ------------------------------------------------------------------------------------------- data

/** Counter-anion of a half cell's salt: charge number (negative). */
enum class Anion(val formula: String, val charge: Int) { SULFATE("SO₄²⁻", -2), NITRATE("NO₃⁻", -1) }

/**
 * The metals of the table, in order of their standard reduction potential (the activity series).
 * [e0] V vs SHE (CRC 2016 / Bard 1985), [molarMass] g/mol (IUPAC 2021 standard atomic weights),
 * [density] g/cm³ (CRC), [salt] the solution the strip stands in.
 */
enum class Metal(val symbol: String, val ion: String, val n: Int, val e0: Double, val molarMass: Double,
                 val density: Double, val salt: String, val anion: Anion) {
    MG("Mg", "Mg²⁺", 2, -2.372, 24.305, 1.738, "MgSO₄", Anion.SULFATE),
    ZN("Zn", "Zn²⁺", 2, -0.7618, 65.38, 7.134, "ZnSO₄", Anion.SULFATE),
    FE("Fe", "Fe²⁺", 2, -0.447, 55.845, 7.874, "FeSO₄", Anion.SULFATE),
    PB("Pb", "Pb²⁺", 2, -0.1262, 207.2, 11.34, "Pb(NO₃)₂", Anion.NITRATE),
    CU("Cu", "Cu²⁺", 2, 0.3419, 63.546, 8.96, "CuSO₄", Anion.SULFATE),
    AG("Ag", "Ag⁺", 1, 0.7996, 107.8682, 10.49, "AgNO₃", Anion.NITRATE);

    /** The next metal of the series (wraps): what a pinch on the electrode swaps in. */
    fun next(): Metal = entries[(ordinal + 1) % entries.size]
}

object Galv {
    /** Faraday constant (C/mol) = N_A e, exact in the 2019 SI. */
    const val F = 96485.33212
    /** Molar gas constant (J/(mol K)) = N_A k, exact in the 2019 SI. */
    const val R = 8.314462618
    const val T25 = 298.15
    private val LN10 = ln(10.0)

    /** Nernst slope (V per decade of activity) for [n] electrons: 59.16 mV / n at 25 °C. */
    fun slope(n: Int, tK: Double = T25) = R * tK * LN10 / (n * F)
    /** Electrode potential of M | M^{n+} (c mol/L), V vs SHE. */
    fun potential(m: Metal, c: Double, tK: Double = T25) = m.e0 + R * tK / (m.n * F) * ln(c)
    /** Standard cell EMF E° = E°(cathode) − E°(anode). */
    fun standardEmf(anode: Metal, cathode: Metal) = cathode.e0 - anode.e0
    /** Electrons transferred per formula of the overall reaction: lcm(n₋, n₊). */
    fun electrons(anode: Metal, cathode: Metal): Int { var a = anode.n; var b = cathode.n; while (b != 0) { val t = a % b; a = b; b = t }; return anode.n / a * cathode.n }
    /** Reaction quotient Q = [anode ion]^{N/n₋} / [cathode ion]^{N/n₊}. */
    fun quotient(anode: Metal, cAnode: Double, cathode: Metal, cCathode: Double): Double {
        val n = electrons(anode, cathode)
        return cAnode.pow(n / anode.n) / cCathode.pow(n / cathode.n)
    }
    /** The Nernst equation of the whole cell: E = E° − (RT/NF) ln Q. */
    fun emf(anode: Metal, cAnode: Double, cathode: Metal, cCathode: Double, tK: Double = T25): Double =
        standardEmf(anode, cathode) - R * tK / (electrons(anode, cathode) * F) * ln(quotient(anode, cAnode, cathode, cCathode))
    /** log₁₀ of the equilibrium constant, N F E° / (RT ln 10): where a discharged cell ends (E = 0). */
    fun log10K(anode: Metal, cathode: Metal, tK: Double = T25) = electrons(anode, cathode) * F * standardEmf(anode, cathode) / (R * tK * LN10)
    /** Grams of [m] dissolved or deposited per coulomb: M / (nF). */
    fun gramsPerCoulomb(m: Metal) = m.molarMass / (m.n * F)

    /** Wet strip below the waterline: 2.0 cm × 7.0 cm × 0.10 cm. */
    const val WET_CM3 = 2.0 * 7.0 * 0.10
    /** Solution in each beaker (L). */
    const val VOLUME_L = 0.100
}

// ------------------------------------------------------------------------------------------- one beaker

/** One beaker: a strip of [metal] (its wet part, [mass] g) in [c] mol/L of its ion. */
class HalfCell(metal: Metal, c: Double, val volumeL: Double = Galv.VOLUME_L) {
    var metal = metal; private set
    var c = c; private set
    var mass0 = 0.0; private set
    var mass = 0.0; private set
    /** Counter-anions of the salt (mol), K⁺ and NO₃⁻ delivered by the salt bridge (mol). */
    var anionMol = 0.0; private set
    var kIn = 0.0; private set
    var no3In = 0.0; private set

    init { replace(metal, c) }

    /** A fresh strip of [m] in fresh solution of its salt. */
    fun replace(m: Metal, c: Double) { metal = m; mass0 = Galv.WET_CM3 * m.density; mass = mass0; fillSolution(c) }
    /** Pour in fresh solution at [c] mol/L (the strip stays as it is). */
    fun fillSolution(c: Double) { this.c = c; anionMol = metal.n * c * volumeL / -metal.anion.charge; kIn = 0.0; no3In = 0.0 }

    fun potential(tK: Double = Galv.T25) = Galv.potential(metal, c, tK)
    /** Mass change of the wet strip since it was put in (g): < 0 dissolving, > 0 plated. */
    val dMass get() = mass - mass0
    /** Net ionic charge in the beaker (mol of e): stays 0 (electroneutrality). */
    val netCharge get() = metal.n * c * volumeL + kIn + metal.anion.charge * anionMol - no3In

    internal fun oxidised(dq: Double) {             // anode: M → M^{n+} + n e⁻, NO₃⁻ in from the bridge
        c += dq / (metal.n * Galv.F * volumeL)
        mass = (mass - dq * Galv.gramsPerCoulomb(metal)).let { if (it < 1e-12) 0.0 else it }   // dissolved through
        no3In += dq / Galv.F
    }
    internal fun reduced(dq: Double, cNew: Double) { // cathode: M^{n+} + n e⁻ → M, K⁺ in from the bridge
        c = cNew
        mass += dq * Galv.gramsPerCoulomb(metal)
        kIn += dq / Galv.F
    }
}

// ------------------------------------------------------------------------------------------- the cell

/**
 * Left and right beakers, the salt bridge, the voltmeter, the switch and the fan. Lab time [t] (s),
 * charge [q] (C) that has passed through the cell. The circuit is solved instantaneously (no
 * capacitance); concentrations and masses integrate with [step].
 */
class GalvanicCell(val rInt: Double = 80.0, val rLoad: Double = 100.0, val rMeter: Double = 1.0e7, val tK: Double = Galv.T25) {
    val left = HalfCell(Metal.ZN, 1.0)
    val right = HalfCell(Metal.CU, 1.0)
    var bridgeIn = true
    var switchClosed = true
    var t = 0.0; private set
    var q = 0.0; private set

    fun half(k: Int) = if (k == 0) left else right

    fun reset(lm: Metal = Metal.ZN, cl: Double = 1.0, rm: Metal = Metal.CU, cr: Double = 1.0) {
        left.replace(lm, cl); right.replace(rm, cr); bridgeIn = true; switchClosed = true; t = 0.0; q = 0.0
    }

    /** φ(right) − φ(left), V: > 0 means the right strip is the + pole. */
    fun emf() = right.potential(tK) - left.potential(tK)
    /** +1 right is the cathode (electrons flow left → right in the wire), −1 the reverse, 0 neither. */
    fun polarity(): Int { val e = emf(); return if (e > 1e-12) 1 else if (e < -1e-12) -1 else 0 }
    fun anode() = if (polarity() >= 0) left else right
    fun cathode() = if (polarity() >= 0) right else left
    /** Standard EMF of the present pair (anode/cathode from the actual potentials). */
    fun standardEmf() = Galv.standardEmf(anode().metal, cathode().metal)

    /** Both strips still reach into their solutions. */
    val intact get() = left.mass > 0.0 && right.mass > 0.0
    /** Ions can migrate and both electrodes exist: the inner circuit is closed. */
    val closedLoop get() = bridgeIn && intact
    fun rExternal() = if (switchClosed) rLoad * rMeter / (rLoad + rMeter) else rMeter
    /** Current through the cell (A, ≥ 0): load + voltmeter. */
    fun cellCurrent() = if (!closedLoop) 0.0 else abs(emf()) / (rInt + rExternal())
    /** Voltmeter reading (V, signed right − left): E when open, E − I r under load, 0 with no loop. */
    fun meterVolts() = if (!closedLoop) 0.0 else emf() * rExternal() / (rInt + rExternal())
    /** Ammeter (in series with the fan) reading, A. */
    fun loadCurrent() = if (switchClosed) abs(meterVolts()) / rLoad else 0.0
    fun loadPower() = loadCurrent() * loadCurrent() * rLoad

    /** Advance [dt] s of lab time. */
    fun step(dt: Double) {
        if (dt <= 0.0) return
        t += dt
        if (!closedLoop) return
        val e0 = emf()
        if (abs(e0) < 1e-15) return
        val an = if (e0 > 0) left else right
        val ca = if (e0 > 0) right else left
        val rt = rInt + rExternal()
        // substeps: the explicit estimate of the EMF change, |dE/dq| · I · dt, kept under 5 mV per substep
        // (capped: backward Euler is stable for any step, it only smooths the final collapse)
        val kC = ca.metal.n * Galv.F * ca.volumeL; val kA = an.metal.n * Galv.F * an.volumeL
        val dEdq = Galv.R * tK / Galv.F * (1.0 / (ca.metal.n * kC * ca.c) + 1.0 / (an.metal.n * kA * an.c))
        val k = ceil(abs(e0) / rt * dt * dEdq / 0.005).coerceIn(1.0, 24.0).toInt()
        val h = dt / k
        repeat(k) { if (!stepImplicit(an, ca, h, rt)) return }
    }

    /**
     * One backward-Euler step dq = h · E(after) / R_tot, solved by bisection in ln c(cathode ion)
     * (E falls monotonically as the charge passes). False when the anode strip has dissolved.
     */
    private fun stepImplicit(an: HalfCell, ca: HalfCell, h: Double, rt: Double): Boolean {
        val cc0 = ca.c; val ca0 = an.c
        val kC = ca.metal.n * Galv.F * ca.volumeL; val kA = an.metal.n * Galv.F * an.volumeL
        fun dqOf(lnc: Double) = (cc0 - exp(lnc)) * kC
        fun g(lnc: Double): Double {
            val dq = dqOf(lnc)
            val e = Galv.potential(ca.metal, exp(lnc), tK) - Galv.potential(an.metal, ca0 + dq / kA, tK)
            return dq - h * e / rt
        }
        if (!(cc0 > 0.0) || an.mass <= 0.0) return false
        var hi = ln(cc0)                                    // no charge: g < 0
        if (g(hi) >= 0.0) return true                       // already at equilibrium
        val dqCapA = an.mass * an.metal.n * Galv.F / an.metal.molarMass
        val cByAnode = cc0 - dqCapA / kC
        var lo = ln(maxOf(cByAnode, 1e-300))
        val anodeBinds = g(lo) < 0.0                        // the strip dissolves before E reaches h-equilibrium
        if (!anodeBinds) repeat(64) { val mid = 0.5 * (lo + hi); if (g(mid) > 0.0) lo = mid else hi = mid }
        val lnc = if (anodeBinds) lo else hi
        val cNew = exp(lnc)
        val dq = if (anodeBinds && cByAnode > 0.0) dqCapA else (cc0 - cNew) * kC
        ca.reduced(dq, cNew)
        an.oxidised(dq)
        q += dq
        return !anodeBinds
    }
}
