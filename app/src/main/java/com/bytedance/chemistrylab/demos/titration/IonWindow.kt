// IonWindow — the molecule window beside the flask. Its POPULATION is not animated by hand: every frame
// the flask's exact state gives target counts (core/titration/MicroScale: H₃O⁺/OH⁻ on a stated log
// scale, spectators and the acetic-acid family linearly), and the window reaches them only through
// real reaction steps, one at a time:
//   NaOH arrives          a drop brings Na⁺ + OH⁻ in from the top (Na⁺ follows n(NaOH) exactly)
//   neutralisation        OH⁻ + H₃O⁺ → 2 H₂O        (warm flash; the waters fade into the solvent)
//   weak acid, buffer     CH₃COOH → CH₃COO⁻ + H₃O⁺  (the acid releases H⁺ to replace what OH⁻ took)
//   weak acid, near end   OH⁻ + CH₃COOH → CH₃COO⁻ + H₂O
//   past the end          OH⁻ simply accumulate (Na⁺ and OH⁻ keep arriving, nothing left to react)
// So the counts on screen always match the flask, and what changes them is chemistry the user can watch.
// Positions: gentle Brownian drift in a shallow glass box (motion across the line of sight).

package com.bytedance.chemistrylab.demos.titration

import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.core.titration.MicroTargets
import com.bytedance.chemistrylab.lab.Q
import com.bytedance.chemistrylab.lab.spec.Node
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

internal class IonWindow(private val hx: Float, private val hy: Float, private val hz: Float) {
    companion object {
        const val H3O = 0; const val OH = 1; const val NA = 2; const val CL = 3; const val HA = 4; const val A = 5; const val H2O = 6
        val CAP = intArrayOf(16, 16, 16, 8, 8, 8, 12)
        const val KINDS = 7
        const val STEER = 0.55f            // s for two reactants to meet
        const val FADE = 1.4f              // s for a product water to dissolve into the solvent
        const val FLASH_N = 10
        const val FLASH_LIFE = 0.5f
    }

    // per-kind slots
    private val alive = Array(KINDS) { BooleanArray(CAP[it]) }
    private val state = Array(KINDS) { IntArray(CAP[it]) }            // 0 free, 1 steering to a reaction, 2 fading
    private val t = Array(KINDS) { FloatArray(CAP[it]) }
    private val x = Array(KINDS) { FloatArray(CAP[it] * 3) }
    private val v = Array(KINDS) { FloatArray(CAP[it] * 3) }
    private val from = Array(KINDS) { FloatArray(CAP[it] * 3) }
    private val to = Array(KINDS) { FloatArray(CAP[it] * 3) }
    private val yaw = Array(KINDS) { FloatArray(CAP[it]) }
    private val spin = Array(KINDS) { FloatArray(CAP[it]) }
    /** For a steering reactant: what it becomes (event id) when it arrives. */
    private val evKind = Array(KINDS) { IntArray(CAP[it]) }
    /** For a steering OH⁻: the slot of its partner (H₃O⁺ or CH₃COOH). */
    private val partner = IntArray(CAP[1]) { -1 }

    lateinit var nodes: Array<Array<Node>>
    lateinit var halos: Array<Array<Node>?>
    lateinit var flashes: Array<Node>
    private val fAge = FloatArray(FLASH_N) { 9f }
    private val fWarm = BooleanArray(FLASH_N)
    private val fPos = FloatArray(FLASH_N * 3)
    private var fHead = 0

    private var rnd = Random(1824)
    private var cool = 0f
    /** Reaction events since reset (for the readout / tests). */
    var neutralisations = 0; private set
    var releases = 0; private set

    fun attach(nodes: Array<Array<Node>>, halos: Array<Array<Node>?>, flashes: Array<Node>) { this.nodes = nodes; this.halos = halos; this.flashes = flashes }

    private fun count(k: Int, freeOnly: Boolean): Int { var n = 0; for (i in 0 until CAP[k]) if (alive[k][i] && (!freeOnly || state[k][i] == 0)) n++; return n }

    /** Snap the population to [tg] (reset, acid swap). */
    fun reset(tg: MicroTargets, seed: Int) {
        rnd = Random(seed)
        for (k in 0 until KINDS) { alive[k].fill(false); state[k].fill(0) }
        partner.fill(-1)
        fAge.fill(9f); cool = 0f; neutralisations = 0; releases = 0
        repeat(tg.h3o) { spawn(H3O, null) }; repeat(tg.oh) { spawn(OH, null) }; repeat(tg.na) { spawn(NA, null) }
        repeat(tg.cl) { spawn(CL, null) }; repeat(tg.ha) { spawn(HA, null) }; repeat(tg.a) { spawn(A, null) }
    }

