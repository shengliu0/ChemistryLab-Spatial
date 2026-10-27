// 反应速率 · 碰撞理论 (Arrhenius, 1889) — the "disappearing cross": Na₂S₂O₃ + 2HCl → 2NaCl + SO₂ + S↓ + H₂O.
//
// LESSON (macro + micro side by side). Four conical flasks of sodium thiosulfate stand in four water
// baths (15 / 25 / 35 / 45 °C), each in front of a card with a bold black X. Pinch a flask (or "start
// all") and a measuring tube tips hydrochloric acid in; a stopwatch starts. Colloidal sulfur makes the
// liquid milky pale-yellow until the X can no longer be seen through it — the hot flask in ~10 s, the
// cold one in ~84 s — and the stopwatch freezes green. Each time lands as a point on the results
// chart (1/t against T, or against concentration), on the Arrhenius curve. Pinch a bath's knob to
// change its temperature (+10 °C, or pinch-drag for any value); the concentration series dilutes the
// thiosulfate instead (1/t ∝ c: half the concentration, twice the time).
// The big glass cube on the right magnifies the SELECTED flask: A (blue, with a yellow reactive end)
// and B (orange) fly with Maxwell–Boltzmann speeds for that flask's temperature and collide; only
// collisions with enough energy along the line of centres AND hitting A's yellow side react (gold
// flash, both turn green), energetic-but-wrong-side hits flash orange, near misses flash faint blue.
// Beside it a live histogram of collision energies with the Ea line (the gold tail = the fraction
// that can react, ×20 so it is visible) and an energy profile where every collision rolls a ball up
// the hump. The catalyst jar lowers the barrier (53 → 40 kJ/mol): the Ea line slides left, a lower
// hump appears, far more collisions get over.
//
// Science: core/rates (Arrhenius, exact first-order run with the stated threshold model, collision
// theory + its Monte-Carlo check, the hard-sphere CollisionBox). The molecule window exaggerates
// molecular energies ×5 (real fraction 5×10⁻¹⁰ — never visible): every ratio it shows is the fifth
// root of the real one, as the readout says. Virtual only: SO₂ is a respiratory irritant.

