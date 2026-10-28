// 酸碱中和滴定 · Acid–Base Titration — the classic bench, a live pH curve and a molecule window.
//
// LESSON. 25.00 mL of acid (HCl, or the weak acid CH₃COOH) and two drops of indicator sit in an Erlenmeyer
// flask on a magnetic stirrer over a white tile; 0.100 mol/L NaOH runs in from a 50 mL burette. Each drop
// flashes the indicator's base colour where it lands and the stirring wipes it out — slowly, then more and
// more slowly as the acid runs out — until ONE drop turns the whole flask. A pH meter reads the solution;
// the board draws pH against the volume added (the curve, the equivalence point and, for the weak acid,
// the half-equivalence point pH = pKa of the buffer), with the indicator's computed colour range as a
// band: phenolphthalein's band sits inside the vertical jump for both acids, methyl orange's sits in the
// acetic acid's buffer region — it turns at 2 mL instead of 25. The molecule window beside the flask shows
// H₃O⁺ meeting OH⁻ (→ H₂O), Na⁺ / Cl⁻ / CH₃COO⁻ as spectators, and the undissociated CH₃COOH molecules
// releasing H⁺ as it is consumed.
//
// HANDS. Pinch the stopcock = one drop (or shut it if it is running). Pinch-and-drag it down (or right)
// = open it: the drip rate grows with the square of the hand travel (2.5 cm ≈ a drop every 2 s, 15 cm =
// a 1 mL/s stream), and it stays where you leave it — go fast, then slow near the end, the real skill.
// Pinch a bottle: the acid bottles refill the flask with that acid, the indicator bottles swap the
// indicator (virtual: same solution, other dye). Pinch the flask: refill. The demo opens mid-titration
// (24.00 mL into HCl with phenolphthalein, dripping once a second): the endpoint arrives in ~20 s.
//
// Chemistry: core/titration — exact pH from the full charge balance (bisection on log[H⁺]), exact
// dilution, the drop's mixing parcel, indicator colour from the two forms' spectra, the micro counts.
// Endpoint verdict: the colour must hold for 30 s with the stopcock shut (school rule); error vs 25.00 mL.
// Credit: Gay-Lussac named the burette and made volumetric analysis a precision method (1824).