    private fun spawn(k: Int, at: FloatArray?, vel: FloatArray? = null): Int {
        val i = alive[k].indexOfFirst { !it }
        if (i < 0) return -1
        alive[k][i] = true; state[k][i] = 0; t[k][i] = 0f
        val p = i * 3
        if (at != null) { x[k][p] = at[0].coerceIn(-hx, hx); x[k][p + 1] = at[1].coerceIn(-hy, hy); x[k][p + 2] = at[2].coerceIn(-hz, hz) }
        else { x[k][p] = (rnd.nextFloat() * 2 - 1) * hx; x[k][p + 1] = (rnd.nextFloat() * 2 - 1) * hy; x[k][p + 2] = (rnd.nextFloat() * 2 - 1) * hz }
        if (vel != null) { v[k][p] = vel[0]; v[k][p + 1] = vel[1]; v[k][p + 2] = vel[2] }
        else for (a in 0 until 3) v[k][p + a] = (rnd.nextFloat() - 0.5f) * 0.04f
        yaw[k][i] = rnd.nextFloat() * 6.28f; spin[k][i] = (rnd.nextFloat() - 0.5f) * 1.6f
        return i
    }

    private fun kill(k: Int, i: Int) { alive[k][i] = false; state[k][i] = 0 }

    private fun flash(px: Float, py: Float, pz: Float, warm: Boolean) {
        val f = fHead; fHead = (fHead + 1) % FLASH_N
        fAge[f] = 0f; fWarm[f] = warm; fPos[f * 3] = px; fPos[f * 3 + 1] = py; fPos[f * 3 + 2] = pz
    }

    /** Nearest FREE slot of kind [k] to point p (−1 if none). */
    private fun nearest(k: Int, px: Float, py: Float, pz: Float): Int {
        var best = -1; var bd = Float.MAX_VALUE
        for (i in 0 until CAP[k]) if (alive[k][i] && state[k][i] == 0) {
            val dx = x[k][i * 3] - px; val dy = x[k][i * 3 + 1] - py; val dz = x[k][i * 3 + 2] - pz
            val d = dx * dx + dy * dy + dz * dz
            if (d < bd) { bd = d; best = i }
        }
        return best
    }
    private fun anyFree(k: Int): Int { for (i in 0 until CAP[k]) if (alive[k][i] && state[k][i] == 0) return i; return -1 }

    /** Pair OH⁻ [o] with partner kind [pk] slot [p]: both glide to a meeting point; [ev] what happens there. */
    private fun pair(o: Int, pk: Int, p: Int, ev: Int) {
        val a = o * 3; val b = p * 3
        val mx = (x[OH][a] + x[pk][b]) / 2; val my = (x[OH][a + 1] + x[pk][b + 1]) / 2; val mz = (x[OH][a + 2] + x[pk][b + 2]) / 2
        for ((k, i, s) in listOf(Triple(OH, o, -1f), Triple(pk, p, 1f))) {
            state[k][i] = 1; t[k][i] = 0f; evKind[k][i] = ev
            for (c in 0 until 3) from[k][i * 3 + c] = x[k][i * 3 + c]
            to[k][i * 3] = mx + s * 0.016f; to[k][i * 3 + 1] = my; to[k][i * 3 + 2] = mz
        }
        partner[o] = p
    }

    /**
     * Advance [dt] (display seconds × speed). [tg] = targets from the flask; [weak] = acetic acid.
     * [eventGap] = minimum time between new events.
     */
    fun step(dt: Float, tg: MicroTargets, weak: Boolean, eventGap: Float) {
        if (dt <= 0f) return
        cool -= dt
        if (cool <= 0f && decide(tg, weak)) cool = eventGap
        move(dt)
    }

