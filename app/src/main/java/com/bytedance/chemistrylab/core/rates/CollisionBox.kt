// CollisionBox — the molecule window: a hard-sphere gas of reactants A and B (plus products) in a
// cube, with collision theory acted out one collision at a time. Pure Kotlin.
//
// DYNAMICS. Smooth hard spheres, elastic collisions, specular walls. Collisions are found EXACTLY
// inside each sub-step (ballistic flights: solve |r + g t| = d for the contact time), processed in
// time order, so the contact normal is the true line of centres — no grazing collisions are lost or
// biased by overlap (either would distort the energetic tail). Impulse along the line of centres:
// momentum and kinetic energy are conserved to rounding (RatesCoreTest). An Andersen thermostat — each
// molecule re-drawn from Maxwell–Boltzmann about once a second, the invisible solvent jostling it —
// holds T; a reset starts at exactly 3/2·NkT (80 molecules drawn at random are easily 5–10 % off);
// changing T rescales all velocities at once (√T).
//
// TAIL DEPLETION (Prigogine–Xhrouet). Reacting removes the fastest reactants, so their energetic tail
// runs a few % short of Maxwell–Boltzmann while reactions are frequent (−3…−8 % here, multi-seed
// RatesCoreTest); the solvent thermostat refills it. Without it (no thermostat) the deficit was −12 %.
//
// CHEMISTRY. For every A–B collision the energy along the line of centres E = ½μ(g·n)² is converted
// to J/mol and compared with the barrier. The energy scale is exaggerated ×[energyScale] (5): at the
// real 53 kJ/mol only 5×10⁻¹⁰ of collisions would make it (about one a year in a box this size),
// so here the fraction is exp(−Ea/(5RT)) — the fifth root of reality, 1.4 % at 25 °C. STERIC FACTOR:
// A carries a reactive atom on one side (unit axis u); the collision only reacts if B arrives on that
// half (u·n ≥ 0), so p = 1/2 for randomly tumbling A. Reaction = identity change (A→C, B→D) with an
// elastic bounce (reaction heat ignored), so the velocity distribution stays Maxwellian. Products
// leave after [productLife] s and fresh reactants come in (a steady "flow" box), keeping the
// composition steady so rates at different T can be compared.

package com.bytedance.chemistrylab.core.rates

