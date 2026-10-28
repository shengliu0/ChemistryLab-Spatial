// 原电池 · 铜锌电池 (Daniell, 1836) — macro cell + micro electrode windows + ions in the salt bridge.
//
// LESSON: a redox reaction split in two places makes a current — but only if the loop is closed TWICE:
// electrons through the wire, ions through the salt bridge. On the bench a zinc strip in colourless
// ZnSO₄ and a copper strip in blue CuSO₄, joined by a glass U-tube of KNO₃ gel (cotton plugs). Above
// them a circuit board: a centre-zero voltmeter across the strips, and through a knife switch a small
// fan and a centre-zero ammeter. Electrons (cyan beads) leave the zinc (−) through the wire to the
// copper (+); in the bridge K⁺ (violet) drift toward the copper beaker and NO₃⁻ toward the zinc beaker,
// so neither beaker charges up. Two glass "magnifier" windows look at the strips' surfaces: zinc
// atoms leave the lattice as smaller Zn²⁺ (2 e⁻ stay behind and run up into the wire); blue Cu²⁺
// arrive, take 2 e⁻ and become copper atoms of the lattice. Time-lapse (1 s = 1 day): the zinc thins
// and pits, a dark rough copper deposit grows, the blue fades as [Cu²⁺] falls, and the voltage stays
// almost flat (Nernst is logarithmic) until the copper ions run out — then it collapses.
//
// Interaction: pinch the salt bridge (lift out → everything stops, voltmeter 0 V; put back); pinch the
// knife switch (open → the voltmeter reads the EMF itself, closed → E − I·r); pinch a strip (next metal of
// the series Mg Zn Fe Pb Cu Ag, with its own salt solution — the voltmeter shows the new E°cell and
// swings the other way when the polarity flips, the electrons reverse). Panel: metals, [ion] sliders,
// speeds, reset.
//
// Chemistry: core/galvanic (CRC standard potentials, Nernst with T, I = E/(r + R_ext), Faraday masses,
// both beakers kept neutral by the bridge, backward-Euler integration to E = 0). Drawn ~2.4× life size;
// the strips' thickness and the deposit are exaggerated so a 0.1 mm change can be seen.