    private fun decide(tg: MicroTargets, weak: Boolean): Boolean {
        val fH = count(H3O, true); val fOH = count(OH, true); val nNa = count(NA, false)
        val fHA = count(HA, true); val nA = count(A, false) + pendingA()
        // more OH⁻ is needed when there is H₃O⁺ to remove, base excess to build, or acid to convert
        val needOH = fH > tg.h3o || fOH < tg.oh || (weak && nA < tg.a && fHA > 0 && fH >= tg.h3o)
        when {
            nNa < tg.na -> { arrive(true, needOH); return true }
            fOH > tg.oh -> {
                val o = anyFree(OH)
                val h = nearest(H3O, x[OH][o * 3], x[OH][o * 3 + 1], x[OH][o * 3 + 2])
                if (h >= 0) { pair(o, H3O, h, 0); return true }
                val ha = if (weak) nearest(HA, x[OH][o * 3], x[OH][o * 3 + 1], x[OH][o * 3 + 2]) else -1
                if (ha >= 0) { pair(o, HA, ha, 1); return true }
                if (fOH > tg.oh + 1) { state[OH][o] = 2; t[OH][o] = 0f; return true }   // nothing left to react: it disperses
                return false
            }
            // buffer: the weak acid releases H⁺ to replace the H₃O⁺ that OH⁻ took
            weak && nA < tg.a && fHA > 0 && fH < tg.h3o -> { release(); return true }
            needOH -> { arrive(false, true); return true }
            weak && fH < tg.h3o && fHA > tg.ha -> { release(); return true }
        }
        return false
    }

    private fun pendingA(): Int { var n = 0; for (i in 0 until CAP[HA]) if (alive[HA][i] && state[HA][i] == 1) n++; return n }

    /**
     * NaOH from the burette enters from the top: Na⁺ (linear scale: one per 1/8 of the acid) and/or OH⁻
     * (log scale: only when the flask's [OH⁻]/[H₃O⁺] targets call for one). Early on, many drops change
     * [H⁺] by less than half a decade, so Na⁺ can arrive without a visible OH⁻ — the scales differ.
     */
    private fun arrive(withNa: Boolean, withOH: Boolean) {
        val px = (rnd.nextFloat() * 2 - 1) * hx * 0.7f; val pz = (rnd.nextFloat() * 2 - 1) * hz * 0.6f
        val top = hy - 0.02f
        if (withOH) spawn(OH, floatArrayOf(px, top, pz), floatArrayOf((rnd.nextFloat() - 0.5f) * 0.03f, -0.09f, 0f))
        if (withNa) spawn(NA, floatArrayOf(px + 0.035f, top, pz), floatArrayOf((rnd.nextFloat() - 0.5f) * 0.03f, -0.08f, 0f))
        flash(px + 0.017f, top, pz, false)
    }

    /** CH₃COOH → CH₃COO⁻ + H₃O⁺ (a proton hops to a neighbouring water). */
    private fun release() {
        val i = anyFreeRandom(HA); if (i < 0) return
        val p = floatArrayOf(x[HA][i * 3], x[HA][i * 3 + 1], x[HA][i * 3 + 2])
        kill(HA, i)
        spawn(A, p, floatArrayOf(-0.02f, 0f, 0f))
        spawn(H3O, floatArrayOf(p[0] + 0.045f, p[1] + 0.01f, p[2]), floatArrayOf(0.05f, 0.02f, 0f))
        flash(p[0] + 0.03f, p[1], p[2], false)
        releases++
    }

    private fun anyFreeRandom(k: Int): Int {
        val n = count(k, true); if (n == 0) return -1
        var r = rnd.nextInt(n)
        for (i in 0 until CAP[k]) if (alive[k][i] && state[k][i] == 0) { if (r-- == 0) return i }
        return -1
    }

    private fun move(dt: Float) {
        for (f in 0 until FLASH_N) fAge[f] += dt
        val damp = exp(-0.8f * dt); val kick = 0.22f * sqrt(dt)
        for (k in 0 until KINDS) for (i in 0 until CAP[k]) {
            if (!alive[k][i]) continue
            val p = i * 3
            yaw[k][i] += spin[k][i] * dt
            when (state[k][i]) {
                1 -> {
                    t[k][i] += dt
                    val u = (t[k][i] / STEER).coerceIn(0f, 1f); val e = u * u * (3 - 2 * u)
                    for (c in 0 until 3) x[k][p + c] = from[k][p + c] + (to[k][p + c] - from[k][p + c]) * e
                    if (u >= 1f && k == OH) react(i)
                }
                2 -> {
                    t[k][i] += dt
                    drift(k, p, dt, damp, kick)
                    if (t[k][i] >= FADE) kill(k, i)
                }
                else -> drift(k, p, dt, damp, kick)
            }
        }
    }