import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class CollisionBox(val nA: Int = 40, val nB: Int = 40, seed: Long = 1889L) {
    companion object {
        const val A = 0; const val B = 1; const val C = 2; const val D = 3
        /** Flash kinds: an A–B collision with ≥ half the barrier, enough energy but the wrong side, a reaction. */
        const val F_NEAR = 0; const val F_WRONG_SIDE = 1; const val F_REACT = 2
    }

    // ---------------------------------------------------------------- parameters (display units: m, s)
    var half = 0.175
    var rA = 0.0135
    var rB = 0.0165
    val mA = 1.0
    val mB = 2.0
    /** rms speed of A at 298.15 K on screen (m/s); B is √2 slower (twice the mass). */
    var vRmsA298 = 0.34
    var energyScale = 5.0
    /** Barrier in J/mol (real-equivalent; compared with energyScale × the model's energy). */
    var barrier = Thiosulfate.EA
    var steric = true
    /** false: count energies and flashes but never react (tests the bare collision statistics). */
    var reactive = true
    var productLife = 6.0
    var thermostatRate = 1.0
    var maxSub = 0.006

    val n = nA + nB
    private val rng = Random(seed)
    private val seed0 = seed

    // ---------------------------------------------------------------- state
    val kind = IntArray(n)
    val x = DoubleArray(n * 3)
    val v = DoubleArray(n * 3)
    val q = DoubleArray(n * 4)
    private val w = DoubleArray(n * 3)
    /** Products: seconds left before they leave the box. */
    val life = DoubleArray(n)
    /** Time since the particle (re)appeared (s), for the pop-in. */
    val born = DoubleArray(n)
    var tK = 298.15; private set
    var nAcur = 0; private set
    var nBcur = 0; private set

    // ---------------------------------------------------------------- statistics
    var simTime = 0.0; private set
    var collisionsAB = 0L; private set
    var collisionsAll = 0L; private set
    var aboveAB = 0L; private set
    var reactions = 0L; private set
    var wrongSide = 0L; private set
    /** ∫ nA·nB dt (reactant counts), for rate normalisation. */
    var pairTimeIntegral = 0.0; private set
    /** Smoothed reactions per second (6 s memory). */
    var reactionRate = 0.0; private set
    var collisionRateAB = 0.0; private set

    /** The last [RING] A–B collision energies (J/mol, scaled): the live histogram reads them. */
    val RING = 2400
    val eRing = DoubleArray(RING)
    var eCount = 0; private set
    private var eHead = 0

    // flashes (ring buffer)
    val flashCap = 28
    val fx = DoubleArray(flashCap * 3)
    val fAge = DoubleArray(flashCap) { 1e9 }
    val fKind = IntArray(flashCap)
    val fEnergy = DoubleArray(flashCap)
    private var fHead = 0
    // line of centres of the latest reactions
    val lineCap = 4
    val lineA = DoubleArray(lineCap * 3); val lineB = DoubleArray(lineCap * 3)
    val lineAge = DoubleArray(lineCap) { 1e9 }
    private var lineHead = 0
    // event stream for the energy-profile balls: kind + E/barrier, consumers keep their own cursor
    val evCap = 32
    val evKind = IntArray(evCap)
    val evE = DoubleArray(evCap)
    var evTotal = 0L; private set

    fun radius(i: Int) = if (kind[i] == A || kind[i] == C) rA else rB
    fun mass(i: Int) = if (kind[i] == A || kind[i] == C) mA else mB
    /** kT in display units (mass·(m/s)²). */
    fun kTd() = mA * vRmsA298 * vRmsA298 / 3.0 * tK / 298.15
    /** J/mol per display energy unit (fixed: the gas at T has kT_d ∝ T). */
    val jPerUnit get() = energyScale * Arrhenius.R * 298.15 * 3.0 / (mA * vRmsA298 * vRmsA298)
    /** Theory for this box: fraction of A–B collisions with E ≥ barrier. */
    fun fractionTheory() = exp(-barrier / (energyScale * Arrhenius.R * tK))
    /** Live measurement over the energy ring. */
    fun fractionMeasured(): Double {
        if (eCount == 0) return 0.0
        var c = 0; for (k in 0 until eCount) if (eRing[k] >= barrier) c++
        return c.toDouble() / eCount
    }

    // ================================================================ setup

    fun reset(tKelvin: Double, seed: Long = seed0) {
        tK = tKelvin
        val r = Random(seed)
        for (i in 0 until n) kind[i] = if (i < nA) A else B
        nAcur = nA; nBcur = nB
        for (i in 0 until n) { place(i, r, i); life[i] = 0.0; born[i] = 1.0 }
        // start AT temperature: with only 80 molecules a random draw of velocities is easily 5–10 % off in
        // total energy, which shifts the tail fraction exp(−Ea/kT) by 20 % until the thermostat catches up
        val f = sqrt(1.5 * n * kTd() / kineticEnergy().coerceAtLeast(1e-12))
        for (k in 0 until n * 3) v[k] *= f
        collisionsAB = 0; collisionsAll = 0; aboveAB = 0; reactions = 0; wrongSide = 0; pairTimeIntegral = 0.0
        simTime = 0.0; eCount = 0; eHead = 0
        fAge.fill(1e9); fHead = 0; lineAge.fill(1e9); lineHead = 0
        reactionRate = 0.0; collisionRateAB = 0.0
    }

    /** A random free spot (no overlap with particles 0 until [upTo]), MB velocity, random orientation. */
    private fun place(i: Int, r: Random, upTo: Int) {
        val rad = radius(i)
        var tries = 0
        while (true) {
            for (a in 0 until 3) x[i * 3 + a] = (r.nextDouble() * 2 - 1) * (half - rad)
            var ok = true
            for (j in 0 until upTo) {
                if (j == i) continue
                val dx = x[j * 3] - x[i * 3]; val dy = x[j * 3 + 1] - x[i * 3 + 1]; val dz = x[j * 3 + 2] - x[i * 3 + 2]
                val d = rad + radius(j) + 0.002
                if (dx * dx + dy * dy + dz * dz < d * d) { ok = false; break }
            }
            if (ok || ++tries > 60) break
        }
        thermalize(i, r)
        for (a in 0 until 4) q[i * 4 + a] = r.nextGaussian()
        val l = sqrt(q[i * 4] * q[i * 4] + q[i * 4 + 1] * q[i * 4 + 1] + q[i * 4 + 2] * q[i * 4 + 2] + q[i * 4 + 3] * q[i * 4 + 3]).coerceAtLeast(1e-9)
        for (a in 0 until 4) q[i * 4 + a] /= l
    }

    private fun thermalize(i: Int, r: Random) {
        val s = sqrt(kTd() / mass(i))
        for (a in 0 until 3) v[i * 3 + a] = r.nextGaussian() * s
        val sw = 2.0 * sqrt(tK / 298.15)
        for (a in 0 until 3) w[i * 3 + a] = r.nextGaussian() * sw
    }

    /** New temperature: all speeds and spins rescale by √(T'/T) at once. */
    fun setTemperature(tKelvin: Double) {
        if (abs(tKelvin - tK) < 1e-9 || tKelvin <= 0.0) return
        val f = sqrt(tKelvin / tK)
        for (k in 0 until n * 3) { v[k] *= f; w[k] *= f }
        tK = tKelvin
    }

    /** Unit axis of A/C particle [i] toward its reactive atom (body +x). */
    fun axis(i: Int, out: DoubleArray) {
        val qx = q[i * 4]; val qy = q[i * 4 + 1]; val qz = q[i * 4 + 2]; val qw = q[i * 4 + 3]
        out[0] = 1 - 2 * (qy * qy + qz * qz); out[1] = 2 * (qx * qy + qw * qz); out[2] = 2 * (qx * qz - qw * qy)
    }

    // ================================================================ time step

    fun step(dt: Double) {
        if (dt <= 0.0) return
        val m = maxOf(1, ceil(dt / maxSub).toInt())
        val h = dt / m
        val r0 = reactions; val c0 = collisionsAB
        repeat(m) { substep(h) }
        simTime += dt
        val keep = exp(-dt / 6.0)
        reactionRate = reactionRate * keep + (reactions - r0) / 6.0
        collisionRateAB = collisionRateAB * keep + (collisionsAB - c0) / 6.0
    }

    private val evT = DoubleArray(4096)
    private val evI = IntArray(4096)
    private val evJ = IntArray(4096)
    private val used = BooleanArray(n)
    private val dirty = BooleanArray(n)
    private val tLast = DoubleArray(n)
    private val ax = DoubleArray(3)

    private fun substep(h: Double) {
        pairTimeIntegral += nAcur.toDouble() * nBcur * h
        for (k in 0 until flashCap) fAge[k] += h
        for (k in 0 until lineCap) lineAge[k] += h
        // tumbling, ageing, product exchange, thermostat
        for (i in 0 until n) {
            integrateQ(i, h)
            born[i] += h
            if (kind[i] == C || kind[i] == D) {
                life[i] -= h
                if (life[i] <= 0.0) {
                    kind[i] = if (kind[i] == C) A else B
                    if (kind[i] == A) nAcur++ else nBcur++
                    place(i, rng, n); born[i] = 0.0
                }
            }
            if (rng.nextDouble() < thermostatRate * h) thermalize(i, rng)
        }

        // collisions inside [0, h], found exactly for straight flights. Every particle's position is the
        // linear form x + v·τ (valid after its last collision, see collide), so a particle that already
        // collided can collide again later in the same sub-step: up to 4 passes, each re-checking only
        // the pairs that involve a particle which just changed course.
        tLast.fill(0.0)
        dirty.fill(true)
        for (pass in 0 until 6) {
            var ne = 0
            for (i in 0 until n) {
                val xi = x[i * 3]; val yi = x[i * 3 + 1]; val zi = x[i * 3 + 2]
                val vxi = v[i * 3]; val vyi = v[i * 3 + 1]; val vzi = v[i * 3 + 2]
                val ri = radius(i)
                for (j in i + 1 until n) {
                    if (!dirty[i] && !dirty[j]) continue
                    val gx = v[j * 3] - vxi; val gy = v[j * 3 + 1] - vyi; val gz = v[j * 3 + 2] - vzi
                    val t0 = maxOf(tLast[i], tLast[j])
                    val rx = x[j * 3] - xi + gx * t0; val ry = x[j * 3 + 1] - yi + gy * t0; val rz = x[j * 3 + 2] - zi + gz * t0
                    val bd = rx * gx + ry * gy + rz * gz
                    if (bd >= 0.0) continue                               // separating
                    val d = ri + radius(j)
                    val c = rx * rx + ry * ry + rz * rz - d * d
                    val t: Double
                    if (c <= 0.0) t = t0 else {
                        val a = gx * gx + gy * gy + gz * gz
                        val disc = bd * bd - a * c
                        if (disc < 0.0) continue
                        t = t0 + (-bd - sqrt(disc)) / a
                        if (t > h) continue
                    }
                    if (ne < evT.size) { evT[ne] = t; evI[ne] = i; evJ[ne] = j; ne++ }
                }
            }
            // wall bounces are events too: a molecule coming back off a wall can meet another head-on
            for (i in 0 until n) {
                if (!dirty[i]) continue
                val rad = radius(i)
                for (a in 0 until 3) {
                    val vv = v[i * 3 + a]
                    if (abs(vv) < 1e-12) continue
                    val wall = if (vv > 0) half - rad else -half + rad
                    var t = (wall - x[i * 3 + a]) / vv
                    if (t < tLast[i]) t = tLast[i]
                    if (t <= h && ne < evT.size) { evT[ne] = t; evI[ne] = i; evJ[ne] = -1 - a; ne++ }
                }
            }
            if (ne == 0) break
            // time order (insertion sort: a handful of events per pass)
            for (a in 1 until ne) {
                val t = evT[a]; val ii = evI[a]; val jj = evJ[a]
                var b = a - 1
                while (b >= 0 && evT[b] > t) { evT[b + 1] = evT[b]; evI[b + 1] = evI[b]; evJ[b + 1] = evJ[b]; b-- }
                evT[b + 1] = t; evI[b + 1] = ii; evJ[b + 1] = jj
            }
            used.fill(false)
            for (e in 0 until ne) {
                val i = evI[e]; val j = evJ[e]
                if (j < 0) {                                              // wall: reflect that component
                    if (used[i]) continue
                    val k = i * 3 + (-1 - j); val t = evT[e]
                    val p = x[k] + v[k] * t
                    val outward = if (p > 0) v[k] > 0 else v[k] < 0
                    if (!outward) continue
                    v[k] = -v[k]; x[k] = p - v[k] * t
                    used[i] = true; tLast[i] = t
                    continue
                }
                if (used[i] || used[j]) continue
                if (collide(i, j, evT[e])) { tLast[i] = evT[e]; tLast[j] = evT[e] }
            }
            System.arraycopy(used, 0, dirty, 0, n)
        }
        // flights + walls
        for (i in 0 until n) {
            val rad = radius(i)
            for (a in 0 until 3) {
                val k = i * 3 + a
                x[k] += v[k] * h
                val lo = -half + rad; val hi = half - rad
                if (x[k] > hi) { x[k] = 2 * hi - x[k]; v[k] = -abs(v[k]) }
                if (x[k] < lo) { x[k] = 2 * lo - x[k]; v[k] = abs(v[k]) }
                if (x[k] > hi || x[k] < lo) x[k] = x[k].coerceIn(lo, hi)
            }
        }
    }

    /** Elastic collision of i and j at sub-step time [t]; false if they were not approaching after all. */
    private fun collide(i: Int, j: Int, t: Double): Boolean {
        val pix = x[i * 3] + v[i * 3] * t; val piy = x[i * 3 + 1] + v[i * 3 + 1] * t; val piz = x[i * 3 + 2] + v[i * 3 + 2] * t
        val pjx = x[j * 3] + v[j * 3] * t; val pjy = x[j * 3 + 1] + v[j * 3 + 1] * t; val pjz = x[j * 3 + 2] + v[j * 3 + 2] * t
        var nx = pjx - pix; var ny = pjy - piy; var nz = pjz - piz
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-12) return false
        nx /= l; ny /= l; nz /= l
        val gn = (v[j * 3] - v[i * 3]) * nx + (v[j * 3 + 1] - v[i * 3 + 1]) * ny + (v[j * 3 + 2] - v[i * 3 + 2]) * nz
        if (gn >= 0.0) return false
        val mi = mass(i); val mj = mass(j); val m = mi + mj
        val mu = mi * mj / m
        val ci = 2 * mj / m * gn; val cj = 2 * mi / m * gn
        v[i * 3] += ci * nx; v[i * 3 + 1] += ci * ny; v[i * 3 + 2] += ci * nz
        v[j * 3] -= cj * nx; v[j * 3 + 1] -= cj * ny; v[j * 3 + 2] -= cj * nz
        // land at x(t) + v'(h − t) after the common flight x += v h
        x[i * 3] = pix - v[i * 3] * t; x[i * 3 + 1] = piy - v[i * 3 + 1] * t; x[i * 3 + 2] = piz - v[i * 3 + 2] * t
        x[j * 3] = pjx - v[j * 3] * t; x[j * 3 + 1] = pjy - v[j * 3 + 1] * t; x[j * 3 + 2] = pjz - v[j * 3 + 2] * t
        used[i] = true; used[j] = true
        collisionsAll++

        val ki = kind[i]; val kj = kind[j]
        if (!((ki == A && kj == B) || (ki == B && kj == A))) return true
        val a = if (ki == A) i else j; val b = if (a == i) j else i
        val sgn = if (a == i) 1.0 else -1.0                         // n from A to B
        val e = 0.5 * mu * gn * gn * jPerUnit
        collisionsAB++
        eRing[eHead] = e; eHead = (eHead + 1) % RING; if (eCount < RING) eCount++
        val ra = radius(a)
        val ctx = if (a == i) pix else pjx; val cty = if (a == i) piy else pjy; val ctz = if (a == i) piz else pjz
        val px = ctx + sgn * nx * ra; val py = cty + sgn * ny * ra; val pz = ctz + sgn * nz * ra   // contact point
        if (e >= barrier) {
            aboveAB++
            axis(a, ax)
            val facing = ax[0] * nx * sgn + ax[1] * ny * sgn + ax[2] * nz * sgn
            if (reactive && (!steric || facing >= 0.0)) {
                kind[a] = C; kind[b] = D; nAcur--; nBcur--
                life[a] = productLife; life[b] = productLife
                reactions++
                flash(px, py, pz, F_REACT, e)
                val L = lineHead; lineHead = (lineHead + 1) % lineCap
                lineA[L * 3] = ctx; lineA[L * 3 + 1] = cty; lineA[L * 3 + 2] = ctz
                lineB[L * 3] = if (b == j) pjx else pix; lineB[L * 3 + 1] = if (b == j) pjy else piy; lineB[L * 3 + 2] = if (b == j) pjz else piz
                lineAge[L] = 0.0
                event(F_REACT, e / barrier)
            } else {
                wrongSide++
                flash(px, py, pz, F_WRONG_SIDE, e)
                event(F_WRONG_SIDE, e / barrier)
            }
        } else if (e >= 0.5 * barrier) {
            flash(px, py, pz, F_NEAR, e)
            event(F_NEAR, e / barrier)
        }
        return true
    }

    private fun flash(px: Double, py: Double, pz: Double, k: Int, e: Double) {
        val s = fHead; fHead = (fHead + 1) % flashCap
        fx[s * 3] = px; fx[s * 3 + 1] = py; fx[s * 3 + 2] = pz; fAge[s] = 0.0; fKind[s] = k; fEnergy[s] = e
    }

    private fun event(k: Int, ratio: Double) {
        val s = (evTotal % evCap).toInt()
        evKind[s] = k; evE[s] = ratio; evTotal++
    }

    /** q ← exp(½ ω h) ⊗ q (ω in box axes). */
    private fun integrateQ(i: Int, h: Double) {
        val wx = w[i * 3]; val wy = w[i * 3 + 1]; val wz = w[i * 3 + 2]
        val wl = sqrt(wx * wx + wy * wy + wz * wz)
        val ang = wl * h
        if (ang < 1e-12) return
        val s = sin(ang / 2) / wl; val c = cos(ang / 2)
        val rx = wx * s; val ry = wy * s; val rz = wz * s
        val o = i * 4
        val qx = q[o]; val qy = q[o + 1]; val qz = q[o + 2]; val qw = q[o + 3]
        val nx = c * qx + rx * qw + ry * qz - rz * qy
        val ny = c * qy - rx * qz + ry * qw + rz * qx
        val nz = c * qz + rx * qy - ry * qx + rz * qw
        val nw = c * qw - rx * qx - ry * qy - rz * qz
        val l = sqrt(nx * nx + ny * ny + nz * nz + nw * nw)
        q[o] = nx / l; q[o + 1] = ny / l; q[o + 2] = nz / l; q[o + 3] = nw / l
    }

    /** Tests: total kinetic energy (display units). */
    fun kineticEnergy(): Double {
        var e = 0.0
        for (i in 0 until n) e += 0.5 * mass(i) * (v[i * 3] * v[i * 3] + v[i * 3 + 1] * v[i * 3 + 1] + v[i * 3 + 2] * v[i * 3 + 2])
        return e
    }
}