package com.bytedance.chemistrylab.demos.rates

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.core.rates.Arrhenius
import com.bytedance.chemistrylab.core.rates.CollisionBox
import com.bytedance.chemistrylab.core.rates.Thiosulfate
import com.bytedance.chemistrylab.core.rates.XRun
import com.bytedance.chemistrylab.lab.Lang
import com.bytedance.chemistrylab.lab.Q
import com.bytedance.chemistrylab.lab.SpecDemo
import com.bytedance.chemistrylab.lab.TapTarget
import com.bytedance.chemistrylab.lab.spec.Blend
import com.bytedance.chemistrylab.lab.spec.LabelSpec
import com.bytedance.chemistrylab.lab.spec.Mat
import com.bytedance.chemistrylab.lab.spec.Node
import com.bytedance.chemistrylab.lab.tr
import com.bytedance.chemistrylab.lab.ui.Button
import com.bytedance.chemistrylab.lab.ui.LabPanel
import com.bytedance.chemistrylab.lab.ui.LabRow
import com.bytedance.chemistrylab.lab.ui.LabSlider
import com.bytedance.chemistrylab.lab.ui.LabSwitch
import com.bytedance.chemistrylab.lab.ui.SegmentControl
import com.bytedance.chemistrylab.lab.ui.SegmentItem
import com.bytedance.chemistrylab.render.Image
import com.bytedance.chemistrylab.render.MeshData
import com.bytedance.chemistrylab.render.MeshGen
import com.bytedance.chemistrylab.render.ProcTex
import com.bytedance.chemistrylab.render.Rgb
import com.pico.spatial.ui.design.Text
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RatesDemo : SpecDemo() {
    override val id = "rates"
    override val title = "反应速率 · 碰撞理论"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Arrhenius · 1889"

    // ---------------------------------------------------------------- layout (metres, volume space)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f
    private val SX = floatArrayOf(-0.79f, -0.55f, -0.31f, -0.07f)
    private const val SZ = 0.08f
    private const val STAND_H = 0.035f
    private const val STAND_TOP = DECK + STAND_H
    private const val STAND_W = 0.225f
    private const val STAND_D = 0.27f
    private const val STAND_ZC = SZ - 0.01f
    private const val DISH_R = 0.108f
    private const val DISH_H = 0.052f
    private const val DISH_FLOOR = STAND_TOP + 0.004f
    private const val WATER_Y = DISH_FLOOR + 0.036f
    private const val FB = DISH_FLOOR + 0.002f                     // flask base (a 100 mL flask drawn ~2.4x)
    private const val RB = 0.086f; private const val HS = 0.178f; private const val RN = 0.0225f; private const val HT = 0.242f
    private const val LIQ_H = 0.113f
    // the X: printed on a paper label taped round the BACK of each flask, below the liquid line, so you
    // always look at it THROUGH the solution, from any angle (a card standing behind showed above the
    // liquid from higher viewpoints). Classic set-ups put the paper under the flask and look down.
    private const val X_HC = 0.045f                                // X centre above the flask base
    private const val X_HALF = 0.030f
    private const val X_INK = 0.0112f
    private const val LABEL_H0 = 0.006f; private const val LABEL_H1 = 0.088f
    private const val LABEL_A = 1.0f                               // half the label's arc, radians, about the back (3π/2)
    private const val TUBE_R = 0.012f; private const val TUBE_H = 0.085f
    private const val CON_Z = SZ + 0.240f
    private const val TIMER_PITCH = 0.030f * 0.546f                // seven-segment digit advance at 30 mm digit height
    // the molecule window, the histogram and the energy profile (right)
    private val MB = P3(0.345f, 0.125f, -0.045f)
    private const val WIN = 0.18f
    private const val HX0 = 0.598f; private const val HX1 = 0.868f; private const val HY0 = 0.000f; private const val HY1 = 0.200f; private const val HZ = -0.085f
    private const val EX0 = 0.598f; private const val EX1 = 0.868f; private const val EY0 = 0.268f; private const val EY1 = 0.392f; private const val EZ = -0.085f
    // the results chart (above the flasks)
    private const val PX0 = -0.80f; private const val PX1 = -0.15f; private const val PY0 = 0.160f; private const val PY1 = 0.365f; private const val PZ = -0.195f
    private val JAR = P3(0.112f, DECK, 0.225f)

    // ---------------------------------------------------------------- experiment constants
    private val SPEEDS = floatArrayOf(1f, 4f, 12f)
    private val SPEED_LABELS get() = listOf(tr("实时 ×1", "Real ×1"), tr("快进 ×4", "Fast ×4"), tr("快进 ×12", "Fast ×12"))
    private val PRESET_T = floatArrayOf(15f, 25f, 35f, 45f)
    private val PRESET_C = floatArrayOf(0.15f, 0.12f, 0.09f, 0.06f)
    private const val TAU_TH = 8.0                                  // s: flask liquid follows its bath
    private const val POUR_IN = 0.45f; private const val POUR_MIX = 0.60f; private const val POUR_OUT = 0.85f; private const val POUR_END = 1.35f
    private const val PREROLL = 420                                 // frames run at build: the demo opens 7 s into "start all"
    private const val MICRO_WARM = 900                              // 15 s of molecule window first, so its histogram opens filled
    private const val HIST_BINS = 24
    private const val HIST_W = 5.0                                  // kJ/mol per bar
    private const val HIST_EMAX = 120.0
    private const val TAIL_GAIN = 20.0
    private const val HIST_FULL = 0.34                              // bar fraction at full height
    private const val E_MIN = -20.0; private const val E_MAX = 62.0; private const val E_PROD = -12.0   // energy profile (schematic levels)
    private val STATION_COL = arrayOf(Rgb(0.40f, 0.72f, 1.0f), Rgb(0.45f, 0.95f, 0.62f), Rgb(1.0f, 0.82f, 0.32f), Rgb(1.0f, 0.48f, 0.32f))

    // ---------------------------------------------------------------- UI state (panel reads these through the readout recomposition)
    var speedIdx = 0
    var modeIdx = 0                  // 0: temperature series, 1: concentration series
    var catalyst = false
    var selected = 3
    val bathT = FloatArray(4)        // °C
    val conc = FloatArray(4)         // mol/L Na₂S₂O₃

    // ---------------------------------------------------------------- run state
    private const val IDLE = 0; private const val POURING = 1; private const val RUNNING = 2
    private val phase = IntArray(4)
    private val pourT = FloatArray(4)
    private val runs = arrayOfNulls<XRun>(4)
    private val liquidK = DoubleArray(4)
    private val doneFlash = FloatArray(4) { 9f }
    private class Result(val station: Int, val mode: Int, val tC: Double, val c0: Double, val t: Double)
    private val results = ArrayList<Result>()
    private var lastDone = -1
    private var lastDoneAge = 99f
    private var realT = 0f
    private var frame = 0
    private var textLang = -1
    private var lastTap = ""
    private var dragBath = -1; private var dragT0 = 0f; private var dragAcc = 0f
    private var lastBathEvent = -1; private var lastBathFrom = 25f; private var lastBathAge = 99f

    // micro
    private val micro = CollisionBox(seed = 1889L)
    private var eaShown = Thiosulfate.EA
    private var evSeen = 0L
    private var histAge = 9f
    private var histCurveT = -1.0; private var histCurveEa = -1.0
    private var chartDirty = true
    private var chartScale = 0.1
    private var chartKey = ""

    // ---------------------------------------------------------------- nodes
    private val liquid = arrayOfNulls<Node>(4)
    private val milky = arrayOfNulls<Node>(4)
    private val milkyIdx = IntArray(4) { -1 }
    private val water = arrayOfNulls<Node>(4)
    private val waterKey = IntArray(4) { Int.MIN_VALUE }
    private val led = arrayOfNulls<Node>(4)
    private val ice = Array(4) { ArrayList<Node>() }
    private val steam = Array(4) { ArrayList<Node>() }
    private val tube = arrayOfNulls<Node>(4)
    private val acid = arrayOfNulls<Node>(4)
    private val stream = arrayOfNulls<Node>(4)
    private val knob = arrayOfNulls<Node>(4)
    private val bathDigits = Array(4) { arrayOfNulls<Node>(2) }
    private val timerDigits = Array(4) { arrayOfNulls<Node>(3) }
    private val timerDot = arrayOfNulls<Node>(4)
    private val digitShown = Array(4) { IntArray(5) { -99 } }
    private val doneRing = arrayOfNulls<Node>(4)
    private lateinit var selRing: Node
    private lateinit var window: Node
    private lateinit var winFloor: Node
    private lateinit var parts: Array<Node>
    private val partMat = IntArray(micro.n) { -1 }
    private lateinit var flashes: Array<Node>
    private lateinit var bursts: Array<Node>
    private lateinit var lines: Array<Node>
    private lateinit var bars: Array<Node>
    private val barMatIdx = IntArray(HIST_BINS + 1) { -1 }
    private lateinit var eaLine: Node
    private lateinit var histCurve: MeshData
    private lateinit var chartCurve: MeshData
    private lateinit var points: Array<Node>
    private lateinit var tickT: List<Node>
    private lateinit var tickC: List<Node>
    private lateinit var yTickMid: Node
    private lateinit var yTickTop: Node
    private lateinit var catHump: Node
    private lateinit var catArrow: Node
    private lateinit var balls: Array<Node>
    private val ballKind = IntArray(6); private val ballPhi = DoubleArray(6); private val ballAge = FloatArray(6) { 99f }
    private val ballDur = FloatArray(6); private val ballCat = BooleanArray(6)
    private lateinit var jarGlow: Node
    private var ribbonTurn = 0

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val blackPlastic = Mat.Pbr(Rgb(0.06f, 0.065f, 0.075f), metallic = 0f, roughness = 0.35f)
    private val glass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.07f)
    private val flaskGlass = Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), metallic = 0f, roughness = 0.02f, alpha = 0.12f)
    private val glassBack = Mat.Pbr(Rgb(0.07f, 0.09f, 0.13f), metallic = 0f, roughness = 0.06f, alpha = 0.66f)
    private val glassClear = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), metallic = 0f, roughness = 0.04f, alpha = 0.05f)
    private val board = Mat.Pbr(Rgb(0.035f, 0.042f, 0.058f), metallic = 0f, roughness = 0.10f)   // opaque: curves in front sort cleanly
    private val axisMat = Mat.Unlit(Rgb(0.78f, 0.86f, 0.95f), alpha = 0.80f, blend = Blend.TRANSPARENT)
    private val gridMat = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.22f, blend = Blend.TRANSPARENT)
    private val solution = Mat.Pbr(Rgb(0.82f, 0.92f, 1.0f), metallic = 0f, roughness = 0.05f, alpha = 0.14f)
    private val acidMat = Mat.Pbr(Rgb(0.80f, 0.93f, 1.0f), metallic = 0f, roughness = 0.05f, alpha = 0.40f)
    private val surfaceRing = Mat.Unlit(Rgb(0.70f, 0.88f, 1.0f), alpha = 0.22f, blend = Blend.ADD)
    private val iceMat = Mat.Pbr(Rgb(0.88f, 0.95f, 1.0f), metallic = 0f, roughness = 0.12f, alpha = 0.78f, emissive = Rgb(0.10f, 0.14f, 0.18f))
    private val selMat = Mat.Unlit(Rgb(0.45f, 0.85f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val slabTex by lazy { ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77) }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val blobTex: Image by lazy { RatesMesh.blob() }
    private val ringTex: Image by lazy { RatesMesh.ringSprite() }
    private val cardTex: Image by lazy {
        RatesMesh.crossOnCone(128, 72, ::coneR, 1.5f * PI.toFloat() - LABEL_A, 1.5f * PI.toFloat() + LABEL_A, LABEL_H0, LABEL_H1, X_HC, X_HALF, X_INK)
    }
    /** Outer radius of the flask's cone wall at height [h] above its base (= RatesMesh.flaskProfile). */
    private fun coneR(h: Float): Float = if (h < 0.016f) RB - 0.0015f else RB - 0.0015f - (RB - 0.0015f - RN - 0.004f) / (HS - 0.022f) * (h - 0.016f)
    private val texA: Image by lazy { RatesMesh.strip(Rgb(0.28f, 0.52f, 1.0f), Rgb(1.0f, 0.88f, 0.22f)) }
    private val texB: Image by lazy { RatesMesh.strip(Rgb(1.0f, 0.42f, 0.22f)) }
    private val texC: Image by lazy { RatesMesh.strip(Rgb(0.36f, 0.88f, 0.52f), Rgb(0.70f, 1.0f, 0.75f)) }
    private val texD: Image by lazy { RatesMesh.strip(Rgb(0.55f, 0.95f, 0.62f)) }
    private val molMats by lazy {
        arrayOf(texA, texB, texC, texD).map { Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.30f, tex = it, emissive = Rgb(0.10f, 0.10f, 0.12f)) }
    }
    private val digitTex: Map<Char, Image> by lazy { "0123456789 ".associateWith { RatesMesh.sevenSeg(it.toString(), 48, ghost = 0.07f) } }
    private val dotTex: Image by lazy { RatesMesh.sevenSeg(".", 48) }
    private val degTex: Image by lazy { RatesMesh.sevenSeg("°C", 48) }
    private val C_RUN = Rgb(1.0f, 0.62f, 0.16f); private val C_DONE = Rgb(0.35f, 1.0f, 0.48f); private val C_IDLE = Rgb(0.55f, 0.36f, 0.12f)
    private val C_BATH = Rgb(0.45f, 0.85f, 1.0f)
    private val digitCols = arrayOf(C_IDLE, C_RUN, C_DONE, C_BATH)
    private val digitMats = HashMap<Int, Mat>()
    private fun digitMat(ch: Char, col: Int): Mat = digitMats.getOrPut(ch.code * 8 + col) {
        Mat.Unlit(digitCols[col], alpha = 1f, tex = digitTex[ch] ?: digitTex[' ']!!, blend = Blend.ADD)
    }
    private val milkyMats = HashMap<Int, Mat>()
    private const val MILK_STEPS = 48
    /** The colloidal sulfur: alpha = 1 − T (direct light from the X that is lost), as a double-sided shell. */
    private fun milkyMat(idx: Int): Mat = milkyMats.getOrPut(idx) {
        val a = idx.toDouble() / MILK_STEPS
        if (a < 0.004) return@getOrPut Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0f, blend = Blend.TRANSPARENT)
        val aS = (1 - sqrt(1 - a.coerceAtMost(0.995))).toFloat()
        val c = ProcTex.okLerp(Rgb(0.97f, 0.95f, 0.84f), Rgb(0.98f, 0.90f, 0.58f), (a * a).toFloat())
        Mat.Unlit(c, alpha = aS, blend = Blend.TRANSPARENT)
    }
    private val barBlue = Mat.Unlit(Rgb(0.32f, 0.58f, 1.0f))
    private val barGold = Mat.Unlit(Rgb(1.0f, 0.80f, 0.28f))
    private val flashMats by lazy {
        Array(3) { k -> Array(5) { j ->
            val c = when (k) { 0 -> Rgb(0.60f, 0.80f, 1.0f); 1 -> Rgb(1.0f, 0.55f, 0.18f); else -> Rgb(1.0f, 0.85f, 0.35f) }
            val a0 = when (k) { 0 -> 0.45f; 1 -> 0.85f; else -> 1.0f }
            Mat.Unlit(c, alpha = a0 * (j + 1) / 5f, tex = blobTex, blend = Blend.ADD)
        } }
    }
    private val burstMats by lazy { Array(5) { j -> Mat.Unlit(Rgb(1.0f, 0.86f, 0.40f), alpha = (j + 1) / 5f, tex = ringTex, blend = Blend.ADD) } }
    private val lineMat = Mat.Unlit(Rgb(1.0f, 0.90f, 0.50f), alpha = 0.9f, blend = Blend.ADD)
    private val ballMats by lazy {
        arrayOf(Mat.Unlit(Rgb(0.60f, 0.80f, 1.0f), alpha = 0.85f, tex = blobTex, blend = Blend.ADD),
            Mat.Unlit(Rgb(1.0f, 0.55f, 0.18f), alpha = 1f, tex = blobTex, blend = Blend.ADD),
            Mat.Unlit(Rgb(1.0f, 0.86f, 0.35f), alpha = 1f, tex = blobTex, blend = Blend.ADD))
    }
    private val steamMat by lazy { Mat.Unlit(Rgb(0.92f, 0.94f, 0.97f), alpha = 0.22f, tex = blobTex, blend = Blend.TRANSPARENT) }
    private val doneMats by lazy { Array(5) { j -> Mat.Unlit(Rgb(0.35f, 1.0f, 0.48f), alpha = (j + 1) / 5f, blend = Blend.ADD) } }
    private val tickTex = HashMap<String, Image>()
    private fun tick(s: String): Image = tickTex.getOrPut(s) { RatesMesh.sevenSeg(s, 40) }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        frame = 0; textLang = -1; lastTap = ""; realT = 0f; ribbonTurn = 0
        milkyIdx.fill(-1); waterKey.fill(Int.MIN_VALUE); partMat.fill(-1); barMatIdx.fill(-1)
        digitShown.forEach { it.fill(-99) }
        ice.forEach { it.clear() }; steam.forEach { it.clear() }
        histCurveT = -1.0; histCurveEa = -1.0; chartDirty = true; chartKey = ""
        modeIdx = 0; catalyst = false; selected = 3
        dragBath = -1
        micro.half = 0.175
        val root = Node("RatesRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 24)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val quad = RatesMesh.quad()
        val sphere = MeshGen.sphere(1f, 10, 18, MeshGen.SphereUv.EQUIRECT)

        root.add("Plinth", MeshGen.box(1.78f, 0.035f, 0.78f), slab, P3(0f, PLINTH_Y, -0.05f))
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, 0.79f), alu, P3(0f, PLINTH_Y, -0.05f))

        buildStations(root, unitCyl, unitBox, quad)
        buildChart(root, unitBox, quad, sphere)
        buildMicro(root, unitCyl, unitBox, quad, sphere)
        buildHistogram(root, unitBox, quad)
        buildProfile(root, unitBox, sphere, quad)

        // the opening: a temperature series that was started 7 s ago, the hottest flask selected
        resetAll()
        startAll()
        repeat(MICRO_WARM) { micro.step(1.0 / 60) }
        evSeen = micro.evTotal
        val sp = speedIdx; speedIdx = 0
        repeat(PREROLL) { update(1f / 60f) }
        speedIdx = sp
        update(0f)
        return root
    }

    private fun buildStations(root: Node, unitCyl: MeshData, unitBox: MeshData, quad: MeshData) {
        val flaskMesh = RatesMesh.lathe(RatesMesh.flaskProfile(RB, HS, RN, HT), 40)
        val liqMesh = RatesMesh.frustum(RB - 0.0065f, coneR(LIQ_H) - 0.004f, LIQ_H - 0.004f, 36)
        val dishWall = RatesMesh.lathe(floatArrayOf(DISH_R, 0f, DISH_R, DISH_H), 48)
        val ring = MeshGen.annulus(DISH_R + 0.010f, DISH_R + 0.020f, 64)
        val selMesh = MeshGen.annulus(DISH_R + 0.024f, DISH_R + 0.034f, 64)
        val iceMesh = MeshGen.box(0.030f, 0.024f, 0.030f)
        val lip = MeshGen.tube(ringPath(DISH_R + 0.001f, 48), 0.003f, 6, true)
        val meniscus = MeshGen.tube(ringPath(DISH_R - 0.003f, 48), 0.0022f, 5, true)
        val liqRing = MeshGen.tube(ringPath(coneR(LIQ_H) - 0.005f, 36), 0.0020f, 5, true)
        val la0 = 1.5f * PI.toFloat() - LABEL_A; val la1 = 1.5f * PI.toFloat() + LABEL_A
        val labelFront = RatesMesh.conePatch(::coneR, la0, la1, LABEL_H0, LABEL_H1, 24, 6, 0.0015f, inward = true)
        val labelBack = RatesMesh.conePatch(::coneR, la0, la1, LABEL_H0, LABEL_H1, 24, 6, 0.0022f, inward = false)
        val paperBack = Mat.Pbr(Rgb(0.92f, 0.92f, 0.89f), roughness = 0.8f)
        val conBody = MeshGen.box(0.175f, 0.076f, 0.022f)
        val cardMat = Mat.Unlit(Rgb(0.93f, 0.93f, 0.91f), tex = cardTex)
        val tubeWall = RatesMesh.lathe(floatArrayOf(TUBE_R, 0f, TUBE_R, TUBE_H, TUBE_R + 0.002f, TUBE_H + 0.002f), 20)
        for (s in 0 until 4) {
            val x = SX[s]
            root.add("Stand$s", MeshGen.box(STAND_W, STAND_H, STAND_D), darkSteel, P3(x, DECK + STAND_H / 2, STAND_ZC))
            root.add("StandFoot$s", MeshGen.box(STAND_W + 0.008f, 0.006f, STAND_D + 0.008f), alu, P3(x, DECK + 0.003f, STAND_ZC))
            led[s] = root.add("Led$s", ring, heaterMat(25f), P3(x, STAND_TOP + 0.0012f, SZ))
            doneRing[s] = root.add("DoneRing$s", ring, doneMats[4], P3(x, STAND_TOP + 0.0016f, SZ)).also { it.visible = false }
            // the water bath: a crystallising dish
            root.add("Dish$s", dishWall, glass, P3(x, DISH_FLOOR - 0.003f, SZ))
            root.add("DishFloor$s", MeshGen.cylinder(DISH_R, 0.004f, 48), glass, P3(x, DISH_FLOOR - 0.002f, SZ))
            root.add("DishLip$s", lip, glass, P3(x, DISH_FLOOR - 0.003f + DISH_H, SZ))
            water[s] = root.add("Water$s", MeshGen.cylinder(DISH_R - 0.002f, WATER_Y - DISH_FLOOR, 48), waterMat(25f), P3(x, (WATER_Y + DISH_FLOOR) / 2, SZ))
            root.add("Meniscus$s", meniscus, Mat.Unlit(Rgb(0.62f, 0.82f, 1f), alpha = 0.28f, blend = Blend.ADD), P3(x, WATER_Y, SZ))
            for (k in 0 until 4) {
                val a = (k * 2 * PI / 4 + 0.7 + s).toFloat(); val rr = 0.090f
                ice[s] += root.add("Ice${s}_$k", iceMesh, iceMat, P3(x + rr * cos(a), WATER_Y + 0.003f, SZ + rr * sin(a))).also {
                    it.rot = Q.axisAngle(0.3f, 1f, 0.2f, a * 1.7f)
                }
            }
            for (k in 0 until 3) steam[s] += root.add("Steam${s}_$k", quad, steamMat).also { it.billboard = true; it.visible = false }
            // the flask: thiosulfate solution, its milky sulfur layer, the waterline
            val fr = root.add(Node("FlaskRig$s")).also { it.pos = P3(x, FB, SZ) }
            liquid[s] = fr.add("Liquid$s", liqMesh, solution, P3(0f, 0.004f, 0f))
            milky[s] = fr.add("Milky$s", liqMesh, milkyMat(0), P3(0f, 0.005f, 0f)).also { it.scale = P3(0.985f, 0.985f, 0.985f) }
            fr.add("LiqRing$s", liqRing, surfaceRing, P3(0f, LIQ_H, 0f))
            fr.add("XLabel$s", labelFront, cardMat)
            fr.add("XLabelBack$s", labelBack, paperBack)
            fr.add("FlaskGlass$s", flaskMesh, flaskGlass)
            fr.add(Node("Flask$s")).also { it.pos = P3(0f, 0.055f, 0f); it.tapRadius = 0.085f }   // pinch zone = the liquid
            // the acid: a small measuring tube on the stand that tips into the neck
            val tg = root.add(Node("Tube$s"))
            tg.add("TubeGlass$s", tubeWall, Mat.Pbr(Rgb(0.88f, 0.94f, 1.0f), roughness = 0.04f, alpha = 0.22f))
            tg.add("TubeBase$s", MeshGen.cylinder(TUBE_R + 0.004f, 0.004f, 20), Mat.Pbr(Rgb(0.85f, 0.92f, 1.0f), roughness = 0.05f, alpha = 0.5f), P3(0f, 0.002f, 0f))
            acid[s] = tg.add("Acid$s", unitCyl, acidMat)
            for (k in 1..4) tg.add("TubeTick$s", MeshGen.box(if (k % 2 == 0) 0.010f else 0.006f, 0.0018f, 0.0015f), axisMat, P3(-0.003f, 0.016f * k, TUBE_R + 0.0008f))
            tube[s] = tg
            stream[s] = root.add("Stream$s", unitCyl, acidMat).also { it.visible = false }
            // the console: bath thermostat (°C, knob) and the stopwatch
            // low and flat (tilted 54° back) so it never hides the bottom of the liquid from 2.5 m
            val con = root.add(Node("Console$s")).also { it.pos = P3(x, DECK + 0.029f, CON_Z); it.rot = Q.axisAngle(1f, 0f, 0f, -0.95f) }
            con.add("ConBody$s", conBody, blackPlastic)
            con.add("ConFace$s", MeshGen.box(0.160f, 0.066f, 0.002f), Mat.Pbr(Rgb(0.02f, 0.02f, 0.025f), roughness = 0.15f), P3(-0.004f, 0f, 0.0115f))
            con.add("ConStripe$s", MeshGen.box(0.004f, 0.060f, 0.002f), Mat.Unlit(STATION_COL[s]), P3(-0.081f, 0f, 0.0125f))
            val dh = 0.026f; val pitch = dh * 0.546f
            for (d in 0 until 2) bathDigits[s][d] = con.add("BathDigit${s}_$d", quad, digitMat(' ', 3), P3(-0.060f + d * pitch, 0.017f, 0.013f)).also { it.scale = P3(dh * 0.846f, dh, 1f) }
            con.add("Deg$s", quad, Mat.Unlit(C_BATH, tex = degTex, blend = Blend.ADD), P3(-0.060f + 2 * pitch + 0.006f, 0.017f, 0.013f)).also { it.scale = P3(dh * 0.6f * 1.392f, dh * 0.6f, 1f) }
            val th = 0.030f; val tp = TIMER_PITCH
            for (d in 0 until 3) timerDigits[s][d] = con.add("TimerDigit${s}_$d", quad, digitMat(' ', 0),
                P3(-0.058f + d * tp + (if (d == 2) 0.008f else 0f), -0.016f, 0.013f)).also { it.scale = P3(th * 0.846f, th, 1f) }
            timerDot[s] = con.add("TimerDot$s", quad, Mat.Unlit(C_RUN, tex = dotTex, blend = Blend.ADD), P3(-0.058f + 1.5f * tp + 0.006f, -0.016f, 0.013f)).also { it.scale = P3(th * 0.454f, th, 1f) }
            knob[s] = con.add("Bath$s", MeshGen.cylinder(0.019f, 0.016f, 28), alu, P3(0.052f, 0.0f, 0.019f)).also {
                it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()); it.tapRadius = 0.05f
            }
            knob[s]!!.add("KnobMark$s", MeshGen.box(0.004f, 0.002f, 0.015f), Mat.Unlit(STATION_COL[s]), P3(0f, 0.0088f, 0.007f))
            con.add("ConFoot$s", MeshGen.box(0.12f, 0.022f, 0.02f), blackPlastic, P3(0f, -0.030f, -0.014f))
        }
        selRing = root.add("SelRing", selMesh, selMat, P3(SX[3], STAND_TOP + 0.0014f, SZ))
    }

    private fun buildChart(root: Node, unitBox: MeshData, quad: MeshData, sphere: MeshData) {
        val cx = (PX0 + PX1) / 2; val cy = (PY0 + PY1) / 2
        root.add("ChartBoard", MeshGen.box(PX1 - PX0 + 0.13f, PY1 - PY0 + 0.085f, 0.012f), board, P3(cx - 0.012f, cy + 0.004f, PZ - 0.014f))
        root.add("ChartPostL", unitBox, darkSteel, P3(PX0 - 0.03f, (DECK + PY0 - 0.04f) / 2, PZ - 0.03f)).also { it.scale = P3(0.010f, PY0 - 0.04f - DECK, 0.010f) }
        root.add("ChartPostR", unitBox, darkSteel, P3(PX1 + 0.02f, (DECK + PY0 - 0.04f) / 2, PZ - 0.03f)).also { it.scale = P3(0.010f, PY0 - 0.04f - DECK, 0.010f) }
        val ax = ArrayList<MeshData>()
        ax += RatesMesh.placed(unitBox, cx, PY0, PZ, PX1 - PX0 + 0.006f, 0.003f, 0.002f)
        ax += RatesMesh.placed(unitBox, PX0, cy, PZ, 0.003f, PY1 - PY0, 0.002f)
        for (k in 1..4) ax += RatesMesh.placed(unitBox, PX0 - 0.006f, PY0 + (PY1 - PY0) * k / 4, PZ, 0.010f, 0.0025f, 0.002f)
        root.add("ChartAxes", RatesMesh.merge(ax), axisMat)
        val grid = ArrayList<MeshData>()
        for (k in 1..4) grid += RatesMesh.placed(unitBox, cx, PY0 + (PY1 - PY0) * k / 4, PZ - 0.003f, PX1 - PX0, 0.0016f, 0.001f)
        root.add("ChartGrid", RatesMesh.merge(grid), gridMat)
        // x ticks for both series (temperature 0..70 °C; concentration 0..0.20 mol/L)
        val tT = ArrayList<Node>(); val tC = ArrayList<Node>()
        val th = 0.020f
        for (t in listOf(0, 20, 40, 60)) {
            val s = "$t"; val x = PX0 + (PX1 - PX0) * t / 70f
            tT += root.add("TickT$t", quad, Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick(s), blend = Blend.ADD), P3(x, PY0 - 0.020f, PZ + 0.002f)).also { it.scale = P3(th * RatesMesh.aspect(s), th, 1f) }
            tT += root.add("TickMT$t", unitBox, axisMat, P3(x, PY0 - 0.005f, PZ)).also { it.scale = P3(0.0025f, 0.010f, 0.002f) }
        }
        for ((k, s) in listOf("0", "0.05", "0.10", "0.15", "0.20").withIndex()) {
            val x = PX0 + (PX1 - PX0) * k / 4f
            tC += root.add("TickC$k", quad, Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick(s), blend = Blend.ADD), P3(x, PY0 - 0.020f, PZ + 0.002f)).also { it.scale = P3(th * RatesMesh.aspect(s), th, 1f) }
            tC += root.add("TickMC$k", unitBox, axisMat, P3(x, PY0 - 0.005f, PZ)).also { it.scale = P3(0.0025f, 0.010f, 0.002f) }
        }
        tickT = tT; tickC = tC
        for (v in SCALES) { tick(fmtScale(v)); tick(fmtScale(v / 2)) }      // every y label baked once, here
        yTickMid = root.add("YTickMid", quad, Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick("0.05"), blend = Blend.ADD), P3(PX0 - 0.045f, (PY0 + PY1) / 2, PZ + 0.002f))
        yTickTop = root.add("YTickTop", quad, Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick("0.10"), blend = Blend.ADD), P3(PX0 - 0.045f, PY1, PZ + 0.002f))
        chartCurve = RatesMesh.ribbon(CURVE_N)
        root.add("ChartCurve", chartCurve, Mat.Unlit(Rgb(0.55f, 0.85f, 1.0f), alpha = 0.95f, blend = Blend.ADD))
        points = Array(12) { k -> root.add("Point$k", sphere, Mat.Unlit(STATION_COL[0])).also { it.uniformScale(0.0115f); it.visible = false } }
    }
    private const val CURVE_N = 48

    private fun buildMicro(root: Node, unitCyl: MeshData, unitBox: MeshData, quad: MeshData, sphere: MeshData) {
        root.add("MicroPost", unitCyl, darkSteel, P3(MB.x, (DECK + MB.y - WIN) / 2, MB.z)).also { it.scale = P3(0.028f, MB.y - WIN - DECK, 0.028f) }
        root.add("MicroFoot", MeshGen.cylinder(0.09f, 0.010f, 40), alu, P3(MB.x, DECK + 0.005f, MB.z))
        window = root.add(Node("MicroWindow")).also { it.pos = MB }
        val w2 = 2 * WIN
        window.add("WinBack", MeshGen.box(w2, w2, 0.004f), glassBack, P3(0f, 0f, -WIN - 0.002f))
        winFloor = window.add("WinFloor", MeshGen.box(w2, 0.004f, w2), glassBack, P3(0f, -WIN - 0.002f, 0f))
        window.add("WinFront", MeshGen.box(w2, w2, 0.004f), glassClear, P3(0f, 0f, WIN + 0.002f))
        window.add("WinTop", MeshGen.box(w2, 0.004f, w2), glassClear, P3(0f, WIN + 0.002f, 0f))
        for (sx in floatArrayOf(-1f, 1f)) window.add("WinSide", MeshGen.box(0.004f, w2, w2), glassClear, P3(sx * (WIN + 0.002f), 0f, 0f))
        val rail = 0.009f
        val rails = ArrayList<MeshData>()
        for (a in floatArrayOf(-1f, 1f)) for (b in floatArrayOf(-1f, 1f)) {
            rails += RatesMesh.placed(unitBox, 0f, a * (WIN + rail / 2), b * (WIN + rail / 2), w2 + 2 * rail, rail, rail)
            rails += RatesMesh.placed(unitBox, a * (WIN + rail / 2), 0f, b * (WIN + rail / 2), rail, w2, rail)
            rails += RatesMesh.placed(unitBox, a * (WIN + rail / 2), b * (WIN + rail / 2), 0f, rail, rail, w2)
        }
        window.add("WinRails", RatesMesh.merge(rails), alu)
        window.add(Node("MicroBox")).also { it.tapRadius = 0.14f }
        val aMesh = RatesMesh.moleculeA(micro.rA.toFloat(), 0.0080f)
        val bMesh = RatesMesh.moleculeB(micro.rB.toFloat())
        parts = Array(micro.n) { i -> window.add("Mol_$i", if (i < micro.nA) aMesh else bMesh, molMats[if (i < micro.nA) 0 else 1]) }
        flashes = Array(micro.flashCap) { k -> window.add("Flash_$k", quad, flashMats[0][4]).also { it.billboard = true; it.visible = false } }
        bursts = Array(micro.lineCap) { k -> window.add("Burst_$k", quad, burstMats[4]).also { it.billboard = true; it.visible = false } }
        lines = Array(micro.lineCap) { k -> window.add("Line_$k", unitCyl, lineMat).also { it.visible = false } }
        // the catalyst jar (front left of the window's post)
        val jar = root.add(Node("Catalyst")).also { it.pos = P3(JAR.x, JAR.y + 0.040f, JAR.z); it.tapRadius = 0.05f }
        jar.add("JarGlass", unitCyl, Mat.Pbr(Rgb(0.85f, 0.92f, 1.0f), roughness = 0.05f, alpha = 0.30f)).also { it.scale = P3(0.030f, 0.070f, 0.030f) }
        jar.add("JarFill", unitCyl, Mat.Pbr(Rgb(0.16f, 0.17f, 0.18f), metallic = 0.5f, roughness = 0.45f), P3(0f, -0.016f, 0f)).also { it.scale = P3(0.027f, 0.036f, 0.027f) }
        jar.add("JarLid", unitCyl, Mat.Pbr(Rgb(0.20f, 0.55f, 0.35f), roughness = 0.4f), P3(0f, 0.040f, 0f)).also { it.scale = P3(0.032f, 0.012f, 0.032f) }
        jar.add("JarLabel", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.92f), roughness = 0.6f), P3(0f, 0.008f, 0f)).also { it.scale = P3(0.0305f, 0.020f, 0.0305f) }
        jarGlow = jar.add("JarGlow", sphere, Mat.Unlit(Rgb(0.35f, 1.0f, 0.55f), alpha = 0.22f, blend = Blend.ADD)).also { it.scale = P3(0.050f, 0.062f, 0.050f); it.visible = false }
    }

    private fun buildHistogram(root: Node, unitBox: MeshData, quad: MeshData) {
        root.add("HistBoard", MeshGen.box(HX1 - HX0 + 0.06f, HY1 - HY0 + 0.075f, 0.012f), board, P3((HX0 + HX1) / 2, (HY0 + HY1) / 2 - 0.004f, HZ - 0.014f))
        val ax = ArrayList<MeshData>()
        ax += RatesMesh.placed(unitBox, (HX0 + HX1) / 2, HY0, HZ, HX1 - HX0 + 0.004f, 0.003f, 0.002f)
        ax += RatesMesh.placed(unitBox, HX0, (HY0 + HY1) / 2, HZ, 0.003f, HY1 - HY0, 0.002f)
        for (e in listOf(0, 40, 80, 120)) ax += RatesMesh.placed(unitBox, hx(e.toDouble()), HY0 - 0.005f, HZ, 0.0025f, 0.010f, 0.002f)
        root.add("HistAxes", RatesMesh.merge(ax), axisMat)
        for (e in listOf(0, 40, 80, 120)) {
            val s = "$e"; val th = 0.017f
            root.add("HistTick$e", quad, Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick(s), blend = Blend.ADD), P3(hx(e.toDouble()), HY0 - 0.018f, HZ + 0.002f)).also { it.scale = P3(th * RatesMesh.aspect(s), th, 1f) }
        }
        bars = Array(HIST_BINS + 1) { k -> root.add("Bar$k", unitBox, barBlue, P3(hx(k * HIST_W), HY0, HZ)).also { it.scale = P3(0.001f, 0.001f, 0.006f) } }
        histCurve = RatesMesh.ribbon(HIST_N)
        root.add("HistCurve", histCurve, Mat.Unlit(Rgb(0.85f, 0.95f, 1.0f), alpha = 0.85f, blend = Blend.ADD))
        eaLine = root.add("EaLine", unitBox, Mat.Unlit(Rgb(1.0f, 0.95f, 0.75f), alpha = 1f, blend = Blend.ADD)).also { it.scale = P3(0.0035f, HY1 - HY0 + 0.016f, 0.004f) }
    }
    private const val HIST_N = 62

    private fun buildProfile(root: Node, unitBox: MeshData, sphere: MeshData, quad: MeshData) {
        root.add("ProfBoard", MeshGen.box(EX1 - EX0 + 0.06f, EY1 - EY0 + 0.05f, 0.012f), board, P3((EX0 + EX1) / 2, (EY0 + EY1) / 2, EZ - 0.014f))
        val ax = ArrayList<MeshData>()
        ax += RatesMesh.placed(unitBox, EX0, (EY0 + EY1) / 2, EZ, 0.003f, EY1 - EY0, 0.002f)
        ax += RatesMesh.placed(unitBox, (EX0 + EX1) / 2, EY0, EZ, EX1 - EX0, 0.003f, 0.002f)
        // dashed reactant level
        for (k in 0 until 12) ax += RatesMesh.placed(unitBox, EX0 + 0.012f + k * 0.021f, ey(0.0), EZ - 0.001f, 0.010f, 0.0016f, 0.001f)
        root.add("ProfAxes", RatesMesh.merge(ax), axisMat)
        val n = 64
        for ((k, ea) in listOf(Thiosulfate.EA, Thiosulfate.EA_CAT).withIndex()) {
            val xs = FloatArray(n); val ys = FloatArray(n)
            for (i in 0 until n) { val s = i / (n - 1.0); xs[i] = EX0 + 0.01f + (EX1 - EX0 - 0.02f) * s.toFloat(); ys[i] = ey(profile(s, ea)) }
            val md = RatesMesh.ribbon(n); RatesMesh.ribbonInto(md, xs, ys, EZ + 0.003f + 0.001f * k, if (k == 0) 0.0032f else 0.0026f)
            val node = root.add(if (k == 0) "Hump" else "HumpCat", md, Mat.Unlit(if (k == 0) Rgb(1.0f, 0.55f, 0.20f) else Rgb(0.35f, 1.0f, 0.55f), alpha = 1f, blend = Blend.ADD))
            val arrow = root.add(if (k == 0) "EaArrow" else "EaArrowCat", unitBox, Mat.Unlit(if (k == 0) Rgb(1.0f, 0.70f, 0.35f) else Rgb(0.45f, 1.0f, 0.60f), alpha = 0.9f, blend = Blend.ADD),
                P3(ex(0.5) + (if (k == 0) -0.006f else 0.006f), (ey(0.0) + ey(ea / 1e3)) / 2, EZ + 0.002f)).also { it.scale = P3(0.0022f, ey(ea / 1e3) - ey(0.0), 0.002f) }
            if (k == 1) { catHump = node; catArrow = arrow }
        }
        balls = Array(6) { k -> root.add("Ball$k", quad, ballMats[0], P3(EX0, EY0, EZ + 0.006f)).also { it.billboard = true; it.visible = false; it.uniformScale(0.026f) } }
    }

    // ================================================================ experiment control

    /** Back to the preset series of the current mode: fresh solutions, clocks at zero, chart cleared. */
    fun resetAll() {
        for (s in 0 until 4) {
            bathT[s] = if (modeIdx == 0) PRESET_T[s] else 25f
            conc[s] = if (modeIdx == 0) 0.15f else PRESET_C[s]
            liquidK[s] = bathT[s] + Arrhenius.KELVIN
            phase[s] = IDLE; pourT[s] = 0f; runs[s] = null; doneFlash[s] = 9f
        }
        results.clear(); lastDone = -1; lastDoneAge = 99f; lastBathEvent = -1; lastBathAge = 99f
        micro.barrier = if (catalyst) Thiosulfate.EA_CAT else Thiosulfate.EA
        eaShown = micro.barrier
        micro.reset(liquidK[selected])
        evSeen = micro.evTotal
        ballAge.fill(99f)
        chartDirty = true
    }

    fun setMode(m: Int) { if (m != modeIdx) { modeIdx = m; resetAll() } }

    /** Pinch a flask: fresh solution, the tube tips the acid in, the stopwatch starts at the mix. */
    private fun startRun(s: Int) {
        phase[s] = POURING; pourT[s] = 0f; runs[s] = null; doneFlash[s] = 9f
        if (lastDone == s) lastDoneAge = 99f
    }

    fun startAll() { for (s in 0 until 4) startRun(s) }

    private fun select(s: Int) { if (s != selected) { selected = s; chartDirty = true } }

    fun setBath(s: Int, t: Float) {
        val v = t.coerceIn(5f, 65f)
        if (v == bathT[s]) return
        if (!(lastBathEvent == s && lastBathAge < 1.5f)) lastBathFrom = bathT[s]
        bathT[s] = v; lastBathEvent = s; lastBathAge = 0f
        chartDirty = true
    }

    fun setConc(s: Int, c: Float) { val v = c.coerceIn(0.03f, 0.20f); if (v != conc[s]) { conc[s] = v; chartDirty = true } }

    fun toggleCatalyst(on: Boolean) {
        catalyst = on
        micro.barrier = if (on) Thiosulfate.EA_CAT else Thiosulfate.EA
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        val sdt = (dt * SPEEDS[speedIdx]).toDouble()
        realT += dt
        lastDoneAge += dt; lastBathAge += dt; histAge += dt
        for (s in 0 until 4) {
            // the flask's liquid follows its bath
            val tb = bathT[s] + Arrhenius.KELVIN
            liquidK[s] += (tb - liquidK[s]) * (1 - exp(-sdt / TAU_TH))
            // pour (real time: a quick gesture at any playback speed)
            if (phase[s] == POURING) {
                val before = pourT[s]
                pourT[s] += dt
                if (before < POUR_MIX && pourT[s] >= POUR_MIX) runs[s] = XRun(conc[s].toDouble())
                if (pourT[s] >= POUR_END) phase[s] = RUNNING
            }
            val r = runs[s]
            if (r != null) {
                val was = r.obscured
                r.step(sdt, liquidK[s])
                if (!was && r.obscured) onObscured(s, r)
            }
            doneFlash[s] += dt
        }
        // micro: the selected flask's temperature, the chosen barrier
        micro.setTemperature(liquidK[selected])
        micro.step(dt.coerceIn(0f, 0.05f).toDouble())
        val k = 1 - exp(-dt / 0.18f)
        eaShown = if (dt == 0f) micro.barrier else eaShown + (micro.barrier - eaShown) * k
        place(dt)
        val lang = if (Lang.en) 1 else 0
        if (frame++ % 6 == 0 || dt == 0f || lang != textLang) { textLang = lang; updateReadout(); updateLabels() }
    }

    private fun onObscured(s: Int, r: XRun) {
        results += Result(s, modeIdx, r.tMeanK - Arrhenius.KELVIN, r.c0, r.tObscured)
        while (results.size > points.size) results.removeAt(0)
        lastDone = s; lastDoneAge = 0f; doneFlash[s] = 0f
        chartDirty = true
    }

    private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

    private fun place(dt: Float) {
        for (s in 0 until 4) placeStation(s)
        selRing.pos = P3(SX[selected], STAND_TOP + 0.0014f, SZ)
        placeMicro()
        placeHistogram()
        placeProfile(dt)
        placeChart()
        jarGlow.visible = catalyst
        winFloor.setMat(if (catalyst) catalystFloor else glassBack)
        // ONE ribbon re-upload per frame at most (~20 µs per vertex on the headset)
        when (ribbonTurn++ % 2) {
            0 -> if (chartDirty) { chartDirty = false; writeChartCurve() }
            else -> if (abs(micro.tK - histCurveT) > 0.25 || abs(eaShown - histCurveEa) > 50.0) writeHistCurve()
        }
    }
    private val catalystFloor = Mat.Pbr(Rgb(0.06f, 0.16f, 0.10f), metallic = 0f, roughness = 0.06f, alpha = 0.70f)

    private fun placeStation(s: Int) {
        val x = SX[s]
        // the X disappearing: milky sulfur, alpha = 1 − T(S)
        val r = runs[s]
        val a = if (r == null) 0.0 else 1 - r.transmittance
        val idx = (a * MILK_STEPS).roundToInt().coerceIn(0, MILK_STEPS)
        if (idx != milkyIdx[s]) { milkyIdx[s] = idx; milky[s]!!.setMat(milkyMat(idx)) }
        // bath: water tint, heater ring, ice, steam
        val t = bathT[s]
        val key = (t / 5f).roundToInt()
        if (key != waterKey[s]) { waterKey[s] = key; water[s]!!.setMat(waterMat(t)); led[s]!!.setMat(heaterMat(t)) }
        for (n in ice[s]) n.visible = t <= 8f
        val nSteam = if (t < 45f) 0 else (1 + ((t - 45f) / 20f * 2f).toInt()).coerceAtMost(3)
        steam[s].forEachIndexed { k, n ->
            n.visible = k < nSteam
            if (n.visible) {
                val ph = ((realT * 0.25 + k * 0.33 + s * 0.17) % 1.0).toFloat()
                val ang = (k * 2.1 + s).toFloat()
                n.pos = P3(x + 0.085f * cos(ang), DISH_FLOOR + DISH_H + 0.08f * ph, SZ + 0.06f * sin(ang))
                n.uniformScale((0.020f + 0.03f * ph) * sin(PI.toFloat() * ph).coerceAtLeast(0.05f))
            }
        }
        knob[s]!!.rot = Q.axisAngle(0f, 0f, 1f, -(t - 35f) / 60f * 4.4f) * Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        // the acid tube: rest on the stand, tip over the neck, pour, return
        val rest = P3(x + 0.090f, STAND_TOP, SZ + 0.100f)
        val pour = P3(x + 0.077f, FB + HT + 0.046f, SZ)
        val p = pourT[s]
        val kk = if (phase[s] != POURING) 0f else when {
            p < POUR_IN -> smooth(p / POUR_IN)
            p < POUR_OUT -> 1f
            else -> smooth(1f - (p - POUR_OUT) / (POUR_END - POUR_OUT))
        }
        val tg = tube[s]!!
        tg.pos = P3(rest.x + (pour.x - rest.x) * kk, rest.y + (pour.y - rest.y) * kk + 0.06f * sin(PI.toFloat() * kk), rest.z + (pour.z - rest.z) * kk)
        tg.rot = Q.axisAngle(0f, 0f, 1f, 2.0f * kk)
        val full = when {
            phase[s] == IDLE -> 1f
            phase[s] == POURING && p < POUR_IN -> 1f
            phase[s] == POURING && p < POUR_OUT -> 1f - (p - POUR_IN) / (POUR_OUT - POUR_IN)
            else -> 0f
        }
        val ac = acid[s]!!
        ac.visible = full > 0.02f
        if (ac.visible) { val h = 0.060f * full; ac.scale = P3(TUBE_R - 0.0015f, h, TUBE_R - 0.0015f); ac.pos = P3(0f, 0.004f + h / 2, 0f) }
        val st = stream[s]!!
        st.visible = phase[s] == POURING && p in POUR_IN..POUR_OUT
        if (st.visible) { val top = FB + HT + 0.012f; val bot = FB + LIQ_H; st.pos = P3(x, (top + bot) / 2, SZ); st.scale = P3(0.0028f, top - bot, 0.0028f) }
        // console: bath °C and the stopwatch
        setDigits2(s, t.roundToInt())
        val (secs, col) = when {
            r == null -> 0.0 to 0
            r.obscured -> r.tObscured to 2
            else -> r.t to 1
        }
        setTimer(s, secs, col)
        // a green ping when the X is gone
        val f = doneFlash[s]
        doneRing[s]!!.visible = f < 1.6f
        if (f < 1.6f) doneRing[s]!!.setMat(doneMats[((1f - f / 1.6f) * 4.99f).toInt().coerceIn(0, 4)])
    }

    private fun setDigits2(s: Int, v: Int) {
        val key = v * 10 + 3
        if (digitShown[s][3] == key) return
        digitShown[s][3] = key
        val str = "%2d".format(v.coerceIn(0, 99))
        for (d in 0 until 2) bathDigits[s][d]!!.setMat(digitMat(str[d], 3))
    }

    private fun setTimer(s: Int, secs: Double, col: Int) {
        val tenth = (if (col == 2) (secs * 10).roundToInt() else (secs * 10).toInt()).coerceIn(0, 9999)
        val key = tenth * 4 + col
        if (digitShown[s][0] == key) return
        digitShown[s][0] = key
        val str: String; val dot: Boolean
        if (tenth < 1000) { str = "%2d%d".format(tenth / 10, tenth % 10); dot = true } else { str = "%3d".format((tenth / 10).coerceAtMost(999)); dot = false }
        for (d in 0 until 3) timerDigits[s][d]!!.setMat(digitMat(str[d], col))
        val d2 = timerDigits[s][2]!!                                  // the last digit closes up when there is no decimal point
        d2.pos = P3(-0.058f + 2 * TIMER_PITCH + (if (dot) 0.008f else 0f), d2.pos.y, d2.pos.z)
        timerDot[s]!!.visible = dot
        timerDot[s]!!.setMat(Mat.Unlit(digitCols[col], tex = dotTex, blend = Blend.ADD))
    }

    private val axisTmp = DoubleArray(3)

    private fun placeMicro() {
        for (i in 0 until micro.n) {
            val n = parts[i]
            val k = micro.kind[i]
            if (partMat[i] != k) { partMat[i] = k; n.setMat(molMats[k]) }
            n.pos = P3(micro.x[i * 3].toFloat(), micro.x[i * 3 + 1].toFloat(), micro.x[i * 3 + 2].toFloat())
            if (i < micro.nA) n.rot = Q(micro.q[i * 4].toFloat(), micro.q[i * 4 + 1].toFloat(), micro.q[i * 4 + 2].toFloat(), micro.q[i * 4 + 3].toFloat())
            var sc = (micro.born[i] / 0.25).toFloat().coerceIn(0f, 1f)
            if (k == CollisionBox.C || k == CollisionBox.D) sc = minOf(sc, (micro.life[i] / 0.3).toFloat().coerceIn(0f, 1f))
            n.uniformScale(maxOf(0.02f, sc))
        }
        for (j in 0 until micro.flashCap) {
            val n = flashes[j]
            val kind = micro.fKind[j]
            val life = when (kind) { CollisionBox.F_NEAR -> 0.22; CollisionBox.F_WRONG_SIDE -> 0.38; else -> 0.55 }
            val age = micro.fAge[j]
            n.visible = age < life
            if (n.visible) {
                val u = (age / life).toFloat()
                val size = when (kind) { CollisionBox.F_NEAR -> 0.040f + 0.025f * (micro.fEnergy[j] / micro.barrier).toFloat(); CollisionBox.F_WRONG_SIDE -> 0.075f; else -> 0.13f }
                n.pos = P3(micro.fx[j * 3].toFloat(), micro.fx[j * 3 + 1].toFloat(), micro.fx[j * 3 + 2].toFloat() + 0.004f)
                n.uniformScale(size * (0.6f + 0.6f * sin(PI.toFloat() * (0.2f + 0.8f * u))))
                n.setMat(flashMats[kind][((1f - u) * 4.99f).toInt().coerceIn(0, 4)])
            }
        }
        for (j in 0 until micro.lineCap) {
            val age = micro.lineAge[j]
            val on = age < 0.6
            lines[j].visible = on; bursts[j].visible = on
            if (on) {
                val a = P3(micro.lineA[j * 3].toFloat(), micro.lineA[j * 3 + 1].toFloat(), micro.lineA[j * 3 + 2].toFloat())
                val b = P3(micro.lineB[j * 3].toFloat(), micro.lineB[j * 3 + 1].toFloat(), micro.lineB[j * 3 + 2].toFloat())
                placeSeg(lines[j], a, b, 0.0022f)
                val u = (age / 0.6).toFloat()
                bursts[j].pos = P3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2 + 0.005f)
                bursts[j].uniformScale(0.05f + 0.16f * u)
                bursts[j].setMat(burstMats[((1f - u) * 4.99f).toInt().coerceIn(0, 4)])
            }
        }
    }

    // ---------------------------------------------------------------- histogram

    private fun hx(eKj: Double) = HX0 + 0.006f + (HX1 - HX0 - 0.012f) * (eKj / HIST_EMAX).toFloat()
    private fun hBar(frac: Double) = ((frac / HIST_FULL).toFloat() * (HY1 - HY0)).coerceIn(0.0015f, HY1 - HY0)

    private val counts = DoubleArray(HIST_BINS + 1)

    private fun placeHistogram() {
        val ea = eaShown / 1000
        eaLine.pos = P3(hx(ea), (HY0 + HY1) / 2 + 0.004f, HZ + 0.003f)
        if (histAge < 0.1f) return
        histAge = 0f
        // counts from the live ring of collision energies; the bin holding Ea is split at Ea
        counts.fill(0.0)
        val nE = micro.eCount
        val split = (ea / HIST_W).toInt().coerceIn(0, HIST_BINS - 1)
        var extra = 0.0
        for (k in 0 until nE) {
            val e = micro.eRing[k] / 1000
            val b = (e / HIST_W).toInt()
            if (b >= HIST_BINS) continue
            if (b == split && e >= ea) extra += 1.0 else counts[b] += 1.0
        }
        counts[HIST_BINS] = extra
        val tot = maxOf(1, nE).toDouble()
        val w = HIST_X_W
        for (k in 0..HIST_BINS) {
            val lo: Double; val hi: Double; val above: Boolean
            if (k == HIST_BINS) { lo = ea; hi = (split + 1) * HIST_W; above = true }
            else if (k == split) { lo = k * HIST_W; hi = ea; above = false }
            else { lo = k * HIST_W; hi = (k + 1) * HIST_W; above = lo >= ea }
            val f = counts[k] / tot * (if (above) TAIL_GAIN else 1.0)
            val n = bars[k]
            val wd = (w * (hi - lo) / HIST_W).toFloat() - 0.0018f
            n.visible = wd > 0.0008f
            if (!n.visible) continue
            val h = hBar(f)
            n.pos = P3((hx(lo) + hx(hi)) / 2, HY0 + h / 2, HZ + 0.001f)
            n.scale = P3(wd, h, 0.006f)
            val mi = if (above) 1 else 0
            if (barMatIdx[k] != mi) { barMatIdx[k] = mi; n.setMat(if (above) barGold else barBlue) }
        }
    }
    private val HIST_X_W get() = (HX1 - HX0 - 0.012f) * (HIST_W / HIST_EMAX).toFloat()

    /** Expected bar heights: density × bar width, exp(−E/(5RT)) — ×20 right of the Ea line. */
    private fun writeHistCurve() {
        histCurveT = micro.tK; histCurveEa = eaShown
        val sc = micro.energyScale * Arrhenius.R * micro.tK / 1000          // kJ/mol: the window's "kT"
        val ea = eaShown / 1000
        val xs = FloatArray(HIST_N); val ys = FloatArray(HIST_N)
        val m = HIST_N - 2
        var j = 0
        for (i in 0 until m) {
            val e = HIST_EMAX * i / (m - 1)
            if (j < HIST_N && i > 0 && HIST_EMAX * (i - 1) / (m - 1) < ea && e >= ea) {
                for (side in 0..1) { xs[j] = hx(ea); ys[j] = HY0 + hBar(HIST_W / sc * exp(-ea / sc) * (if (side == 0) 1.0 else TAIL_GAIN)); j++ }
            }
            if (j >= HIST_N) break
            xs[j] = hx(e); ys[j] = HY0 + hBar(HIST_W / sc * exp(-e / sc) * (if (e >= ea) TAIL_GAIN else 1.0)); j++
        }
        while (j < HIST_N) { xs[j] = xs[j - 1]; ys[j] = ys[j - 1]; j++ }
        RatesMesh.ribbonInto(histCurve, xs, ys, HZ + 0.006f, 0.0016f)
        meshEdited(histCurve)
    }

    // ---------------------------------------------------------------- energy profile

    /** Schematic energy (kJ/mol) along the reaction coordinate s ∈ [0, 1]: reactants 0, hump [ea], products E_PROD. */
    private fun profile(s: Double, ea: Double): Double {
        val e = ea / 1000
        val base = E_PROD * smoothD((s - 0.38) / 0.24)
        return base + (e - E_PROD / 2) * exp(-((s - 0.5) / 0.13) * ((s - 0.5) / 0.13))
    }
    private fun smoothD(t: Double): Double { val x = t.coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
    private fun ex(s: Double) = EX0 + 0.01f + (EX1 - EX0 - 0.02f) * s.toFloat()
    private fun ey(eKj: Double) = EY0 + (EY1 - EY0) * ((eKj - E_MIN) / (E_MAX - E_MIN)).toFloat()

    private fun placeProfile(dt: Float) {
        catHump.visible = catalyst; catArrow.visible = catalyst
        // new collisions -> balls (reactions and wrong-side hits first; near misses only into spare balls)
        val newest = micro.evTotal
        var from = maxOf(evSeen, newest - micro.evCap)
        while (from < newest) {
            val slot = (from % micro.evCap).toInt()
            val kind = micro.evKind[slot]
            val free = ballAge.indices.filter { ballAge[it] >= ballDur[it] }
            if (free.isNotEmpty() && (kind != CollisionBox.F_NEAR || free.size >= 3)) {
                val b = free.first()
                ballKind[b] = kind; ballPhi[b] = micro.evE[slot].coerceIn(0.0, 1.0); ballAge[b] = 0f; ballCat[b] = catalyst
                ballDur[b] = if (kind == CollisionBox.F_REACT) 1.5f else 1.1f
                balls[b].setMat(ballMats[kind])
            }
            from++
        }
        evSeen = newest
        for (b in balls.indices) {
            ballAge[b] += dt
            val on = ballAge[b] < ballDur[b]
            balls[b].visible = on
            if (!on) continue
            val u = (ballAge[b] / ballDur[b]).toDouble()
            val ea = if (ballCat[b]) Thiosulfate.EA_CAT else Thiosulfate.EA
            val s = when (ballKind[b]) {
                CollisionBox.F_REACT -> 0.04 + 0.92 * smoothD(u)
                else -> {
                    val top = sTop(ballPhi[b], ea)
                    0.04 + (top - 0.04) * sin(PI * u)
                }
            }
            balls[b].pos = P3(ex(s), ey(profile(s, ea)) + 0.009f, EZ + 0.008f)
        }
    }

    /** Where on the rising side the profile reaches [phi] of the hump height (bisection). */
    private fun sTop(phi: Double, ea: Double): Double {
        val target = phi * ea / 1000
        var lo = 0.04; var hi = 0.5
        repeat(30) { val m = (lo + hi) / 2; if (profile(m, ea) < target) lo = m else hi = m }
        return lo
    }

    // ---------------------------------------------------------------- results chart

    private fun cx(v: Double) = if (modeIdx == 0) PX0 + (PX1 - PX0) * (v / 70.0).toFloat() else PX0 + (PX1 - PX0) * (v / 0.20).toFloat()
    private fun cy(inv: Double) = PY0 + (PY1 - PY0) * (inv / chartScale).toFloat().coerceIn(0f, 1.04f)

    private fun placeChart() {
        for (n in tickT) n.visible = modeIdx == 0
        for (n in tickC) n.visible = modeIdx == 1
        var k = 0
        for (r in results) {
            if (r.mode != modeIdx || k >= points.size) continue
            val p = points[k++]
            p.visible = true
            p.pos = P3(cx(if (modeIdx == 0) r.tC else r.c0), cy(1 / r.t), PZ + 0.006f)
            p.setMat(pointMats[r.station])
        }
        while (k < points.size) points[k++].visible = false
    }
    private val pointMats by lazy { Array(4) { Mat.Unlit(STATION_COL[it]) } }

    /** The Arrhenius prediction 1/t for the selected flask's concentration (T series) or temperature (c series). */
    private fun writeChartCurve() {
        val sel = selected
        // y scale: the largest 1/t among the four set-ups and the measured points
        var top = 0.0
        for (s in 0 until 4) top = maxOf(top, 1 / Thiosulfate.tObscure(conc[s].toDouble(), bathT[s] + Arrhenius.KELVIN))
        for (r in results) if (r.mode == modeIdx) top = maxOf(top, 1 / r.t)
        chartScale = SCALES.firstOrNull { it >= top * 1.05 } ?: SCALES.last()
        val key = "%.3f".format(chartScale)
        if (key != chartKey) {
            chartKey = key
            yTickTop.setMat(Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick(fmtScale(chartScale)), blend = Blend.ADD))
            yTickMid.setMat(Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), tex = tick(fmtScale(chartScale / 2)), blend = Blend.ADD))
            val th = 0.018f
            yTickTop.scale = P3(th * RatesMesh.aspect(fmtScale(chartScale)), th, 1f)
            yTickMid.scale = P3(th * RatesMesh.aspect(fmtScale(chartScale / 2)), th, 1f)
            yTickTop.pos = P3(PX0 - 0.012f - yTickTop.scale.x / 2, PY1, PZ + 0.002f)
            yTickMid.pos = P3(PX0 - 0.012f - yTickMid.scale.x / 2, (PY0 + PY1) / 2, PZ + 0.002f)
        }
        val xs = FloatArray(CURVE_N); val ys = FloatArray(CURVE_N)
        for (i in 0 until CURVE_N) {
            val f = i / (CURVE_N - 1.0)
            val inv = if (modeIdx == 0) 1 / Thiosulfate.tObscure(conc[sel].toDouble(), 70.0 * f + Arrhenius.KELVIN)
                      else { val c = 0.20 * f; if (c <= Thiosulfate.S_STAR * 1.01) 0.0 else 1 / Thiosulfate.tObscure(c, bathT[sel] + Arrhenius.KELVIN) }
            xs[i] = cx(if (modeIdx == 0) 70.0 * f else 0.20 * f)
            ys[i] = cy(inv)
            if (i > 0 && inv > chartScale) {                                   // leaves the top: end the curve there
                val yRaw = PY0 + (PY1 - PY0) * (inv / chartScale).toFloat()
                val u = ((PY1 - ys[i - 1]) / (yRaw - ys[i - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
                val xe = xs[i - 1] + (xs[i] - xs[i - 1]) * u
                for (j in i until CURVE_N) { xs[j] = xe; ys[j] = PY1 }
                break
            }
        }
        RatesMesh.ribbonInto(chartCurve, xs, ys, PZ + 0.004f, 0.0022f)
        meshEdited(chartCurve)
    }
    private val SCALES = doubleArrayOf(0.05, 0.10, 0.15, 0.20, 0.30, 0.40, 0.60, 0.80)
    private fun fmtScale(v: Double) = if (abs(v * 100 - Math.round(v * 100)) < 1e-6) "%.2f".format(v) else "%.3f".format(v)

    // ================================================================ text

    private fun sci(v: Double): String {
        if (v <= 0) return "0"
        val e = kotlin.math.floor(kotlin.math.log10(v)).toInt()
        val sup = e.toString().map { if (it == '-') '⁻' else "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
        return "%.2f×10%s".format(v / Math.pow(10.0, e.toDouble()), sup)
    }

    private fun predicted(s: Int): Double {
        val tK = bathT[s] + Arrhenius.KELVIN
        val r = runs[s] ?: return Thiosulfate.tObscure(conc[s].toDouble(), tK)
        if (r.obscured) return r.tObscured
        val left = Thiosulfate.kStar(r.c0) - r.kInt
        return r.t + left / Thiosulfate.k(liquidK[s])
    }

    private fun updateReadout() {
        val s = selected
        val tC = liquidK[s] - Arrhenius.KELVIN
        val k = Thiosulfate.k(liquidK[s])
        val r = runs[s]
        val tPart = when {
            r != null && r.obscured -> tr("X 消失：t = %.1f s", "X gone at t = %.1f s").format(r.tObscured)
            r != null -> tr("计时 %.1f s（预计 %.1f s）", "timing %.1f s (expect %.1f s)").format(r.t, predicted(s))
            phase[s] == POURING -> tr("正在倒酸（预计 %.1f s）", "pouring acid (expect %.1f s)").format(predicted(s))
            else -> tr("未开始（预计 %.1f s）", "not started (expect %.1f s)").format(predicted(s))
        }
        val fm = micro.fractionMeasured(); val ft = micro.fractionTheory()
        val line1 = tr("烧瓶 %d · %.1f °C · %.2f mol/L · k = %s s⁻¹ · %s · 分子窗口 ≥Ea 碰撞 %.1f%%（理论 %.1f%%）\n",
            "Flask %d · %.1f °C · %.2f mol/L · k = %s s⁻¹ · %s · window: collisions ≥ Ea %.1f%% (theory %.1f%%)\n")
            .format(s + 1, tC, conc[s], sci(k), tPart, fm * 100, ft * 100)
        val r10 = Arrhenius.ratio(Thiosulfate.EA, liquidK[s], liquidK[s] + 10)
        val line2 = tr("Arrhenius：k = A·e^(−Ea/RT)，Ea = 53 kJ/mol → 升温 10 °C，k ×%.2f · 速率 = k[S₂O₃²⁻]，X 消失时间 t ∝ 1/速率\n",
            "Arrhenius: k = A·e^(−Ea/RT), Ea = 53 kJ/mol → +10 °C gives k ×%.2f · rate = k[S₂O₃²⁻], time for the X t ∝ 1/rate\n").format(r10)
        val line3 = when {
            catalyst -> tr("催化剂：能垒 53 → 40 kJ/mol，越过能垒的碰撞 ×%.0f（窗口里 ×%.1f）—— 不必升温，同样的碰撞就有更多能反应",
                "Catalyst: barrier 53 → 40 kJ/mol, collisions over it ×%.0f (×%.1f in the window): same collisions, far more succeed")
                .format(Arrhenius.catalystFactor(Thiosulfate.EA - Thiosulfate.EA_CAT, liquidK[s]),
                    Math.pow(Arrhenius.catalystFactor(Thiosulfate.EA - Thiosulfate.EA_CAT, liquidK[s]), 1 / micro.energyScale))
            modeIdx == 1 -> tr("浓度：S₂O₃²⁻ 越浓，碰撞越多 → 速率 ∝ 浓度：浓度减半，X 消失的时间加倍（Ea 不变）",
                "Concentration: more S₂O₃²⁻, more collisions → rate ∝ c: half the concentration, twice the time (same Ea)")
            else -> tr("为什么：升温 10 °C，分子平均速度只快 %.1f%%，越过 Ea 的碰撞却 ×%.2f —— 是碰撞更猛，不只是更多",
                "Why: +10 °C makes molecules only %.1f%% faster, yet collisions above Ea ×%.2f: harder hits, not just more")
                .format((Arrhenius.speedRatio(liquidK[s], liquidK[s] + 10) - 1) * 100, r10)
        }
        readoutText = line1 + line2 + line3
    }

    private fun updateLabels() {
        labels.clear()
        labels += LabelSpec((if (modeIdx == 0) tr("Na₂S₂O₃ + 2HCl → 2NaCl + SO₂ + S↓ + H₂O\n1/t（s⁻¹）对温度（°C）· 点 = 测得，线 = Arrhenius 预测",
                "Na₂S₂O₃ + 2HCl → 2NaCl + SO₂ + S↓ + H₂O\n1/t (s⁻¹) vs temperature (°C) · dots measured, line Arrhenius")
            else tr("Na₂S₂O₃ + 2HCl → 2NaCl + SO₂ + S↓ + H₂O\n1/t（s⁻¹）对浓度（mol/L）· 点 = 测得，线 = 一级反应预测",
                "Na₂S₂O₃ + 2HCl → 2NaCl + SO₂ + S↓ + H₂O\n1/t (s⁻¹) vs concentration (mol/L) · dots measured, line first order")),
            P3((PX0 + PX1) / 2, PY1 + 0.065f, PZ + 0.01f))
        val cond = if (modeIdx == 0) tr("烧瓶 1–4：%s °C · 均为 %.2f mol/L", "Flasks 1–4: %s °C · all %.2f mol/L").format(bathT.joinToString(" / ") { "%d".format(it.roundToInt()) }, conc[0])
                   else tr("烧瓶 1–4：%s mol/L · 均为 %d °C", "Flasks 1–4: %s mol/L · all %d °C").format(conc.joinToString(" / ") { "%.2f".format(it) }, bathT[0].roundToInt())
        val done = lastDone
        labels += if (done >= 0 && lastDoneAge < 5f && runs[done]?.obscured == true)
            LabelSpec(tr("烧瓶 %d：X 看不见了！t = %.1f s", "Flask %d: the X is gone! t = %.1f s").format(done + 1, runs[done]!!.tObscured), P3(SX[done], 0.075f, SZ + 0.02f))
        else LabelSpec(cond, P3((SX[0] + SX[3]) / 2, 0.075f, SZ))
        labels += LabelSpec(tr("分子窗口 · 烧瓶 %d · %.0f °C · 能量放大 ×5\nA（蓝，黄端能反应）+ B（橙）→ 产物（绿）", "Molecule window · flask %d · %.0f °C · energies ×5\nA (blue, yellow end reacts) + B (orange) → products (green)")
            .format(selected + 1, liquidK[selected] - Arrhenius.KELVIN), P3(MB.x, MB.y + WIN + 0.06f, MB.z + WIN))
        labels += LabelSpec(tr("碰撞能量（沿连心线，kJ/mol）\n竖线 = Ea · 金色可反应 %.1f%%（放大 ×20）", "Collision energy along the line of centres (kJ/mol)\nline = Ea · gold can react: %.1f%% (×20)")
            .format(micro.fractionMeasured() * 100), P3((HX0 + HX1) / 2, HY0 - 0.065f, HZ + 0.02f))
        labels += LabelSpec(if (catalyst) tr("能量剖面（示意）· Ea 53 → 40 kJ/mol（催化）", "Energy profile (sketch) · Ea 53 → 40 kJ/mol (catalysed)")
            else tr("能量剖面（示意）· Ea = 53 kJ/mol", "Energy profile (sketch) · Ea = 53 kJ/mol"), P3((EX0 + EX1) / 2, EY1 + 0.043f, EZ + 0.02f))
    }

    // ================================================================ interaction

    override val tapTargets: List<TapTarget> get() {
        val list = ArrayList<TapTarget>()
        for (s in 0 until 4) list += TapTarget("Flask$s", "烧瓶 ${s + 1} → 开始", "Flask ${s + 1} → start") { phase[s] != IDLE && runs[s]?.obscured != true }
        for (s in 0 until 4) list += TapTarget("Bath$s", "水浴 ${s + 1} → +10 °C", "Bath ${s + 1} → +10 °C")
        list += TapTarget("Catalyst", "催化剂 → 加/撤", "Catalyst → on/off") { catalyst }
        list += TapTarget("MicroBox", "分子盒 → 重置", "Molecules → reset")
        return list
    }

    override fun isTarget(name: String) = name.startsWith("Flask") || name.startsWith("Bath") || name == "Catalyst" || name == "MicroBox"
    override fun isDragTarget(name: String) = name.startsWith("Bath")

    override fun onTap(name: String) {
        lastTap = when {
            name.startsWith("Flask") -> {
                val s = name.removePrefix("Flask").toIntOrNull() ?: return
                val wasSel = selected == s
                select(s)
                val r = runs[s]
                if (phase[s] != IDLE && (phase[s] == POURING || (r != null && !r.obscured))) {
                    tr("烧瓶 %d 正在计时：%.1f s，预计 %.1f s 时 X 消失%s", "Flask %d is timing: %.1f s, the X should go at %.1f s%s")
                        .format(s + 1, r?.t ?: 0.0, predicted(s), if (wasSel) "" else tr(" · 分子窗口已切到它", " · window now shows it"))
                } else {
                    startRun(s)
                    tr("烧瓶 %d：倒入盐酸，秒表开始 —— %d °C、%.2f mol/L，预计 %.0f s 后 X 消失", "Flask %d: acid in, stopwatch on · %d °C, %.2f mol/L, the X should go in %.0f s")
                        .format(s + 1, bathT[s].roundToInt(), conc[s], Thiosulfate.tObscure(conc[s].toDouble(), bathT[s] + Arrhenius.KELVIN))
                }
            }
            name.startsWith("Bath") -> {
                val s = name.removePrefix("Bath").toIntOrNull() ?: return
                select(s)
                val old = bathT[s]
                // 5 → 15 → … → 65 → 5 °C
                val v = if (old >= 64.5f) 5f else ((kotlin.math.floor((old - 5f) / 10f) + 1) * 10f + 5f).coerceIn(5f, 65f)
                setBath(s, v)
                bathMessage(s, old, v)
            }
            name == "Catalyst" -> {
                toggleCatalyst(!catalyst)
                if (catalyst) tr("加入催化剂：能垒 53 → 40 kJ/mol，金色碰撞变多（窗口 ×2.9，真实 ×190）", "Catalyst in: barrier 53 → 40 kJ/mol, more gold collisions (×2.9 here, ×190 for real)")
                else tr("撤去催化剂：能垒回到 53 kJ/mol", "Catalyst out: the barrier is back at 53 kJ/mol")
            }
            name == "MicroBox" -> {
                micro.reset(liquidK[selected], 1889L + frame); evSeen = micro.evTotal
                tr("分子盒重置：%d 个 A + %d 个 B，统计重新开始", "Box reset: %d A + %d B, statistics start again").format(micro.nA, micro.nB)
            }
            else -> ""
        }
    }

    private fun bathMessage(s: Int, old: Float, v: Float): String {
        val r = Arrhenius.ratio(Thiosulfate.EA, old + Arrhenius.KELVIN.toDouble(), v + Arrhenius.KELVIN.toDouble())
        val sp = Arrhenius.speedRatio(old + Arrhenius.KELVIN.toDouble(), v + Arrhenius.KELVIN.toDouble())
        return if (v > old) tr("水浴 %d → %d °C：平均速度只增加 %.1f%%，但越过活化能的碰撞 ×%.1f", "Bath %d → %d °C: molecules only %.1f%% faster, but collisions over Ea ×%.1f")
                .format(s + 1, v.roundToInt(), (sp - 1) * 100, r)
            else tr("水浴 %d → %d °C（加冰）：越过活化能的碰撞只剩 ×%.2f，X 要很久才消失", "Bath %d → %d °C (iced): collisions over Ea ×%.2f, the X will take a long time")
                .format(s + 1, v.roundToInt(), r)
    }

    override fun tapMessage(name: String): String? = lastTap.ifEmpty { null }

    override fun onDragStart(name: String) {
        val s = name.removePrefix("Bath").toIntOrNull() ?: return
        dragBath = s; dragT0 = bathT[s]; dragAcc = 0f; select(s)
    }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        val s = dragBath; if (s < 0) return
        dragAcc += dx + dy                                     // right or up = warmer, 1 °C per 4 mm
        setBath(s, (dragT0 + dragAcc * 250f).roundToInt().toFloat())
    }
    override fun onDragEnd(name: String) { dragBath = -1 }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "all_done" to { modeIdx = 0; resetAll(); startAll(); val sp = speedIdx; speedIdx = 2; repeat(480) { update(1f / 60f) }; speedIdx = sp },
        "concentration" to { setMode(1); startAll(); val sp = speedIdx; speedIdx = 2; repeat(560) { update(1f / 60f) }; speedIdx = sp },
        "catalyst" to { setMode(0); toggleCatalyst(true); select(1); repeat(600) { update(1f / 60f) } },
    )

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(8.dp))
                SegmentControl {
                    SegmentItem(selected = modeIdx == 0, onClick = { setMode(0) }, title = { Text(tr("温度系列", "T series")) })
                    SegmentItem(selected = modeIdx == 1, onClick = { setMode(1) }, title = { Text(tr("浓度系列", "c series")) })
                }
                Spacer(Modifier.width(8.dp))
                LabSwitch(tr("催化剂", "Catalyst"), catalyst) { toggleCatalyst(it) }
                Button(onClick = { startAll() }) { Text(tr("全部开始", "Start all")) }
                Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
            }
            val s = selected
            LabSlider(tr("烧瓶 %d 水浴", "Bath %d").format(s + 1), bathT[s], 5f..65f, "%d °C".format(bathT[s].roundToInt())) { setBath(s, it.roundToInt().toFloat()) }
            LabSlider(tr("烧瓶 %d 浓度", "Flask %d c").format(s + 1), conc[s], 0.03f..0.20f, "%.2f mol/L".format(conc[s])) { setConc(s, (it * 100).roundToInt() / 100f) }
        }
    }

    // ================================================================ helpers

    private fun waterMat(t: Float): Mat {
        val cold = Rgb(0.62f, 0.88f, 1.0f); val room = Rgb(0.62f, 0.80f, 0.96f); val hot = Rgb(0.86f, 0.80f, 0.74f)
        val c = if (t < 25f) ProcTex.okLerp(room, cold, ((25f - t) / 20f).coerceIn(0f, 1f)) else ProcTex.okLerp(room, hot, ((t - 25f) / 40f).coerceIn(0f, 1f))
        return Mat.Pbr(c, metallic = 0f, roughness = 0.05f, alpha = 0.10f)
    }

    /** Thermostat ring: icy blue when cooling, dim at room temperature, orange-red when heating. */
    private fun heaterMat(t: Float): Mat {
        val warm = Rgb(1.00f, 0.42f, 0.12f); val cold = Rgb(0.30f, 0.72f, 1.00f); val idle = Rgb(0.28f, 0.26f, 0.24f)
        val c = if (t >= 25f) ProcTex.okLerp(idle, warm, ((t - 25f) / 35f).coerceIn(0f, 1f)) else ProcTex.okLerp(idle, cold, ((25f - t) / 18f).coerceIn(0f, 1f))
        return Mat.Unlit(c, alpha = 1f, blend = Blend.ADD)
    }

    private fun ringPath(r: Float, n: Int) = FloatArray(n * 3) { k -> val a = 2 * PI * (k / 3) / n; when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() } }

    private fun placeSeg(n: Node, a: P3, b: P3, r: Float) {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        n.pos = P3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        n.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, l, r)
    }
}