    private fun drift(k: Int, p: Int, dt: Float, damp: Float, kick: Float) {
        val vv = v[k]; val xx = x[k]
        for (c in 0 until 3) { vv[p + c] = vv[p + c] * damp + (rnd.nextFloat() - 0.5f) * kick; }
        val sp = sqrt(vv[p] * vv[p] + vv[p + 1] * vv[p + 1] + vv[p + 2] * vv[p + 2])
        val vmax = 0.10f
        if (sp > vmax) for (c in 0 until 3) vv[p + c] *= vmax / sp
        val lim = floatArrayOf(hx, hy, hz)
        for (c in 0 until 3) {
            xx[p + c] += vv[p + c] * dt
            if (xx[p + c] > lim[c]) { xx[p + c] = lim[c]; vv[p + c] = -abs(vv[p + c]) }
            if (xx[p + c] < -lim[c]) { xx[p + c] = -lim[c]; vv[p + c] = abs(vv[p + c]) }
        }
    }

    /** OH⁻ slot [o] has met its partner. */
    private fun react(o: Int) {
        val ev = evKind[OH][o]
        val pk = if (ev == 0) H3O else HA
        val p = partner[o]; partner[o] = -1
        val m = floatArrayOf(to[OH][o * 3] + 0.016f, to[OH][o * 3 + 1], to[OH][o * 3 + 2])
        kill(OH, o)
        if (p >= 0 && alive[pk][p]) kill(pk, p)
        flash(m[0], m[1], m[2], true)
        if (ev == 0) {
            // OH⁻ + H₃O⁺ → 2 H₂O: the two waters drift apart and dissolve into the solvent
            for (s in floatArrayOf(-1f, 1f)) {
                val w = spawn(H2O, floatArrayOf(m[0] + s * 0.02f, m[1], m[2]), floatArrayOf(s * 0.05f, 0.015f, 0f))
                if (w >= 0) { state[H2O][w] = 2; t[H2O][w] = 0f }
            }
            neutralisations++
        } else {
            // OH⁻ + CH₃COOH → CH₃COO⁻ + H₂O
            spawn(A, floatArrayOf(m[0] + 0.02f, m[1], m[2]), floatArrayOf(0.02f, 0f, 0f))
            val w = spawn(H2O, floatArrayOf(m[0] - 0.025f, m[1], m[2]), floatArrayOf(-0.05f, 0.015f, 0f))
            if (w >= 0) { state[H2O][w] = 2; t[H2O][w] = 0f }
            neutralisations++
        }
    }

    /** Count of a kind as drawn (tests, labels). */
    fun shown(k: Int) = count(k, false)
    fun shownFree(k: Int) = count(k, true)

    /** Push positions to the nodes. [visible] = window shown. */
    fun place(visible: Boolean) {
        for (k in 0 until KINDS) {
            val ns = nodes[k]; val hs = halos[k]
            for (i in 0 until CAP[k]) {
                val on = visible && alive[k][i]
                val n = ns[i]
                n.visible = on
                hs?.get(i)?.visible = on
                if (!on) continue
                val p = i * 3
                n.pos = P3(x[k][p], x[k][p + 1], x[k][p + 2])
                n.rot = Q.axisAngle(0.25f, 1f, 0.15f, yaw[k][i])
                if (k == H2O) n.uniformScale((1f - (t[k][i] / FADE)).coerceIn(0.05f, 1f))
                hs?.get(i)?.pos = P3(x[k][p], x[k][p + 1], x[k][p + 2] - 0.012f)
            }
        }
        for (f in 0 until FLASH_N) {
            val n = flashes[f]
            val a = fAge[f] / FLASH_LIFE
            n.visible = visible && a < 1f
            if (!n.visible) continue
            n.pos = P3(fPos[f * 3], fPos[f * 3 + 1], fPos[f * 3 + 2] + 0.006f)
            n.uniformScale((if (fWarm[f]) 0.11f else 0.06f) * kotlin.math.sin(Math.PI.toFloat() * (0.12f + 0.88f * a)) * (1.2f - 0.5f * a))
        }
    }

    fun flashWarm(f: Int) = fWarm[f]
    val flashCount get() = FLASH_N
}

/** Kind indices of [IonWindow] (for tests). */
internal object IonWindowKinds { const val H3O = IonWindow.H3O; const val OH = IonWindow.OH; const val NA = IonWindow.NA; const val CL = IonWindow.CL; const val HA = IonWindow.HA; const val A = IonWindow.A }