package com.bytedance.chemistrylab.demos.titration

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.core.titration.Acid
import com.bytedance.chemistrylab.core.titration.Indicator
import com.bytedance.chemistrylab.core.titration.IndicatorOptics
import com.bytedance.chemistrylab.core.titration.MicroScale
import com.bytedance.chemistrylab.core.titration.MicroTargets
import com.bytedance.chemistrylab.core.titration.Titration
import com.bytedance.chemistrylab.demos.flame.StrokeText
import com.bytedance.chemistrylab.lab.BakeCache
import com.bytedance.chemistrylab.lab.Bi
import com.bytedance.chemistrylab.lab.Challenge
import com.bytedance.chemistrylab.lab.Lang
import com.bytedance.chemistrylab.lab.Lesson
import com.bytedance.chemistrylab.lab.LessonStep
import com.bytedance.chemistrylab.lab.Q
import com.bytedance.chemistrylab.lab.SpecDemo
import com.bytedance.chemistrylab.lab.TapTarget
import com.bytedance.chemistrylab.lab.spec.Blend
import com.bytedance.chemistrylab.lab.spec.LabelSpec
import com.bytedance.chemistrylab.lab.spec.Mat
import com.bytedance.chemistrylab.lab.spec.Node
import com.bytedance.chemistrylab.lab.tr
import com.bytedance.chemistrylab.lab.ui.Button
import com.bytedance.chemistrylab.lab.ui.FieldRef
import com.bytedance.chemistrylab.lab.ui.LabPanel
import com.bytedance.chemistrylab.lab.ui.LabRow
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object TitrationDemo : SpecDemo() {
    override val id = "titration"
    override val title = "酸碱中和滴定"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Gay-Lussac · 1824"

    // ---------------------------------------------------------------- layout (metres, volume space)
    private const val PLINTH_Y = -0.262f
    private const val DECK = PLINTH_Y + 0.0175f
    private const val FX = -0.20f
    private const val FZ = 0.03f
    private const val BASE_TOP = DECK + 0.014f
    private const val STIR_H = 0.050f
    private const val STIR_TOP = BASE_TOP + STIR_H
    private const val TILE_TOP = STIR_TOP + 0.006f
    private const val FB = TILE_TOP                     // flask outer bottom
    private const val IB = FB + 0.004f                  // inner bottom
    private const val R0 = 0.090f                       // inner radius at the bottom (a 250 mL flask drawn ~2.1x)
    private const val R1 = 0.024f                       // neck inner radius
    private const val H_BODY = 0.165f                   // inner bottom -> shoulder
    private const val NECK_TOP = FB + 0.215f
    /** Drawn liquid volume / real volume: 25 mL in a 250 mL flask is a 5 mm layer; drawn ×22 (≈ 2.8³) it is 2.5 cm deep. */
    private const val VIS = 22.0
    // burette (0 mL at the top, 50 mL at the bottom, drawn 2.8x wide so the column and meniscus read)
    private const val BX = FX
    private const val BZ = FZ
    private const val TIP_Y = NECK_TOP - 0.016f
    private const val COCK_Y = 0.068f
    private const val Y50 = 0.112f
    private const val Y0 = 0.412f
    private const val MPM = (Y0 - Y50) / 50f
    private const val TUBE_BOT = 0.092f
    private const val TUBE_TOP = 0.436f
    private const val TR = 0.0155f
    private const val WR = 0.0125f
    private const val RD = 0.0042f
    // stand
    private const val ROD_X = -0.375f
    private const val ROD_Z = -0.09f
    // pH meter
    private const val MX = 0.10f
    private const val MZ = 0.17f
    // chart
    private const val CX0 = 0.37f; private const val CX1 = 0.85f
    private const val CY0 = -0.150f; private const val CY1 = 0.270f
    private const val CZ = -0.06f
    private const val SXV = (CX1 - CX0) / 50f
    private const val SYP = (CY1 - CY0) / 14f
    private const val N_SEG = 90
    private const val N_CELL = 50
    // molecule window
    private val WC = P3(-0.64f, 0.165f, -0.03f)
    private const val WW = 0.40f; private const val WH = 0.34f; private const val WD = 0.12f
    private const val MOL_S = 0.016f                    // metres per Å in the window
    // bottles
    private val IND_X = floatArrayOf(-0.845f, -0.75f, -0.655f)
    private const val IND_Z = 0.22f
    private val ACID_X = floatArrayOf(-0.535f, -0.425f)
    private const val ACID_Z = 0.235f

    // ---------------------------------------------------------------- process constants
    private const val DROP = Titration.DROP_ML
    private const val MAX_DROPS = 1000                  // 50 mL
    private const val TAU_MIX = 0.35                    // s, e-folding of the drop's parcel growth (stirred)
    private const val RMAX = 1.0f                       // mL/s, stopcock wide open
    private const val DRAG_FULL = 0.15f                 // m of hand travel to open fully
    private const val BURST = 0.42f                     // mL/s while a requested drop / +1 mL is delivered
    private const val STREAM_AT = 0.35f                 // mL/s: drops merge into a stream
    private const val HOLD_S = 30f
    private const val MAX_PARCELS = 8
    private const val K_SPR = 5
    private const val N_DP = 14
    private const val G_DRAW = 6.0f
    private const val OMEGA = (2 * PI * 0.8).toFloat()
    private val SPEEDS = floatArrayOf(0.5f, 1f, 3f)
    private val SPEED_LABELS get() = listOf(tr("慢 ×0.5", "Slow ×0.5"), tr("实时 ×1", "Real ×1"), tr("快 ×3", "Fast ×3"))

    // ---------------------------------------------------------------- UI state
    var speedIdx = 1
    var showMicro = true
    var acid = Acid.HCL; private set
    var indicator = Indicator.PHENOLPHTHALEIN; private set
    /** Stopcock opening as a flow rate, mL/s (simulated time). */
    var flow = 0f; private set

    // ---------------------------------------------------------------- state
    private var tit = Titration(Acid.HCL)
    private var nOut = 0                                // drops that left the burette (its reading)
    private var nLanded = 0                             // drops that reached the flask
    private var nMixed = 0                              // drops stirred into the bulk (the chemistry state)
    private var pending = 0                             // requested single drops not yet delivered
    private var tipFill = 0f                            // the drop forming at the tip, 0..1
    private val dOn = BooleanArray(N_DP); private val dY = FloatArray(N_DP); private val dV = FloatArray(N_DP)
    private class Parcel(val vbDrops: Int, var n: Int, val theta: Float, val tMix: Float) {
        var age = 0f; var shown = 0f; var ever = false; var pH = 13.0
    }
    private val parcels = ArrayList<Parcel>()
    private var fShown = 0.0                            // displayed base fraction of the bulk (eased)
    private var pHm = 7.0                               // pH meter reading (first-order response)
    private var holdT = 0f
    private var lastFlash = -1f
    private var splashT = 9f
    private var stirA = 0f
    private var simT = 0.0
    private var frame = 0
    private var textEn = false
    private var lastTap = ""
    private var dragD = 0f
    private var rnd = Random(1824)
    private var curveFor = -1; private var curveAcid: Acid? = null; private var curveInd: Indicator? = null
    private var microFor = -1; private var microAcid: Acid? = null; private var microNeedsReset = true
    private var targets = MicroTargets(0, 0, 0, 0, 0, 0)
    private val pHTab = HashMap<Acid, DoubleArray>()
    private val ions = IonWindow(0.180f, 0.142f, 0.034f)

    // ---------------------------------------------------------------- nodes
    private lateinit var colLiquid: Node
    private lateinit var menisc: Node
    private lateinit var cardW: Node; private lateinit var cardB: Node
    private lateinit var leverRot: Node
    private lateinit var hanging: Node
    private lateinit var drops: Array<Node>
    private lateinit var stream: Node
    private lateinit var liqBody: Node; private lateinit var liqColour: Node; private lateinit var liqSurf: Node
    private var liqIdx = -1
    private lateinit var sprites: Array<Node>
    private lateinit var splash: Node
    private lateinit var stirBar: Node
    private lateinit var segs: Array<Node>
    private lateinit var digits: Array<Array<Node>>
    private lateinit var dpNode: Node
    private val segShown = IntArray(4) { -1 }
    private lateinit var curve: Array<Node>
    private lateinit var cells: Array<Node>
    private lateinit var liveDot: Node; private lateinit var liveGlow: Node
    private lateinit var eqDot: Node; private lateinit var halfDot: Node; private lateinit var halfDash: Node
    private lateinit var band: Node; private lateinit var bandLo: Node; private lateinit var bandHi: Node
    private lateinit var strip: Node
    private lateinit var window: Node
    private lateinit var beams: Array<Node>
    private lateinit var indRing: Node; private lateinit var acidRing: Node
    private val px = FloatArray(MAX_DROPS + 1); private val py = FloatArray(MAX_DROPS + 1); private val sl = FloatArray(MAX_DROPS + 1)

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val blackRubber = Mat.Pbr(Rgb(0.08f, 0.08f, 0.09f), roughness = 0.6f)
    private val redRubber = Mat.Pbr(Rgb(0.55f, 0.16f, 0.12f), roughness = 0.55f)
    private val flaskGlass = Mat.Pbr(Rgb(0.70f, 0.80f, 0.90f), roughness = 0.03f, alpha = 0.20f)
    private val buretteGlass = Mat.Pbr(Rgb(0.88f, 0.94f, 1.0f), roughness = 0.03f, alpha = 0.20f)
    private val bottleGlass = Mat.Pbr(Rgb(0.86f, 0.93f, 1.0f), roughness = 0.04f, alpha = 0.26f)
    private val naohMat = Mat.Pbr(Rgb(0.62f, 0.80f, 1.0f), roughness = 0.05f, alpha = 0.36f)
    private val waterBody = Mat.Pbr(Rgb(0.50f, 0.68f, 0.92f), roughness = 0.05f, alpha = 0.20f)
    private val menMat = Mat.Unlit(Rgb(0.80f, 0.95f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val surfMat = Mat.Unlit(Rgb(0.30f, 0.50f, 0.75f), alpha = 0.55f, blend = Blend.TRANSPARENT)
    private val tickMat = Mat.Unlit(Rgb(0.90f, 0.95f, 1f), alpha = 0.90f, blend = Blend.TRANSPARENT)
    private val whiteTile = Mat.Unlit(Rgb(0.86f, 0.86f, 0.84f))
    private val whiteCard = Mat.Unlit(Rgb(0.80f, 0.80f, 0.78f))
    private val inkCard = Mat.Unlit(Rgb(0.04f, 0.04f, 0.05f))
    private val housing = Mat.Pbr(Rgb(0.86f, 0.87f, 0.88f), metallic = 0.1f, roughness = 0.45f)
    private val panelDark = Mat.Pbr(Rgb(0.12f, 0.13f, 0.15f), roughness = 0.40f)
    private val meterCase = Mat.Pbr(Rgb(0.16f, 0.19f, 0.24f), metallic = 0.2f, roughness = 0.45f)
    private val ptfe = Mat.Pbr(Rgb(0.97f, 0.97f, 0.96f), roughness = 0.35f, emissive = Rgb(0.16f, 0.16f, 0.16f))
    private val probeBody = Mat.Pbr(Rgb(0.20f, 0.22f, 0.25f), roughness = 0.35f)
    private val probeBulb = Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), roughness = 0.05f, alpha = 0.45f)
    private val boardMat = Mat.Pbr(Rgb(0.05f, 0.06f, 0.08f), roughness = 0.08f)
    private val gridMat = Mat.Unlit(Rgb(0.55f, 0.70f, 0.85f), alpha = 0.20f, blend = Blend.TRANSPARENT)
    private val axisMat = Mat.Unlit(Rgb(0.80f, 0.88f, 0.96f), alpha = 0.80f, blend = Blend.TRANSPARENT)
    private val curveMat = Mat.Unlit(Rgb(1.0f, 0.97f, 0.90f), alpha = 1f, blend = Blend.ADD)
    private val eqMat = Mat.Unlit(Rgb(1.0f, 0.78f, 0.30f), alpha = 0.85f, blend = Blend.ADD)
    private val halfMat = Mat.Unlit(Rgb(0.40f, 0.85f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val segOn = Mat.Unlit(Rgb(0.45f, 1.0f, 0.75f), alpha = 1f, blend = Blend.ADD)
    private val segOff = Mat.Unlit(Rgb(0.25f, 0.40f, 0.34f), alpha = 0.10f, blend = Blend.TRANSPARENT)
    private val ledOn = Mat.Unlit(Rgb(0.30f, 1.0f, 0.45f), alpha = 1f, blend = Blend.ADD)
    private val dropMat = Mat.Unlit(Rgb(0.82f, 0.94f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val beamMat = Mat.Unlit(Rgb(0.45f, 0.80f, 1.0f), alpha = 0.22f, blend = Blend.ADD)
    private val selMat = Mat.Unlit(Rgb(0.45f, 0.85f, 1.0f), alpha = 0.85f, blend = Blend.ADD)
    private val glassBack = Mat.Pbr(Rgb(0.05f, 0.07f, 0.11f), roughness = 0.08f, alpha = 0.82f)
    private val glassFront = Mat.Pbr(Rgb(0.70f, 0.82f, 0.95f), roughness = 0.04f, alpha = 0.06f)
    private val slabTex by lazy { ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77) }
    private val blobTex: Image by lazy { TitrationMesh.blob() }
    private val atomTex: Image by lazy { TitrationMesh.atomTex() }
    private val splashMats by lazy { Array(5) { k -> Mat.Unlit(Rgb(0.85f, 0.95f, 1.0f), alpha = 0.75f * (1f - k / 5f), blend = Blend.ADD) } }
    private val strips = HashMap<Indicator, Image>()
    private val numTex = HashMap<String, Image>()
    private val NUMS = listOf("0", "2", "4", "6", "8", "10", "12", "14", "20", "30", "40", "50")

    // colour of the solution: a pure absorber drawn as an alpha layer that reproduces white × T exactly
    // over the white tile/card (closed shell: both faces blend, each with 1 − √(1 − a))
    private val layerCache = HashMap<Long, Mat>()
    private fun layerMat(t: DoubleArray, faces: Int, tex: Image?): Mat {
        val q = IntArray(3) { (t[it] * 127).roundToInt().coerceIn(0, 127) }
        val key = q[0].toLong() or (q[1].toLong() shl 7) or (q[2].toLong() shl 14) or (faces.toLong() shl 21) or ((if (tex != null) 1L else 0L) shl 23)
        return layerCache.getOrPut(key) {
            val tq = DoubleArray(3) { q[it] / 127.0 }
            val aT = (1 - minOf(tq[0], tq[1], tq[2])).coerceIn(0.0, 0.97)
            if (aT < 0.004) return@getOrPut Mat.Unlit(Rgb(1f, 1f, 1f), alpha = 0f, tex = tex, blend = Blend.TRANSPARENT)
            val c = DoubleArray(3) { k -> ((tq[k] - (1 - aT)) / aT).coerceIn(0.0, 1.0) }
            val a = if (faces == 2) 1 - sqrt(1 - aT) else aT
            Mat.Unlit(Rgb(TitrationMesh.srgb(c[0]), TitrationMesh.srgb(c[1]), TitrationMesh.srgb(c[2])), alpha = a.toFloat(), tex = tex, blend = Blend.TRANSPARENT)
        }
    }
    /** What the solution looks like against white (opaque swatch, for the chart). */
    private val seenCache = HashMap<Long, Mat>()
    private fun seenMat(t: DoubleArray): Mat {
        val q = IntArray(3) { (t[it] * 63).roundToInt().coerceIn(0, 63) }
        val key = q[0].toLong() or (q[1].toLong() shl 6) or (q[2].toLong() shl 12)
        return seenCache.getOrPut(key) { Mat.Unlit(Rgb(TitrationMesh.srgb(q[0] / 63.0), TitrationMesh.srgb(q[1] / 63.0), TitrationMesh.srgb(q[2] / 63.0))) }
    }

    private fun table(a: Acid): DoubleArray = pHTab.getOrPut(a) { val t = Titration(a); DoubleArray(MAX_DROPS + 1) { t.pH(it * DROP) } }
    private fun pHAt(drops: Int) = table(acid)[drops.coerceIn(0, MAX_DROPS)]
    private val pe get() = IndicatorOptics.endpointPH(indicator)

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        frame = 0; textEn = Lang.en; lastTap = ""; speedIdx = 1; showMicro = true
        rnd = Random(1824)
        liqIdx = -1; segShown.fill(-1); curveFor = -1; curveAcid = null; curveInd = null
        val imgs = BakeCache.load(cacheDir, "titration", 1) { NUMS.map { StrokeText.bake(it, 64) } }
        numTex.clear(); NUMS.forEachIndexed { i, s -> numTex[s] = imgs[i] }
        strips.clear()
        for (ind in Indicator.values()) strips[ind] = TitrationMesh.strip(141) { u ->
            val c = IndicatorOptics.colourAtPH(ind, 14.0 * u)
            floatArrayOf(TitrationMesh.srgb(c[0]), TitrationMesh.srgb(c[1]), TitrationMesh.srgb(c[2]))
        }

        val root = Node("TitrationRoot")
        val unitCyl = MeshGen.cylinder(1f, 1f, 20)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val sphere = MeshGen.sphere(1f, 12, 20, MeshGen.SphereUv.EQUIRECT)
        val quad = TitrationMesh.quad()

        root.add("Plinth", MeshGen.box(1.78f, 0.035f, 0.80f), Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.55f, tex = slabTex), P3(0f, PLINTH_Y, -0.02f))
        root.add("PlinthBand", MeshGen.box(1.79f, 0.008f, 0.81f), alu, P3(0f, PLINTH_Y, -0.02f))

        buildStand(root, unitCyl, unitBox)
        buildFlask(root, unitCyl, unitBox, sphere, quad)
        buildBurette(root, unitCyl, unitBox, sphere, quad)
        buildProbeAndMeter(root, unitCyl, unitBox, sphere)
        buildChart(root, unitBox, sphere, quad)
        buildWindow(root, unitCyl, unitBox, quad)
        buildBottles(root, unitCyl, sphere)

        // the opening: mid-titration, 24.00 mL of NaOH into HCl + phenolphthalein, dripping once a second
        setup(Acid.HCL, Indicator.PHENOLPHTHALEIN, 480)
        flow = DROP.toFloat()
        repeat(24) { update(1f / 60f) }
        update(0f)
        return root
    }

    private fun buildStand(root: Node, unitCyl: MeshData, unitBox: MeshData) {
        root.add("StandBase", MeshGen.box(0.38f, 0.014f, 0.30f), darkSteel, P3(FX - 0.045f, DECK + 0.007f, FZ - 0.04f))
        root.add("StandRod", unitCyl, alu, P3(ROD_X, (BASE_TOP + 0.445f) / 2, ROD_Z)).also { it.scale = P3(0.0075f, 0.445f - BASE_TOP, 0.0075f) }
        for (y in floatArrayOf(0.330f, 0.200f)) {
            root.add("ClampBoss", unitBox, darkSteel, P3(ROD_X, y, ROD_Z)).also { it.scale = P3(0.026f, 0.026f, 0.026f) }
            root.add("ClampArm", MeshGen.tube(floatArrayOf(ROD_X, y, ROD_Z, BX - TR - 0.004f, y, BZ - 0.004f), 0.0048f, 10, false), darkSteel)
            root.add("ClampJaw", TitrationMesh.ring(TR + 0.0035f, 0.0032f, 28), blackRubber, P3(BX, y, BZ))
        }
        // stirrer: light housing, dark front, speed knob and a green LED; its top is the white tile
        root.add("Stirrer", MeshGen.box(0.235f, STIR_H, 0.215f), housing, P3(FX, BASE_TOP + STIR_H / 2, FZ + 0.005f))
        root.add("StirFront", MeshGen.box(0.225f, STIR_H - 0.012f, 0.004f), panelDark, P3(FX, BASE_TOP + STIR_H / 2, FZ + 0.1125f + 0.002f))
        root.add("StirKnob", MeshGen.cylinder(0.015f, 0.012f, 20), alu, P3(FX - 0.065f, BASE_TOP + STIR_H / 2, FZ + 0.121f)).rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        root.add("StirLed", MeshGen.sphere(0.0048f, 8, 12, MeshGen.SphereUv.EQUIRECT), ledOn, P3(FX + 0.07f, BASE_TOP + STIR_H / 2, FZ + 0.118f))
        root.add("Tile", MeshGen.box(0.215f, 0.006f, 0.20f), whiteTile, P3(FX, TILE_TOP - 0.003f, FZ + 0.005f))
        // a white card behind the flask (how a titration colour is judged), on a small foot
        root.add("Card", MeshGen.box(0.27f, TILE_TOP + 0.25f - BASE_TOP, 0.006f), whiteCard, P3(FX, (BASE_TOP + TILE_TOP + 0.25f) / 2, FZ - 0.12f))
        root.add("CardFoot", MeshGen.box(0.28f, 0.012f, 0.03f), darkSteel, P3(FX, BASE_TOP + 0.006f, FZ - 0.12f))
    }

    private fun rAt(h: Double) = R0 - (R0 - R1) * h / H_BODY
    private fun frustumVol(h: Double): Double { val r = rAt(h); return PI * h / 3 * (R0 * R0 + R0 * r + r * r) }
    /** Liquid height above the inner bottom for [vMl] mL in the flask (drawn volume = VIS × real). */
    private fun levelOf(vMl: Double): Float {
        val target = vMl * 1e-6 * VIS
        var lo = 0.0; var hi = H_BODY - 0.006
        repeat(32) { val m = (lo + hi) / 2; if (frustumVol(m) < target) lo = m else hi = m }
        return ((lo + hi) / 2).toFloat()
    }
    private val liqMeshes = HashMap<Int, MeshData>()
    private fun liqMesh(idx: Int): MeshData = liqMeshes.getOrPut(idx) {
        val h = idx * 0.0015f
        TitrationMesh.frustum(R0 - 0.0008f, rAt(h.toDouble()).toFloat() - 0.0008f, h, 40)
    }

    private fun buildFlask(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData, quad: MeshData) {
        val sh = IB + H_BODY
        val ys = floatArrayOf(FB + 0.0004f, FB + 0.004f, FB + 0.010f, sh - 0.016f, sh - 0.004f, sh + 0.006f, NECK_TOP)
        val rs = floatArrayOf(R0 - 0.003f, R0 + 0.0025f, R0 + 0.003f, rAt((sh - 0.016f - IB).toDouble()).toFloat() + 0.004f, R1 + 0.0055f, R1 + 0.003f, R1 + 0.003f)
        root.add("FlaskGlass", TitrationMesh.lathe(ys, rs, 48), flaskGlass, P3(FX, 0f, FZ))
        root.add("FlaskFloor", MeshGen.cylinder(R0 + 0.001f, 0.003f, 48), flaskGlass, P3(FX, FB + 0.0018f, FZ))
        root.add("FlaskLip", TitrationMesh.ring(R1 + 0.0045f, 0.003f, 32), Mat.Pbr(Rgb(0.60f, 0.70f, 0.82f), roughness = 0.05f, alpha = 0.60f), P3(FX, NECK_TOP, FZ))
        liqBody = root.add("Liquid", liqMesh(15), waterBody, P3(FX, IB, FZ))
        liqColour = root.add("LiquidColour", liqMesh(15), layerMat(doubleArrayOf(1.0, 1.0, 1.0), 2, null), P3(FX, IB, FZ))
        liqSurf = root.add("LiquidSurf", TitrationMesh.ring(1f, 0.028f, 48), surfMat, P3(FX, IB, FZ))
        stirBar = root.add("StirBar", TitrationMesh.capsule(0.036f, 0.0056f), ptfe, P3(FX, IB + 0.0062f, FZ))
        sprites = Array(MAX_PARCELS * K_SPR) { root.add("Plume$it", quad, layerMat(doubleArrayOf(1.0, 1.0, 1.0), 1, blobTex)).also { n -> n.billboard = true; n.visible = false } }
        splash = root.add("Splash", MeshGen.annulus(0.80f, 1.0f, 32), splashMats[0]).also { it.visible = false }
        root.add(Node("Flask")).also { it.pos = P3(FX, IB + 0.06f, FZ + 0.02f); it.tapRadius = 0.10f }
    }

    private fun buildBurette(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData, quad: MeshData) {
        root.add("BuretteTube", MeshGen.tube(floatArrayOf(0f, TUBE_BOT, 0f, 0f, TUBE_TOP, 0f), TR, 28, false), buretteGlass, P3(BX, 0f, BZ))
        root.add("BuretteLip", TitrationMesh.ring(TR + 0.0012f, 0.0022f, 28), Mat.Pbr(Rgb(0.90f, 0.95f, 1.0f), roughness = 0.05f, alpha = 0.5f), P3(BX, TUBE_TOP, BZ))
        val lowY = floatArrayOf(COCK_Y + 0.006f, COCK_Y + 0.014f, TUBE_BOT - 0.008f, TUBE_BOT + 0.0005f)
        root.add("BuretteTaper", TitrationMesh.lathe(lowY, floatArrayOf(0.0065f, 0.0068f, 0.0105f, TR), 24), buretteGlass, P3(BX, 0f, BZ))
        root.add("BuretteTaperLiq", TitrationMesh.lathe(lowY, floatArrayOf(0.004f, 0.0044f, 0.0078f, WR), 20), naohMat, P3(BX, 0f, BZ))
        val tipY = floatArrayOf(TIP_Y, TIP_Y + 0.006f, COCK_Y - 0.006f)
        root.add("BuretteTip", TitrationMesh.lathe(tipY, floatArrayOf(0.0024f, 0.0036f, 0.0058f), 16), buretteGlass, P3(BX, 0f, BZ))
        root.add("BuretteTipLiq", TitrationMesh.lathe(tipY, floatArrayOf(0.0012f, 0.0022f, 0.0036f), 12), naohMat, P3(BX, 0f, BZ))
        colLiquid = root.add("NaOHColumn", unitCyl, naohMat, P3(BX, 0.2f, BZ))
        menisc = root.add("BMeniscus", unitCyl, menMat, P3(BX, 0.2f, BZ))
        // graduations every 1 mL (5 and 10 longer), 0 mL at the top, on the side facing the viewer
        val ticks = ArrayList<TitrationMesh.Part>()
        for (ml in 0..50) {
            val w = if (ml % 10 == 0) 0.022f else if (ml % 5 == 0) 0.015f else 0.009f
            ticks += TitrationMesh.Part(unitBox, -0.007f + w / 2, Y0 - ml * MPM, TR + 0.0008f, w, if (ml % 5 == 0) 0.0017f else 0.0012f, 0.0012f)
        }
        root.add("BTicks", TitrationMesh.merge(ticks), tickMat, P3(BX, 0f, BZ))
        for (ml in 0..50 step 10) {
            val img = numTex["$ml"] ?: continue
            val h = 0.018f
            root.add("BNum$ml", quad, Mat.Unlit(Rgb(0.92f, 0.96f, 1f), tex = img, blend = Blend.ADD), P3(BX + TR + 0.006f + h * img.w / img.h / 2, Y0 - ml * MPM, BZ + 0.004f))
                .also { it.scale = P3(h * img.w / img.h, h, 1f) }
        }
        // reading card behind the meniscus: white above, black band just below (sharpens the meniscus)
        cardW = root.add("CardW", unitBox, Mat.Unlit(Rgb(0.88f, 0.88f, 0.86f)), P3(BX, 0.2f, BZ - TR - 0.006f)).also { it.scale = P3(0.046f, 0.022f, 0.002f) }
        cardB = root.add("CardB", unitBox, inkCard, P3(BX, 0.2f, BZ - TR - 0.006f)).also { it.scale = P3(0.046f, 0.022f, 0.002f) }
        // stopcock: barrel across the line of sight, PTFE plug, a key that turns from horizontal (shut) to down (open)
        root.add("CockBarrel", unitCyl, buretteGlass, P3(BX, COCK_Y, BZ)).also { it.scale = P3(0.0088f, 0.048f, 0.0088f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        root.add("CockPlug", unitCyl, ptfe, P3(BX, COCK_Y, BZ)).also { it.scale = P3(0.0060f, 0.052f, 0.0060f); it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()) }
        val cock = root.add(Node("Stopcock")).also { it.pos = P3(BX, COCK_Y, BZ + 0.028f); it.tapRadius = 0.05f }
        cock.add("CockHub", MeshGen.cylinder(0.0105f, 0.010f, 20), ptfe).rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        leverRot = cock.add(Node("CockKey"))
        leverRot.add("CockLever", MeshGen.box(0.058f, 0.013f, 0.008f), ptfe, P3(-0.031f, 0f, 0.002f))
        leverRot.add("CockGrip", MeshGen.box(0.012f, 0.020f, 0.010f), ptfe, P3(-0.058f, 0f, 0.002f))
        hanging = root.add("TipDrop", sphere, dropMat).also { it.visible = false }
        drops = Array(N_DP) { root.add("Drop$it", sphere, dropMat).also { n -> n.visible = false; n.uniformScale(RD) } }
        stream = root.add("Stream", unitCyl, Mat.Unlit(Rgb(0.80f, 0.93f, 1.0f), alpha = 0.55f, blend = Blend.ADD)).also { it.visible = false }
    }

    private val P_BULB = P3(FX - 0.040f, IB + 0.018f, FZ + 0.014f)
    private val P_NECK = P3(FX + 0.013f, NECK_TOP - 0.003f, FZ + 0.004f)
    private fun probeAt(y: Float): P3 { val t = (y - P_BULB.y) / (P_NECK.y - P_BULB.y); return P3(P_BULB.x + (P_NECK.x - P_BULB.x) * t, y, P_BULB.z + (P_NECK.z - P_BULB.z) * t) }

    private fun buildProbeAndMeter(root: Node, unitCyl: MeshData, unitBox: MeshData, sphere: MeshData) {
        val up = probeAt(0.165f); val cap = probeAt(0.205f)
        seg(root.add("ProbeRod", unitCyl, probeBody), P_BULB, up, 0.0055f)
        seg(root.add("ProbeCap", unitCyl, blackRubber), up, cap, 0.0075f)
        root.add("ProbeBulb", sphere, probeBulb, P_BULB).uniformScale(0.0078f)
        // electrode holder from the stand rod (passes behind the burette)
        val hy = 0.172f; val hp = probeAt(hy)
        root.add("ProbeArm", MeshGen.tube(floatArrayOf(ROD_X, hy, ROD_Z, hp.x - 0.004f, hy, hp.z - 0.004f), 0.004f, 8, false), darkSteel)
        root.add("ProbeRing", TitrationMesh.ring(0.0085f, 0.0026f, 20), blackRubber, hp)
        root.add("ClampBoss2", unitBox, darkSteel, P3(ROD_X, hy, ROD_Z)).also { it.scale = P3(0.024f, 0.024f, 0.024f) }
        // the cable to the meter
        val path = spline(listOf(cap, P3(cap.x + 0.05f, cap.y + 0.035f, cap.z + 0.01f), P3(cap.x + 0.15f, 0.12f, 0.08f), P3(0.03f, -0.05f, 0.12f),
            P3(MX - 0.02f, DECK + 0.075f, MZ - 0.085f)), 60)
        root.add("Cable", MeshGen.tube(path, 0.0028f, 6, false), blackRubber)
        // bench pH meter: case, tilted display head, big 7-segment digits
        root.add("MeterCase", MeshGen.box(0.25f, 0.055f, 0.15f), meterCase, P3(MX, DECK + 0.0275f, MZ))
        root.add("MeterKeys", MeshGen.box(0.20f, 0.004f, 0.04f), panelDark, P3(MX, DECK + 0.057f, MZ + 0.05f))
        val disp = root.add(Node("MeterHead")).also { it.pos = P3(MX, DECK + 0.098f, MZ - 0.012f); it.rot = Q.axisAngle(1f, 0f, 0f, -0.42f) }
        disp.add("MeterHeadBox", MeshGen.box(0.25f, 0.105f, 0.034f), meterCase, P3(0f, 0f, -0.017f))
        disp.add("MeterWindow", MeshGen.box(0.226f, 0.082f, 0.002f), Mat.Unlit(Rgb(0.015f, 0.025f, 0.025f)), P3(0f, 0f, 0.0005f))
        root.add("MeterStem", MeshGen.box(0.20f, 0.05f, 0.04f), meterCase, P3(MX, DECK + 0.065f, MZ - 0.03f))
        val segBox = unitBox
        // "pH" small, then dd.dd
        val small = listOf(10 to -0.094f, 11 to -0.071f)
        val big = floatArrayOf(-0.034f, 0.006f, 0.052f, 0.092f)
        val all = ArrayList<Array<Node>>()
        for ((ch, x) in small) {
            val w = 0.015f; val h = 0.026f; val t = 0.0036f
            for (k in 0 until 7) { val r = TitrationMesh.segRect(k, w, h, t)
                disp.add("PSeg${ch}_$k", segBox, if ((TitrationMesh.SEG[ch] shr k) and 1 == 1) segOn else segOff, P3(x + r[0], -0.010f + r[1], 0.0025f)).also { it.scale = P3(r[2], r[3], 0.0015f) } }
        }
        for ((d, x) in big.withIndex()) {
            val w = 0.029f; val h = 0.052f; val t = 0.0056f
            all += Array(7) { k -> val r = TitrationMesh.segRect(k, w, h, t)
                disp.add("DSeg${d}_$k", segBox, segOff, P3(x + r[0], r[1], 0.0025f)).also { it.scale = P3(r[2], r[3], 0.0015f) } }
        }
        digits = all.toTypedArray()
        dpNode = disp.add("DSegDp", segBox, segOn, P3(0.029f, -0.026f, 0.0025f)).also { it.scale = P3(0.0062f, 0.0062f, 0.0015f) }
    }

    private fun buildChart(root: Node, unitBox: MeshData, sphere: MeshData, quad: MeshData) {
        val bx = (CX0 + CX1) / 2 - 0.0f
        root.add("Board", MeshGen.box(0.56f, 0.535f, 0.012f), boardMat, P3(0.605f, (CY0 + CY1) / 2 + 0.02f, CZ - 0.014f))
        root.add("BoardPost", MeshGen.cylinder(0.008f, CY0 - 0.05f - DECK, 12), darkSteel, P3(0.605f, (DECK + CY0 - 0.05f) / 2, CZ - 0.03f))
        root.add("BoardFoot", MeshGen.box(0.30f, 0.012f, 0.10f), darkSteel, P3(0.605f, DECK + 0.006f, CZ - 0.03f))
        val grid = ArrayList<TitrationMesh.Part>()
        for (p in 1..14) grid += TitrationMesh.Part(unitBox, bx, CY0 + p * SYP, CZ - 0.004f, CX1 - CX0, if (p == 7) 0.0022f else 0.0011f, 0.001f)
        for (v in 5..50 step 5) grid += TitrationMesh.Part(unitBox, CX0 + v * SXV, (CY0 + CY1) / 2, CZ - 0.004f, if (v % 10 == 0) 0.0016f else 0.001f, CY1 - CY0, 0.001f)
        root.add("Grid", TitrationMesh.merge(grid), gridMat)
        val ax = ArrayList<TitrationMesh.Part>()
        ax += TitrationMesh.Part(unitBox, bx, CY0, CZ - 0.002f, CX1 - CX0 + 0.004f, 0.003f, 0.002f)
        ax += TitrationMesh.Part(unitBox, CX0, (CY0 + CY1) / 2, CZ - 0.002f, 0.003f, CY1 - CY0, 0.002f)
        for (p in 0..14 step 2) ax += TitrationMesh.Part(unitBox, CX0 - 0.005f, CY0 + p * SYP, CZ - 0.002f, 0.010f, 0.0022f, 0.002f)
        for (v in 0..50 step 10) ax += TitrationMesh.Part(unitBox, CX0 + v * SXV, CY0 - 0.005f, CZ - 0.002f, 0.0022f, 0.010f, 0.002f)
        root.add("Axes", TitrationMesh.merge(ax), axisMat)
        val nm = Mat.Unlit(Rgb(0.85f, 0.92f, 1f), blend = Blend.ADD)
        for (p in 0..14 step 2) { val img = numTex["$p"] ?: continue; val h = 0.021f; val w = h * img.w / img.h
            root.add("PNum$p", quad, nm.copy(tex = img), P3(CX0 - 0.016f - w / 2 + 0.006f, CY0 + p * SYP, CZ)).also { it.scale = P3(w, h, 1f) } }
        for (v in 0..50 step 10) { val img = numTex["$v"] ?: continue; val h = 0.021f; val w = h * img.w / img.h
            root.add("VNum$v", quad, nm.copy(tex = img), P3(CX0 + v * SXV, CY0 - 0.024f, CZ)).also { it.scale = P3(w, h, 1f) } }
        // the indicator: colour scale up the pH axis, its computed transition band across the plot
        strip = root.add("PhStrip", quad, Mat.Unlit(Rgb(1f, 1f, 1f), tex = strips[Indicator.PHENOLPHTHALEIN]), P3(CX0 + 0.007f, (CY0 + CY1) / 2, CZ - 0.001f)).also { it.scale = P3(0.009f, CY1 - CY0, 1f) }
        band = root.add("Band", quad, gridMat, P3(bx, 0f, CZ - 0.003f))
        bandLo = root.add("BandLo", unitBox, axisMat, P3(bx, 0f, CZ - 0.002f)).also { it.scale = P3(CX1 - CX0, 0.0016f, 0.001f) }
        bandHi = root.add("BandHi", unitBox, axisMat, P3(bx, 0f, CZ - 0.002f)).also { it.scale = P3(CX1 - CX0, 0.0016f, 0.001f) }
        // equivalence (dashed vertical at 25.00 mL) and, for the weak acid, the half-equivalence point
        val eqX = CX0 + 25f * SXV
        val dashes = ArrayList<TitrationMesh.Part>()
        var y = CY0; while (y < CY1 - 0.004f) { dashes += TitrationMesh.Part(unitBox, eqX, y + 0.006f, CZ - 0.001f, 0.0022f, 0.010f, 0.001f); y += 0.018f }
        root.add("EqDash", TitrationMesh.merge(dashes), eqMat)
        eqDot = root.add("EqDot", sphere, eqMat).also { it.uniformScale(0.0085f) }
        halfDot = root.add("HalfDot", sphere, halfMat).also { it.uniformScale(0.0085f) }
        val hpH = Titration(Acid.ACETIC).pH(12.5).toFloat()
        val hd = ArrayList<TitrationMesh.Part>()
        var x = CX0; while (x < CX0 + 12.5f * SXV - 0.004f) { hd += TitrationMesh.Part(unitBox, x + 0.005f, CY0 + hpH * SYP, CZ - 0.001f, 0.009f, 0.0020f, 0.001f); x += 0.016f }
        y = CY0; while (y < CY0 + hpH * SYP - 0.004f) { hd += TitrationMesh.Part(unitBox, CX0 + 12.5f * SXV, y + 0.005f, CZ - 0.001f, 0.0020f, 0.009f, 0.001f); y += 0.016f }
        halfDash = root.add("HalfDash", TitrationMesh.merge(hd), halfMat)
        // the curve (segments by transform), the flask colour under it (one cell per mL), the live point
        cells = Array(N_CELL) { root.add("Cell$it", unitBox, seenMat(doubleArrayOf(1.0, 1.0, 1.0))).also { n -> n.visible = false } }
        curve = Array(N_SEG) { root.add("Curve$it", unitBox, curveMat).also { n -> n.visible = false } }
        liveGlow = root.add("LiveGlow", quad, Mat.Unlit(Rgb(1f, 0.95f, 0.8f), alpha = 0.6f, tex = blobTex, blend = Blend.ADD)).also { it.billboard = true; it.uniformScale(0.045f) }
        liveDot = root.add("LiveDot", sphere, curveMat).also { it.uniformScale(0.0075f) }
    }

    private fun buildWindow(root: Node, unitCyl: MeshData, unitBox: MeshData, quad: MeshData) {
        window = root.add(Node("MolWindow")).also { it.pos = WC }
        window.add("WinBack", MeshGen.box(WW, WH, 0.004f), glassBack, P3(0f, 0f, -WD / 2))
        window.add("WinFront", MeshGen.box(WW, WH, 0.003f), glassFront, P3(0f, 0f, WD / 2))
        val r = 0.007f
        val rails = ArrayList<TitrationMesh.Part>()
        for (sy in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(-WD / 2, WD / 2)) rails += TitrationMesh.Part(unitBox, 0f, sy * (WH / 2 + r / 2), z, WW + 2 * r, r, r)
        for (sx in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(-WD / 2, WD / 2)) rails += TitrationMesh.Part(unitBox, sx * (WW / 2 + r / 2), 0f, z, r, WH, r)
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f)) rails += TitrationMesh.Part(unitBox, sx * (WW / 2 + r / 2), sy * (WH / 2 + r / 2), 0f, r, r, WD)
        window.add("WinRails", TitrationMesh.merge(rails), alu)
        val bottom = WC.y - WH / 2 - r
        root.add("WinPost", unitCyl, alu, P3(WC.x, (DECK + bottom) / 2, WC.z - WD / 2 + 0.012f)).also { it.scale = P3(0.007f, bottom - DECK, 0.007f) }
        root.add("WinFoot", MeshGen.cylinder(0.035f, 0.010f, 24), darkSteel, P3(WC.x, DECK + 0.005f, WC.z - WD / 2 + 0.012f))
        val molMat = Mat.Pbr(Rgb(1f, 1f, 1f), roughness = 0.32f, tex = atomTex)
        val meshes = arrayOf(TitrationMesh.hydronium(MOL_S), TitrationMesh.hydroxide(MOL_S), TitrationMesh.sodium(), TitrationMesh.chloride(),
            TitrationMesh.acetic(MOL_S, true), TitrationMesh.acetic(MOL_S, false), TitrationMesh.water(MOL_S))
        val pools = Array(IonWindow.KINDS) { k -> Array(IonWindow.CAP[k]) { i -> window.add("Ion${k}_$i", meshes[k], molMat).also { it.visible = false } } }
        val hMat = Mat.Unlit(Rgb(1.0f, 0.45f, 0.22f), alpha = 0.55f, tex = blobTex, blend = Blend.ADD)
        val oMat = Mat.Unlit(Rgb(0.30f, 0.62f, 1.0f), alpha = 0.60f, tex = blobTex, blend = Blend.ADD)
        val halos: Array<Array<Node>?> = Array(IonWindow.KINDS) { k ->
            when (k) {
                IonWindow.H3O -> Array(IonWindow.CAP[k]) { i -> window.add("HaloH_$i", quad, hMat).also { it.billboard = true; it.visible = false; it.uniformScale(0.075f) } }
                IonWindow.OH -> Array(IonWindow.CAP[k]) { i -> window.add("HaloOH_$i", quad, oMat).also { it.billboard = true; it.visible = false; it.uniformScale(0.070f) } }
                else -> null
            }
        }
        val flashes = Array(IonWindow.FLASH_N) { window.add("IonFlash$it", quad, warmFlash).also { n -> n.billboard = true; n.visible = false } }
        ions.attach(pools, halos, flashes)
        beams = Array(4) { root.add("Beam$it", unitCyl, beamMat) }
    }
    private val warmFlash = Mat.Unlit(Rgb(1.0f, 0.86f, 0.55f), alpha = 0.95f, tex = TitrationMesh.blob(), blend = Blend.ADD)
    private val coolFlash = Mat.Unlit(Rgb(0.60f, 0.85f, 1.0f), alpha = 0.80f, tex = warmFlash.tex, blend = Blend.ADD)

    private fun buildBottles(root: Node, unitCyl: MeshData, sphere: MeshData) {
        // indicator dropper bottles: clear glass, the stock solution inside, a band in the colour it turns
        val stock = listOf(Rgb(0.95f, 0.96f, 1.0f), Rgb(0.95f, 0.45f, 0.10f), Rgb(0.15f, 0.48f, 0.78f))
        for ((k, ind) in Indicator.values().withIndex()) {
            val g = root.add(Node("Ind$k")).also { it.pos = P3(IND_X[k], DECK + 0.05f, IND_Z); it.tapRadius = 0.048f }
            val bc = IndicatorOptics.baseColour(ind)
            val band = Rgb(TitrationMesh.srgb(bc[0]), TitrationMesh.srgb(bc[1]), TitrationMesh.srgb(bc[2]))
            g.add("IndBody$k", unitCyl, bottleGlass, P3(0f, -0.014f, 0f)).also { it.scale = P3(0.022f, 0.072f, 0.022f) }
            g.add("IndLiq$k", unitCyl, Mat.Pbr(stock[k], roughness = 0.1f, alpha = if (k == 0) 0.25f else 0.85f), P3(0f, -0.024f, 0f)).also { it.scale = P3(0.019f, 0.046f, 0.019f) }
            g.add("IndLabel$k", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.92f), roughness = 0.6f), P3(0f, -0.006f, 0f)).also { it.scale = P3(0.0224f, 0.022f, 0.0224f) }
            g.add("IndBand$k", unitCyl, Mat.Pbr(band, roughness = 0.5f, emissive = Rgb(band.r * 0.25f, band.g * 0.25f, band.b * 0.25f)), P3(0f, -0.006f, 0f)).also { it.scale = P3(0.0228f, 0.008f, 0.0228f) }
            g.add("IndShoulder$k", sphere, bottleGlass, P3(0f, 0.022f, 0f)).also { it.scale = P3(0.022f, 0.012f, 0.022f) }
            g.add("IndNeck$k", unitCyl, blackRubber, P3(0f, 0.036f, 0f)).also { it.scale = P3(0.010f, 0.012f, 0.010f) }
            g.add("IndPipette$k", unitCyl, bottleGlass, P3(0f, 0.046f, 0f)).also { it.scale = P3(0.0045f, 0.012f, 0.0045f) }
            g.add("IndTeat$k", sphere, redRubber, P3(0f, 0.058f, 0f)).also { it.scale = P3(0.0105f, 0.016f, 0.0105f) }
        }
        // acid reagent bottles: HCl (red band), CH₃COOH (amber band)
        val acidBand = listOf(Rgb(0.88f, 0.20f, 0.15f), Rgb(0.92f, 0.62f, 0.15f))
        for (k in 0..1) {
            val g = root.add(Node(if (k == 0) "AcidHCl" else "AcidAc")).also { it.pos = P3(ACID_X[k], DECK + 0.065f, ACID_Z); it.tapRadius = 0.052f }
            g.add("AcidBody$k", unitCyl, bottleGlass, P3(0f, -0.012f, 0f)).also { it.scale = P3(0.031f, 0.105f, 0.031f) }
            g.add("AcidLiq$k", unitCyl, Mat.Pbr(Rgb(0.80f, 0.90f, 1.0f), roughness = 0.08f, alpha = 0.30f), P3(0f, -0.026f, 0f)).also { it.scale = P3(0.028f, 0.072f, 0.028f) }
            g.add("AcidLabel$k", unitCyl, Mat.Pbr(Rgb(0.95f, 0.95f, 0.92f), roughness = 0.6f), P3(0f, -0.004f, 0f)).also { it.scale = P3(0.0315f, 0.034f, 0.0315f) }
            g.add("AcidBand$k", unitCyl, Mat.Pbr(acidBand[k], roughness = 0.5f, emissive = Rgb(acidBand[k].r * 0.2f, acidBand[k].g * 0.2f, acidBand[k].b * 0.2f)), P3(0f, -0.004f, 0f)).also { it.scale = P3(0.032f, 0.010f, 0.032f) }
            g.add("AcidShoulder$k", sphere, bottleGlass, P3(0f, 0.040f, 0f)).also { it.scale = P3(0.031f, 0.016f, 0.031f) }
            g.add("AcidNeck$k", unitCyl, bottleGlass, P3(0f, 0.058f, 0f)).also { it.scale = P3(0.012f, 0.018f, 0.012f) }
            g.add("AcidStopper$k", sphere, Mat.Pbr(Rgb(0.85f, 0.90f, 0.95f), roughness = 0.1f, alpha = 0.6f), P3(0f, 0.072f, 0f)).also { it.scale = P3(0.014f, 0.011f, 0.014f) }
        }
        indRing = root.add("IndRing", MeshGen.annulus(0.027f, 0.036f, 40), selMat)
        acidRing = root.add("AcidRing", MeshGen.annulus(0.036f, 0.046f, 40), selMat)
    }

    // ================================================================ experiment control

    /** Fresh flask: 25.00 mL of [a] + [ind], burette filled to [drops] already delivered (0 = full), stopcock shut. */
    private fun setup(a: Acid, ind: Indicator, drops: Int) {
        acid = a; indicator = ind; tit = Titration(a); setups++
        nOut = drops.coerceIn(0, MAX_DROPS); nLanded = nOut; nMixed = nOut
        pending = 0; tipFill = 0f; flow = 0f
        dOn.fill(false); parcels.clear()
        fShown = IndicatorOptics.baseFraction(ind, pHAt(nMixed)); pHm = pHAt(nMixed)
        holdT = 0f; lastFlash = -1f; splashT = 9f; simT = 0.0; dragD = 0f
        microNeedsReset = true; microFor = -1
        curveFor = -1
    }

    // read-only views for the headless flow test
    internal val mixedDrops get() = nMixed
    internal val outDrops get() = nOut
    internal val holdSeconds get() = holdT
    internal val microTargets get() = targets
    internal fun ionsShown(k: Int) = ions.shownFree(k)
    internal val liquidMat get() = liqColour.mat
    internal fun setupFor(a: Acid, ind: Indicator, drops: Int) = setup(a, ind, drops)

    fun refill() = setup(acid, indicator, 0)
    fun resetAll() { speedIdx = 1; showMicro = true; setup(Acid.HCL, Indicator.PHENOLPHTHALEIN, 0) }

    /** Ask for [n] more drops (one drop / +1 mL), within the burette's 50 mL. */
    fun addDrops(n: Int) { pending = (pending + n).coerceAtMost(MAX_DROPS - nOut); holdT = 0f }

    private fun setIndicator(ind: Indicator) { indicator = ind; holdT = 0f; lastFlash = -1f; curveFor = -1 }

    // ================================================================ per frame

    override fun update(dt: Float) {
        val sdt = dt * SPEEDS[speedIdx]
        simT += sdt
        stepBurette(sdt)
        stepFlask(sdt, dt == 0f)
        stepMicro(sdt)
        place(dt, sdt)
        if (frame++ % 6 == 0 || dt == 0f || Lang.en != textEn) { textEn = Lang.en; updateReadout(); updateLabels() }
    }

    private fun flowNow() = if (nOut >= MAX_DROPS) 0f else flow + if (pending > 0) BURST else 0f

    private fun stepBurette(sdt: Float) {
        if (nOut >= MAX_DROPS) { flow = 0f; pending = 0; tipFill = 0f }
        tipFill += flowNow() * sdt / DROP.toFloat()
        while (tipFill >= 1f && nOut < MAX_DROPS) {
            tipFill -= 1f; nOut++
            if (pending > 0) pending--
            val i = dOn.indexOfFirst { !it }
            if (i < 0) landDrop() else { dOn[i] = true; dY[i] = TIP_Y - RD * 1.4f; dV[i] = 0.25f }
        }
        // shut: the half-formed drop stays in the tip (the reading is then exactly the drops delivered)
        if (pending == 0 && flow == 0f) tipFill = maxOf(0f, tipFill - sdt * 2f)
        val surf = IB + levelOf(tit.vaMl + nLanded * DROP)
        for (i in 0 until N_DP) if (dOn[i]) {
            dV[i] += G_DRAW * sdt
            dY[i] -= dV[i] * sdt
            if (dY[i] <= surf) { dOn[i] = false; landDrop() }
        }
    }

    private fun landDrop() {
        nLanded++
        val last = parcels.lastOrNull()
        if (last != null && (last.age < 0.10f || parcels.size >= MAX_PARCELS)) { last.n++; return }
        val vTot = tit.vaMl + nLanded * DROP
        parcels += Parcel(nLanded - 1, 1, rnd.nextFloat() * 6.283f, (TAU_MIX * ln(vTot / DROP)).toFloat())
        splashT = 0f
    }

    private fun stepFlask(sdt: Float, snap: Boolean) {
        val pE = pe
        for (p in parcels) {
            p.age += sdt
            p.pH = tit.parcelPH(p.vbDrops * DROP, p.n, p.age.toDouble(), TAU_MIX)
            if (p.pH >= pE) { p.shown += sdt; p.ever = true }
        }
        while (parcels.isNotEmpty() && parcels[0].age >= parcels[0].tMix) {
            val p = parcels.removeAt(0)
            nMixed += p.n
            if (p.ever) lastFlash = p.shown
        }
        val pH = pHAt(nMixed)
        val f = IndicatorOptics.baseFraction(indicator, pH)
        fShown = if (snap) f else fShown + (f - fShown) * (1 - exp(-sdt / 0.30))
        pHm = if (snap) pH else pHm + (pH - pHm) * (1 - exp(-sdt / 0.45))
        // the 30 s rule: the colour must hold with the stopcock shut and nothing in flight
        val reached = pH >= pE
        val idle = flow == 0f && pending == 0 && dOn.none { it } && parcels.isEmpty()
        holdT = if (reached && idle) holdT + sdt else 0f
        stirA += sdt * OMEGA * 2.2f
        splashT += sdt
    }

    private fun stepMicro(sdt: Float) {
        if (microFor != nMixed || microAcid != acid) { microFor = nMixed; microAcid = acid; targets = MicroScale.targets(tit, nMixed * DROP) }
        if (microNeedsReset) { ions.reset(targets, 7 + nMixed); microNeedsReset = false }
        if (showMicro) ions.step(sdt, targets, !acid.strong, 0.16f)
    }

    // ================================================================ placement

    private fun place(dt: Float, sdt: Float) {
        // burette column and meniscus (reading = drops that left + the one forming)
        val read = (nOut + tipFill) * DROP.toFloat()
        val ym = Y0 - read * MPM
        val bot = TUBE_BOT + 0.0008f
        colLiquid.visible = ym > bot + 0.001f
        colLiquid.pos = P3(BX, (bot + ym) / 2, BZ); colLiquid.scale = P3(WR, maxOf(0.001f, ym - bot), WR)
        menisc.visible = colLiquid.visible
        menisc.pos = P3(BX, ym, BZ); menisc.scale = P3(WR + 0.0006f, 0.0035f, WR + 0.0006f)
        cardW.pos = P3(BX, ym + 0.0095f, BZ - TR - 0.006f); cardB.pos = P3(BX, ym - 0.0125f, BZ - TR - 0.006f)
        cardW.visible = colLiquid.visible; cardB.visible = colLiquid.visible
        val fl = flowNow()
        leverRot.rot = Q.axisAngle(0f, 0f, 1f, (PI / 2).toFloat() * sqrt((fl / RMAX).coerceIn(0f, 1f)))
        // the forming drop, the falling drops, a stream when it runs fast
        hanging.visible = tipFill > 0.06f
        if (hanging.visible) { val r = RD * Math.cbrt(tipFill.toDouble()).toFloat() * 1.15f; hanging.uniformScale(r); hanging.pos = P3(BX, TIP_Y - r * 0.9f, BZ) }
        for (i in 0 until N_DP) { drops[i].visible = dOn[i]; if (dOn[i]) drops[i].pos = P3(BX, dY[i], BZ) }
        val h = levelOf(tit.vaMl + nLanded * DROP)
        val surf = IB + h
        stream.visible = fl >= STREAM_AT
        if (stream.visible) { stream.pos = P3(BX, (TIP_Y + surf) / 2, BZ); stream.scale = P3(0.0018f + 0.0012f * (fl / RMAX), TIP_Y - surf, 0.0018f + 0.0012f * (fl / RMAX)) }
        // the liquid: level, colour (the bulk, eased), surface ring
        val idx = (h / 0.0015f).roundToInt().coerceAtLeast(2)
        if (idx != liqIdx) { liqIdx = idx; val m = liqMesh(idx); liqBody.setMesh(m); liqColour.setMesh(m) }
        val bulkT = IndicatorOptics.colour(indicator, fShown)
        liqColour.setMat(layerMat(bulkT, 2, null))
        val rs = rAt((idx * 0.0015f).toDouble()).toFloat() - 0.002f
        liqSurf.pos = P3(FX, IB + idx * 0.0015f, FZ); liqSurf.uniformScale(rs)
        stirBar.rot = Q.axisAngle(0f, 1f, 0f, stirA)
        placePlume(surf, h)
        splash.visible = splashT < 0.3f
        if (splash.visible) {
            val u = splashT / 0.3f
            splash.pos = P3(BX, surf + 0.0015f, BZ); splash.uniformScale(0.006f + 0.026f * u)
            splash.setMat(splashMats[(u * 4.99f).toInt().coerceIn(0, 4)])
        }
        placeMeter()
        updateCurve()
        // live point on the curve, at the meter's reading
        val lx = CX0 + nMixed * DROP.toFloat() * SXV; val ly = CY0 + pHm.toFloat() * SYP
        liveDot.pos = P3(lx, ly, CZ + 0.004f); liveGlow.pos = P3(lx, ly, CZ + 0.006f)
        // molecule window + its magnifier beams from the flask
        window.visible = showMicro
        ions.place(showMicro)
        for (f in 0 until ions.flashCount) if (ions.flashes[f].visible) ions.flashes[f].setMat(if (ions.flashWarm(f)) warmFlash else coolFlash)
        val src = P3(FX - 0.045f, IB + 0.016f, FZ + 0.03f)
        for (b in 0 until 4) {
            val sy = if (b % 2 == 0) -1f else 1f; val sz = if (b < 2) -1f else 1f
            placeSeg(beams[b], src, P3(WC.x + WW / 2, WC.y + sy * WH / 2, WC.z + sz * WD / 2), 0.0013f)
            beams[b].visible = showMicro
        }
        // selection rings under the chosen bottles
        val ii = indicator.ordinal
        indRing.pos = P3(IND_X[ii], DECK + 0.0015f, IND_Z)
        acidRing.pos = P3(ACID_X[if (acid == Acid.HCL) 0 else 1], DECK + 0.0015f, ACID_Z)
    }

    private fun placePlume(surf: Float, h: Float) {
        val fBulk = IndicatorOptics.baseFraction(indicator, pHAt(nMixed))
        for (j in 0 until MAX_PARCELS) {
            val p = parcels.getOrNull(j)
            var vis = false
            var mat: Mat? = null
            var fr = 0f
            if (p != null) {
                val fLoc = IndicatorOptics.baseFraction(indicator, p.pH)
                val vd = p.n * DROP; val vTot = tit.vaMl + (p.vbDrops + p.n) * DROP
                fr = Math.cbrt((vd * exp(p.age / TAU_MIX)).coerceAtMost(vTot) / vTot).toFloat()
                vis = abs(fLoc - fBulk) > 0.02 && abs(fLoc - fShown) > 0.02
                if (vis) mat = layerMat(IndicatorOptics.colour(indicator, fLoc, 0.35 + 0.65 * fr), 1, blobTex)
            }
            for (k in 0 until K_SPR) {
                val n = sprites[j * K_SPR + k]
                n.visible = vis
                if (!vis || p == null) continue
                val t = p.age
                val th = p.theta + OMEGA * t - 0.55f * k
                val rr = (0.006f + 0.55f * (R0 - 0.01f) * (1 - exp(-t / 0.8f))) * (1 - 0.06f * k)
                val yy = (surf - 0.005f - 0.45f * h * (1 - exp(-t / 1.0f)) + 0.002f * k).coerceIn(IB + 0.006f, surf - 0.002f)
                val rMax = rAt((yy - IB).toDouble()).toFloat() - 0.012f
                val r = minOf(rr, rMax)
                n.pos = P3(FX + r * cos(th), yy, FZ + r * sin(th))
                n.uniformScale((0.020f + 0.090f * fr) * (1 - 0.12f * k))
                n.setMat(mat)
            }
        }
    }

    private fun placeMeter() {
        val v = (pHm * 100).roundToInt().coerceIn(0, 1499)
        val d = intArrayOf(v / 1000, (v / 100) % 10, (v / 10) % 10, v % 10)
        for (i in 0 until 4) {
            val code = if (i == 0 && d[0] == 0) 0 else TitrationMesh.SEG[d[i]]
            if (segShown[i] == code) continue
            segShown[i] = code
            for (k in 0 until 7) digits[i][k].setMat(if ((code shr k) and 1 == 1) segOn else segOff)
        }
    }

    private fun updateCurve() {
        if (curveFor == nMixed && curveAcid == acid && curveInd == indicator) return
        val indChanged = curveInd != indicator || curveAcid != acid
        curveFor = nMixed; curveAcid = acid; curveInd = indicator
        val tab = table(acid)
        val n = nMixed
        for (k in 0..n) { px[k] = CX0 + k * DROP.toFloat() * SXV; py[k] = CY0 + tab[k].toFloat() * SYP }
        if (n == 0) for (s in curve) s.visible = false
        else if (n <= N_SEG) {
            for (i in 0 until N_SEG) { curve[i].visible = i < n; if (i < n) placeSeg2(curve[i], px[i], py[i], px[i + 1], py[i + 1]) }
        } else {
            sl[0] = 0f
            for (k in 1..n) { val dx = px[k] - px[k - 1]; val dy = py[k] - py[k - 1]; sl[k] = sl[k - 1] + sqrt(dx * dx + dy * dy) }
            var j = 0
            var qx = px[0]; var qy = py[0]
            for (i in 1..N_SEG) {
                val target = sl[n] * i / N_SEG
                while (j < n - 1 && sl[j + 1] < target) j++
                val span = (sl[j + 1] - sl[j]).coerceAtLeast(1e-9f)
                val u = ((target - sl[j]) / span).coerceIn(0f, 1f)
                val rx = px[j] + (px[j + 1] - px[j]) * u; val ry = py[j] + (py[j + 1] - py[j]) * u
                curve[i - 1].visible = true
                placeSeg2(curve[i - 1], qx, qy, rx, ry)
                qx = rx; qy = ry
            }
        }
        // flask colour under the curve, one cell per mL
        val vNow = n * DROP.toFloat()
        for (c in 0 until N_CELL) {
            val on = vNow > c + 1e-4f
            cells[c].visible = on
            if (!on) continue
            val v1 = minOf(c + 1f, vNow)
            cells[c].pos = P3(CX0 + (c + v1) / 2 * SXV, CY0 + 0.012f, CZ - 0.0005f)
            cells[c].scale = P3((v1 - c) * SXV + 0.0002f, 0.014f, 0.002f)
            val k = (((c + v1) / 2) / DROP).roundToInt().coerceIn(0, MAX_DROPS)
            cells[c].setMat(seenMat(IndicatorOptics.colourAtPH(indicator, tab[k])))
        }
        if (indChanged) {
            val (lo, hi) = IndicatorOptics.transitionRange(indicator)
            val yLo = CY0 + lo.toFloat() * SYP; val yHi = CY0 + hi.toFloat() * SYP
            band.pos = P3((CX0 + CX1) / 2, (yLo + yHi) / 2, CZ - 0.003f); band.scale = P3(CX1 - CX0, yHi - yLo, 1f)
            val mid = IndicatorOptics.colourAtPH(indicator, IndicatorOptics.endpointPH(indicator))
            val m = seenMat(mid) as Mat.Unlit
            band.setMat(Mat.Unlit(m.color, alpha = 0.22f, blend = Blend.TRANSPARENT))
            bandLo.pos = P3((CX0 + CX1) / 2, yLo, CZ - 0.002f); bandHi.pos = P3((CX0 + CX1) / 2, yHi, CZ - 0.002f)
            bandLo.setMat(Mat.Unlit((seenMat(IndicatorOptics.colourAtPH(indicator, lo)) as Mat.Unlit).color, alpha = 0.85f, blend = Blend.TRANSPARENT))
            bandHi.setMat(Mat.Unlit((seenMat(IndicatorOptics.colourAtPH(indicator, hi)) as Mat.Unlit).color, alpha = 0.85f, blend = Blend.TRANSPARENT))
            strip.setMat(Mat.Unlit(Rgb(1f, 1f, 1f), tex = strips[indicator]))
            val eqPH = tab[(tit.veqMl / DROP).roundToInt()].toFloat()
            eqDot.pos = P3(CX0 + tit.veqMl.toFloat() * SXV, CY0 + eqPH * SYP, CZ + 0.003f)
            val weak = !acid.strong
            halfDot.visible = weak; halfDash.visible = weak
            if (weak) halfDot.pos = P3(CX0 + 12.5f * SXV, CY0 + tab[250].toFloat() * SYP, CZ + 0.003f)
        }
    }

    private fun placeSeg2(n: Node, x0: Float, y0: Float, x1: Float, y1: Float) {
        val dx = x1 - x0; val dy = y1 - y0
        val l = sqrt(dx * dx + dy * dy)
        n.pos = P3((x0 + x1) / 2, (y0 + y1) / 2, CZ + 0.002f)
        n.rot = Q.axisAngle(0f, 0f, 1f, atan2(dy, dx))
        n.scale = P3(l + 0.0032f, 0.0040f, 0.003f)
    }

    private fun placeSeg(n: Node, a: P3, b: P3, r: Float) {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        n.pos = P3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        n.rot = Q.fromTo(0f, 1f, 0f, dx, dy, dz)
        n.scale = P3(r, l, r)
    }
    private fun seg(n: Node, a: P3, b: P3, r: Float) = placeSeg(n, a, b, r)

    // ================================================================ text

    private fun indName(i: Indicator = indicator) = when (i) {
        Indicator.PHENOLPHTHALEIN -> tr("酚酞", "phenolphthalein")
        Indicator.METHYL_ORANGE -> tr("甲基橙", "methyl orange")
        Indicator.BROMOTHYMOL_BLUE -> tr("溴百里酚蓝", "bromothymol blue")
    }
    private fun colourName(i: Indicator, pH: Double): String {
        val s = IndicatorOptics.stage(i, pH)
        return when (i) {
            Indicator.PHENOLPHTHALEIN -> when (s) { 0 -> tr("无色", "colourless"); 1 -> tr("浅粉红", "faint pink"); 2 -> tr("粉红", "pink"); else -> tr("紫红", "magenta") }
            Indicator.METHYL_ORANGE -> when (s) { 0 -> tr("红色", "red"); 1 -> tr("橙红", "orange-red"); 2 -> tr("橙色", "orange"); else -> tr("黄色", "yellow") }
            Indicator.BROMOTHYMOL_BLUE -> when (s) { 0 -> tr("黄色", "yellow"); 1 -> tr("黄绿", "yellow-green"); 2 -> tr("蓝绿", "blue-green"); else -> tr("蓝色", "blue") }
        }
    }
    private fun turnWord(i: Indicator = indicator) = when (i) {
        Indicator.PHENOLPHTHALEIN -> tr("粉红", "pink"); Indicator.METHYL_ORANGE -> tr("橙", "orange"); Indicator.BROMOTHYMOL_BLUE -> tr("绿", "green")
    }
    private fun acidName(a: Acid = acid) = if (a == Acid.HCL) "HCl" else "CH₃COOH"
    private fun rangeText(i: Indicator = indicator): String { val (lo, hi) = IndicatorOptics.transitionRange(i); return "%.1f–%.1f".format(lo, hi) }
    private fun vNow() = nMixed * DROP
    /** Where this indicator turns for this acid, minus the equivalence volume (mL). */
    private fun indicatorError(i: Indicator = indicator, a: Acid = acid): Double { val t = Titration(a); return t.volumeAt(IndicatorOptics.endpointPH(i)) - t.veqMl }

    private fun cockText(): String {
        val fl = flowNow()
        return when {
            nOut >= MAX_DROPS -> tr("滴定管已空", "burette empty")
            pending > 0 && flow == 0f -> tr("正在滴加 %d 滴", "adding %d drops").format(pending)
            fl <= 0f -> tr("活塞关", "stopcock shut")
            fl >= STREAM_AT -> tr("活塞全开 %.2f mL/s，成线", "wide open %.2f mL/s, a stream").format(fl)
            else -> tr("活塞开 %.2f mL/s（%.1f 滴/秒）", "open %.2f mL/s (%.1f drops/s)").format(fl, fl / DROP)
        }
    }

    private fun updateReadout() {
        val pH = pHAt(nMixed)
        val l1 = tr("V(NaOH) = %.2f mL · pH %.2f · 溶液%s（%s %s 变色）· %s", "V(NaOH) = %.2f mL · pH %.2f · %s (%s turns %s) · %s")
            .format((nOut + tipFill) * DROP, pHm, colourName(indicator, pH), indName(), rangeText(), cockText())
        readoutText = l1 + "\n" + verdictLine(pH) + "\n" + chemistryLine(pH)
    }

    private fun verdictLine(pH: Double): String {
        val v = vNow(); val veq = tit.veqMl
        val reached = pH >= pe
        val err = v - veq
        if (nOut >= MAX_DROPS) return tr("滴定管已放空（50.00 mL）—— 捏烧瓶重新装液", "The burette is empty (50.00 mL): pinch the flask to refill")
        if (reached && holdT >= HOLD_S) {
            val drops = (err / DROP).roundToInt()
            val tail = when {
                abs(indicatorError()) > 1.0 -> tr(" —— %s在 %s 的缓冲区就变色，不适合！", " — %s turns inside %s's buffer region: unsuitable!").format(indName(), acidName())
                abs(err) <= DROP + 1e-9 -> tr(" —— 在 1 滴之内，好！", " — within one drop, well done!")
                err > 0 -> tr(" —— 多加了 %d 滴", " — %d drops too many").format(drops)
                else -> tr(" —— 早了 %d 滴", " — %d drops early").format(-drops)
            }
            return tr("颜色保持 30 s 不褪 → 终点 %.2f mL，误差 %+.2f mL（%+.1f%%）", "Colour held 30 s → endpoint %.2f mL, error %+.2f mL (%+.1f%%)").format(v, err, 100 * err / veq) + tail
        }
        if (reached) {
            return if (flow > 0f || pending > 0) tr("已变%s！快关活塞 —— 现在每滴都在过量（%+.2f mL）", "It turned %s! Shut the stopcock: every drop now overshoots (%+.2f mL)").format(turnWord(), nOut * DROP - veq)
            else tr("%s保持 %.0f s / 30 s 不褪 …… 判断终点（%.2f mL，误差 %+.2f mL）", "%s held %.0f s of 30 s … judging the endpoint (%.2f mL, error %+.2f mL)").format(colourName(indicator, pH), holdT, v, err)
        }
        val flash = if (lastFlash > 0f) tr("，最近一滴的%s %.1f s 就褪了", "; the last drop's %s faded in %.1f s").format(turnWord(), lastFlash) else ""
        return if (err < 0) tr("距等当点 %.2f mL（%.3f mmol 酸未中和）%s —— 未到终点", "%.2f mL to equivalence (%.3f mmol acid left)%s: not the endpoint yet")
            .format(-err, -err * tit.cb, flash)
        else tr("已过等当点 %.2f mL，但%s还没变色%s", "%.2f mL past equivalence, yet %s has not turned%s").format(err, indName(), flash)
    }

    private fun chemistryLine(pH: Double): String {
        if (acid.strong) return tr("H⁺ + OH⁻ → H₂O（Na⁺、Cl⁻ 是旁观离子）· n(HCl) = 25.00 mL × 0.100 mol/L = 2.500 mmol → 等当点 25.00 mL，pH 7.00",
            "H⁺ + OH⁻ → H₂O (Na⁺, Cl⁻ are spectators) · n(HCl) = 25.00 mL × 0.100 mol/L = 2.500 mmol → equivalence 25.00 mL, pH 7.00")
        val v = vNow()
        return when {
            v <= 0.0 -> tr("CH₃COOH 是弱酸：Ka = 1.75×10⁻⁵，0.100 mol/L 只电离 1.3%%，所以起点 pH 2.88 而不是 1.00",
                "CH₃COOH is a weak acid: Ka = 1.75×10⁻⁵, only 1.3%% ionised at 0.100 mol/L, so it starts at pH 2.88, not 1.00")
            v < tit.veqMl - 1e-9 -> {
                val ratio = com.bytedance.chemistrylab.core.titration.AcidBase.KA_ACETIC / tit.h(v)
                tr("CH₃COOH + OH⁻ → CH₃COO⁻ + H₂O · 缓冲：pH = pKa + lg(c(CH₃COO⁻)/c(CH₃COOH)) = 4.76 + lg %.2f · 半中和点 pH = pKa",
                    "CH₃COOH + OH⁻ → CH₃COO⁻ + H₂O · buffer: pH = pKa + lg([CH₃COO⁻]/[CH₃COOH]) = 4.76 + lg %.2f · half-way pH = pKa").format(ratio)
            }
            else -> tr("等当点是 CH₃COONa 溶液：CH₃COO⁻ + H₂O ⇌ CH₃COOH + OH⁻，pH 8.73 > 7 —— 所以要选碱性区变色的酚酞",
                "At equivalence it is CH₃COONa: CH₃COO⁻ + H₂O ⇌ CH₃COOH + OH⁻, pH 8.73 > 7, so pick phenolphthalein (turns on the basic side)")
        }
    }

    private fun updateLabels() {
        labels.clear()
        labels += LabelSpec(tr("NaOH 0.100 mol/L · 读数 %.2f mL", "NaOH 0.100 mol/L · reads %.2f mL").format((nOut + tipFill) * DROP), P3(BX + 0.085f, 0.445f, BZ))
        labels += LabelSpec(tr("%s 25.00 mL + %s · %s", "%s 25.00 mL + %s · %s").format(acidName(), indName(), colourName(indicator, pHAt(nMixed))), P3(FX + 0.165f, -0.030f, FZ + 0.06f))
        labels += LabelSpec(tr("分子窗口 · H₃O⁺ %d · OH⁻ %d（对数刻度：多 2 个 = 浓度 ×10）", "Molecules · H₃O⁺ %d · OH⁻ %d (log scale: 2 more = 10× the concentration)")
            .format(ions.shownFree(IonWindow.H3O), ions.shownFree(IonWindow.OH)), P3(WC.x, WC.y + WH / 2 + 0.032f, WC.z + WD / 2), visible = showMicro)
        val eqPH = table(acid)[500]
        labels += LabelSpec(tr("滴定曲线 pH–V(NaOH) · 等当点 %.2f mL，pH %.2f", "Titration curve pH–V(NaOH) · equivalence %.2f mL, pH %.2f").format(tit.veqMl, eqPH),
            P3((CX0 + CX1) / 2, CY1 + 0.075f, CZ))
        val (lo, hi) = IndicatorOptics.transitionRange(indicator)
        val bandTxt = tr("%s变色区 %.1f–%.1f", "%s range %.1f–%.1f").format(indName(), lo, hi)
        labels += LabelSpec(if (acid.strong) bandTxt else tr("半中和点 pH = pKa = %.2f\n%s", "Half-way pH = pKa = %.2f\n%s").format(table(acid)[250], bandTxt),
            P3(CX1 - 0.075f, CY0 + hi.toFloat() * SYP + 0.035f, CZ + 0.01f))
        labels += LabelSpec(tr("酸 %s · 指示剂 %s（捏瓶子更换）", "Acid %s · indicator %s (pinch a bottle)").format(acidName(), indName()), P3((IND_X[0] + ACID_X[1]) / 2, DECK + 0.20f, 0.23f))
    }

    // ================================================================ interaction

    override val tapTargets: List<TapTarget> get() = listOf(
        TapTarget("Stopcock", "活塞 → 滴 1 滴 / 关", "Stopcock → 1 drop / shut") { flow > 0f },
        TapTarget("AcidHCl", "盐酸 HCl → 装入", "HCl → fill") { acid == Acid.HCL },
        TapTarget("AcidAc", "醋酸 CH₃COOH → 装入", "CH₃COOH → fill") { acid == Acid.ACETIC },
        TapTarget("Ind0", "酚酞 → 换用", "Phenolphthalein → use") { indicator == Indicator.PHENOLPHTHALEIN },
        TapTarget("Ind1", "甲基橙 → 换用", "Methyl orange → use") { indicator == Indicator.METHYL_ORANGE },
        TapTarget("Ind2", "溴百里酚蓝 → 换用", "Bromothymol blue → use") { indicator == Indicator.BROMOTHYMOL_BLUE },
        TapTarget("Flask", "烧瓶 → 重新装液", "Flask → refill"),
    )

    private val TARGETS = setOf("Stopcock", "Flask", "AcidHCl", "AcidAc", "Ind0", "Ind1", "Ind2")
    override fun isTarget(name: String) = name in TARGETS
    override fun isDragTarget(name: String) = name == "Stopcock"

    override fun onTap(name: String) {
        lastTap = when (name) {
            "Stopcock" -> if (flow > 0f || pending > 0) { flow = 0f; pending = 0; tr("关闭活塞：停在 %.2f mL", "Stopcock shut at %.2f mL").format(nOut * DROP) }
                          else dropMessage().also { addDrops(1) }
            "Flask" -> { refill(); tr("重新装液：25.00 mL %s + 2 滴%s，滴定管加满到 0.00 mL", "Refilled: 25.00 mL %s + 2 drops of %s, burette at 0.00 mL").format(acidName(), indName()) }
            "AcidHCl", "AcidAc" -> {
                setup(if (name == "AcidHCl") Acid.HCL else Acid.ACETIC, indicator, 0)
                if (acid.strong) tr("换成盐酸 HCl（强酸，完全电离）：25.00 mL，起点 pH 1.00", "HCl (strong, fully ionised): 25.00 mL, starts at pH 1.00")
                else tr("换成醋酸（弱酸，Ka = 1.75×10⁻⁵）：起点 pH 2.88 而不是 1.00", "Acetic acid (weak, Ka = 1.75×10⁻⁵): starts at pH 2.88, not 1.00")
            }
            else -> {
                val k = name.removePrefix("Ind").toIntOrNull() ?: return
                val ind = Indicator.values().getOrNull(k) ?: return
                setIndicator(ind)
                val e = indicatorError()
                val fit = when { abs(e) < 0.1 -> tr(" —— 合适", " — suitable"); abs(e) < 1.0 -> tr(" —— 早几滴", " — a few drops early"); else -> tr(" —— 不合适！", " — unsuitable!") }
                tr("换成%s（%s 变色）：滴 %s 时 %.2f mL 变色，误差 %+.2f mL%s", "%s (turns at %s): with %s it turns at %.2f mL, error %+.2f mL%s")
                    .format(indName(), rangeText(), acidName(), tit.veqMl + e, e, fit)
            }
        }
    }

    // ================================================================ guided track (导学)
    // KEY INSIGHT: pH creeps up while acid is left, then JUMPS at the equivalence point — the drop before
    // 25.00 mL gives pH 4.0, the drop after gives pH 10.0 — and the indicator only reads that jump as a colour.
    // A weak acid adds a flat buffer region (pH = pKa = 4.76 half-way) and ends above 7 (pH 8.73).

    /** Lesson bookkeeping: flasks set up so far (refill / acid bottle) and the baseline a step compares against. */
    private var setups = 0
    private var mark = 0

    /** The first drop (burette reading / 0.05 mL) at which the current indicator turns in the current acid. */
    internal fun turnDrop(): Int = table(acid).indexOfFirst { it >= pe }.let { if (it < 0) MAX_DROPS else it }

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("先捏烧瓶（或「烧瓶 → 重新装液」）从头开始：25.00 mL 盐酸 + 酚酞，pH 1.00。", "First pinch the flask (or “Flask → refill”) to start over: 25.00 mL of HCl + phenolphthalein, pH 1.00."),
                    target = "Flask",
                    onEnter = { mark = setups },
                    done = { setups > mark && acid == Acid.HCL },
                    autoplay = { onTap(if (acid == Acid.HCL) "Flask" else "AcidHCl") },
                ),
                LessonStep(
                    say = Bi("连按「+1 mL」（或向下拖活塞放快）加到 23–24.5 mL，关上活塞，看 pH 计。", "Tap “+1 mL” again and again (or drag the stopcock down) to 23–24.5 mL, shut it, and read the pH meter."),
                    target = "Stopcock",
                    done = { nOut in 460..490 && flow == 0f && pending == 0 },
                    autoplay = { addDrops((480 - nOut).coerceAtLeast(1)) },
                    insight = Bi("碱已加了九成多，pH 才从 1 升到 3 左右：只要还剩一点酸，pH 就涨得很慢。", "Over 90 % of the base is in, yet pH has only crept from 1 to about 3: while any acid is left, pH rises slowly."),
                ),
                LessonStep(
                    say = Bi("离等当点 25.00 mL 很近了。就在 25.00 mL 前后再加 1 滴（0.05 mL），pH 会变多少？", "Equivalence is at 25.00 mL. Right around it, how much will ONE more drop (0.05 mL) change the pH?"),
                    options = listOf(
                        Bi("几乎不变", "Barely at all"),
                        Bi("约 0.3", "About 0.3"),
                        Bi("好几个单位", "Several units"),
                    ),
                    answer = 2,
                ),
                LessonStep(
                    say = Bi("现在一滴一滴加（按「+1 滴」或捏活塞），直到整瓶变粉红。", "Now add drop by drop (tap “+1 drop” or pinch the stopcock) until the whole flask turns pink."),
                    target = "Stopcock",
                    done = { pHAt(nMixed) >= pe },
                    autoplay = { addDrops((turnDrop() - nOut).coerceAtLeast(1)) },
                    reveal = true,
                    insight = Bi("等当点前后各 1 滴，pH 从 4.0 跳到 10.0！酸一用完，一点 OH⁻ 就过量；酚酞只是把这一跳变成颜色。", "One drop either side of equivalence: pH 4.0 → 10.0! Once the acid is gone, a trace of OH⁻ is excess; the dye just shows the jump."),
                ),
                LessonStep(
                    say = Bi("捏醋酸瓶（或「醋酸 CH₃COOH → 装入」）换成弱酸，加到约 12.5 mL，看曲线。", "Pinch the acetic acid bottle (or “CH₃COOH → fill”) for a weak acid, add about 12.5 mL, and watch the curve."),
                    target = "AcidAc",
                    onEnter = { mark = setups },
                    done = { setups > mark && acid == Acid.ACETIC && nMixed >= 240 },
                    autoplay = { if (acid != Acid.ACETIC || setups == mark) onTap("AcidAc"); addDrops((250 - nOut).coerceAtLeast(1)) },
                    insight = Bi("弱酸先有一段平缓的缓冲区：加到一半（12.5 mL）时 pH = pKa = 4.76；等当点 pH 8.73，高于 7。", "A weak acid has a flat buffer region: half-way (12.5 mL) pH = pKa = 4.76; at equivalence pH is 8.73, above 7."),
                ),
            ),
            challenge = Challenge(
                say = Bi("滴到变色就关上活塞，误差在 ±0.05 mL（1 滴）以内。先快后慢！", "Stop at the colour change with the stopcock shut, within ±0.05 mL (one drop). Fast first, then slow!"),
                progress = {
                    val err = nMixed * DROP - tit.veqMl
                    if (holdT > 0f) tr("已变色、活塞已关：误差 %+.2f mL", "Turned, stopcock shut: error %+.2f mL").format(err)
                    else if (nOut * DROP > tit.veqMl + DROP + 1e-9) tr("V = %.2f mL · 已过等当点 %.2f mL —— 捏烧瓶重来", "V = %.2f mL · %.2f mL past equivalence: pinch the flask to restart").format(nOut * DROP, nOut * DROP - tit.veqMl)
                    else tr("V = %.2f mL · 距等当点 %.2f mL", "V = %.2f mL · %.2f mL to equivalence").format(nOut * DROP, maxOf(0.0, tit.veqMl - nOut * DROP))
                },
                done = { holdT > 0.5f && abs(nMixed * DROP - tit.veqMl) <= DROP + 1e-9 },
                // a careful student: a suitable dye, start over if past the end, run to the turning drop, stop
                autoplay = {
                    if (flow > 0f || pending > 0) onTap("Stopcock")
                    if (abs(indicatorError()) > 0.1) onTap("Ind0")
                    if (nOut > turnDrop()) onTap("Flask")
                    if (turnDrop() > nOut) addDrops(turnDrop() - nOut)
                },
            ),
        )
    }

    /** What the next single drop means (said before it lands). */
    private fun dropMessage(): String {
        val rem = tit.veqMl - (nOut + pending) * DROP
        val ie = indicatorError()
        val pH = pHAt(nMixed)
        return when {
            nOut >= MAX_DROPS -> tr("滴定管已空：捏烧瓶重新装液", "Burette empty: pinch the flask to refill")
            pH >= pe -> tr("颜色已变（%s）：再加就是过量，已过等当点 %+.2f mL", "Already %s: more is overshoot, %+.2f mL past equivalence").format(colourName(indicator, pH), -rem)
            rem + ie > 3.0 -> tr("滴入 1 滴（0.05 mL）：%s一闪就褪 —— 离终点还远，可以拖动活塞放快", "1 drop (0.05 mL): a %s flash that fades at once — far from the end, drag the stopcock to run faster").format(turnWord())
            rem + ie > 0.5 -> tr("1 滴：%s褪得越来越慢 —— 还差约 %.2f mL，改成一滴一滴加", "1 drop: the %s fades more and more slowly — about %.2f mL to go, add drop by drop").format(turnWord(), rem + ie)
            else -> tr("接近终点：每滴都可能让%s变%s —— 慢慢加", "Near the endpoint: any drop may turn %s %s — go slowly").format(indName(), turnWord())
        }
    }

    override fun tapMessage(name: String): String? = lastTap.ifEmpty { null }

    override fun onDragStart(name: String) { pending = 0; dragD = DRAG_FULL * sqrt((flow / RMAX).coerceIn(0f, 1f)) }
    /** Down (or right) opens: rate = RMAX · (travel / 15 cm)², so the last centimetres are a fine drip. */
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        dragD = (dragD - dy + dx).coerceIn(0f, DRAG_FULL)
        val u = dragD / DRAG_FULL
        flow = RMAX * u * u
        if (flow > 0f) holdT = 0f
    }
    override fun onDragEnd(name: String) { if (flow < 0.004f) flow = 0f }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "endpoint" to { setup(Acid.HCL, Indicator.PHENOLPHTHALEIN, 501); repeat(30) { update(1f / 60f) }; holdT = HOLD_S + 0.5f },
        "weak_pp" to { setup(Acid.ACETIC, Indicator.PHENOLPHTHALEIN, 500); repeat(30) { update(1f / 60f) }; holdT = HOLD_S + 0.5f },
        "weak_mo" to { setup(Acid.ACETIC, Indicator.METHYL_ORANGE, 160); repeat(30) { update(1f / 60f) } },
        "btb_flow" to { setup(Acid.HCL, Indicator.BROMOTHYMOL_BLUE, 300); flow = 0.6f; repeat(40) { update(1f / 60f) } },
        "mo_verdict" to { setup(Acid.ACETIC, Indicator.METHYL_ORANGE, 41); repeat(30) { update(1f / 60f) }; holdT = HOLD_S + 0.5f },
        "overshoot" to { setup(Acid.HCL, Indicator.PHENOLPHTHALEIN, 502); flow = 0.25f; repeat(30) { update(1f / 60f) } },
    )

    @Composable
    override fun Panel() {
        var si by FieldRef(::speedIdx)
        var mv by FieldRef(::showMicro)
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = si == k, onClick = { si = k }, title = { Text(l) }) }
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { addDrops(1) }) { Text(tr("+1 滴", "+1 drop")) }
                Button(onClick = { addDrops(20) }) { Text("+1 mL") }
                LabSwitch(tr("分子视图", "Molecule view"), mv) { mv = it }
                Button(onClick = { resetAll() }) { Text(tr("复位", "Reset")) }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Catmull-Rom through [pts], [n] samples. */
    private fun spline(pts: List<P3>, n: Int): FloatArray {
        val out = FloatArray(n * 3)
        val segsN = pts.size - 1
        for (i in 0 until n) {
            val u = i.toFloat() / (n - 1) * segsN
            val k = u.toInt().coerceAtMost(segsN - 1); val f = u - k
            val p0 = pts[maxOf(0, k - 1)]; val p1 = pts[k]; val p2 = pts[k + 1]; val p3 = pts[minOf(segsN, k + 2)]
            fun cr(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (-a + 3 * b - 3 * c + d) * f * f * f)
            out[i * 3] = cr(p0.x, p1.x, p2.x, p3.x); out[i * 3 + 1] = cr(p0.y, p1.y, p2.y, p3.y); out[i * 3 + 2] = cr(p0.z, p1.z, p2.z, p3.z)
        }
        return out
    }
}