package com.bytedance.chemistrylab.demos.daniell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.core.galvanic.Anion
import com.bytedance.chemistrylab.core.galvanic.Galv
import com.bytedance.chemistrylab.core.galvanic.GalvanicCell
import com.bytedance.chemistrylab.core.galvanic.HalfCell
import com.bytedance.chemistrylab.core.galvanic.Metal
import com.bytedance.chemistrylab.lab.About
import com.bytedance.chemistrylab.lab.Bi
import com.bytedance.chemistrylab.lab.Challenge
import com.bytedance.chemistrylab.lab.Lang
import com.bytedance.chemistrylab.lab.Lesson
import com.bytedance.chemistrylab.lab.LessonStep
import com.bytedance.chemistrylab.lab.Q
import com.bytedance.chemistrylab.lab.SpecDemo
import com.bytedance.chemistrylab.lab.Sfx
import com.bytedance.chemistrylab.lab.TapTarget
import com.bytedance.chemistrylab.lab.spec.Blend
import com.bytedance.chemistrylab.lab.spec.LabelSpec
import com.bytedance.chemistrylab.lab.spec.Mat
import com.bytedance.chemistrylab.lab.spec.Node
import com.bytedance.chemistrylab.lab.tr
import com.bytedance.chemistrylab.lab.ui.Button
import com.bytedance.chemistrylab.lab.ui.FieldRef
import com.bytedance.chemistrylab.lab.ui.LabPanel
import com.bytedance.chemistrylab.lab.ui.LabSlider
import com.bytedance.chemistrylab.lab.ui.SegmentControl
import com.bytedance.chemistrylab.lab.ui.SegmentItem
import com.bytedance.chemistrylab.render.MeshData
import com.bytedance.chemistrylab.render.MeshGen
import com.bytedance.chemistrylab.render.ProcTex
import com.bytedance.chemistrylab.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DaniellDemo : SpecDemo() {
    override val id = "daniell"
    override val title = "原电池 · 铜锌电池"   // i18n-ok: Chinese title; the English one is DemoRegistry.TITLE_EN
    override val credit = "Daniell · 1836"

    // ---------------------------------------------------------------- layout (volume metres)
    private const val PLINTH_Y = -0.262f
    private const val BENCH = PLINTH_Y + 0.0175f
    private val BX = floatArrayOf(-0.26f, 0.26f)           // beaker centres
    private const val BZ = 0.04f
    private const val BR = 0.10f                            // beaker inner radius (a 250 mL beaker ~2.8x)
    private const val B_TOP = -0.005f
    private const val IN_BOT = BENCH + 0.006f
    private const val LIQ = -0.065f                         // solution surface
    private val SX = floatArrayOf(-0.31f, 0.31f)           // strips: outer side of each beaker, facing the viewer
    private const val SZ = 0.072f
    private const val STRIP_W = 0.06f; private const val STRIP_D = 0.010f
    private const val STRIP_BOT = -0.222f; private const val STRIP_TOP = 0.055f
    // salt bridge (inverted U, legs on the inner side of each beaker; the arm is low so that, lifted,
    // it stays below the voltmeter as seen from the viewer's eye height)
    private const val LEG_X = 0.205f; private const val LEG_Z = 0.008f
    private const val TIP_Y = -0.175f; private const val ARM_Y = 0.035f; private const val BEND = 0.05f
    private const val TUBE_R = 0.017f; private const val GEL_R = 0.0125f
    private const val LIFT = 0.12f                           // tips clear the solution
    // circuit board behind the beakers
    private const val BOARD_Z = -0.118f
    private const val BOARD_FRONT = BOARD_Z + 0.007f
    private const val WZ = -0.097f                           // wire plane
    private const val VX = 0.30f                             // vertical runs
    private const val TOP_Y = 0.385f
    private const val JUNC_Y = 0.178f
    private val FAN = P3(-0.30f, 0.255f, -0.054f)
    private val AMM = P3(0.30f, 0.255f, -0.096f)
    private val VM = P3(0f, 0.285f, -0.096f)
    private const val VM_W = 0.20f; private const val VM_H = 0.13f
    private const val SW_X = 0.05f
    // magnifier windows: [0] looks at the left strip (metal on its +x wall), [1] at the right strip
    private const val WIN_W = 0.30f; private const val WIN_H = 0.40f; private const val WIN_D = 0.10f
    private val WIN_C = arrayOf(P3(-0.67f, 0.06f, 0f), P3(0.67f, 0.06f, 0f))
    private const val COLW = 0.029f; private const val ROWH = 0.034f   // lattice: column spacing, row spacing
    private const val NC = 4; private const val NR = 9                 // column 0 = growth layer, 1..3 bulk
    private const val SURF = WIN_W / 2 - 3 * COLW                       // u = 0 (the metal surface), window-local |x|
    private const val R_M = 0.0158f
    private const val V_TOP = 0.185f                                    // electrons enter/leave the metal here
    private const val U_E = -1.6f * COLW
    private const val NI = 3; private const val NE = 6; private const val NS = 3; private const val NW = 3
    private const val D_LEAVE = 2.6f; private const val T_DET = 0.12f
    private const val D_ARR = 2.8f; private const val T_LAND = 0.92f

    private const val V_FS = 2.5                           // voltmeter ±2.5 V (a Mg cell pins it, the label has the number)
    private const val A_FS = 20.0                          // ammeter ±20 mA
    private val LAPSE = doubleArrayOf(1.0, 3600.0, 86400.0)
    private val SPEED_LABELS get() = listOf(tr("实时 ×1", "Real ×1"), tr("1 秒 = 1 小时", "1 s = 1 h"), tr("1 秒 = 1 天", "1 s = 1 day"))
    private const val I_REF = 0.00613                      // the Zn|Cu cell under load (A)
    private const val N_BEAD = 30; private const val N_K = 10; private const val N_NO3 = 10
    private const val EXT = 0.06f                          // bridge ions continue this far (path fraction) into the solution
    private const val NP = 12; private const val NN = 14   // pits, deposit nodules

    /** Ionic / metallic radius (Shannon 1976 ionic, CN 6; metallic radii, CRC): the ion is smaller than the atom. */
    private fun ionRatio(m: Metal) = when (m) { Metal.MG -> 0.45f; Metal.ZN -> 0.55f; Metal.FE -> 0.62f; Metal.PB -> 0.68f; Metal.CU -> 0.57f; Metal.AG -> 0.80f }

    // ---------------------------------------------------------------- state
    var speedIdx = 2
    private val cell = GalvanicCell()
    private val cSet = doubleArrayOf(1.0, 1.0)
    private var clock = 0f
    private var frame = 0
    private var textEn = false
    private var seed = 1836L
    private var lift = 0f
    private var sndFlow = false                                 // last frame: current flowing in the closed loop (sound)
    private val sndGone = BooleanArray(2)                      // last frame: strip fully dissolved (sound)
    private var blade = 0f
    private var vNeedle = 0f; private var vNeedleV = 0f
    private var aNeedle = 0f; private var aNeedleV = 0f
    private var fanW = 0f; private var fanA = 0f
    private var beadPhase = 0.0
    private val kS = FloatArray(N_K); private val nS = FloatArray(N_NO3)
    private var lastTap = ""
    private val liqKey = IntArray(2) { -1 }
    private val stripKey = IntArray(2) { -1 }
    private val clipPol = IntArray(2) { 99 }
    private var beadsLit = -1

    // ---------------------------------------------------------------- nodes
    private lateinit var liquid: Array<Node>
    private lateinit var stripDry: Array<Node>
    private lateinit var stripWet: Array<Node>
    private lateinit var deposit: Array<Node>
    private lateinit var pits: Array<Array<Node>>
    private lateinit var nodules: Array<Array<Node>>
    private val pitXY = Array(2) { Array(NP) { FloatArray(3) } }
    private val nodXY = Array(2) { Array(NN) { FloatArray(3) } }
    private lateinit var clips: Array<Array<Node>>
    private lateinit var signV: Array<Node>
    private lateinit var signH: Array<Node>
    private lateinit var bridgeRig: Node
    private lateinit var bridgePath: Path
    private lateinit var kNodes: Array<Node>
    private lateinit var no3Nodes: Array<Node>
    private lateinit var loop: Path
    private var bladeS0 = 0f; private var bladeS1 = 0f      // the blade's stretch of the loop (fractions)
    private lateinit var beads: Array<Node>
    private lateinit var fanRot: Node
    private lateinit var swHinge: Node
    private lateinit var vPivot: Node
    private lateinit var aPivot: Node
    private lateinit var wireL: Node
    private lateinit var wireR: Node
    private lateinit var wins: Array<Win>

    /** One magnifier window on strip [k]: [s] = +1 metal on the window's +x wall, −1 on its −x wall. */
    private class Win(val k: Int, val s: Float, val c: P3) {
        lateinit var atoms: Array<Node>
        lateinit var plate: Node
        val occ = BooleanArray(NC * NR)
        val reserved = BooleanArray(NC * NR)
        var colBase = 0
        var slide = 0f
        lateinit var ions: Array<Node>
        lateinit var ionHalos: Array<Node>
        val ionOn = BooleanArray(NI); val ionArr = BooleanArray(NI); val ionT = FloatArray(NI); val ionSite = IntArray(NI)
        val ionEnd = Array(NI) { FloatArray(3) }
        lateinit var elec: Array<Node>
        val eOn = BooleanArray(NE); val eArr = BooleanArray(NE); val eT = FloatArray(NE); val eDur = FloatArray(NE)
        val eSite = IntArray(NE); val eLane = FloatArray(NE)
        lateinit var sparks: Array<Node>
        val spT = FloatArray(NS) { -1f }; val spSite = IntArray(NS)
        lateinit var cats: Array<Node>
        lateinit var catHalos: Array<Node>
        lateinit var so4: Array<Node>
        lateinit var no3: Array<Node>
        lateinit var anHalos: Array<Node>
        val wander = Array(2 * NW) { FloatArray(6) }
        var budget = 0f
        var metal: Metal? = null
        /** window frame (u from the metal surface into the solution, v up, w toward the viewer) -> volume */
        fun at(u: Float, v: Float, w: Float) = P3(c.x + s * (SURF - u), c.y + v, c.z + w)
        fun colU(j: Int) = COLW * (0.5f - j + slide)
        fun rowV(j: Int, r: Int) = (r - (NR - 1) / 2f + if (Math.floorMod(j + colBase, 2) == 1) 0.25f else -0.25f) * ROWH
    }

    /** A polyline with an arc-length parameter. */
    private class Path(val p: FloatArray) {
        val n = p.size / 3
        val cum = FloatArray(n)
        init { for (i in 1 until n) { val dx = p[i * 3] - p[i * 3 - 3]; val dy = p[i * 3 + 1] - p[i * 3 - 2]; val dz = p[i * 3 + 2] - p[i * 3 - 1]; cum[i] = cum[i - 1] + sqrt(dx * dx + dy * dy + dz * dz) } }
        val len get() = cum[n - 1]
        fun at(f: Float): P3 {
            val d = f.coerceIn(0f, 1f) * len
            var lo = 0; var hi = n - 1
            while (hi - lo > 1) { val m = (lo + hi) / 2; if (cum[m] <= d) lo = m else hi = m }
            val seg = (cum[hi] - cum[lo]).coerceAtLeast(1e-9f); val w = (d - cum[lo]) / seg
            return P3(p[lo * 3] + (p[hi * 3] - p[lo * 3]) * w, p[lo * 3 + 1] + (p[hi * 3 + 1] - p[lo * 3 + 1]) * w, p[lo * 3 + 2] + (p[hi * 3 + 2] - p[lo * 3 + 2]) * w)
        }
        fun fracAt(i: Int) = cum[i] / len
    }

    // ---------------------------------------------------------------- look (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.32f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val rubber = Mat.Pbr(Rgb(0.10f, 0.10f, 0.11f), metallic = 0f, roughness = 0.6f)
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.11f)
    private val tubeGlass = Mat.Pbr(Rgb(0.88f, 0.94f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.10f)
    private val gelMat = Mat.Pbr(Rgb(0.80f, 0.90f, 1.0f), metallic = 0f, roughness = 0.3f, alpha = 0.06f)
    private val cotton = Mat.Pbr(Rgb(0.97f, 0.97f, 0.95f), metallic = 0f, roughness = 0.95f, emissive = Rgb(0.18f, 0.18f, 0.18f))
    private val boardMat = Mat.Pbr(Rgb(0.11f, 0.13f, 0.17f), metallic = 0.2f, roughness = 0.35f)
    private val cream = Mat.Pbr(Rgb(0.94f, 0.92f, 0.85f), metallic = 0f, roughness = 0.6f)
    private val ink = Mat.Unlit(Rgb(0.10f, 0.10f, 0.12f))
    private val needleMat = Mat.Unlit(Rgb(0.85f, 0.10f, 0.08f))
    private val ceramic = Mat.Pbr(Rgb(0.93f, 0.93f, 0.90f), metallic = 0f, roughness = 0.4f)
    private val copperBright = Mat.Pbr(Rgb(0.96f, 0.62f, 0.42f), metallic = 1f, roughness = 0.22f)
    private val ebonite = Mat.Pbr(Rgb(0.08f, 0.08f, 0.09f), metallic = 0f, roughness = 0.45f)
    private val RED = Rgb(0.88f, 0.16f, 0.12f)
    private val redMat = Mat.Pbr(RED, roughness = 0.40f)
    private val blackMat = Mat.Pbr(Rgb(0.30f, 0.30f, 0.33f), roughness = 0.40f)
    private val greyLead = Mat.Pbr(Rgb(0.62f, 0.64f, 0.68f), metallic = 0.3f, roughness = 0.45f)
    private val bladeMat = Mat.Pbr(Rgb(1.0f, 0.58f, 0.16f), metallic = 0f, roughness = 0.35f, emissive = Rgb(0.30f, 0.12f, 0.02f))
    private val elecMat = Mat.Unlit(Rgb(0.45f, 0.95f, 1.0f), alpha = 1f, blend = Blend.ADD)
    private val elecDim = Mat.Unlit(Rgb(0.45f, 0.95f, 1.0f), alpha = 0.30f, blend = Blend.ADD)
    private val kMat = Mat.Pbr(Rgb(0.68f, 0.42f, 1.0f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.28f, 0.14f, 0.45f))
    private val nMat = Mat.Pbr(Rgb(0.30f, 0.50f, 1.0f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.08f, 0.14f, 0.32f))
    private val oMat = Mat.Pbr(Rgb(0.95f, 0.22f, 0.18f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.30f, 0.05f, 0.04f))
    private val sMat = Mat.Pbr(Rgb(1.0f, 0.85f, 0.22f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.30f, 0.25f, 0.05f))
    private val catHalo = Mat.Unlit(Rgb(1.0f, 0.55f, 0.28f), alpha = 0.13f, blend = Blend.ADD)
    private val anHalo = Mat.Unlit(Rgb(0.35f, 0.65f, 1.0f), alpha = 0.13f, blend = Blend.ADD)
    private val sparkMat = Mat.Unlit(Rgb(1.0f, 0.95f, 0.75f), alpha = 0.9f, blend = Blend.ADD)
    private val zoomMat = Mat.Unlit(Rgb(0.55f, 0.80f, 1.0f), alpha = 0.22f, blend = Blend.ADD)
    private val menMat = Mat.Unlit(Rgb(0.70f, 0.88f, 1.0f), alpha = 0.40f, blend = Blend.ADD)
    private val tickMat = Mat.Unlit(Rgb(0.85f, 0.93f, 1f), alpha = 0.6f, blend = Blend.TRANSPARENT)
    private val winBack = Mat.Pbr(Rgb(0.03f, 0.045f, 0.07f), metallic = 0f, roughness = 0.35f, alpha = 0.92f)
    private val winFront = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), metallic = 0f, roughness = 0.04f, alpha = 0.06f)
    private val pitMat = Mat.Pbr(Rgb(0.16f, 0.16f, 0.18f), metallic = 0.4f, roughness = 0.9f)
    private val plusMat = Mat.Unlit(Rgb(1.0f, 0.36f, 0.26f), alpha = 1f, blend = Blend.ADD)
    private val minusMat = Mat.Unlit(Rgb(0.45f, 0.78f, 1.0f), alpha = 1f, blend = Blend.ADD)
    private val offSign = Mat.Unlit(Rgb(0.35f, 0.36f, 0.38f))
    private val slabTex by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }

    private fun metalColor(m: Metal) = when (m) {
        Metal.MG -> Rgb(0.86f, 0.87f, 0.88f); Metal.ZN -> Rgb(0.80f, 0.84f, 0.90f); Metal.FE -> Rgb(0.55f, 0.56f, 0.59f)
        Metal.PB -> Rgb(0.50f, 0.52f, 0.58f); Metal.CU -> Rgb(0.96f, 0.60f, 0.42f); Metal.AG -> Rgb(0.96f, 0.96f, 0.97f)
    }
    private fun metalRough(m: Metal) = when (m) { Metal.AG -> 0.15f; Metal.CU -> 0.25f; Metal.PB -> 0.50f; Metal.FE -> 0.40f; else -> 0.30f }
    /** The ion's colour = the colour its solution has (Cu²⁺ is what makes CuSO₄ blue). */
    private fun ionColor(m: Metal) = when (m) {
        Metal.CU -> Rgb(0.22f, 0.58f, 1.0f); Metal.FE -> Rgb(0.55f, 0.88f, 0.55f); Metal.ZN -> Rgb(0.86f, 0.88f, 0.94f)
        Metal.AG -> Rgb(0.92f, 0.92f, 0.96f); Metal.PB -> Rgb(0.72f, 0.74f, 0.80f); Metal.MG -> Rgb(0.90f, 0.90f, 0.88f)
    }
    private val matCache = HashMap<String, Mat>()
    private fun stripMat(m: Metal, etch: Int) = matCache.getOrPut("strip${m.name}$etch") {
        val c = ProcTex.okLerp(metalColor(m), Rgb(0.34f, 0.35f, 0.37f), 0.28f * etch)
        Mat.Pbr(c, metallic = 0.85f, roughness = metalRough(m) + 0.22f * etch, emissive = Rgb(c.r * 0.34f, c.g * 0.34f, c.b * 0.34f))
    }
    private fun atomMat(m: Metal) = matCache.getOrPut("atom${m.name}") { Mat.Pbr(metalColor(m), metallic = 0.9f, roughness = metalRough(m) + 0.05f, emissive = ProcTex.okLerp(metalColor(m), Rgb(0f, 0f, 0f), 0.75f)) }
    private fun ionMat(m: Metal) = matCache.getOrPut("ion${m.name}") { val c = ionColor(m); Mat.Pbr(c, metallic = 0f, roughness = 0.3f, emissive = Rgb(c.r * 0.3f, c.g * 0.3f, c.b * 0.3f)) }
    private fun depositMat(m: Metal) = matCache.getOrPut("dep${m.name}") {
        when (m) {
            Metal.CU -> Mat.Pbr(Rgb(0.52f, 0.22f, 0.13f), metallic = 0.45f, roughness = 0.88f)       // dark, spongy electrolytic copper
            Metal.AG -> Mat.Pbr(Rgb(0.80f, 0.80f, 0.78f), metallic = 0.5f, roughness = 0.75f)        // grey-white silver crystals
            Metal.PB -> Mat.Pbr(Rgb(0.42f, 0.43f, 0.48f), metallic = 0.5f, roughness = 0.80f)
            Metal.FE -> Mat.Pbr(Rgb(0.28f, 0.28f, 0.30f), metallic = 0.4f, roughness = 0.85f)
            Metal.ZN -> Mat.Pbr(Rgb(0.55f, 0.57f, 0.61f), metallic = 0.5f, roughness = 0.80f)
            Metal.MG -> Mat.Pbr(Rgb(0.70f, 0.70f, 0.70f), metallic = 0.4f, roughness = 0.80f)
        }
    }

    /**
     * Solution colour: Beer–Lambert per channel, A = ε c ℓ with display ε (≈ the aqua ions' visible
     * absorption: Cu²⁺ absorbs red, Fe²⁺ a little red and blue) over ℓ = 0.5 cm, quantised in lg c.
     */
    private fun liquidMat(m: Metal, c: Double): Pair<Int, Mat> {
        val eps = when (m) { Metal.CU -> floatArrayOf(4.5f, 0.55f, 0.06f); Metal.FE -> floatArrayOf(0.5f, 0.06f, 0.35f); else -> null }
        val lq = if (eps == null) 0 else (log10(c.coerceAtLeast(1e-6)) * 20).roundToInt().coerceIn(-120, 8)
        val key = m.ordinal * 1000 + lq + 500
        return key to matCache.getOrPut("liq$key") {
            val water = Rgb(0.74f, 0.86f, 0.98f)
            if (eps == null) Mat.Pbr(water, metallic = 0f, roughness = 0.05f, alpha = 0.15f)
            else {
                val cc = 10.0.pow(lq / 20.0).toFloat()
                val t = FloatArray(3) { 10f.pow(-eps[it] * cc * 0.5f) }
                val strength = (1f - (t[0] + t[1] + t[2]) / 3f).coerceIn(0f, 1f)
                val col = Rgb(water.r * t[0], water.g * t[1], water.b * t[2])
                Mat.Pbr(col, metallic = 0f, roughness = 0.05f, alpha = 0.15f + 0.45f * strength, emissive = Rgb(col.r * 0.22f * strength, col.g * 0.22f * strength, col.b * 0.22f * strength))
            }
        }
    }

    // ---------------------------------------------------------------- build

    override fun buildSpec(cacheDir: File): Node {
        // a fresh cell on every (re)build: defaults, pools at rest, every "already applied" cache cleared
        cell.reset(); cSet[0] = 1.0; cSet[1] = 1.0
        speedIdx = 2; clock = 0f; frame = 0; textEn = Lang.en; seed = 1836L
        lift = 0f; blade = 0f; sndFlow = true; vNeedle = 0f; vNeedleV = 0f; aNeedle = 0f; aNeedleV = 0f; fanW = 0f; fanA = 0f; beadPhase = 0.0
        switchTaps = 0; bridgeTaps = 0
        lastTap = ""; liqKey.fill(-1); stripKey.fill(-1); clipPol.fill(99); beadsLit = -1
        for (i in 0 until N_K) kS[i] = (1f + EXT) * i / N_K
        for (i in 0 until N_NO3) nS[i] = (1f + EXT) * (i + 0.5f) / N_NO3

        val root = Node("DaniellRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val sphere = MeshGen.sphere(1f, 12, 20, MeshGen.SphereUv.EQUIRECT)
        val lowSphere = MeshGen.sphere(1f, 8, 14, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.76f, 0.035f, 0.80f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.02f))
        root.add("PlinthBand", MeshGen.box(1.77f, 0.008f, 0.81f), alu, P3(0f, PLINTH_Y, -0.02f))

        buildBeakers(root, unitCyl, unitBox, lowSphere)
        buildBridge(root, sphere)
        buildBoard(root, unitCyl, unitBox, sphere, lowSphere)
        wins = arrayOf(Win(0, 1f, WIN_C[0]), Win(1, -1f, WIN_C[1]))
        for (w in wins) buildWindow(root, w, unitBox, unitCyl, sphere, lowSphere)

        update(0f)
        return root
    }

    private fun buildBeakers(root: Node, unitCyl: MeshData, unitBox: MeshData, lowSphere: MeshData) {
        val wall = MeshGen.tube(floatArrayOf(0f, 0f, 0f, 0f, B_TOP - IN_BOT + 0.006f, 0f), BR + 0.003f, 48, false)
        val liqMesh = MeshGen.cylinder(BR - 0.002f, LIQ - IN_BOT, 48)
        liquid = Array(2) { k -> root.add("Liquid$k", liqMesh, liquidMat(Metal.ZN, 1.0).second, P3(BX[k], (LIQ + IN_BOT) / 2, BZ)) }
        stripDry = Array(2) { k -> root.add("StripDry$k", unitBox, stripMat(Metal.ZN, 0), P3(SX[k], (STRIP_TOP + LIQ) / 2, SZ)).also { it.scale = P3(STRIP_W, STRIP_TOP - LIQ, STRIP_D) } }
        stripWet = Array(2) { k -> root.add("StripWet$k", unitBox, stripMat(Metal.ZN, 0), P3(SX[k], (STRIP_BOT + LIQ) / 2, SZ)) }
        deposit = Array(2) { k -> root.add("Deposit$k", unitBox, depositMat(Metal.CU)).also { it.visible = false } }
        pits = Array(2) { k -> Array(NP) { i -> root.add("Pit${k}_$i", lowSphere, pitMat).also { it.visible = false } } }
        nodules = Array(2) { k -> Array(NN) { i -> root.add("Nod${k}_$i", lowSphere, depositMat(Metal.CU)).also { it.visible = false } } }
        for (k in 0..1) {
            for (i in 0 until NP) { pitXY[k][i][0] = (rnd() - 0.5f) * 0.8f; pitXY[k][i][1] = STRIP_BOT + 0.012f + (LIQ - STRIP_BOT - 0.03f) * (i + rnd()) / NP; pitXY[k][i][2] = 0.6f + 0.8f * rnd() }
            for (i in 0 until NN) { nodXY[k][i][0] = (rnd() - 0.5f) * 0.9f; nodXY[k][i][1] = STRIP_BOT + 0.008f + (LIQ - STRIP_BOT - 0.02f) * (i + rnd()) / NN; nodXY[k][i][2] = 0.6f + 0.8f * rnd() }
        }
        clips = Array(2) { k ->
            val g = root.add(Node("Clip$k")).also { it.pos = P3(SX[k], STRIP_TOP + 0.004f, SZ) }
            arrayOf(g.add("ClipJawF$k", unitBox, blackMat, P3(0f, 0.004f, 0.0075f)).also { it.scale = P3(0.034f, 0.026f, 0.005f) },
                g.add("ClipJawB$k", unitBox, blackMat, P3(0f, 0.004f, -0.0075f)).also { it.scale = P3(0.034f, 0.026f, 0.005f) },
                g.add("ClipGrip$k", unitBox, blackMat, P3(0f, 0.022f, 0f)).also { it.scale = P3(0.020f, 0.012f, 0.020f) })
        }
        signH = Array(2) { k -> root.add("SignH$k", unitBox, minusMat, P3(SX[k] + (if (k == 0) -0.055f else 0.055f), STRIP_TOP + 0.022f, SZ)).also { it.scale = P3(0.026f, 0.0065f, 0.004f) } }
        signV = Array(2) { k -> root.add("SignV$k", unitBox, plusMat, P3(SX[k] + (if (k == 0) -0.055f else 0.055f), STRIP_TOP + 0.022f, SZ)).also { it.scale = P3(0.0065f, 0.026f, 0.004f) } }
        for (k in 0..1) {
            val x = BX[k]
            root.add("Beaker$k", wall, glass, P3(x, IN_BOT - 0.006f, BZ))
            root.add("BeakerFoot$k", MeshGen.cylinder(BR + 0.003f, 0.006f, 48), glass, P3(x, IN_BOT - 0.003f, BZ))
            root.add("BeakerLip$k", MeshGen.tube(circleXZ(BR + 0.004f, 56), 0.0032f, 6, true), glass, P3(x, B_TOP, BZ))
            root.add("Meniscus$k", MeshGen.tube(circleXZ(BR - 0.003f, 56), 0.0022f, 5, true), menMat, P3(x, LIQ, BZ))
            for (j in 0..3) root.add("BTick$k", MeshGen.box(if (j % 2 == 0) 0.036f else 0.020f, 0.0026f, 0.002f), tickMat,
                P3(x + (if (k == 0) 0.04f else -0.04f) - (if (j % 2 == 0) 0f else 0.008f), IN_BOT + 0.04f * (j + 1), BZ + BR * 0.93f))
            // the strip's pinch zone: its dry upper part, under the clip
            root.add(Node(if (k == 0) "ElecL" else "ElecR")).also { it.pos = P3(SX[k], (LIQ + STRIP_TOP) / 2, SZ + 0.01f); it.tapRadius = 0.05f }
        }
    }

    private fun buildBridge(root: Node, sphere: MeshData) {
        bridgeRig = root.add(Node("BridgeRig"))
        val pts = ArrayList<P3>()
        val y0 = ARM_Y - BEND                                    // where the legs start to bend
        for (i in 0..10) pts += P3(-LEG_X, TIP_Y + (y0 - TIP_Y) * i / 10f, LEG_Z)
        for (i in 1..8) { val a = PI.toFloat() * (1f - i / 16f); pts += P3(-LEG_X + BEND + BEND * cos(a), y0 + BEND * sin(a), LEG_Z) }
        for (i in 1..9) pts += P3(-LEG_X + BEND + (2 * (LEG_X - BEND)) * i / 10f, ARM_Y, LEG_Z)
        for (i in 0..8) { val a = PI.toFloat() * (0.5f - i / 16f); pts += P3(LEG_X - BEND + BEND * cos(a), y0 + BEND * sin(a), LEG_Z) }
        for (i in 1..10) pts += P3(LEG_X, y0 + (TIP_Y - y0) * i / 10f, LEG_Z)
        val arr = FloatArray(pts.size * 3) { val p = pts[it / 3]; when (it % 3) { 0 -> p.x; 1 -> p.y; else -> p.z } }
        bridgePath = Path(arr)
        bridgeRig.add("BridgeGel", MeshGen.tube(arr, GEL_R, 12, false), gelMat)
        bridgeRig.add("BridgeGlass", MeshGen.tube(arr, TUBE_R, 16, false), tubeGlass)
        for (sx in floatArrayOf(-1f, 1f)) {
            bridgeRig.add("Plug", MeshGen.cylinder(GEL_R + 0.0012f, 0.026f, 16), cotton, P3(sx * LEG_X, TIP_Y + 0.012f, LEG_Z))
            bridgeRig.add("TipRim", MeshGen.tube(circleXZ(TUBE_R, 20), 0.0022f, 5, true), tubeGlass, P3(sx * LEG_X, TIP_Y, LEG_Z))
        }
        bridgeRig.add(Node("Bridge")).also { it.pos = P3(0f, ARM_Y, LEG_Z + 0.01f); it.tapRadius = 0.05f }
        kNodes = Array(N_K) { bridgeRig.add("Kb$it", sphere, kMat).also { n -> n.uniformScale(0.0088f) } }
        no3Nodes = Array(N_NO3) { i ->
            val g = bridgeRig.add(Node("NO3b$i"))
            g.add("NO3bN$i", sphere, nMat).uniformScale(0.0064f)
            for (o in 0..2) { val a = o * 2 * PI.toFloat() / 3; g.add("NO3bO${i}_$o", sphere, oMat, P3(0.0098f * cos(a), 0.0098f * sin(a), 0f)).uniformScale(0.0054f) }
            g
        }
    }

    private fun buildBoard(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData, lowSphere: MeshData) {
        val bH = 0.35f; val bY = 0.26f
        root.add("Board", MeshGen.box(0.86f, bH, 0.014f), boardMat, P3(0f, bY, BOARD_Z))
        root.add("BoardTrim", MeshGen.box(0.872f, bH + 0.012f, 0.006f), alu, P3(0f, bY, BOARD_Z - 0.006f))
        val legTop = bY - bH / 2
        for (sx in floatArrayOf(-1f, 1f)) {
            root.add("BoardLeg", unitCyl, darkSteel, P3(sx * 0.40f, (BENCH + legTop) / 2, BOARD_Z - 0.012f)).scale = P3(0.008f, legTop - BENCH, 0.008f)
            root.add("BoardFoot", MeshGen.cylinder(0.030f, 0.010f, 24), darkSteel, P3(sx * 0.40f, BENCH + 0.005f, BOARD_Z - 0.012f))
        }
        val rotX = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())

        // the series loop: left clip -> fan -> switch -> ammeter -> right clip (electrons ride it)
        val pL = spline(listOf(P3(SX[0], STRIP_TOP + 0.030f, SZ), P3(SX[0] + 0.004f, STRIP_TOP + 0.075f, SZ - 0.06f), P3(-VX, 0.158f, WZ), P3(-VX, 0.20f, WZ),
            P3(-VX, 0.31f, WZ), P3(-VX, TOP_Y - 0.03f, WZ), P3(-VX + 0.03f, TOP_Y, WZ), P3(-0.15f, TOP_Y, WZ), P3(-SW_X, TOP_Y, WZ)), 80)
        val pR = FloatArray(pL.size) { i -> val src = (pL.size / 3 - 1 - i / 3) * 3 + i % 3; if (i % 3 == 0) -pL[src] else pL[src] }
        wireL = root.add("WireL", MeshGen.tube(pL, 0.0042f, 8, false), blackMat)
        wireR = root.add("WireR", MeshGen.tube(pR, 0.0042f, 8, false), redMat)
        val nL = pL.size / 3
        val all = FloatArray(pL.size + pR.size)
        System.arraycopy(pL, 0, all, 0, pL.size); System.arraycopy(pR, 0, all, pL.size, pR.size)
        // the blade crosses at the wire height but in front: nudge the joint points forward
        all[(nL - 1) * 3 + 2] = WZ + 0.012f; all[nL * 3 + 2] = WZ + 0.012f
        loop = Path(all)
        bladeS0 = loop.fracAt(nL - 1); bladeS1 = loop.fracAt(nL)
        beads = Array(N_BEAD) { root.add("Bead$it", lowSphere, elecMat).also { n -> n.uniformScale(0.0062f) } }

        // voltmeter branch (high impedance, in parallel): junction dots on the verticals, grey leads
        for (sx in floatArrayOf(-1f, 1f)) {
            root.add("Junction", sphere, greyLead, P3(sx * VX, JUNC_Y, WZ)).uniformScale(0.0075f)
            root.add("VLead", MeshGen.tube(floatArrayOf(sx * VX, JUNC_Y, WZ, sx * 0.062f, JUNC_Y, WZ, sx * 0.062f, VM.y - VM_H / 2 - 0.004f, WZ), 0.0026f, 6, false), greyLead)
        }

        // fan: motor on the board, four blades spinning in the board's plane (across the line of sight)
        root.add("FanMotor", unitCyl, darkSteel, P3(FAN.x, FAN.y, -0.086f)).also { it.scale = P3(0.022f, 0.044f, 0.022f); it.rot = rotX }
        root.add("FanSpokeH", unitBox, alu, P3(FAN.x, FAN.y, -0.066f)).scale = P3(0.140f, 0.004f, 0.004f)
        root.add("FanSpokeV", unitBox, alu, P3(FAN.x, FAN.y, -0.066f)).scale = P3(0.004f, 0.140f, 0.004f)
        root.add("FanGuard", MeshGen.tube(circleXY(0.070f, 56, FAN.x, FAN.y, -0.050f), 0.0024f, 6, true), alu)
        fanRot = root.add(Node("FanRot")).also { it.pos = FAN }
        for (b in 0..3) {
            val arm = fanRot.add(Node("FanArm$b")).also { it.rot = Q.axisAngle(0f, 0f, 1f, b * (PI / 2).toFloat()) }
            arm.add("Blade$b", unitBox, bladeMat, P3(0.036f, 0f, 0f)).also { it.scale = P3(0.052f, 0.024f, 0.003f); it.rot = Q.axisAngle(1f, 0f, 0f, 0.45f) }
        }
        fanRot.add("FanHub", sphere, darkSteel).uniformScale(0.012f)

        // meters (centre zero: the needle swings the other way when the polarity flips)
        vPivot = buildMeter(root, "V", VM, VM_W, VM_H, 0.080f, V_FS, 0.5, 1.0, unitCyl, unitBox)
        aPivot = buildMeter(root, "A", AMM, 0.15f, 0.12f, 0.062f, A_FS, 5.0, 10.0, unitCyl, unitBox)

        // knife switch: ceramic base, copper jaw and hinge, the blade lifts in the board's plane
        root.add("SwBase", MeshGen.box(0.15f, 0.046f, 0.008f), ceramic, P3(0f, TOP_Y, BOARD_FRONT + 0.004f))
        for (sx in floatArrayOf(-1f, 1f)) root.add("SwPost", unitBox, copperBright, P3(sx * SW_X, TOP_Y, WZ + 0.004f)).scale = P3(0.010f, 0.018f, 0.022f)
        swHinge = root.add(Node("SwHinge")).also { it.pos = P3(SW_X, TOP_Y, WZ + 0.012f) }
        swHinge.add("SwBlade", unitBox, copperBright, P3(-SW_X, 0f, 0f)).scale = P3(2 * SW_X + 0.010f, 0.008f, 0.004f)
        swHinge.add("SwKnob", unitCyl, ebonite, P3(-2 * SW_X - 0.002f, 0f, 0.013f)).also { it.scale = P3(0.0085f, 0.026f, 0.0085f); it.rot = rotX }
        root.add(Node("Switch")).also { it.pos = P3(0f, TOP_Y + 0.012f, WZ + 0.02f); it.tapRadius = 0.05f }
    }

    /** A centre-zero moving-coil meter on the board; returns the needle pivot. */
    private fun buildMeter(root: Node, letter: String, c: P3, w: Float, h: Float, arcR: Float, fs: Double, minor: Double, major: Double,
                           unitCyl: MeshData, unitBox: MeshData): Node {
        val zf = c.z + 0.017f
        root.add("Meter$letter", MeshGen.box(w, h, 0.03f), rubber, c)
        root.add("MeterFace$letter", MeshGen.box(w - 0.014f, h - 0.014f, 0.003f), cream, P3(c.x, c.y, zf - 0.001f))
        val piv = P3(c.x, c.y - h / 2 + 0.024f, zf)
        val nA = 40
        val arc = FloatArray((nA + 1) * 3) { k -> val a = meterAngle(fs * (2.0 * (k / 3) / nA - 1.0), fs); when (k % 3) { 0 -> piv.x + arcR * sin(a); 1 -> piv.y + arcR * cos(a); else -> zf + 0.002f } }
        root.add("Arc$letter", MeshGen.tube(arc, 0.0011f, 5, false), ink)
        val nT = (2 * fs / minor).roundToInt()
        for (i in 0..nT) {
            val v = -fs + i * minor; val a = meterAngle(v, fs)
            val big = abs(v / major - (v / major).roundToInt()) < 1e-6
            val zero = abs(v) < 1e-9
            val r1 = arcR + if (zero) 0.017f else if (big) 0.011f else 0.006f
            root.add("Tick$letter", MeshGen.tube(floatArrayOf(piv.x + arcR * sin(a), piv.y + arcR * cos(a), zf + 0.002f, piv.x + r1 * sin(a), piv.y + r1 * cos(a), zf + 0.002f),
                if (big || zero) 0.0015f else 0.0009f, 5, false), if (zero) needleMat else ink)
        }
        // the unit letter, from two or three strokes
        val gy = piv.y + arcR * 0.42f; val gh = 0.024f; val t = 0.0032f
        val lean = Math.toRadians(20.0).toFloat(); val dx = gh * kotlin.math.tan(lean) / 2
        if (letter == "V") {
            root.add("GlyphV1", unitBox, ink, P3(piv.x - dx, gy, zf + 0.002f)).also { it.scale = P3(t, gh / cos(lean), 0.002f); it.rot = Q.axisAngle(0f, 0f, 1f, lean) }
            root.add("GlyphV2", unitBox, ink, P3(piv.x + dx, gy, zf + 0.002f)).also { it.scale = P3(t, gh / cos(lean), 0.002f); it.rot = Q.axisAngle(0f, 0f, 1f, -lean) }
        } else {
            root.add("GlyphA1", unitBox, ink, P3(piv.x - dx, gy, zf + 0.002f)).also { it.scale = P3(t, gh / cos(lean), 0.002f); it.rot = Q.axisAngle(0f, 0f, 1f, -lean) }
            root.add("GlyphA2", unitBox, ink, P3(piv.x + dx, gy, zf + 0.002f)).also { it.scale = P3(t, gh / cos(lean), 0.002f); it.rot = Q.axisAngle(0f, 0f, 1f, lean) }
            root.add("GlyphA3", unitBox, ink, P3(piv.x, gy - gh * 0.14f, zf + 0.002f)).scale = P3(dx * 1.1f, t * 0.9f, 0.002f)
        }
        // terminals under the meter
        for (sx in floatArrayOf(-1f, 1f)) root.add("Post$letter", unitCyl, if (sx < 0) blackMat else redMat, P3(c.x + sx * w * 0.31f, c.y - h / 2 - 0.006f, c.z + 0.006f)).scale = P3(0.007f, 0.012f, 0.007f)
        val pivot = root.add(Node("Pivot$letter")).also { it.pos = P3(piv.x, piv.y, zf + 0.004f) }
        pivot.add("Needle$letter", unitBox, needleMat, P3(0f, arcR * 0.5f, 0f)).scale = P3(0.0028f, arcR * 1.06f, 0.0018f)
        root.add("Hub$letter", unitCyl, rubber, P3(piv.x, piv.y, zf + 0.006f)).also { it.scale = P3(0.0065f, 0.004f, 0.0065f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        return pivot
    }

    private fun buildWindow(root: Node, w: Win, unitBox: MeshData, unitCyl: MeshData, sphere: MeshData, lowSphere: MeshData) {
        val k = w.k; val c = w.c
        val back = c.z - WIN_D / 2; val front = c.z + WIN_D / 2
        root.add("WinBack$k", MeshGen.box(WIN_W, WIN_H, 0.004f), winBack, P3(c.x, c.y, back))
        root.add("WinFront$k", MeshGen.box(WIN_W, WIN_H, 0.003f), winFront, P3(c.x, c.y, front))
        val rail = 0.007f
        for (sy in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(back, front))
            root.add("WinRailX$k", unitBox, alu, P3(c.x, c.y + sy * (WIN_H / 2 + rail / 2), z)).scale = P3(WIN_W + 2 * rail, rail, rail)
        for (sx in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(back, front))
            root.add("WinRailY$k", unitBox, alu, P3(c.x + sx * (WIN_W / 2 + rail / 2), c.y, z)).scale = P3(rail, WIN_H, rail)
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f))
            root.add("WinRailZ$k", unitBox, alu, P3(c.x + sx * (WIN_W / 2 + rail / 2), c.y + sy * (WIN_H / 2 + rail / 2), c.z)).scale = P3(rail, rail, WIN_D)
        val bottom = c.y - WIN_H / 2 - rail
        root.add("WinPost$k", unitCyl, alu, P3(c.x, (BENCH + bottom) / 2, back + 0.01f)).scale = P3(0.007f, bottom - BENCH, 0.007f)
        root.add("WinFoot$k", MeshGen.cylinder(0.035f, 0.010f, 24), darkSteel, P3(c.x, BENCH + 0.005f, back + 0.01f))
        // the bulk metal behind the drawn lattice, on the wall nearest the strip, and the lead out of it
        w.plate = root.add("WinPlate$k", MeshGen.box(0.014f, WIN_H - 0.01f, WIN_D - 0.012f), stripMat(Metal.ZN, 0), P3(c.x + w.s * (WIN_W / 2 - 0.007f), c.y, c.z))
        val lx = w.at(U_E, 0f, 0f).x
        root.add("WinLead$k", unitCyl, greyLead, P3(lx, c.y + WIN_H / 2 - 0.005f, c.z + 0.024f)).scale = P3(0.0028f, 0.05f, 0.0028f)
        // zoom lines: from the wet strip to the window's near face (this window magnifies THAT surface)
        val src = P3(SX[k], -0.15f, SZ + 0.006f)
        val nearX = c.x + w.s * (WIN_W / 2 + rail)
        for (sy in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f))
            root.add("Zoom$k", MeshGen.tube(floatArrayOf(src.x, src.y, src.z, nearX, c.y + sy * WIN_H / 2, c.z + sz * WIN_D / 2), 0.0011f, 5, false), zoomMat)

        // lattice: columns 1..3 full, column 0 (the growth layer) empty
        for (j in 0 until NC) for (r in 0 until NR) w.occ[j * NR + r] = j >= 1
        w.atoms = Array(NC * NR) { root.add("Atom${k}_$it", sphere, atomMat(Metal.ZN)).also { n -> n.uniformScale(R_M) } }
        w.ions = Array(NI) { root.add("Ion${k}_$it", sphere, ionMat(Metal.ZN)).also { n -> n.visible = false } }
        w.ionHalos = Array(NI) { root.add("IonHalo${k}_$it", lowSphere, catHalo).also { n -> n.visible = false } }
        w.elec = Array(NE) { root.add("WinE${k}_$it", lowSphere, elecMat).also { n -> n.visible = false; n.uniformScale(0.0062f) } }
        w.sparks = Array(NS) { root.add("Spark${k}_$it", lowSphere, sparkMat).also { n -> n.visible = false } }
        // the solution: its own cations and the salt's anions wander (spectators of the electrode step)
        w.cats = Array(NW) { root.add("Cat${k}_$it", sphere, ionMat(Metal.ZN)) }
        w.catHalos = Array(NW) { root.add("CatHalo${k}_$it", lowSphere, catHalo).also { n -> n.uniformScale(0.025f) } }
        w.so4 = Array(NW) { j ->
            val g = root.add(Node("SO4w${k}_$j"))
            g.add("SO4wS${k}_$j", sphere, sMat).uniformScale(0.0098f)
            val d = 0.0158f / sqrt(3f)
            for ((m, v) in listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(-1f, 1f, -1f), floatArrayOf(-1f, -1f, 1f)).withIndex())
                g.add("SO4wO${k}_${j}_$m", sphere, oMat, P3(v[0] * d, v[1] * d, v[2] * d)).uniformScale(0.0078f)
            g
        }
        w.no3 = Array(NW) { j ->
            val g = root.add(Node("NO3w${k}_$j"))
            g.add("NO3wN${k}_$j", sphere, nMat).uniformScale(0.0085f)
            for (o in 0..2) { val a = o * 2 * PI.toFloat() / 3; g.add("NO3wO${k}_${j}_$o", sphere, oMat, P3(0.0135f * cos(a), 0.0135f * sin(a), 0f)).uniformScale(0.0075f) }
            g
        }
        w.anHalos = Array(NW) { root.add("AnHalo${k}_$it", lowSphere, anHalo).also { n -> n.uniformScale(0.027f) } }
        for (a in w.wander) { a[0] = 0.08f + 0.12f * rnd(); a[1] = -0.14f + 0.28f * rnd(); a[2] = 6.28f * rnd(); a[3] = 0.25f + 0.25f * rnd(); a[4] = 0.3f + 0.3f * rnd(); a[5] = 6.28f * rnd() }
    }

    // ---------------------------------------------------------------- experiment control

    fun setMetal(k: Int, m: Metal) { cell.half(k).replace(m, cSet[k]) }
    fun setConc(k: Int, c: Double) { cSet[k] = c; cell.half(k).fillSolution(c) }

    fun resetAll() {
        cell.reset(); cSet[0] = 1.0; cSet[1] = 1.0; speedIdx = 2
    }

    /** −1 anode, +1 cathode, 0 no polarity, for strip [k]. */
    private fun roleOf(k: Int): Int { val p = cell.polarity(); if (p == 0) return 0; val anode = if (p > 0) 0 else 1; return if (k == anode) -1 else 1 }

    // ---------------------------------------------------------------- per frame

    override fun update(dt: Float) {
        clock += dt
        cell.step(dt.toDouble() * LAPSE[speedIdx])
        val iCell = cell.cellCurrent()
        val rho = if (iCell > 1e-5) (1.4 * (iCell / I_REF).coerceIn(0.5, 2.6)).toFloat() else 0f      // display e⁻ per second
        val flow = if (iCell > 1e-5 && cell.switchClosed) (iCell / I_REF).coerceIn(0.35, 2.6).toFloat() else 0f
        // the bridge and the blade move in real time
        val lt = if (cell.bridgeIn) 0f else 1f
        val lift0 = lift; val blade0 = blade
        lift = if (lift < lt) minOf(lt, lift + dt / 0.6f) else maxOf(lt, lift - dt / 0.6f)
        val bt = if (cell.switchClosed) 0f else 1f
        blade = if (blade < bt) minOf(bt, blade + dt / 0.25f) else maxOf(bt, blade - dt / 0.25f)
        if (lift0 > 0f && lift == 0f) Sfx.play(Sfx.GLASS, 0.35f, 0.9f)                       // sound: the salt-bridge tube settles into both beakers
        if (blade0 > 0f && blade == 0f) Sfx.play(Sfx.CLACK, 0.5f, 1.3f, SW_X / 0.9f)          // sound: the knife switch blade seats in its jaws
        if (blade0 < 1f && blade == 1f) Sfx.play(Sfx.TICK, 0.3f, 0.9f, SW_X / 0.9f)           // sound: the blade lifts out of the jaws
        // sound: the circuit is complete (wire AND bridge) and a current starts to flow - a small spark at the contact
        val flowing = flow > 0f
        if (flowing && !sndFlow) Sfx.play(Sfx.ZAP, 0.35f, 1.2f, SW_X / 0.9f)
        sndFlow = flowing

        placeBeakers()
        placeBridge(dt, flow)
        placeCircuit(dt, flow)
        for (w in wins) placeWindow(w, dt, roleOf(w.k), rho)
        if (frame++ % 6 == 0 || dt == 0f || Lang.en != textEn) { textEn = Lang.en; updateReadout(); updateLabels() }
    }

    private fun placeBeakers() {
        val pol = cell.polarity()
        for (k in 0..1) {
            val h = cell.half(k); val m = h.metal
            val (lk, lm) = liquidMat(m, h.c)
            if (lk != liqKey[k]) { liqKey[k] = lk; liquid[k].setMat(lm) }
            val f = ((h.mass0 - h.mass) / h.mass0).coerceIn(0.0, 1.0).toFloat()
            val g = ((h.mass - h.mass0) / h.mass0).coerceAtLeast(0.0).toFloat()
            val etch = if (f > 0.35f) 2 else if (f > 0.08f) 1 else 0
            val sk = m.ordinal * 10 + etch
            if (sk != stripKey[k]) {
                stripKey[k] = sk
                stripWet[k].setMat(stripMat(m, etch)); stripDry[k].setMat(stripMat(m, 0)); deposit[k].setMat(depositMat(m))
                for (n in nodules[k]) n.setMat(depositMat(m))
            }
            val gone = h.mass <= 0.0
            if (gone && !sndGone[k]) Sfx.play(Sfx.THUD, 0.4f, 0.8f, SX[k] / 0.9f)    // sound: the anode strip has dissolved away - the cell is spent
            sndGone[k] = gone
            val ww = STRIP_W * (1f - 0.30f * f); val dw = STRIP_D * maxOf(0.15f, 1f - 0.80f * f)
            stripWet[k].visible = !gone
            stripWet[k].pos = P3(SX[k], (STRIP_BOT + LIQ) / 2, SZ); stripWet[k].scale = P3(ww, LIQ - STRIP_BOT, dw)
            val nPit = if (gone) 0 else minOf(NP, (f * 60f).toInt())
            for (i in 0 until NP) {
                val n = pits[k][i]; n.visible = i < nPit
                if (n.visible) { val a = pitXY[k][i]; val r = (0.0028f + 0.0055f * minOf(1f, f * 2.5f)) * a[2]; n.pos = P3(SX[k] + a[0] * ww, a[1], SZ + dw / 2 + 0.0004f); n.scale = P3(r, r * 1.3f, 0.0012f) }
            }
            // deposit: a rough skin that thickens, and nodules on its face
            val gs = minOf(1f, g / 0.5f)
            deposit[k].visible = g > 0.002f
            val td = 0.0015f + 0.010f * gs
            if (deposit[k].visible) { deposit[k].pos = P3(SX[k], (STRIP_BOT + LIQ) / 2 - 0.002f, SZ); deposit[k].scale = P3(STRIP_W + 1.4f * td, LIQ - STRIP_BOT - 0.006f, STRIP_D + 2 * td) }
            val nNod = minOf(NN, (g * 45f).toInt())
            for (i in 0 until NN) {
                val n = nodules[k][i]; n.visible = i < nNod
                if (n.visible) { val a = nodXY[k][i]; val r = (0.0035f + 0.0050f * gs) * a[2]; n.pos = P3(SX[k] + a[0] * (STRIP_W / 2 + 0.7f * td), a[1], SZ + STRIP_D / 2 + td); n.uniformScale(r) }
            }
            // polarity: red clip and a "+" on the cathode, dark clip and "−" on the anode
            val role = if (pol == 0) 0 else if ((pol > 0) == (k == 1)) 1 else -1
            if (clipPol[k] != role) {
                clipPol[k] = role
                for (n in clips[k]) n.setMat(if (role > 0) redMat else blackMat)
                signH[k].setMat(when (role) { 1 -> plusMat; -1 -> minusMat; else -> offSign })
                signV[k].visible = role > 0; signV[k].setMat(plusMat)
                (if (k == 0) wireL else wireR).setMat(if (role > 0) redMat else blackMat)
            }
        }
    }

    private fun placeBridge(dt: Float, flow: Float) {
        val e = ease(lift)
        bridgeRig.pos = P3(0f, LIFT * e, 0f)
        val dir = cell.polarity()                                   // +1: cathode on the right -> K⁺ move left -> right
        val moving = cell.closedLoop && dir != 0 && cell.cellCurrent() > 1e-5
        val v = if (moving) 0.040f * maxOf(flow, 0.6f) / bridgePath.len else 0f      // path fraction per second
        for (i in 0 until N_K) {
            if (moving) kS[i] += v * dt
            if (kS[i] > 1f + EXT) kS[i] -= 1f + EXT
            placeBridgeIon(kNodes[i], kS[i], if (dir >= 0) 1 else -1, 0.0088f, 0.004f)
        }
        for (i in 0 until N_NO3) {
            if (moving) nS[i] += v * dt
            if (nS[i] > 1f + EXT) nS[i] -= 1f + EXT
            placeBridgeIon(no3Nodes[i], nS[i], if (dir >= 0) -1 else 1, 1f, -0.004f)
            no3Nodes[i].rot = Q.axisAngle(0.3f, 1f, 0.2f, clock * 0.9f + i)
        }
    }

    /** [s] = progress from the source tip toward the destination tip; [d] = +1 left -> right. */
    private fun placeBridgeIon(n: Node, s: Float, d: Int, size: Float, dz: Float) {
        val grow = ((s - 0.015f) / 0.06f).coerceIn(0f, 1f)
        val shrink = if (s > 1f) (1f - (s - 1f) / EXT).coerceIn(0f, 1f) else 1f
        val sc = grow * shrink
        n.visible = sc > 0.02f && !(lift > 0.01f && s > 1f)
        if (!n.visible) return
        val p: P3
        if (s <= 1f) {
            val q = bridgePath.at(if (d > 0) s else 1f - s)
            p = P3(q.x, q.y, q.z + dz)
        } else {
            // out of the plug into the destination beaker's solution, spreading a little
            val u = (s - 1f) / EXT
            val tipX = if (d > 0) LEG_X else -LEG_X
            p = P3(tipX + d * 0.012f * u, TIP_Y - 0.040f * u, LEG_Z + dz + 0.010f * u)
        }
        n.pos = p
        n.uniformScale(size * maxOf(sc, 0.05f))
    }

    private fun placeCircuit(dt: Float, flow: Float) {
        // electrons: anode -> cathode through the wire, speed ∝ current; frozen and dim when no current
        val dir = cell.polarity()
        if (flow > 0f && dir != 0) beadPhase += dir * 0.10 * flow / loop.len * dt
        val lit = if (flow > 0f) 1 else 0
        if (lit != beadsLit) { beadsLit = lit; for (b in beads) b.setMat(if (lit == 1) elecMat else elecDim) }
        for (k in 0 until N_BEAD) {
            val s = ((((k.toDouble() / N_BEAD + beadPhase) % 1.0) + 1.0) % 1.0).toFloat()
            val n = beads[k]
            n.visible = !(blade > 0.05f && s > bladeS0 - 0.005f && s < bladeS1 + 0.005f)
            if (n.visible) n.pos = loop.at(s)
        }
        // needles: damped followers (moving-coil meters swing, they do not jump)
        val vt = meterAngle(cell.meterVolts(), V_FS)
        val at = meterAngle(cell.loadCurrent() * 1e3 * (if (dir < 0) -1 else 1), A_FS)
        val wN = 9f
        if (dt > 0f) {
            vNeedleV += (wN * wN * (vt - vNeedle) - 2 * wN * vNeedleV) * dt; vNeedle += vNeedleV * dt
            aNeedleV += (wN * wN * (at - aNeedle) - 2 * wN * aNeedleV) * dt; aNeedle += aNeedleV * dt
        } else { vNeedle = vt; aNeedle = at }
        vPivot.rot = Q.axisAngle(0f, 0f, 1f, -vNeedle)
        aPivot.rot = Q.axisAngle(0f, 0f, 1f, -aNeedle)
        // fan: angular speed ∝ the voltage across it (a motor's no-load speed), spins up and down
        val wt = (32.0 * cell.loadCurrent() * cell.rLoad / 0.613).coerceAtMost(70.0).toFloat()
        fanW += (wt - fanW) * (1f - kotlin.math.exp(-dt / 0.5f))
        fanA = (fanA + fanW * dt) % (2 * PI.toFloat())
        fanRot.rot = Q.axisAngle(0f, 0f, 1f, -fanA)
        swHinge.rot = Q.axisAngle(0f, 0f, 1f, -0.75f * ease(blade))
    }

    // ---------------------------------------------------------------- micro windows

    private fun placeWindow(w: Win, dt: Float, role: Int, rho: Float) {
        val m = cell.half(w.k).metal
        if (w.metal != m) applyWindowMetal(w, m)
        if (w.slide > 0f) w.slide = maxOf(0f, w.slide - dt / 0.5f) else if (w.slide < 0f) w.slide = minOf(0f, w.slide + dt / 0.5f)
        if (rho > 0f && role != 0) {
            w.budget = minOf(w.budget + rho * dt, 2f * m.n + 0.5f)
            if (w.budget >= m.n && (if (role < 0) spawnLeave(w, m.n) else spawnArrive(w, m.n))) w.budget -= m.n
        } else w.budget = 0f
        if (w.ionOn.none { it } && w.slide == 0f) maybeSlide(w)

        val ir = R_M * (0.55f + 0.45f * ionRatio(m))
        for (i in 0 until NI) {
            val n = w.ions[i]; val hn = w.ionHalos[i]
            if (!w.ionOn[i]) { n.visible = false; hn.visible = false; continue }
            val site = w.ionSite[i]; val j = site / NR; val r = site % NR
            val su = w.colU(j); val sv = w.rowV(j, r)
            val e = w.ionEnd[i]
            val t0 = w.ionT[i]
            if (!w.ionArr[i]) {
                w.ionT[i] += dt / D_LEAVE
                val t = w.ionT[i]
                if (t0 < T_DET && t >= T_DET) { w.occ[site] = false; w.reserved[site] = false; spawnElectrons(w, site, false, m.n) }
                if (t >= 1f) { w.ionOn[i] = false; n.visible = false; hn.visible = false; continue }
                n.visible = t >= T_DET; hn.visible = n.visible
                if (n.visible) {
                    val f = ease(((t - T_DET) / (1f - T_DET)).coerceIn(0f, 1f))
                    val fade = if (t > 0.86f) ((1f - t) / 0.14f).coerceIn(0f, 1f) else 1f
                    val pos = w.at(su + (e[0] - su) * f, sv + (e[1] - sv) * f, e[2] * f)
                    // the atom shrinks to the ion as it leaves (it lost n electrons)
                    val sc = (R_M + (ir - R_M) * ((t - T_DET) / 0.15f).coerceIn(0f, 1f)) * fade
                    n.pos = pos; n.uniformScale(maxOf(sc, 1e-4f))
                    hn.pos = pos; hn.uniformScale(maxOf(0.025f * ((t - T_DET) / 0.2f).coerceIn(0.2f, 1f) * fade, 1e-4f))
                }
            } else {
                w.ionT[i] += dt / D_ARR
                val t = w.ionT[i]
                if (t >= T_LAND) { w.occ[site] = true; w.reserved[site] = false; spark(w, site); w.ionOn[i] = false; n.visible = false; hn.visible = false; continue }
                val f = ease((t / T_LAND).coerceIn(0f, 1f))
                val grow = (t / 0.08f).coerceIn(0f, 1f)
                val pos = w.at(e[0] + (su - e[0]) * f, e[1] + (sv - e[1]) * f, e[2] * (1f - f))
                n.visible = true; hn.visible = true
                n.pos = pos; n.uniformScale(maxOf(ir * grow, 1e-4f))
                hn.pos = pos; hn.uniformScale(maxOf(0.025f * grow * (1f - 0.6f * ((t - 0.75f) / 0.17f).coerceIn(0f, 1f)), 1e-4f))
            }
        }
        for (e in 0 until NE) {
            val n = w.elec[e]
            if (!w.eOn[e]) { n.visible = false; continue }
            w.eT[e] += dt / w.eDur[e]
            val t = w.eT[e]
            if (t >= 1f) { w.eOn[e] = false; n.visible = false; continue }
            val site = w.eSite[e]; val su = w.colU(site / NR); val sv = w.rowV(site / NR, site % NR)
            val u: Float; val v: Float
            if (!w.eArr[e]) {
                if (t < 0.25f) { u = su + (U_E - su) * (t / 0.25f); v = sv } else { u = U_E; v = sv + (V_TOP - sv) * ((t - 0.25f) / 0.75f) }
            } else {
                if (t < 0.75f) { u = U_E; v = V_TOP + (sv - V_TOP) * (t / 0.75f) } else { u = U_E + (su - U_E) * ((t - 0.75f) / 0.25f); v = sv }
            }
            n.visible = true
            n.pos = w.at(u, v, w.eLane[e])
        }
        for (j in 0 until NC) for (r in 0 until NR) {
            val idx = j * NR + r; val n = w.atoms[idx]
            n.visible = w.occ[idx]
            if (n.visible) {
                n.pos = w.at(w.colU(j), w.rowV(j, r), 0f)
                n.uniformScale(if (j == NC - 1 && w.slide < 0f) maxOf(R_M * (1f + w.slide), 1e-4f) else R_M)
            }
        }
        for (i in 0 until NS) {
            val n = w.sparks[i]
            if (w.spT[i] < 0f) { n.visible = false; continue }
            w.spT[i] += dt
            val b = w.spT[i] / 0.35f
            if (b >= 1f) { w.spT[i] = -1f; n.visible = false; continue }
            val site = w.spSite[i]
            n.visible = true
            n.pos = w.at(w.colU(site / NR), w.rowV(site / NR, site % NR), 0.012f)
            n.uniformScale(0.006f + 0.018f * sin(PI.toFloat() * b))
        }
        // spectators: the solution's cations and anions wander
        val nitrate = m.anion == Anion.NITRATE
        for (j in 0 until NW) {
            val a = w.wander[j]
            val p = w.at((a[0] + 0.02f * sin(a[2] + a[3] * clock)).coerceIn(0.06f, 0.198f), (a[1] + 0.02f * sin(a[5] + a[4] * clock)).coerceIn(-0.17f, 0.17f), 0.02f * sin(a[2] + 0.7f * clock))
            w.cats[j].pos = p; w.catHalos[j].pos = p
            val b = w.wander[NW + j]
            val q = w.at((b[0] + 0.02f * sin(b[2] + b[3] * clock)).coerceIn(0.06f, 0.198f), (b[1] + 0.02f * sin(b[5] + b[4] * clock)).coerceIn(-0.17f, 0.17f), 0.02f * sin(b[5] + 0.6f * clock))
            w.so4[j].visible = !nitrate; w.no3[j].visible = nitrate
            val g = if (nitrate) w.no3[j] else w.so4[j]
            g.pos = q; g.rot = Q.axisAngle(0.3f, 1f, 0.2f, clock * 0.8f + j + w.k)
            w.anHalos[j].pos = q
        }
    }

    private fun applyWindowMetal(w: Win, m: Metal) {
        w.metal = m
        for (n in w.atoms) n.setMat(atomMat(m))
        for (n in w.ions) n.setMat(ionMat(m))
        for (n in w.cats) { n.setMat(ionMat(m)); n.uniformScale(R_M * (0.55f + 0.45f * ionRatio(m))) }
        w.plate.setMat(stripMat(m, 0))
    }

    private fun maybeSlide(w: Win) {
        val c0 = (0 until NR).count { w.occ[it] }
        val c1 = (0 until NR).count { w.occ[NR + it] }
        if (c0 == NR) {                                            // a new layer is complete: the view backs off one layer
            for (j in NC - 1 downTo 1) for (r in 0 until NR) w.occ[j * NR + r] = w.occ[(j - 1) * NR + r]
            for (r in 0 until NR) w.occ[r] = false
            w.colBase -= 1; w.slide = 1f
        } else if (c0 == 0 && c1 == 0) {                           // the surface layer is gone: follow the receding surface
            for (j in 1 until NC - 1) for (r in 0 until NR) w.occ[j * NR + r] = w.occ[(j + 1) * NR + r]
            for (r in 0 until NR) w.occ[(NC - 1) * NR + r] = true
            w.colBase += 1; w.slide = -1f
        }
    }

    private fun freeIon(w: Win) = (0 until NI).firstOrNull { !w.ionOn[it] }
    private fun freeElectrons(w: Win) = (0 until NE).count { !w.eOn[it] }

    /** Anode: an outer atom gives up n electrons and leaves as the (smaller) ion. */
    private fun spawnLeave(w: Win, n: Int): Boolean {
        val i = freeIon(w) ?: return false
        if (freeElectrons(w) < n) return false
        var cand = (0 until NR).map { it }.filter { w.occ[it] && !w.reserved[it] }
        if (cand.isEmpty()) cand = (0 until NR).map { NR + it }.filter { w.occ[it] && !w.reserved[it] }
        if (cand.isEmpty()) return false
        val site = cand[(rnd() * cand.size).toInt().coerceAtMost(cand.size - 1)]
        w.reserved[site] = true
        w.ionOn[i] = true; w.ionArr[i] = false; w.ionT[i] = 0f; w.ionSite[i] = site
        val sv = w.rowV(site / NR, site % NR)
        w.ionEnd[i][0] = 0.15f + 0.05f * rnd(); w.ionEnd[i][1] = (sv + (rnd() - 0.5f) * 0.12f).coerceIn(-0.16f, 0.16f); w.ionEnd[i][2] = (rnd() - 0.5f) * 0.05f
        spark(w, site)
        return true
    }

    /** Cathode: an ion from the solution lands on a free lattice site while n electrons come down to meet it. */
    private fun spawnArrive(w: Win, n: Int): Boolean {
        val i = freeIon(w) ?: return false
        if (freeElectrons(w) < n) return false
        var cand = (0 until NR).map { NR + it }.filter { !w.occ[it] && !w.reserved[it] }
        if (cand.isEmpty()) cand = (0 until NR).filter { !w.occ[it] && !w.reserved[it] }
        if (cand.isEmpty()) return false
        val site = cand[(rnd() * cand.size).toInt().coerceAtMost(cand.size - 1)]
        w.reserved[site] = true
        w.ionOn[i] = true; w.ionArr[i] = true; w.ionT[i] = 0f; w.ionSite[i] = site
        w.ionEnd[i][0] = 0.192f; w.ionEnd[i][1] = -0.15f + 0.30f * rnd(); w.ionEnd[i][2] = (rnd() - 0.5f) * 0.05f
        spawnElectrons(w, site, true, n)
        return true
    }

    private fun spawnElectrons(w: Win, site: Int, arriving: Boolean, n: Int) {
        var lane = 0
        for (e in 0 until NE) {
            if (lane >= n) break
            if (w.eOn[e]) continue
            w.eOn[e] = true; w.eArr[e] = arriving; w.eT[e] = 0f; w.eSite[e] = site
            w.eDur[e] = if (arriving) T_LAND * D_ARR else 1.6f
            w.eLane[e] = 0.020f + 0.010f * lane
            lane++
        }
    }

    private fun spark(w: Win, site: Int) {
        val i = (0 until NS).firstOrNull { w.spT[it] < 0f } ?: 0
        w.spT[i] = 0f; w.spSite[i] = site
    }

    // ---------------------------------------------------------------- text

    private fun eOf(n: Int) = if (n == 1) "e⁻" else "${n}e⁻"
    private fun oxid(m: Metal) = "${m.symbol} → ${m.ion} + ${eOf(m.n)}"
    private fun redn(m: Metal) = "${m.ion} + ${eOf(m.n)} → ${m.symbol}"
    private fun signed3(v: Double) = if (v < 0) "−%.3f".format(-v) else "%.3f".format(v)
    private fun grams(v: Double) = if (v < -5e-4) "−%.3f".format(-v) else "+%.3f".format(maxOf(v, 0.0))

    private val SUP = mapOf('-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹')
    /** mol/L: 1.000 · 0.0420 · 3.2×10⁻⁵ */
    private fun fmtC(c: Double): String = when {
        c >= 0.0995 -> "%.3f".format(c)
        c >= 0.000995 -> "%.4f".format(c)
        c <= 0.0 -> "0"
        else -> { val e = floor(log10(c)).toInt(); "%.1f×10%s".format(c / 10.0.pow(e), e.toString().map { SUP[it] ?: it }.joinToString("")) }
    }

    private fun elapsed(t: Double): String {
        val s = t.toLong()
        return when {
            s < 60 -> tr("%d 秒", "%d s").format(s)
            s < 3600 -> tr("%d 分 %02d 秒", "%d min %02d s").format(s / 60, s % 60)
            s < 86400 -> tr("%d 时 %02d 分", "%d h %02d min").format(s / 3600, (s % 3600) / 60)
            else -> tr("%d 天 %02d 时", "%d d %02d h").format(s / 86400, (s % 86400) / 3600)
        }
    }

    private fun updateReadout() {
        val pol = cell.polarity()
        val an = cell.anode(); val ca = cell.cathode()
        val nn = Galv.electrons(an.metal, ca.metal)
        val l1 = if (pol == 0) tr("两极都是 %s|%s：E° = 0，浓度也相同 → 没有电势差（改一边浓度 = 浓差电池）",
                "Both sides %s|%s: E° = 0 and equal concentrations → no potential difference (change one: a concentration cell)").format(an.metal.symbol, an.metal.ion)
            else tr("负极 %s · 正极 %s · E°池 = %s − (%s) = %.3f V",
                "Anode (−) %s · Cathode (+) %s · E°cell = %s − (%s) = %.3f V").format(oxid(an.metal), redn(ca.metal), signed3(ca.metal.e0), signed3(an.metal.e0), cell.standardEmf())
        val e = abs(cell.emf())
        val l2 = when {
            !cell.bridgeIn -> tr("盐桥已拿走：离子不能迁移，两杯电荷堆积 → 内电路断开：I = 0，电压表 0 V（电动势 E = %.3f V 还在）",
                "Salt bridge out: ions cannot migrate, charge piles up → loop open: I = 0, voltmeter 0 V (EMF still %.3f V)").format(e)
            !cell.intact -> tr("负极 %s 浸在溶液里的部分已经溶完：电路断开，I = 0", "The anode %s has dissolved through below the waterline: the circuit is open, I = 0").format(an.metal.symbol)
            pol == 0 -> tr("E = 0：两个电极电势相等，电子没有理由流动", "E = 0: both electrodes at the same potential, electrons have no reason to flow")
            !cell.switchClosed -> tr("E = E° − (0.0592/%d) lg Q = %.3f V · 开关断开：I = 0 → 高阻电压表读到的就是电动势 E",
                "E = E° − (0.0592/%d) lg Q = %.3f V · switch open: I = 0 → the high-impedance voltmeter reads the EMF itself").format(nn, e)
            else -> tr("E = E° − (0.0592/%d) lg Q = %.3f V · 端电压 U = E − Ir = %.3f V（r = 80 Ω）· I = %.2f mA · 风扇 %.2f mW",
                "E = E° − (0.0592/%d) lg Q = %.3f V · terminal U = E − Ir = %.3f V (r = 80 Ω) · I = %.2f mA · fan %.2f mW").format(
                nn, e, abs(cell.meterVolts()), cell.loadCurrent() * 1e3, cell.loadPower() * 1e3)
        }
        val speed = SPEED_LABELS[speedIdx]
        val q = cell.q
        val l3 = tr("通电 %s（%s）· Q = %s C · %s %s g · %s %s g · [%s] %s · [%s] %s mol/L",
            "Elapsed %s (%s) · Q = %s C · %s %s g · %s %s g · [%s] %s · [%s] %s mol/L").format(
            elapsed(cell.t), speed, if (q < 10) "%.2f".format(q) else "%.0f".format(q),
            cell.left.metal.symbol, grams(cell.left.dMass), cell.right.metal.symbol, grams(cell.right.dMass),
            cell.left.metal.ion, fmtC(cell.left.c), cell.right.metal.ion, fmtC(cell.right.c))
        readoutText = "$l1\n$l2\n$l3"
    }

    private fun updateLabels() {
        labels.clear()
        val moving = cell.cellCurrent() > 1e-5
        for (k in 0..1) {
            val h = cell.half(k)
            val head = when (roleOf(k)) { -1 -> tr("负极（−）", "Anode (−) "); 1 -> tr("正极（+）", "Cathode (+) "); else -> "" }
            labels += LabelSpec("%s%s | %s %s M".format(head, h.metal.symbol, h.metal.salt, fmtC(h.c)), P3(BX[k], BENCH + 0.03f, BZ + BR + 0.05f))
        }
        labels += LabelSpec(if (cell.bridgeIn) tr("盐桥 KNO₃：K⁺ → 正极 · NO₃⁻ → 负极", "Salt bridge KNO₃: K⁺ → cathode · NO₃⁻ → anode")
            else tr("盐桥已拿出 —— 捏它放回", "Salt bridge lifted out — pinch it to put it back"), P3(0f, -0.05f, 0.11f))
        labels += LabelSpec(tr("电压表 %s V · 电流表 %.2f mA", "Voltmeter %s V · Ammeter %.2f mA").format(signed3(cell.meterVolts()), cell.loadCurrent() * 1e3),
            P3(0f, 0.465f, -0.08f))
        for (k in 0..1) {
            val m = cell.half(k).metal
            val txt = when (roleOf(k)) {
                -1 -> tr("负极表面：%s（氧化）", "Anode surface: %s (oxidation)").format(oxid(m))
                1 -> tr("正极表面：%s（还原）", "Cathode surface: %s (reduction)").format(redn(m))
                else -> tr("%s 表面：无净反应", "%s surface: no net reaction").format(m.symbol)
            }
            labels += LabelSpec(txt + if (moving || roleOf(k) == 0) "" else tr(" · 已停止", " · stopped"), P3(WIN_C[k].x, WIN_C[k].y + WIN_H / 2 + 0.045f, WIN_C[k].z))
        }
    }

    // ---------------------------------------------------------------- interaction

    override val tapTargets get() = listOf(
        TapTarget("Bridge", "盐桥 → 拿出/放回", "Bridge → lift/replace") { cell.bridgeIn },
        TapTarget("Switch", "开关 → 合上/断开", "Switch → on/off") { cell.switchClosed },
        TapTarget("ElecL", "左电极 → 换金属", "Left strip → swap"),
        TapTarget("ElecR", "右电极 → 换金属", "Right strip → swap"),
    )

    override fun isTarget(name: String) = name == "Bridge" || name == "Switch" || name == "ElecL" || name == "ElecR"

    override fun onTap(name: String) {
        lastTap = when (name) {
            "Bridge" -> {
                cell.bridgeIn = !cell.bridgeIn; bridgeTaps++
                if (!cell.bridgeIn) tr("拿走盐桥：离子不能迁移，两边电荷堆积 → 电流立刻停止，电压表回到 0 V",
                    "Salt bridge out: ions can no longer migrate, charge piles up on both sides → the current stops at once, the voltmeter drops to 0 V")
                else if (!cell.switchClosed) tr("放回盐桥：内电路接通；开关断开，电压表读到电动势 E = %.3f V",
                    "Salt bridge back: the inner circuit is closed; with the switch open the voltmeter reads the EMF, E = %.3f V").format(abs(cell.meterVolts()))
                else tr("放回盐桥：K⁺ 流向正极、NO₃⁻ 流向负极，两杯保持电中性 → 电流恢复 %.2f mA",
                    "Salt bridge back: K⁺ drift to the cathode, NO₃⁻ to the anode, both beakers stay neutral → the current is back, %.2f mA").format(cell.loadCurrent() * 1e3)
            }
            "Switch" -> {
                cell.switchClosed = !cell.switchClosed; switchTaps++
                when {
                    !cell.switchClosed && !cell.closedLoop -> tr("断开开关：盐桥不在，电路本来就是断的，I = 0、电压表 0 V",
                        "Switch open: the salt bridge is out, so the loop was open anyway: I = 0, voltmeter 0 V")
                    !cell.switchClosed -> tr("断开开关：外电路无电流，风扇停转 —— 电压表读到的是电动势 E = %.3f V",
                        "Switch open: no current in the wire, the fan stops — the voltmeter now reads the EMF, E = %.3f V").format(abs(cell.meterVolts()))
                    !cell.closedLoop -> tr("合上开关：但盐桥不在，电路仍然是断的，I = 0", "Switch closed — but without the salt bridge the loop is still open, I = 0")
                    else -> tr("合上开关：I = %.2f mA，风扇转起来；端电压降到 U = E − Ir = %.3f V",
                        "Switch closed: I = %.2f mA, the fan spins; the terminal voltage drops to U = E − Ir = %.3f V").format(cell.loadCurrent() * 1e3, abs(cell.meterVolts()))
                }
            }
            "ElecL", "ElecR" -> {
                val k = if (name == "ElecL") 0 else 1
                val p0 = cell.polarity()
                val m = cell.half(k).metal.next()
                setMetal(k, m)
                val p1 = cell.polarity()
                val side = if (k == 0) tr("左", "Left") else tr("右", "Right")
                val head = tr("%s电极换成 %s（%s %s M）：", "%s strip is now %s (%s %s M): ").format(side, m.symbol, m.salt, fmtC(cSet[k]))
                head + when {
                    p1 == 0 -> tr("两边金属和浓度都相同 → E = 0，没有电流", "same metal, same concentration on both sides → E = 0, no current")
                    p0 != 0 && p1 != p0 -> tr("E°池 = %.3f V，极性反转！电子改为从%s流向%s", "E°cell = %.3f V, the polarity flips! Electrons now flow %s to %s").format(
                        cell.standardEmf(), if (p1 > 0) tr("左", "left") else tr("右", "right"), if (p1 > 0) tr("右", "right") else tr("左", "left"))
                    else -> tr("E°池 = %.3f V，%s 是负极", "E°cell = %.3f V, %s is the anode").format(cell.standardEmf(), cell.anode().metal.symbol)
                }
            }
            else -> ""
        }
        frame = 0
    }

    // ---------------------------------------------------------------- guided track (导学)
    // KEY INSIGHT: the voltage is a difference in how readily two metals give up electrons — Zn more than
    // Cu, E° = 0.34 − (−0.76) = 1.10 V — and it only drives a current when the loop is closed twice:
    // electrons through the wire AND ions through the salt bridge. Metals further apart in the series
    // (Mg Zn Fe Pb Cu Ag) give a higher voltage.

    /** Lesson bookkeeping: switch / bridge pinches seen so far, and the baseline a step compares against. */
    private var switchTaps = 0
    private var bridgeTaps = 0
    private var mark = 0

    // ---- background card (📖): context for a viewer who has never met a galvanic cell
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("桌上两只烧杯：锌片泡在无色硫酸锌里，铜片泡在蓝色硫酸铜里，中间用装硝酸钾凝胶的 U 形管（盐桥）连通。上方电路板有电压表、开关、小风扇和电流表。青色小珠是导线里的电子，紫色是盐桥里的离子；两扇放大窗看金属表面原子的变化。捏盐桥、捏开关，或捏金属片换成镁、铁、铅、银。", "Two beakers: a zinc strip in colourless zinc sulfate and a copper strip in blue copper sulfate, joined by a U-tube of potassium nitrate gel, the salt bridge. Above: a voltmeter, a switch, a small fan and an ammeter. Cyan beads are electrons in the wire, violet ones ions in the bridge, and two magnifier windows watch the metal surfaces. Pinch the bridge, the switch, or a strip to swap in magnesium, iron, lead or silver."),
        ),
        About.Page(
            Bi("一块稳定的电池", "A battery that kept going"),
            Bi("1800 年伏打发明了电堆，人类第一次有了持续的电流，但它的电压用一会儿就往下掉。1836 年，英国化学家丹尼尔把锌和铜分装在两种溶液里，中间隔开，做出了电压稳定、能长时间供电的电池。那时电报刚刚兴起，丹尼尔电池成了早期电报线路的可靠电源。", "Volta's pile of 1800 gave the first steady electric current, but its voltage sagged after a short while. In 1836 the English chemist John Daniell put zinc and copper in two separate solutions, kept apart, and got a cell whose voltage held steady for a long time. The electric telegraph was just taking off, and the Daniell cell became a dependable power source for early telegraph lines."),
        ),
        About.Page(
            Bi("核心想法：两条路缺一不可", "Key idea: two paths, one loop"),
            Bi("锌比铜更愿意丢电子。锌直接泡进硫酸铜，电子当场交给铜离子，只放出热。把两边分开，电子只能绕道走导线，途中转动风扇。但回路要闭合两次：电子走导线，离子走盐桥。像一圈环形水渠，水车装在导线这段，盐桥是水流回去的那段，断了哪一段都停。锌铜之间是 1.10 V。", "Zinc gives up electrons more readily than copper. Drop zinc straight into copper sulfate and the electrons jump across on the spot, giving only heat. Separate the two and the electrons must detour through the wire, turning the fan on the way. But the loop must close twice: electrons through the wire, ions through the bridge. Like a ring-shaped water channel: break either part and the flow stops. Zinc against copper gives 1.10 V."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("你手里的每节电池都是同一个思路：两种得失电子能力不同的材料，加一条只让离子通过的通道。碱性电池用锌和二氧化锰；锂电池里锂离子穿过隔膜来回跑，隔膜就是今天的“盐桥”。锂在金属序列里排得最远，所以单节锂电池电压可达约 3.7 V。船身上焊锌块，也是让锌替钢铁先被腐蚀。", "Every battery you own follows the same plan: two materials that differ in how eagerly they give up electrons, plus a path that lets only ions through. Alkaline cells pair zinc with manganese dioxide; in a lithium-ion cell the ions shuttle through a separator, today's salt bridge. Lithium sits furthest out in the series, so one cell gives about 3.7 V. Zinc blocks on ship hulls corrode in place of the steel by the same rule."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("电压表现在约 0.61 V。捏电路板顶上的开关（或「开关 → 合上/断开」）断开它，再读电压表。", "The voltmeter reads about 0.61 V. Pinch the switch on top of the board (or “Switch → on/off”) to open it, then read it again."),
                    target = "Switch",
                    // real time for the lesson: at 1 s = 1 day the Cu²⁺ would run out (E → 0) within ~35 s
                    onEnter = { speedIdx = 0; mark = switchTaps },
                    done = { !cell.switchClosed && switchTaps > mark },
                    autoplay = { repeat(if (cell.switchClosed) 1 else 2) { onTap("Switch") } },
                    insight = Bi("开关断开时读到 1.10 V，这就是电动势：锌比铜更爱丢电子，E° 差 0.34 − (−0.76) = 1.10 V。", "With the switch open it reads 1.10 V, the EMF: zinc gives up electrons more readily than copper, E° 0.34 − (−0.76) = 1.10 V."),
                ),
                LessonStep(
                    say = Bi("如果把中间的盐桥拿走（导线还连着），电压表会怎样？", "If you lift out the salt bridge in the middle (the wires stay connected), what does the voltmeter do?"),
                    options = listOf(
                        Bi("还是 1.10 V", "Still 1.10 V"),
                        Bi("降到 0 V", "Drops to 0 V"),
                        Bi("指针反偏", "Swings backwards"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏住盐桥（或「盐桥 → 拿出/放回」）把它提出来，看电压表。", "Pinch the salt bridge (or “Bridge → lift/replace”) to lift it out — watch the voltmeter."),
                    target = "Bridge",
                    onEnter = { mark = bridgeTaps },
                    done = { !cell.bridgeIn && bridgeTaps > mark },
                    autoplay = { repeat(if (cell.bridgeIn) 1 else 2) { onTap("Bridge") } },
                    reveal = true,
                    insight = Bi("电压表 0 V：电子走导线，离子要走盐桥。两条路缺一条，回路就断了。", "The voltmeter falls to 0 V: electrons take the wire, ions must take the bridge. Lose either path and the loop is open."),
                ),
                LessonStep(
                    say = Bi("捏盐桥放回去，再捏右边的铜片（或「右电极 → 换金属」）换成银 Ag。", "Pinch the bridge back in, then pinch the copper strip on the right (or “Right strip → swap”) to make it silver, Ag."),
                    target = "ElecR",
                    done = { cell.bridgeIn && cell.right.metal == Metal.AG },
                    autoplay = {
                        if (!cell.bridgeIn) onTap("Bridge")
                        if (cell.right.metal == Metal.CU) onTap("ElecR") else setMetal(1, Metal.AG)
                    },
                    insight = Bi("Zn|Ag：E° = 0.80 − (−0.76) = 1.56 V。两种金属在 Mg Zn Fe Pb Cu Ag 里隔得越远，电压越高。", "Zn|Ag: E° = 0.80 − (−0.76) = 1.56 V. The further apart two metals sit in Mg Zn Fe Pb Cu Ag, the higher the voltage."),
                ),
            ),
            challenge = Challenge(
                say = Bi("只换金属，搭一个电动势超过 3 V 的电池（盐桥要放好）。", "Only by swapping metals, build a cell whose EMF is above 3 V (with the salt bridge in)."),
                progress = { tr("E°池 = %.2f V（%s | %s）· 目标 > 3 V", "E°cell = %.2f V (%s | %s) · goal > 3 V").format(cell.standardEmf(), cell.left.metal.symbol, cell.right.metal.symbol) },
                done = { cell.closedLoop && cell.standardEmf() > 3.0 },
                // the two ends of the series: magnesium gives electrons most readily, silver least
                autoplay = {
                    if (!cell.bridgeIn) onTap("Bridge")
                    setMetal(0, Metal.MG); setMetal(1, Metal.AG)
                },
            ),
        )
    }

    override fun tapMessage(name: String): String? = lastTap.ifEmpty { null }

    /** The switch sits at the top of the board: its note goes beside it, not up into the top menu bar. */
    override fun anchorOf(name: String): FloatArray? = if (name == "Switch") floatArrayOf(0.17f, 0.43f, -0.03f) else super.anchorOf(name)

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "aged" to { resetAll(); cell.step(26 * 86400.0) },
        "bridge_out" to { resetAll(); onTap("Bridge") },
        "silver_flip" to { resetAll(); setMetal(0, Metal.AG) },
        "mg_ag" to { resetAll(); setMetal(0, Metal.MG); setMetal(1, Metal.AG) },
    )

    @Composable
    override fun Panel() {
        var si by FieldRef(::speedIdx)
        LabPanel(readout) {
            for (k in 0..1) {
                val h: HalfCell = cell.half(k)
                SegmentControl {
                    Metal.entries.forEach { m -> SegmentItem(selected = h.metal == m, onClick = { setMetal(k, m) }, title = { Text(m.symbol) }) }
                }
                val lc = log10(h.c.coerceAtLeast(1e-3)).toFloat().coerceIn(-3f, 0.3f)
                val label = (if (k == 0) tr("左杯 [%s]", "Left [%s]") else tr("右杯 [%s]", "Right [%s]")).format(h.metal.ion)
                LabSlider(label, lc, -3f..0.3f, "%s M".format(fmtC(h.c)), width = 380) { setConc(k, 10.0.pow((it * 20).roundToInt() / 20.0)) }
            }
            SegmentControl {
                SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k; speedIdx = k }, title = { Text(l) }) }
            }
            Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Needle angle (rad, clockwise from up): ±55° over ±full scale, pinned just past the stops. */
    private fun meterAngle(v: Double, fs: Double): Float = Math.toRadians(55.0 * (v / fs).coerceIn(-1.06, 1.06)).toFloat()

    private fun ease(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    private fun rnd(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 40).toInt() and 0xFFFFFF) / 16777216f }

    private fun circleXZ(r: Float, n: Int) = FloatArray(n * 3) { k -> val a = 2.0 * PI * (k / 3) / n; when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() } }
    private fun circleXY(r: Float, n: Int, cx: Float, cy: Float, cz: Float) = FloatArray(n * 3) { k ->
        val a = 2.0 * PI * (k / 3) / n
        when (k % 3) { 0 -> cx + (r * cos(a)).toFloat(); 1 -> cy + (r * sin(a)).toFloat(); else -> cz }
    }

    /** Catmull-Rom through [pts], [n] samples. */
    private fun spline(pts: List<P3>, n: Int): FloatArray {
        val out = FloatArray(n * 3)
        val segs = pts.size - 1
        for (i in 0 until n) {
            val u = i.toFloat() / (n - 1) * segs
            val k = u.toInt().coerceAtMost(segs - 1); val f = u - k
            val p0 = pts[maxOf(0, k - 1)]; val p1 = pts[k]; val p2 = pts[k + 1]; val p3 = pts[minOf(segs, k + 2)]
            fun cr(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f * f * f)
            out[i * 3] = cr(p0.x, p1.x, p2.x, p3.x); out[i * 3 + 1] = cr(p0.y, p1.y, p2.y, p3.y); out[i * 3 + 2] = cr(p0.z, p1.z, p2.z, p3.z)
        }
        return out
    }
}
