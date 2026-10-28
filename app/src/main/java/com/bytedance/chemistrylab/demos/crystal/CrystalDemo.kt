// 晶体结构 (Bragg, 1913) — walk into a crystal: why salt cleaves, diamond is hard, graphite writes, copper bends.
//
// LESSON (macro + micro side by side): on the left a display tray of real specimens — a stepped halite (rock-salt)
// cube with a cleaving chisel above it, a vial of caesium chloride, a polished copper block with a coil of wire,
// a brilliant-cut diamond that sparkles, a graphite lump and a pencil on a paper card. Pinch a specimen and its
// lattice grows out of it ("magnified 260 million times") onto the stand: a 0.6-0.8 m model built from the REAL
// lattice constants, CPK colours, true ionic radii (Shannon 1976) for the space-filling view.
// The user DOES crystallography with their hands:
//   * pinch an atom (the haloed Na⁺ / Cl⁻, C, Cu ...) -> its coordination shell lights up, with the polyhedron
//     (octahedron 6, cube 8, tetrahedron 4, triangle 3, cuboctahedron 12) and the count;
//   * unit cell -> the cell's atoms become cut-aways (corner 1/8, edge 1/4, face 1/2): count Z with your eyes;
//   * pinch the chisel -> the salt crystal splits on the tray AND the lattice turns its {100} plane edge-on, the
//     right half slips by one ion spacing (like ions face to face glow red: repulsion) and flies off: cleavage;
//   * graphite: pinch-drag the top layer sideways -> it slides over the next one (weak interlayer forces) and the
//     pencil on the tray draws a line as you do it;
//   * pinch-drag an atom or the trackball to turn the lattice; "walk in" blows it up to fill the room-sized volume.
//
// Science: core/crystal (lattice generators, nearest-neighbour shells, Z by counting and by sharing, density
// rho = Z M / (N_A V), packing fractions, coordination polyhedra) — all computed, JVM-tested.
// Per frame: only the lattice root / moving part / specimen transforms change. Atoms are two pools of nodes sharing
// one sphere mesh (LOD by count); bonds are ONE merged mesh per lattice part, built when the crystal or the
// supercell changes; highlights only swap materials / meshes once per toggle.

package com.bytedance.chemistrylab.demos.crystal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.core.crystal.Bonding
import com.bytedance.chemistrylab.core.crystal.CellMember
import com.bytedance.chemistrylab.core.crystal.Crystal
import com.bytedance.chemistrylab.core.crystal.Crystals
import com.bytedance.chemistrylab.core.crystal.Cut
import com.bytedance.chemistrylab.core.crystal.Model
import com.bytedance.chemistrylab.core.crystal.Polyhedron
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
import com.bytedance.chemistrylab.lab.ui.LabPanel
import com.bytedance.chemistrylab.lab.ui.LabRow
import com.bytedance.chemistrylab.lab.ui.LabSwitch
import com.bytedance.chemistrylab.lab.ui.SegmentControl
import com.bytedance.chemistrylab.lab.ui.SegmentItem
import com.bytedance.chemistrylab.render.BuoyMesh
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object CrystalDemo : SpecDemo() {
    override val id = "crystal"
    override val title = "晶体结构"  // i18n-ok (zh half of DemoRegistry.titleOf; English in TITLE_EN)
    override val credit = "Bragg · 1913"

    // ---------------------------------------------------------------- layout (metres, volume space)
    private const val PLINTH_Y = -0.262f
    private const val TOP = PLINTH_Y + 0.0175f          // bench top
    private const val LX = 0.30f                        // lattice stand
    private const val LZ = -0.06f
    private const val TABLE_Y = -0.226f
    private const val BAND_TOP = 0.415f; private const val BAND_BOT = -0.180f; private const val R_MAX = 0.42f
    private const val W_TOP = 0.53f; private const val W_BOT = -0.47f; private const val W_R = 0.88f   // walk-in: the whole volume
    private const val R_NOM = 0.36f                     // model bounding radius before fitting (model metres)
    /** Ball-and-stick ball = this x the true radius (carbon's contact radius is small: drawn a bit bigger). */
    private fun ballK(cr: Crystal) = when (cr.id) { "graphite" -> 0.62f; "diamond" -> 0.55f; else -> 0.45f }
    private val SPEC_H = floatArrayOf(0.085f, 0.118f, 0.075f, 0.075f, 0.095f)   // specimen heights (beam start, label)
    private const val POOL_A = 343
    private const val POOL_B = 160
    private const val MAX_SHELL = 12
    private const val MAX_EDGES = 24
    private const val MAX_GLOW = 49
    private const val DRAG_GAIN = 6f
    private val TB = P3(0.71f, -0.200f, 0.26f)          // trackball
    private val TB_WALK = P3(-0.93f, -0.24f, 0.40f)
    private val SPIN = floatArrayOf(0f, 0.12f, 0.30f, 0.65f)
    private val SPEED_LABELS get() = listOf(tr("停转", "Stop"), tr("慢", "Slow"), tr("中", "Medium"), tr("快", "Fast"))

    /** Specimen spot per crystal (Crystals.ALL order: NaCl, CsCl, diamond, graphite, Cu): x, z, pedestal top. */
    private const val RISER_H = 0.050f
    private const val SPOT_TOP = TOP + RISER_H + 0.010f   // riser + pedestal
    private const val SPOT_Z = 0.0f
    private val SPOT = arrayOf(floatArrayOf(-0.79f, SPOT_Z, SPOT_TOP), floatArrayOf(-0.645f, SPOT_Z, SPOT_TOP),
        floatArrayOf(-0.50f, SPOT_Z, SPOT_TOP), floatArrayOf(-0.355f, SPOT_Z, SPOT_TOP), floatArrayOf(-0.21f, SPOT_Z, SPOT_TOP))
    private val SPEC_ID = arrayOf("SpecNaCl", "SpecCsCl", "SpecDiamond", "SpecGraphite", "SpecCu")
    private const val SALT = 0.080f                     // salt crystal edge
    private val CLIP = P3(-0.43f, TOP + 0.003f, 0.215f)  // clipboard (pencil writing station), leaning back
    private const val CLIP_LEAN = 0.96f                 // rad from vertical
    private const val STROKE_MAX = 0.120f
    private val STROKE_Y = floatArrayOf(0.074f, 0.052f, 0.030f)

    // ---------------------------------------------------------------- UI state (snapshot: taps update the panel)
    var crystalIdx by mutableIntStateOf(0)
    var supercell by mutableIntStateOf(2)
    var spaceFill by mutableStateOf(false)
    var cellOn by mutableStateOf(false)
    var coordOn by mutableStateOf(false)
    var probeK by mutableIntStateOf(0)
    var walkIn by mutableStateOf(false)
    var speedIdx by mutableIntStateOf(1)

    // ---------------------------------------------------------------- state
    private val models = HashMap<Int, Model>()
    private class Geo(val k: Float, val bondR: Float, val a: MeshData?, val b: MeshData?, val x: MeshData?, val cell: MeshData?)
    private val geos = HashMap<Int, Geo>()
    private val hulls = HashMap<Int, Polyhedron>()
    private var shownKey = -1
    private var geoKey = -1
    private lateinit var model: Model
    private lateinit var geo: Geo
    private var nodeOf: Array<Node?> = emptyArray()
    private var radiusOf = FloatArray(0)                 // displayed radius per model atom (model metres)
    private var userRot = Q.identity()
    private var dragging = false
    private var sCur = 1f; private var sTarget = 1f
    private val cCur = floatArrayOf(LX, 0.1f, LZ); private val cTarget = floatArrayOf(LX, 0.1f, LZ)
    private var topExt = 0.3f                             // model top above its centre (model metres, current pose)
    private var loadT = 1f
    private var loadFrom = P3(0f, 0f, 0f)
    private var tapScale = -1f
    // cleave (NaCl)
    private var cleaveState = 0                           // 0 intact, 1 opening, 2 open, 3 closing
    private var cleaveT = 0f
    private var cleavePending = false
    private var alignYaw = 0f; private var alignApplied = 0f
    private var slip = 0f; private var sep = 0f           // part-B offsets (model metres): slip along local y, separation along x
    private var pairs: List<Pair<Int, Int>> = emptyList()
    // graphite slide
    private var slideX = 0f; private var slideZ = 0f      // part-B offset in the layer plane (model metres)
    private var slideAnimLeft = 0f; private var slideVX = 0f; private var slideVZ = 0f
    private var slideTotal = 0f                           // pm slid (for the text)
    private var strokeLen = 0f                            // pencil line drawn on the card (m)
    private var sliding = false
    // specimens
    private var clock = 0f
    private var frame = 0
    private var textEn = false
    private var lastMsg = ""

    // ---------------------------------------------------------------- nodes
    private lateinit var bench: Node
    private lateinit var latt: Node
    private lateinit var partB: Node
    private lateinit var poolA: Array<Node>
    private lateinit var poolB: Array<Node>
    private lateinit var probes: Array<Node>
    private lateinit var bondsA: Node
    private lateinit var bondsB: Node
    private lateinit var bondsX: Node
    private lateinit var bondsCell: Node
    private lateinit var cellEdges: Array<Node>
    private lateinit var shellNodes: Array<Node>
    private lateinit var spokes: Array<Node>
    private lateinit var polyEdges: Array<Node>
    private lateinit var hullNode: Node
    private lateinit var hullMd: MeshData
    private lateinit var centreGlow: Node
    private lateinit var glows: Array<Node>
    private lateinit var grip: Node
    private lateinit var trackball: Node
    private lateinit var beams: Array<Node>
    private lateinit var rims: Array<Node>
    private lateinit var specNodes: Array<Node>
    private lateinit var saltL: Node
    private lateinit var saltR: Node
    private lateinit var chiselRig: Node
    private lateinit var diamondRig: Node
    private lateinit var sparkles: Array<Node>
    private lateinit var pencil: Node
    private lateinit var strokes: Array<Node>
    private lateinit var sphHi: MeshData
    private lateinit var sphMid: MeshData
    private lateinit var sphLo: MeshData
    private lateinit var cutMesh: Map<Cut, MeshData>
    private lateinit var unitCyl: MeshData

    // ---------------------------------------------------------------- materials (sRGB)
    private val alu = Mat.Pbr(Rgb(0.80f, 0.82f, 0.85f), metallic = 1f, roughness = 0.30f)
    private val darkSteel = Mat.Pbr(Rgb(0.30f, 0.31f, 0.33f), metallic = 1f, roughness = 0.40f)
    private val steel = Mat.Pbr(Rgb(0.78f, 0.80f, 0.83f), metallic = 1f, roughness = 0.18f)
    private val slabTex: Image by lazy {
        ProcTex.bakePlanet(256, 128, Rgb(0.07f, 0.075f, 0.085f), Rgb(0.12f, 0.125f, 0.14f), Rgb(0.20f, 0.205f, 0.22f), 0f, 77)
    }
    private val slab get() = Mat.Pbr(Rgb(1f, 1f, 1f), metallic = 0f, roughness = 0.55f, tex = slabTex)
    private val velvet = Mat.Pbr(Rgb(0.06f, 0.06f, 0.08f), metallic = 0f, roughness = 0.92f)
    private val smoked = Mat.Pbr(Rgb(0.06f, 0.07f, 0.09f), metallic = 0f, roughness = 0.08f, alpha = 0.85f)
    private val rimOn = Mat.Unlit(Rgb(0.40f, 0.85f, 1.00f), alpha = 0.95f, blend = Blend.ADD)
    private val rimOff = Mat.Unlit(Rgb(0.30f, 0.36f, 0.44f), alpha = 0.55f, blend = Blend.ADD)
    private val beamMat = Mat.Unlit(Rgb(0.40f, 0.78f, 1.00f), alpha = 0.16f, blend = Blend.ADD)
    private val cellEdgeOn = Mat.Unlit(Rgb(0.35f, 0.88f, 1.00f), alpha = 1f, blend = Blend.ADD)
    private val cellEdgeOff = Mat.Unlit(Rgb(0.30f, 0.70f, 0.95f), alpha = 0.40f, blend = Blend.ADD)
    private val polyEdgeMat = Mat.Unlit(Rgb(1.00f, 0.80f, 0.32f), alpha = 1f, blend = Blend.ADD)
    private val spokeMat = Mat.Unlit(Rgb(1.00f, 0.93f, 0.62f), alpha = 0.85f, blend = Blend.ADD)
    private val gripMat = Mat.Unlit(Rgb(0.45f, 0.85f, 1.00f), alpha = 0.9f, blend = Blend.ADD)
    private val bondMat = Mat.Pbr(Rgb(0.78f, 0.80f, 0.84f), metallic = 0.2f, roughness = 0.32f)
    private val bondDim = Mat.Pbr(Rgb(0.20f, 0.21f, 0.23f), metallic = 0.1f, roughness = 0.55f)
    private val bondCellMat = Mat.Pbr(Rgb(0.88f, 0.92f, 0.98f), metallic = 0.1f, roughness = 0.25f, emissive = Rgb(0.10f, 0.16f, 0.22f))
    private val blobTex: Image by lazy { CrystalMesh.blob() }
    private val starTex: Image by lazy { CrystalMesh.star() }
    private val glowMat get() = Mat.Unlit(Rgb(1.00f, 0.35f, 0.22f), alpha = 0.95f, tex = blobTex, blend = Blend.ADD)
    private val centreGlowMat get() = Mat.Unlit(Rgb(1.00f, 0.88f, 0.55f), alpha = 0.75f, tex = blobTex, blend = Blend.ADD)
    private val sparkMat get() = Mat.Unlit(Rgb(0.95f, 0.97f, 1.00f), alpha = 1f, tex = starTex, blend = Blend.ADD)

    /** CPK-ish element colours (sRGB): Na violet, Cl green, Cs purple, C dark grey, Cu copper. */
    private fun elColor(sym: String) = when (sym) {
        "Na" -> Rgb(0.66f, 0.42f, 0.98f); "Cl" -> Rgb(0.32f, 0.90f, 0.32f); "Cs" -> Rgb(0.50f, 0.26f, 0.76f)
        "C" -> Rgb(0.44f, 0.45f, 0.50f); "Cu" -> Rgb(0.95f, 0.60f, 0.42f); else -> Rgb(0.8f, 0.8f, 0.8f)
    }
    private fun dim(c: Rgb, k: Float) = Rgb(c.r * k, c.g * k, c.b * k)
    private val atomMats = HashMap<String, Mat>()
    private fun atomMat(sym: String, kind: Int): Mat = atomMats.getOrPut("$sym$kind") {
        val c = elColor(sym); val metal = sym == "Cu"
        when (kind) {
            0 -> Mat.Pbr(c, metallic = if (metal) 0.85f else 0f, roughness = if (metal) 0.25f else 0.28f, emissive = dim(c, 0.10f))
            1 -> Mat.Pbr(Rgb(c.r * 0.34f + 0.05f, c.g * 0.34f + 0.05f, c.b * 0.34f + 0.06f), metallic = 0f, roughness = 0.60f)  // dimmed context
            else -> Mat.Pbr(c, metallic = if (metal) 0.6f else 0f, roughness = 0.22f, emissive = dim(c, 0.42f)) // lit shell
        }
    }

    // ================================================================ build

    override fun buildSpec(cacheDir: File): Node {
        // every cached field starts fresh (rebuild idempotence)
        if (crystalIdx != 0) crystalIdx = 0
        if (supercell != 2) supercell = 2
        if (spaceFill) spaceFill = false
        if (cellOn) cellOn = false
        if (coordOn) coordOn = false
        if (probeK != 0) probeK = 0
        if (walkIn) walkIn = false
        if (speedIdx != 1) speedIdx = 1
        shownKey = -1; geoKey = -1; tapScale = -1f
        userRot = V0; dragging = false; loadT = 1f
        cleaveState = 0; cleaveT = 0f; cleavePending = false; alignYaw = 0f; alignApplied = 0f; slip = 0f; sep = 0f
        slideX = 0f; slideZ = 0f; slideAnimLeft = 0f; slideTotal = 0f; strokeLen = 0f; sliding = false
        clock = 0f; frame = 0; textEn = Lang.en; lastMsg = ""
        atomMats.clear(); geos.clear()

        val root = Node("CrystalRoot")
        unitCyl = MeshGen.cylinder(1f, 1f, 12)
        val unitBox = MeshGen.box(1f, 1f, 1f)
        val disc = MeshGen.cylinder(1f, 1f, 40)
        val quad = CrystalMesh.quad()
        sphHi = MeshGen.sphere(1f, 20, 40, MeshGen.SphereUv.EQUIRECT)
        sphMid = MeshGen.sphere(1f, 14, 28, MeshGen.SphereUv.EQUIRECT)
        sphLo = MeshGen.sphere(1f, 10, 20, MeshGen.SphereUv.EQUIRECT)
        cutMesh = Cut.values().associateWith { CrystalMesh.cutSphere(it) }

        // ================= bench, stand and specimen tray (hidden when walking into the lattice)
        bench = root.add(Node("Bench"))
        bench.add("Plinth", MeshGen.box(1.72f, 0.035f, 0.66f), slab, P3(0f, PLINTH_Y, -0.02f))
        bench.add("PlinthBand", MeshGen.box(1.73f, 0.008f, 0.67f), alu, P3(0f, PLINTH_Y, -0.02f))
        bench.add("StandFoot", unitCyl, darkSteel, P3(LX, (TOP + TABLE_Y) / 2, LZ)).also { it.scale = P3(0.10f, TABLE_Y - TOP, 0.10f) }
        bench.add("Turntable", disc, smoked, P3(LX, TABLE_Y, LZ)).also { it.scale = P3(0.21f, 0.008f, 0.21f) }
        bench.add("TableRim", MeshGen.tube(CrystalMesh.ring(0.21f, 96), 0.0032f, 6, true), Mat.Unlit(Rgb(0.40f, 0.72f, 1.00f), alpha = 0.75f, blend = Blend.ADD),
            P3(LX, TABLE_Y + 0.005f, LZ))
        // display riser: one velvet step carrying the five specimens on smoked-glass pedestals
        bench.add("Riser", unitBox, velvet, P3(-0.505f, TOP + RISER_H / 2, SPOT_Z)).also { it.scale = P3(0.71f, RISER_H, 0.17f) }
        bench.add("RiserTrim", unitBox, alu, P3(-0.505f, TOP + RISER_H, SPOT_Z + 0.0855f)).also { it.scale = P3(0.71f, 0.004f, 0.003f) }
        val rimMesh = MeshGen.tube(CrystalMesh.ring(0.060f, 72), 0.0028f, 6, true)
        rims = Array(5) { ci ->
            val s = SPOT[ci]
            bench.add("Pedestal$ci", disc, smoked, P3(s[0], s[2] - 0.005f, s[1])).also { it.scale = P3(0.060f, 0.010f, 0.060f) }
            bench.add("PedRim$ci", rimMesh, rimOff, P3(s[0], s[2], s[1]))
        }
        specNodes = Array(5) { Node("_") }
        buildSalt(unitCyl, unitBox)
        buildCsCl(disc)
        buildDiamond(quad)
        buildGraphite(unitCyl, unitBox)
        buildCopper(unitBox)
        // the magnifier: faint beams from the chosen specimen up to the lattice
        beams = Array(4) { root.add("Beam$it", unitCyl, beamMat) }

        // trackball (front right of the stand): pinch-drag to turn the lattice, tap = reset the view
        trackball = root.add("Trackball", sphMid, Mat.Pbr(Rgb(0.30f, 0.55f, 1.00f), metallic = 0.2f, roughness = 0.18f, emissive = Rgb(0.04f, 0.10f, 0.25f)), TB)
        trackball.uniformScale(0.032f); trackball.tapRadius = 1.75f
        trackball.add("TrackOrbit", MeshGen.tube(CrystalMesh.ring(1.55f, 48), 0.07f, 6, true), Mat.Unlit(Rgb(0.45f, 0.78f, 1.00f), alpha = 0.8f, blend = Blend.ADD))
            .also { it.rot = Q.axisAngle(1f, 0f, 0f, 0.35f) }
        bench.add("TrackCup", unitCyl, darkSteel, P3(TB.x, (TB.y - 0.030f + TOP) / 2, TB.z)).also { it.scale = P3(0.026f, TB.y - 0.030f - TOP, 0.026f) }

        // ================= the lattice
        latt = root.add(Node("Lattice"))
        partB = latt.add(Node("PartB"))
        bondsA = latt.add("BondsA", null, bondMat); bondsX = latt.add("BondsX", null, bondMat)
        bondsB = partB.add("BondsB", null, bondMat); bondsCell = latt.add("BondsCell", null, bondCellMat)
        poolA = Array(POOL_A) { latt.add("A$it", sphMid, atomMat("C", 0)).also { n -> n.visible = false } }
        poolB = Array(POOL_B) { partB.add("B$it", sphMid, atomMat("C", 0)).also { n -> n.visible = false } }
        probes = Array(2) { latt.add("Probe$it", sphMid, atomMat("C", 0)).also { n -> n.visible = false } }
        cellEdges = Array(12) { latt.add("CellEdge$it", unitCyl, cellEdgeOff) }
        centreGlow = latt.add("CoordGlow", quad, centreGlowMat).also { it.billboard = true; it.visible = false }
        shellNodes = Array(MAX_SHELL) { latt.add("Shell$it", sphMid, atomMat("C", 2)).also { n -> n.visible = false } }
        spokes = Array(MAX_SHELL) { latt.add("Spoke$it", unitCyl, spokeMat).also { n -> n.visible = false } }
        polyEdges = Array(MAX_EDGES) { latt.add("PolyEdge$it", unitCyl, polyEdgeMat).also { n -> n.visible = false } }
        hullMd = CrystalMesh.hullBuffer()
        hullNode = latt.add("PolyFaces", hullMd, Mat.Unlit(Rgb(1f, 0.85f, 0.5f), alpha = 0.18f, blend = Blend.TRANSPARENT)).also { it.visible = false }
        glows = Array(MAX_GLOW) { latt.add("Repel$it", quad, glowMat).also { n -> n.billboard = true; n.visible = false } }
        grip = partB.add("TopLayer", CrystalMesh.slideArrow(), gripMat).also { it.visible = false }

        update(0f)
        return root
    }

    private val V0 get() = viewOf(0)
    /** Presentation pose per crystal: graphite is tipped further toward the viewer so its hexagons and layers show. */
    private fun viewOf(ci: Int): Q = if (ci == 3) Q.axisAngle(0f, 1f, 0f, 0.35f) * Q.axisAngle(1f, 0f, 0f, 0.62f)
        else Q.axisAngle(0f, 1f, 0f, 0.42f) * Q.axisAngle(1f, 0f, 0f, 0.30f)

    // ---------------------------------------------------------------- specimens

    private fun spotPos(ci: Int) = SPOT[ci].let { P3(it[0], it[2], it[1]) }

    private fun buildSalt(unitCyl: MeshData, unitBox: MeshData) {
        val s = SALT
        val rig = bench.add(Node("SaltRig")).also { it.pos = spotPos(0) }
        val saltMat = Mat.Pbr(Rgb(0.93f, 0.94f, 0.97f), metallic = 0f, roughness = 0.24f, emissive = Rgb(0.10f, 0.10f, 0.12f))
        saltL = rig.add("SpecNaCl", CrystalMesh.saltHalf(s, -1f), saltMat)
        saltR = rig.add("SaltRight", CrystalMesh.saltHalf(s, 1f), saltMat)
        saltL.tapRadius = 0.060f
        specNodes[0] = saltL
        // the chisel, hovering above the crystal (blade down along the cleave plane x = 0)
        chiselRig = bench.add(Node("ChiselRig")).also { it.pos = P3(SPOT[0][0], SPOT[0][2] + s + CHISEL_GAP, SPOT[0][1]) }
        chiselRig.add("Chisel", MeshGen.box(0.007f, 0.070f, 0.056f), steel, P3(0f, 0.035f, 0f)).tapRadius = 0.055f
        chiselRig.add("ChiselEdge", com.bytedance.chemistrylab.render.OptGeo.triPrism(doubleArrayOf(-0.0035, 0.0, 0.0, -0.016, 0.0035, 0.0), 0.056f), steel)
        chiselRig.add("ChiselHandle", unitCyl, Mat.Pbr(Rgb(0.80f, 0.20f, 0.14f), roughness = 0.45f), P3(0f, 0.115f, 0f)).also { it.scale = P3(0.015f, 0.095f, 0.015f) }
        chiselRig.add("ChiselCap", unitCyl, darkSteel, P3(0f, 0.166f, 0f)).also { it.scale = P3(0.012f, 0.008f, 0.012f) }
    }

    private const val CHISEL_GAP = 0.060f

    private fun buildCsCl(disc: MeshData) {
        val rig = bench.add(Node("CsClRig")).also { it.pos = spotPos(1) }
        val glass = Mat.Pbr(Rgb(0.88f, 0.94f, 1.0f), metallic = 0f, roughness = 0.04f, alpha = 0.16f)
        val powder = Mat.Pbr(Rgb(0.95f, 0.95f, 0.96f), metallic = 0f, roughness = 0.75f, emissive = Rgb(0.08f, 0.08f, 0.09f))
        rig.add("VialFill", disc, powder, P3(0f, 0.032f, 0f)).also { it.scale = P3(0.0235f, 0.064f, 0.0235f) }
        specNodes[1] = rig.add("SpecCsCl", MeshGen.tube(floatArrayOf(0f, 0.001f, 0f, 0f, 0.092f, 0f), 0.027f, 32, false), glass)
        specNodes[1].tapRadius = 0.060f
        rig.add("VialBase", disc, glass, P3(0f, 0.001f, 0f)).also { it.scale = P3(0.027f, 0.002f, 0.027f) }
        rig.add("VialCap", disc, Mat.Pbr(Rgb(0.08f, 0.08f, 0.09f), roughness = 0.5f), P3(0f, 0.102f, 0f)).also { it.scale = P3(0.029f, 0.024f, 0.029f) }
        // a few loose cubic crystals beside the vial
        val cube = MeshGen.box(1f, 1f, 1f)
        for ((k, o) in listOf(floatArrayOf(0.044f, 0.020f), floatArrayOf(0.040f, -0.026f), floatArrayOf(-0.042f, 0.028f)).withIndex())
            rig.add("CsClXtal$k", cube, powder, P3(o[0], 0.007f - k * 0.001f, o[1])).also { it.uniformScale(0.014f - k * 0.002f); it.rot = Q.axisAngle(0f, 1f, 0f, 0.5f + k) }
    }

    private fun buildDiamond(quad: MeshData) {
        val p = spotPos(2)
        bench.add("DiamondCup", MeshGen.cylinder(0.016f, 0.014f, 24), darkSteel, P3(p.x, p.y + 0.007f, p.z))
        diamondRig = bench.add(Node("DiamondRig")).also { it.pos = P3(p.x, p.y + 0.054f, p.z); it.uniformScale(0.046f) }
        val gem = Mat.Pbr(Rgb(0.92f, 0.96f, 1.0f), metallic = 0f, roughness = 0.03f, alpha = 0.62f, emissive = Rgb(0.10f, 0.12f, 0.16f))
        specNodes[2] = diamondRig.add("SpecDiamond", CrystalMesh.brilliant(), gem).also { it.tapRadius = 1.35f }
        val at = listOf(floatArrayOf(0.35f, 0.34f, 0.15f), floatArrayOf(-0.55f, 0.20f, 0.45f), floatArrayOf(0.70f, 0.05f, -0.40f),
            floatArrayOf(-0.20f, -0.30f, 0.65f), floatArrayOf(0.05f, 0.33f, -0.45f))
        sparkles = Array(at.size) { k -> diamondRig.add("Sparkle$k", quad, sparkMat, P3(at[k][0], at[k][1], at[k][2])).also { it.billboard = true } }
    }

    private fun buildGraphite(unitCyl: MeshData, unitBox: MeshData) {
        val p = spotPos(3)
        val rig = bench.add(Node("GraphiteRig")).also { it.pos = p; it.uniformScale(1.55f) }
        // a lump of stacked, slightly offset plates: the layers you can see in real graphite
        val sheen = Mat.Pbr(Rgb(0.27f, 0.28f, 0.31f), metallic = 0.65f, roughness = 0.28f)
        val dk = Mat.Pbr(Rgb(0.17f, 0.18f, 0.20f), metallic = 0.5f, roughness = 0.42f)
        val (shiny, dull) = CrystalMesh.graphiteLump()
        specNodes[3] = rig.add("SpecGraphite", shiny, sheen).also { it.tapRadius = 0.040f }
        rig.add("GraphiteDull", dull, dk)
        // the writing station: a clipboard leaning back toward the viewer, paper, and a pencil that writes as a layer slides
        val clip = bench.add(Node("Clipboard")).also { it.pos = CLIP; it.rot = Q.axisAngle(1f, 0f, 0f, -CLIP_LEAN) }
        clip.add("ClipBoard", unitBox, Mat.Pbr(Rgb(0.30f, 0.20f, 0.12f), roughness = 0.6f), P3(0f, 0.052f, -0.002f)).also { it.scale = P3(0.17f, 0.104f, 0.004f) }
        clip.add("Paper", unitBox, Mat.Pbr(Rgb(0.95f, 0.95f, 0.93f), roughness = 0.85f), P3(0f, 0.050f, 0.0006f)).also { it.scale = P3(0.150f, 0.088f, 0.0012f) }
        clip.add("ClipClamp", unitBox, steel, P3(0f, 0.100f, 0.004f)).also { it.scale = P3(0.05f, 0.010f, 0.006f) }
        val strokeMat = Mat.Pbr(Rgb(0.20f, 0.20f, 0.22f), metallic = 0.45f, roughness = 0.35f)
        strokes = Array(3) { k -> clip.add("Stroke$k", unitBox, strokeMat, P3(0f, STROKE_Y[k], 0.0016f)).also { it.visible = false } }
        pencil = clip.add(Node("Pencil"))
        val body = Mat.Pbr(Rgb(0.98f, 0.76f, 0.16f), roughness = 0.38f)
        val wood = Mat.Pbr(Rgb(0.88f, 0.70f, 0.48f), roughness = 0.7f)
        val lead = Mat.Pbr(Rgb(0.20f, 0.20f, 0.22f), metallic = 0.5f, roughness = 0.3f)
        val cone = BuoyMesh.cone(16)
        // pencil local frame: tip at the origin, body along +Y
        val down = Q.axisAngle(1f, 0f, 0f, PI.toFloat())          // cone apex pointing down to the tip
        pencil.add("PencilLead", cone, lead, P3(0f, 0.008f, 0f)).also { it.scale = P3(0.0020f, 0.008f, 0.0020f); it.rot = down }
        pencil.add("PencilWood", cone, wood, P3(0f, 0.027f, 0f)).also { it.scale = P3(0.0050f, 0.021f, 0.0050f); it.rot = down }
        pencil.add("PencilBody", MeshGen.cylinder(1f, 1f, 6), body, P3(0f, 0.027f + 0.065f, 0f)).also { it.scale = P3(0.0050f, 0.130f, 0.0050f) }
        pencil.add("PencilFerrule", unitCyl, alu, P3(0f, 0.163f, 0f)).also { it.scale = P3(0.0053f, 0.013f, 0.0053f) }
        pencil.add("PencilEraser", unitCyl, Mat.Pbr(Rgb(0.95f, 0.52f, 0.58f), roughness = 0.7f), P3(0f, 0.175f, 0f)).also { it.scale = P3(0.0050f, 0.011f, 0.0050f) }
        pencil.rot = Q.fromTo(0f, 1f, 0f, 0.42f, 0.30f, 0.86f)
    }

    private fun buildCopper(unitBox: MeshData) {
        val rig = bench.add(Node("CuRig")).also { it.pos = spotPos(4) }
        val cu = Mat.Pbr(Rgb(0.96f, 0.64f, 0.52f), metallic = 1f, roughness = 0.20f)
        specNodes[4] = rig.add("SpecCu", unitBox, cu, P3(-0.008f, 0.029f, 0.004f)).also { it.uniformScale(0.058f); it.rot = Q.axisAngle(0f, 1f, 0f, 0.4f); it.tapRadius = 1.05f }
        rig.add("CuCoil", BuoyMesh.helix(0.015f, 0.055f, 7, 0.0028f), cu, P3(0.040f, 0.062f, -0.024f))
    }

    // ================================================================ lattice geometry

    private val crystal: Crystal get() = Crystals.ALL[crystalIdx]
    private fun modelOf(ci: Int, n: Int) = synchronized(models) { models.getOrPut(ci * 4 + n) { Model(Crystals.ALL[ci], n) } }

    /** Bond meshes (one per part) for model [m]: built once per crystal x supercell. */
    private fun geoOf(ci: Int, n: Int, m: Model): Geo = geos.getOrPut(ci * 4 + n) {
        val cr = m.crystal
        val k = R_NOM / m.boundRadius.toFloat()
        val dnn = cr.nnDistance(0).toFloat()
        val r = 0.075f * dnn * k
        if (cr.bonding == Bonding.METALLIC) return@getOrPut Geo(k, r, null, null, null, null)
        val a = ArrayList<Float>(); val b = ArrayList<Float>(); val x = ArrayList<Float>(); val c = ArrayList<Float>()
        val cell = m.cellMembers.map { it.atom }.toHashSet()
        val bd = m.bonds
        for (q in bd.indices step 2) {
            val i = bd[q]; val j = bd[q + 1]
            val pi = m.atoms[i].p; val pj = m.atoms[j].p
            val seg = floatArrayOf(pi[0].toFloat() * k, pi[1].toFloat() * k, pi[2].toFloat() * k, pj[0].toFloat() * k, pj[1].toFloat() * k, pj[2].toFloat() * k)
            val list = when { m.partB[i] && m.partB[j] -> b; !m.partB[i] && !m.partB[j] -> a; else -> x }
            // part-B bonds live in part B's frame (it moves): same coordinates, the group starts at the origin
            seg.forEach { list += it }
            if (i in cell && j in cell) seg.forEach { c += it }
        }
        // 6-sided sticks; 5-sided for the biggest blocks (NaCl 3x3x3: 882 bonds) to trim the one-time upload
        val sides = if (bd.size / 2 > 400) 5 else 6
        fun mk(l: List<Float>, rr: Float) = if (l.isEmpty()) null else CrystalMesh.bonds(l.toFloatArray(), rr, sides)
        Geo(k, r, mk(a, r), mk(b, r), mk(x, r), mk(c, r * 1.25f))
    }

    private fun hullOf(ci: Int, cls: Int) = hulls.getOrPut(ci * 4 + cls) { Polyhedron.hull(Crystals.ALL[ci].shell(cls).map { it.second }) }

    private fun viewKey() = crystalIdx or (supercell shl 3) or ((if (spaceFill) 1 else 0) shl 5) or ((if (cellOn) 1 else 0) shl 6) or
        ((if (coordOn) 1 else 0) shl 7) or (probeK shl 8) or ((if (walkIn) 1 else 0) shl 10)

    /** Rebuild the displayed lattice for the current state (only when something changed — never per frame). */
    private fun applyView() {
        val gk = crystalIdx * 4 + supercell
        model = modelOf(crystalIdx, supercell)
        geo = geoOf(crystalIdx, supercell, model)
        val m = model; val cr = m.crystal; val k = geo.k
        if (probeK >= cr.classCount) probeK = 0
        if (gk != geoKey) {
            geoKey = gk
            bondsA.setMesh(geo.a); bondsB.setMesh(geo.b); bondsX.setMesh(geo.x); bondsCell.setMesh(geo.cell)
            pairs = m.slipPairs(0.5)
            nodeOf = arrayOfNulls(m.atoms.size)
            radiusOf = FloatArray(m.atoms.size)
        }
        val sph = when { m.atoms.size <= 40 -> sphHi; m.atoms.size <= 150 -> sphMid; else -> sphLo }
        val members = HashMap<Int, CellMember>()
        if (cellOn) for (c in m.cellMembers) members[c.atom] = c
        val highlight = cellOn || coordOn
        val activeProbe = if (coordOn) m.probes[probeK] else -1
        var ia = 0; var ib = 0
        for ((i, at) in m.atoms.withIndex()) {
            val node = if (m.partB[i]) poolB[ib++] else poolA[ia++]
            nodeOf[i] = node
            val sym = cr.elements[at.el].symbol
            val r = cr.radius(at.el).toFloat() * k * (if (spaceFill) 1f else ballK(cr))
            radiusOf[i] = r
            node.visible = true
            node.pos = P3(at.p[0].toFloat() * k, at.p[1].toFloat() * k, at.p[2].toFloat() * k)
            val mem = members[i]
            val lit = !highlight || mem != null || i == activeProbe
            if (mem != null && i != activeProbe) { node.setMesh(cutMesh.getValue(mem.cut)); node.rot = cutRot(mem) }
            else { node.setMesh(sph); node.rot = Q.identity() }
            node.uniformScale(if (lit || !spaceFill) r else r * 0.28f)
            node.setMat(atomMat(sym, if (lit) 0 else 1))
        }
        for (j in ia until POOL_A) poolA[j].visible = false
        for (j in ib until POOL_B) poolB[j].visible = false
        // probes: real atoms with stable names (the pinch targets); the pool node underneath is hidden
        for (q in 0 until 2) {
            val pn = probes[q]
            if (q >= cr.classCount) { pn.visible = false; pn.tapRadius = 0f; continue }
            val i = m.probes[q]; val src = nodeOf[i]!!
            src.visible = false
            pn.visible = true; pn.pos = src.pos; pn.rot = src.rot; pn.scale = src.scale; pn.setMesh(src.mesh)
            val sym = cr.elements[m.atoms[i].el].symbol
            if (coordOn && probeK == q) { pn.setMat(atomMat(sym, 2)); if (!spaceFill) pn.uniformScale(src.scale.x * 1.3f) } else pn.setMat(src.mat)
        }
        tapScale = -1f
        // bonds
        val sticks = !spaceFill && cr.bonding != Bonding.METALLIC
        for (bn in listOf(bondsA, bondsB, bondsX)) { bn.visible = sticks && bn.mesh != null; bn.setMat(if (highlight) bondDim else bondMat) }
        if (cleaveState != 0) bondsX.visible = false
        bondsCell.visible = sticks && cellOn && bondsCell.mesh != null
        // unit-cell outline (always drawn, bright when highlighted)
        val cc = m.cellCorners.map { floatArrayOf(it[0].toFloat() * k, it[1].toFloat() * k, it[2].toFloat() * k) }
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        for ((e, pr) in edges.withIndex()) {
            placeSeg(cellEdges[e], cc[pr.first], cc[pr.second], if (cellOn) 0.0034f else 0.0022f)
            cellEdges[e].setMat(if (cellOn) cellEdgeOn else cellEdgeOff)
        }
        placeCoordination()
        // graphite: the slide grip sits on the front edge of the top layer
        grip.visible = cr.hex && !walkIn
        if (cr.hex) {
            val top = m.atoms.indices.filter { m.partB[it] }
            val y = top.maxOf { m.atoms[it].p[1] }.toFloat() * k
            val zf = top.maxOf { m.atoms[it].p[2] }.toFloat() * k
            grip.pos = P3(0f, y + 0.02f, zf + 0.05f); grip.uniformScale(0.17f)
        } else grip.tapRadius = 0f
        for (g in glows) g.visible = false
        fitTarget()
    }

    /** The coordination overlay: lit neighbours, spokes, polyhedron edges and faces around the active probe. */
    private fun placeCoordination() {
        val m = model; val cr = m.crystal; val k = geo.k
        val on = coordOn
        centreGlow.visible = on; hullNode.visible = on
        if (!on) {
            for (n in shellNodes) n.visible = false; for (n in spokes) n.visible = false; for (n in polyEdges) n.visible = false
            return
        }
        val i = m.probes[probeK]
        val c = floatArrayOf(m.atoms[i].p[0].toFloat() * k, m.atoms[i].p[1].toFloat() * k, m.atoms[i].p[2].toFloat() * k)
        val shell = cr.shell(m.atoms[i].cls)
        val pts = shell.map { (_, v) -> floatArrayOf(c[0] + v[0].toFloat() * k, c[1] + v[1].toFloat() * k, c[2] + v[2].toFloat() * k) }
        val scaleK = if (spaceFill) 1f else ballK(cr) * 1.3f         // lit neighbours drawn a little proud
        for (s in 0 until MAX_SHELL) {
            val n = shellNodes[s]; val sp = spokes[s]
            n.visible = s < pts.size; sp.visible = s < pts.size && !spaceFill
            if (s >= pts.size) continue
            val el = shell[s].first
            n.pos = P3(pts[s][0], pts[s][1], pts[s][2]); n.uniformScale(cr.radius(el).toFloat() * k * scaleK * 1.05f)
            n.setMesh(if (pts.size > 8) sphMid else sphHi)
            n.setMat(atomMat(cr.elements[el].symbol, 2))
            placeSeg(sp, c, pts[s], max(geo.bondR * 1.35f, 0.0030f))
        }
        val poly = hullOf(crystalIdx, m.atoms[i].cls)
        for (e in 0 until MAX_EDGES) {
            val n = polyEdges[e]
            n.visible = e < poly.edges.size
            if (e < poly.edges.size) placeSeg(n, pts[poly.edges[e][0]], pts[poly.edges[e][1]], 0.0034f)
        }
        // faces: fan-triangulate every polygon into the fixed-capacity buffer, collapse the rest onto the centre
        val md = hullMd; val cap = md.idx.size / 3
        var t = 0
        for (f in poly.faces) for (q in 1 until f.size - 1) {
            if (t >= cap) break
            for ((slot, vi) in intArrayOf(f[0], f[q], f[q + 1]).withIndex()) {
                val v = t * 3 + slot
                md.pos[v * 3] = pts[vi][0]; md.pos[v * 3 + 1] = pts[vi][1]; md.pos[v * 3 + 2] = pts[vi][2]
                md.idx[v] = v
            }
            t++
        }
        for (v in t * 3 until md.idx.size) { md.pos[v * 3] = c[0]; md.pos[v * 3 + 1] = c[1]; md.pos[v * 3 + 2] = c[2]; md.idx[v] = v }
        meshEdited(md)
        val tint = elColor(cr.elements[m.atoms[i].el].symbol)
        hullNode.setMat(Mat.Unlit(ProcTex.okLerp(tint, Rgb(1f, 0.9f, 0.6f), 0.4f), alpha = 0.20f, blend = Blend.TRANSPARENT))
        centreGlow.pos = P3(c[0], c[1], c[2]); centreGlow.uniformScale(radiusOf[i] * 4.5f + 0.03f)
    }

    /** Rotation that turns a canonical cut-away (see CrystalMesh.cutSphere) into the cell's inward octant / edge / face. */
    private fun cutRot(c: CellMember): Q {
        fun ax(code: Int) = FloatArray(3).also { it[abs(code) - 1] = if (code > 0) 1f else -1f }
        return when {
            c.cut == Cut.WEDGE60 || c.cut == Cut.WEDGE120 -> Q.axisAngle(0f, 1f, 0f, -Math.toRadians(c.startDeg).toFloat())
            c.cut == Cut.HALF && c.dirs.isEmpty() -> {             // hexagonal cell face: inward normal in the layer plane
                val a = Math.toRadians(c.startDeg).toFloat()
                quatFromBasis(floatArrayOf(0f, 1f, 0f), floatArrayOf(cos(a), 0f, sin(a)), floatArrayOf(sin(a), 0f, -cos(a)))
            }
            c.cut == Cut.HALF -> { val y = ax(c.dirs[0]); val x = perpAxis(y); quatFromBasis(x, y, cross(x, y)) }
            c.cut == Cut.QUARTER -> { val x = ax(c.dirs[0]); val z = ax(c.dirs[1]); quatFromBasis(x, cross(z, x), z) }
            c.cut == Cut.EIGHTH -> {
                var x = ax(c.dirs[0]); val y = ax(c.dirs[1]); var z = ax(c.dirs[2])
                if (dot(x, cross(y, z)) < 0f) { val t = x; x = z; z = t }
                quatFromBasis(x, y, z)
            }
            else -> Q.identity()
        }
    }

    private fun perpAxis(y: FloatArray) = if (abs(y[0]) < 0.5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 0f, 1f)

    // ---------------------------------------------------------------- fitting the lattice into the volume

    /**
     * Largest scale that keeps the model (as presented by userRot) inside the action band — or, walking in, inside
     * the whole volume. World yaw (auto-spin) never changes heights or the horizontal radius, so this holds while it
     * spins; it is re-done after a drag tilts the model.
     */
    private fun fitTarget(snap: Boolean = false) {
        val m = model; val k = geo.k
        var top = -1e9f; var bot = 1e9f; var rh = 0f
        for ((i, at) in m.atoms.withIndex()) {
            val w = userRot.rotate(floatArrayOf(at.p[0].toFloat() * k, at.p[1].toFloat() * k, at.p[2].toFloat() * k))
            val r = radiusOf.getOrElse(i) { 0f }
            top = max(top, w[1] + r); bot = min(bot, w[1] - r); rh = max(rh, sqrt(w[0] * w[0] + w[2] * w[2]) + r)
        }
        val bt = if (walkIn) W_TOP else BAND_TOP; val bb = if (walkIn) W_BOT else BAND_BOT; val rm = if (walkIn) W_R else R_MAX
        val s = min((bt - bb) / (top - bot), rm / rh)
        sTarget = s
        cTarget[0] = if (walkIn) 0f else LX; cTarget[2] = if (walkIn) 0f else LZ
        cTarget[1] = (bt + bb) / 2 - (top + bot) / 2 * s
        topExt = top
        if (snap) { sCur = sTarget; for (a in 0 until 3) cCur[a] = cTarget[a] }
    }

    // ================================================================ per frame

    override fun update(dt: Float) {
        clock += dt
        val key = viewKey()
        if (key != shownKey) {
            val first = shownKey < 0                       // a new crystal / a rebuild: no easing (it zooms in anyway)
            shownKey = key
            applyView()
            if (first || loadT < 1f) fitTarget(snap = true)
        }
        bench.visible = !walkIn

        // ---- load: the lattice grows out of the specimen ("magnify")
        if (loadT < 1f) loadT = min(1f, loadT + dt / 0.85f)
        if (cleavePending && loadT >= 1f && crystal.id == "nacl") { cleavePending = false; startCleave() }

        // ---- spin (world yaw: heights and the fit never change)
        val busy = dragging || cleaveState == 1 || cleaveState == 3 || loadT < 1f
        if (!busy) userRot = Q.axisAngle(0f, 1f, 0f, SPIN[speedIdx] * dt) * userRot

        // ---- ease scale / centre to the fit
        val e = if (dt == 0f) 1f else 1f - exp(-dt / 0.18f)
        sCur += (sTarget - sCur) * e
        for (a in 0 until 3) cCur[a] += (cTarget[a] - cCur[a]) * e

        // ---- cleave and slide animations (part B moves as one group)
        stepCleave(dt)
        stepSlide(dt)
        P3(sep + slideX, slip, slideZ).let { if (partB.pos != it) partB.pos = it }

        // ---- place the lattice
        val u = smooth(loadT)
        if (loadT < 1f && !walkIn) {
            val from = loadFrom
            latt.pos = P3(from.x + (cCur[0] - from.x) * u, from.y + (cCur[1] - from.y) * u, from.z + (cCur[2] - from.z) * u)
            latt.uniformScale(sCur * (0.03f + 0.97f * u))
        } else { latt.pos = P3(cCur[0], cCur[1], cCur[2]); latt.uniformScale(sCur) }
        latt.rot = userRot
        val s = latt.scale.x
        if (abs(s - tapScale) > 0.03f * s) { tapScale = s; updateTapRadii(s) }
        (if (walkIn) TB_WALK else TB).let { if (trackball.pos != it) trackball.pos = it }

        // ---- specimens: selection rims, magnifier beams, diamond sparkle, pencil
        for (ci in 0 until 5) rims[ci].setMat(if (ci == crystalIdx) rimOn else rimOff)
        placeBeams(u)
        diamondRig.rot = Q.axisAngle(0f, 1f, 0f, clock * 0.45f)
        for ((k, sp) in sparkles.withIndex()) {
            val ph = (clock * 0.9f + k * 0.37f) % 1f
            val tw = max(0f, sin(ph * 2 * PI.toFloat())).pow(6)
            sp.visible = tw > 0.02f && !walkIn
            sp.uniformScale(0.15f + 1.15f * tw)
        }
        placePencil()

        if (frame++ % 6 == 0 || Lang.en != textEn || dt == 0f) { textEn = Lang.en; updateText() }
        else updateLabelPositions()
    }

    private fun updateTapRadii(s: Float) {
        for (q in 0 until 2) {
            val pn = probes[q]
            if (!pn.visible) { pn.tapRadius = 0f; continue }
            pn.tapRadius = max(1.15f, 0.050f / (pn.scale.x * s))
        }
        grip.tapRadius = if (grip.visible) 0.060f / (grip.scale.x * s) else 0f
    }

    /** The magnifier: four faint rays from the chosen specimen into the lattice it shows. */
    private fun placeBeams(u: Float) {
        val on = !walkIn && u > 0.05f
        val sp = spotPos(crystalIdx); val tip = P3(sp.x, sp.y + SPEC_H[crystalIdx] + 0.01f, sp.z)
        val h = topExt * sCur * u * 0.62f
        for (b in 0 until 4) {
            if (beams[b].visible != on) beams[b].visible = on
            if (!on) continue
            val dy = if (b % 2 == 0) -h else h; val dz = if (b < 2) -h * 0.7f else h * 0.7f
            placeSegW(beams[b], tip, P3(latt.pos.x - 0.06f, latt.pos.y + dy, latt.pos.z + dz), 0.0017f)
        }
    }

    // ---------------------------------------------------------------- cleave (NaCl)

    private val isCleaved get() = cleaveState == 1 || cleaveState == 2
    private const val SLIP_T = 0.55f                    // the half slips by a/2 from here (s)
    private const val SEP_T = 1.10f                     // ... and flies off from here

    private fun startCleave() {
        if (cellOn) cellOn = false
        if (coordOn) coordOn = false
        cleaveState = 1; cleaveT = 0f
        // turn the lattice so its cleave plane (normal = local x) is seen edge-on: local x across the line of sight
        // (and local +x to the RIGHT, so the half that flies off moves away from the specimen tray)
        val wx = userRot.rotate(floatArrayOf(1f, 0f, 0f))
        alignYaw = -atan2(-wx[2], wx[0]); alignApplied = 0f
    }

    private fun stepCleave(dt: Float) {
        val m = if (::model.isInitialized) model else return
        val a = m.crystal.a.toFloat() * geo.k
        val sepMax = 0.15f / max(0.2f, sCur)
        when (cleaveState) {
            1 -> {
                val t0 = cleaveT
                cleaveT += dt
                val t = cleaveT
                val pan = latt.pos.x / 0.9f
                if (t0 == 0f && dt > 0f) Sfx.play(Sfx.CLACK, 0.6f, 0.8f, pan)                      // sound: the chisel strikes the crystal
                if (t0 < SLIP_T && t >= SLIP_T) Sfx.play(Sfx.TICK, 0.3f, 0.8f, pan)                 // sound: the layers slip half a spacing
                if (t0 < SEP_T && t >= SEP_T) Sfx.play(Sfx.GLASS, 0.55f, 1.25f, pan)                // sound: like ions repel - the crystal cracks apart on {100}
                val al = smooth(t / 0.70f); userRot = Q.axisAngle(0f, 1f, 0f, alignYaw * (al - alignApplied)) * userRot; alignApplied = al
                slip = a / 2 * smooth((t - SLIP_T) / 0.45f)
                sep = sepMax * smooth((t - SEP_T) / 0.85f)
                if (t >= SLIP_T) bondsX.visible = false
                if (t >= SEP_T + 0.9f) { cleaveState = 2; for (g in glows) g.visible = false }
            }
            3 -> {
                cleaveT += dt
                val w = 1f - smooth(cleaveT / 0.8f)
                slip = a / 2 * w; sep = sepMax * w
                if (cleaveT >= 0.8f) { Sfx.play(Sfx.CLACK, 0.3f, 1.2f, latt.pos.x / 0.9f) /* sound: the two halves meet again */; cleaveState = 0; slip = 0f; sep = 0f; bondsX.visible = !spaceFill && bondsX.mesh != null }
            }
        }
        // like-charge repulsion glows between the facing ions (only while the halves part)
        val glowA = if (cleaveState == 1) smooth((cleaveT - SLIP_T - 0.2f) / 0.3f) * (1f - smooth((cleaveT - SEP_T - 0.15f) / 0.6f)) else 0f
        for ((g, node) in glows.withIndex()) {
            val on = glowA > 0.01f && g < pairs.size
            node.visible = on
            if (!on) continue
            val (ib, ja) = pairs[g]
            val pb = m.atoms[ib].p; val pa = m.atoms[ja].p; val k = geo.k
            node.pos = P3((pa[0].toFloat() * k + pb[0].toFloat() * k + sep) / 2, (pa[1].toFloat() * k + pb[1].toFloat() * k + slip) / 2, (pa[2].toFloat() * k + pb[2].toFloat() * k) / 2)
            node.uniformScale(radiusOf.getOrElse(ib) { 0.02f } * 2.4f * glowA + 0.004f)
        }
        // the macro crystal and the chisel
        val strike = if (cleaveState == 1) cleaveT else 99f
        val drop = if (strike < 0.22f) smooth(strike / 0.22f) else 1f - smooth((strike - 0.30f) / 0.6f)
        P3(SPOT[0][0], SPOT[0][2] + SALT + CHISEL_GAP - (CHISEL_GAP - 0.014f) * drop, SPOT[0][1]).let { if (chiselRig.pos != it) chiselRig.pos = it }
        val open = when (cleaveState) { 1 -> smooth((cleaveT - 0.22f) / 0.4f); 2 -> 1f; 3 -> 1f - smooth(cleaveT / 0.8f); else -> 0f }
        P3(-0.016f * open, 0f, 0f).let { if (saltL.pos != it) { saltL.pos = it; saltL.rot = Q.axisAngle(0f, 0f, 1f, 0.14f * open) } }
        P3(0.016f * open, 0f, 0f).let { if (saltR.pos != it) { saltR.pos = it; saltR.rot = Q.axisAngle(0f, 0f, 1f, -0.14f * open) } }
    }

    // ---------------------------------------------------------------- slide (graphite)

    private fun stepSlide(dt: Float) {
        if (slideAnimLeft > 0f) {
            val step = min(dt, slideAnimLeft)
            moveLayer(slideVX * step, slideVZ * step)
            slideAnimLeft -= step
        }
    }

    /** Slide the top layer by (dx, dz) model metres (in its own plane); the pencil writes as much. */
    private fun moveLayer(dx: Float, dz: Float) {
        val k = geo.k; val lim = 2.6f * model.crystal.a.toFloat() * k
        val nx = (slideX + dx).coerceIn(-lim, lim); val nz = (slideZ + dz).coerceIn(-lim, lim)
        val moved = sqrt((nx - slideX) * (nx - slideX) + (nz - slideZ) * (nz - slideZ))
        slideX = nx; slideZ = nz
        slideTotal += moved / k
        strokeLen = min(3 * STROKE_MAX, strokeLen + moved * sCur * 0.55f)
    }

    private fun placePencil() {
        var line = min(2, (strokeLen / STROKE_MAX).toInt()); var len = strokeLen - line * STROKE_MAX
        if (strokeLen >= 3 * STROKE_MAX - 1e-6f) { line = 2; len = STROKE_MAX }
        val x0 = -STROKE_MAX / 2
        for (k in 0 until 3) {
            val l = if (k < line) STROKE_MAX else if (k == line) len else 0f
            val on = l > 0.002f
            if (strokes[k].visible != on) strokes[k].visible = on
            if (on) {
                val p = P3(x0 + l / 2, STROKE_Y[k], 0.0016f); val sc = P3(l, 0.0026f, 0.0010f)
                if (strokes[k].pos != p) strokes[k].pos = p; if (strokes[k].scale != sc) strokes[k].scale = sc
            }
        }
        val wob = if (sliding || slideAnimLeft > 0f) 0.0012f * sin(clock * 40f) else 0f
        val p = P3(x0 + len, STROKE_Y[line] + wob, 0.0016f)
        if (pencil.pos != p) pencil.pos = p
    }

    // ================================================================ text

    private fun nm(ci: Int): String = when (ci) {
        0 -> tr("食盐", "Salt"); 1 -> tr("氯化铯", "Caesium chloride"); 2 -> tr("金刚石", "Diamond"); 3 -> tr("石墨", "Graphite"); else -> tr("铜", "Copper")
    }

    private fun polyName(n: Int) = when (n) {
        3 -> tr("平面三角形", "triangle"); 4 -> tr("正四面体", "tetrahedron"); 6 -> tr("正八面体", "octahedron")
        8 -> tr("立方体", "cube"); 12 -> tr("立方八面体", "cuboctahedron"); else -> "—"
    }

    private fun classLabel(cr: Crystal, cls: Int): String = when (cr.id) {
        "diamond" -> if (cls == 0) "C (A)" else "C (B)"
        "graphite" -> if (cls == 0) "C α" else "C β"
        else -> cr.elements[cr.classElement[cls]].label
    }

    private fun frac(f: Double): String = when {
        abs(f - 0.125) < 1e-6 -> "⅛"; abs(f - 0.25) < 1e-6 -> "¼"; abs(f - 0.5) < 1e-6 -> "½"
        abs(f - 1.0 / 6) < 1e-6 -> "⅙"; abs(f - 1.0 / 3) < 1e-6 -> "⅓"; else -> ""
    }

    /** "8×⅛ + 6×½ = 4" for site class [cls] (whole atoms counted plainly). */
    private fun sharing(cls: Int): String {
        val t = model.sharingTerms(cls)
        val sum = t.sumOf { it.first * it.second }
        return t.joinToString(" + ") { (n, f) -> if (f >= 0.999) "$n" else "$n×${frac(f)}" } + " = " + (sum.roundToInt())
    }

    private fun sci(x: Double): String {
        val e = floor(log10(x)).toInt(); val mnt = x / 10.0.pow(e)
        val sup = e.toString().map { ch -> when (ch) { '-' -> '⁻'; else -> "⁰¹²³⁴⁵⁶⁷⁸⁹"[ch - '0'] } }.joinToString("")
        return "%.3f×10%s".format(mnt, sup)
    }

    /** Magnification of the model vs the real crystal: display metres per pm / 1e-12. */
    private fun magnification(): String {
        val mag = geo.k * sCur / 1e-12
        return if (Lang.en) "%.0f million ×".format(mag / 1e6) else "%.1f 亿倍".format(mag / 1e8)   // i18n-ok (language-specific unit)
    }

    private fun updateText() {
        val cr = crystal; val m = model
        val a = cr.a; val d = cr.nnDistance(0)
        val cn = (0 until cr.classCount).map { cr.coordination(it) }.distinct().joinToString(":")
        val cnTxt = if (cr.classCount == 2 && cr.elements.size == 2) cn.let { if (it.contains(":")) it else "$it:$it" } else cn
        val bondTxt = when (cr.id) { "nacl" -> "Na–Cl"; "cscl" -> "Cs–Cl"; "cu" -> "Cu–Cu"; else -> "C–C" }
        val l1 = when (cr.id) {
            "nacl" -> tr("NaCl 岩盐型：Cl⁻ 面心立方，Na⁺ 填满八面体空隙 · a = %.1f pm · Z = %d · 配位 %s · %s %.1f pm",
                "NaCl rock salt: fcc Cl⁻, Na⁺ in every octahedral hole · a = %.1f pm · Z = %d · CN %s · %s %.1f pm").format(a, cr.zFormula, cnTxt, bondTxt, d)
            "cscl" -> tr("CsCl：Cl⁻ 简单立方，Cs⁺ 在每个立方体中心 · a = %.1f pm · Z = %d · 配位 %s · %s %.1f pm",
                "CsCl: simple-cubic Cl⁻, Cs⁺ at every cube centre · a = %.1f pm · Z = %d · CN %s · %s %.1f pm").format(a, cr.zFormula, cnTxt, bondTxt, d)
            "diamond" -> tr("金刚石：面心立方 + 沿体对角线平移 ¼ 的副本 · a = %.1f pm · Z = %d · 配位 %s（正四面体）· %s %.1f pm",
                "Diamond: fcc + a copy shifted ¼ of the body diagonal · a = %.1f pm · Z = %d · CN %s (tetrahedral) · %s %.1f pm").format(a, cr.zFormula, cn, bondTxt, d)
            "graphite" -> tr("石墨：六方层状 ABAB 堆叠 · a = %.1f pm，c = %.1f pm · Z = %d · 层内配位 %s · %s %.1f pm · 层距 %.1f pm",
                "Graphite: hexagonal layers, ABAB · a = %.1f, c = %.1f pm · Z = %d · CN %s in-plane · %s %.1f pm · layers %.1f pm").format(a, cr.c, cr.zFormula, cn, bondTxt, d, cr.c / 2)
            else -> tr("铜：面心立方（立方最密堆积）· a = %.1f pm · Z = %d · 配位 %s · %s %.1f pm",
                "Copper: face-centred cubic (cubic close packing) · a = %.1f pm · Z = %d · CN %s · %s %.1f pm").format(a, cr.zFormula, cn, bondTxt, d)
        }
        val rho = cr.density(); val dev = 100 * (rho / cr.measuredDensity - 1)
        val l2 = tr("ρ = Z·M/(N_A·V) = %d × %.2f g/mol ÷ (N_A × %s cm³) = %.3f g/cm³ · 实测 %.3f（%+.2f %%）· 空间占有率 %.1f %%",
            "ρ = Z·M/(N_A·V) = %d × %.2f g/mol ÷ (N_A × %s cm³) = %.3f g/cm³ · measured %.3f (%+.2f %%) · space filled %.1f %%").format(
            cr.zFormula, cr.formulaMass, sci(cr.cellVolume * 1e-30), rho, cr.measuredDensity, dev, 100 * cr.packingFraction())
        readoutText = l1 + "\n" + l2 + "\n" + whyLine()

        labels.clear()
        // 0 title above the lattice
        labels += LabelSpec("%s · %s".format(nm(crystalIdx), cr.formula), titlePos())
        // 1 the specimen, and how much the model magnifies it
        val sp = spotPos(crystalIdx)
        labels += LabelSpec(tr("实物 · 放大 %s", "Specimen · magnified %s").format(magnification()),
            if (crystalIdx == 0) P3(sp.x, SPOT[0][2] + SALT + CHISEL_GAP + 0.29f, sp.z + 0.02f)      // salt: above the chisel's label
            else P3(sp.x, sp.y + SPEC_H[crystalIdx] + 0.06f, sp.z + 0.04f), visible = !walkIn)
        // 2 coordination count at the lit atom
        val pi = m.probes[min(probeK, m.probes.size - 1)]
        val sh = cr.shell(m.atoms[pi].cls)
        labels += LabelSpec(tr("%s 配位数 %d · %s · %.1f pm", "%s CN %d · %s · %.1f pm").format(classLabel(cr, m.atoms[pi].cls), sh.size, polyName(sh.size), cr.nnDistance(m.atoms[pi].cls)),
            coordLabelPos(), visible = coordOn)
        // 3 unit cell: Z by sharing
        val zt = (0 until cr.classCount).joinToString(" · ") { c -> if (cr.classCount > 1 && cr.elements.size > 1) "${classLabel(cr, c)} ${sharing(c)}" else sharing(c) }
        labels += LabelSpec(tr("晶胞 Z：%s", "Cell Z: %s").format(if (cr.classCount > 1 && cr.elements.size == 1) sharingAll() else zt), cellLabelPos(), visible = cellOn)
        // 4 the chisel
        labels += LabelSpec(tr("劈刀 · {100} 解理", "Chisel · {100} cleavage"), P3(SPOT[0][0], SPOT[0][2] + SALT + CHISEL_GAP + 0.22f, SPOT[0][1] + 0.02f), visible = !walkIn)
        // 5 what is happening now: like-charge repulsion while cleaving / the slide grip on graphite
        labels += when {
            cleaveState == 1 && cleaveT in SLIP_T..(SEP_T + 0.6f) -> LabelSpec(tr("同号离子正对 → 互相排斥", "Like ions face to face → repel"), eventPos())
            cr.hex && !walkIn -> LabelSpec(tr("⟷ 捏住顶层拖动", "⟷ Pinch-drag the top layer"), gripLabelPos())
            else -> LabelSpec("", P3(0f, 0f, 0f), visible = false)
        }
    }

    private fun sharingAll(): String {
        // one element on two site classes (diamond, graphite): add the terms of both classes
        val t = (0 until model.crystal.classCount).flatMap { model.sharingTerms(it) }.groupBy { it.second }
            .map { (f, l) -> l.sumOf { it.first } to f }.sortedBy { it.second }
        val sum = t.sumOf { it.first * it.second }
        return t.joinToString(" + ") { (n, f) -> if (f >= 0.999) "$n" else "$n×${frac(f)}" } + " = " + sum.roundToInt()
    }

    private fun updateLabelPositions() {
        if (labels.size < 6) return
        labels[0] = labels[0].copy(pos = titlePos())
        labels[2] = labels[2].copy(pos = coordLabelPos())
        labels[3] = labels[3].copy(pos = cellLabelPos())
        if (labels[5].visible) labels[5] = labels[5].copy(pos = if (cleaveState == 1) eventPos() else gripLabelPos())
    }

    /** Volume position of a lattice-local point (model metres). */
    private fun world(p: FloatArray): P3 {
        val w = latt.rot.rotate(p); val s = latt.scale.x
        return P3(latt.pos.x + w[0] * s, latt.pos.y + w[1] * s, latt.pos.z + w[2] * s)
    }

    private fun titlePos() = P3(cCur[0], min(0.45f, cCur[1] + topExt * sCur + 0.045f), cCur[2] + 0.05f)
    private fun coordLabelPos(): P3 {
        val i = model.probes[min(probeK, model.probes.size - 1)]; val n = nodeOf.getOrNull(i) ?: return P3(0f, 0f, 0f)
        val w = world(floatArrayOf(n.pos.x, n.pos.y, n.pos.z))
        val r = radiusOf.getOrElse(i) { 0.02f } * latt.scale.x
        return P3(w.x, min(0.47f, w.y + r * 3.2f + 0.07f), w.z + 0.06f)
    }
    private fun cellLabelPos(): P3 {
        val k = geo.k
        val top = model.cellCorners.subList(4, 8).maxByOrNull { val w = userRot.rotate(floatArrayOf(it[0].toFloat() * k, it[1].toFloat() * k, it[2].toFloat() * k)); w[1] + 0.3f * w[2] }!!
        val w = world(floatArrayOf(top[0].toFloat() * k, top[1].toFloat() * k, top[2].toFloat() * k))
        return P3(w.x, min(0.47f, w.y + 0.05f), w.z + 0.05f)
    }
    private fun eventPos(): P3 = world(floatArrayOf(model.cleaveX.toFloat() * geo.k + sep / 2, topExt * 0.55f, 0f)).let { P3(it.x, min(0.47f, it.y + 0.04f), it.z + 0.08f) }
    private fun gripLabelPos(): P3 = world(floatArrayOf(grip.pos.x + partB.pos.x, grip.pos.y + partB.pos.y + 0.03f, grip.pos.z + partB.pos.z)).let { P3(it.x, min(0.47f, it.y + 0.03f), it.z + 0.04f) }

    private fun whyLine(): String {
        val cr = crystal
        return when {
            cr.id == "nacl" && cleaveState != 0 -> tr("劈开！离子层错开半格（%.0f pm）→ 同号离子正对、互相排斥 → 沿 {100} 平面裂开：食盐硬而脆，解理面平整",
                "Cleaved! A slip of half a spacing (%.0f pm) puts like ions face to face; they repel → salt splits on flat {100} planes").format(cr.a / 2)
            cr.hex && slideTotal > 1.0 -> tr("顶层已滑过 %.0f pm：层间只有微弱的范德华力（层距 %.0f pm ≫ 键长 %.0f pm）→ 层层滑开，铅笔在纸上留下石墨",
                "Top layer slid %.0f pm: only weak van der Waals forces across the %.0f pm gap (bond %.0f pm) → layers rub off: pencils write").format(slideTotal, cr.c / 2, cr.nnDistance())
            coordOn -> {
                val m = model; val i = m.probes[min(probeK, m.probes.size - 1)]; val sh = cr.shell(m.atoms[i].cls)
                val other = sh.groupBy { cr.elements[it.first].label }.keys.joinToString("/")
                val ion = if (cr.bonding == Bonding.IONIC) tr(" · r⁺/r⁻ = %.2f → 半径比规则预言 %d 配位", " · r⁺/r⁻ = %.2f → radius-ratio rule predicts CN %d").format(
                    cr.radiusRatio(), Crystal.cnFromRadiusRatio(cr.radiusRatio())) else ""
                tr("%s 的最近邻：%d 个 %s，%.1f pm，围成%s（在无限晶格中搜索得到）%s", "%s: %d nearest %s at %.1f pm form a %s (searched in the infinite lattice)%s").format(
                    classLabel(cr, m.atoms[i].cls), sh.size, other, cr.nnDistance(m.atoms[i].cls), polyName(sh.size), ion)
            }
            cellOn -> (if (cr.hex) tr("数晶胞：六方晶胞 60° 棱上的原子只有 ⅙ 在胞内，120° 棱 ⅓，体内 1 → 每个晶胞 %d 个原子",
                "Counting the hexagonal cell: an atom on a 60° edge is ⅙ inside, on a 120° edge ⅓, inside 1 → %d atoms per cell").format(cr.zAtoms)
                else tr("数晶胞：顶点上的原子只有 ⅛ 属于这个晶胞，棱上 ¼，面上 ½，体内 1 → 每个晶胞 %d 个原子%s",
                "Counting the cell: a corner atom is ⅛ inside, edge ¼, face ½, interior 1 → %d atoms per cell%s").format(cr.zAtoms,
                    if (cr.atomsPerFormula > 1) tr("（%d 个 %s）", " (%d %s)").format(cr.zFormula, cr.formula) else ""))
            cr.id == "nacl" -> tr("离子键强但同号相斥：离子层一错开半格，同号离子就正对 → 食盐沿 {100} 面劈开，硬而脆",
                "Strong ionic bonds, but a slip of half a spacing puts like charges face to face → salt splits on {100} planes: brittle")
            cr.id == "cscl" -> tr("Cs⁺ 大：r⁺/r⁻ = %.2f > 0.732，周围放得下 8 个 Cl⁻（NaCl 只有 %.2f，只够 6 个）→ 8:8 配位",
                "Cs⁺ is big: r⁺/r⁻ = %.2f > 0.732 leaves room for 8 Cl⁻ (NaCl's %.2f fits only 6) → 8:8 coordination").format(cr.radiusRatio(), Crystals.NACL.radiusRatio())
            cr.id == "diamond" -> tr("每个 C 用 4 根 %.0f pm 共价键连成三维网络，没有薄弱面 → 自然界最硬（莫氏 10），虽然只填满 %.0f %% 空间",
                "Each C holds 4 covalent bonds (%.0f pm) in a 3-D network with no weak plane → hardest natural material, though only %.0f %% filled").format(cr.nnDistance(), 100 * cr.packingFraction())
            cr.hex -> tr("层内 C–C %.0f pm 是强共价键，层间 %.0f pm 只靠微弱的范德华力 → 层易滑开：铅笔能写字，石墨摸起来滑",
                "Strong C–C bonds (%.0f pm) inside a layer, only weak van der Waals forces across %.0f pm → layers slide: pencils write").format(cr.nnDistance(), cr.c / 2)
            else -> tr("金属键没有方向：原子最密堆积（%.0f %%），原子层滑移后仍被“电子海”粘住 → 铜能拉成丝、敲成片",
                "Metallic bonding has no direction: close-packed (%.0f %%) layers slip and stay bonded → copper draws into wire").format(100 * cr.packingFraction())
        }
    }

    // ================================================================ guided track (导学)
    // KEY INSIGHT: a solid's properties come from HOW its atoms are connected in the lattice, not just from which
    // atoms they are: each Na⁺ sits among 6 Cl⁻ (octahedron, 282 pm); diamond and graphite are the SAME carbon —
    // 4 bonds in 3-D (154 pm, no weak plane: hardest) versus sheets of 3 bonds (142 pm) held 335 pm apart by weak
    // forces (they slide: pencils write).

    private val ready get() = loadT >= 1f
    private fun probeOn(k: Int = 0) = coordOn && probeK == k
    /** Pinch the probe atom unless its shell is already lit (a second pinch would switch it off). */
    private fun lightProbe() { if (!probeOn()) onTap("Probe0") }
    private fun litCn(): Int = if (!coordOn) 0 else crystal.coordination(model.atoms[model.probes[min(probeK, model.probes.size - 1)]].cls)

    // ---- background card (📖): context for a viewer who has never met crystal lattices
    override val about = About(listOf(
        About.Page(
            Bi("这个实验在做什么？", "What is this experiment?"),
            Bi("左边托盘上是真实标本：阶梯状的岩盐立方体（上方有一把劈刀）、一瓶氯化铯、铜块和铜丝、闪光的金刚石、石墨块和铅笔。捏一个标本，它的晶格就“放大 2.6 亿倍”长到展台上，间距和原子大小都按真实数据。捏带光圈的原子，数它有几个邻居；捏劈刀劈开食盐；拖石墨的顶层，看它滑开。", "On the tray on the left are real specimens: a stepped rock-salt cube with a chisel above it, a vial of caesium chloride, a copper block and wire, a sparkling diamond, a graphite lump and a pencil. Pinch one and its lattice grows onto the stand, magnified 260 million times, with true spacings and atom sizes. Pinch a haloed atom to count its neighbours, pinch the chisel to split the salt, drag graphite's top layer to slide it."),
        ),
        About.Page(
            Bi("第一次看清晶体内部", "Seeing inside a crystal"),
            Bi("晶体为什么有平整的面和固定的角？1912 年劳厄发现，X 射线穿过晶体会衍射出规则的斑点。1913 年，布拉格父子用这些斑点第一次“看”清了食盐内部：钠离子和氯离子交替排成立方格子，里面根本没有成对的“NaCl 分子”。X 射线晶体学由此诞生，后来 DNA 双螺旋也是靠 X 射线衍射照片解出来的。", "Why do crystals have flat faces and fixed angles? In 1912 Laue found that X-rays passing through a crystal scatter into a regular pattern of spots. In 1913 the Braggs, father and son, used such spots to see inside table salt for the first time: sodium and chloride ions alternate in a cubic grid, with no separate NaCl molecules at all. X-ray crystallography was born; the DNA double helix was later solved from X-ray photos too."),
        ),
        About.Page(
            Bi("核心想法：排法决定性质", "Key idea: the packing decides"),
            Bi("同样的原子，排法不同，性质就天差地别。金刚石和石墨都是纯碳：金刚石里每个碳向四个方向各连一根键，织成一张没有弱面的立体网，所以最硬。石墨是一层层的蜂窝网，层内很牢，层间只靠弱力，就像一叠扑克牌，一推就滑开，于是铅笔能写字。食盐一劈就裂，铜却能弯，也都是排法决定的。", "Same atoms, different packing, very different behaviour. Diamond and graphite are both pure carbon. In diamond every carbon bonds in four directions, weaving a 3-D net with no weak plane: the hardest material. Graphite is a stack of honeycomb sheets, strong within each sheet but only weakly held together, like a deck of cards that slides at a push: that is how a pencil writes. Salt cleaves and copper bends for the same reason."),
        ),
        About.Page(
            Bi("今天在哪里？", "Where is it today?"),
            Bi("手机芯片里的硅，原子排法和金刚石一样，而且要做成几乎没有缺陷的大单晶。从石墨上剥下的单层就是石墨烯，2010 年获诺贝尔物理学奖。药厂用 X 射线晶体学看清蛋白质的结构来设计新药。铜能拉成导线、敲成薄片，是因为金属原子层可以滑动而不断开。", "The silicon in a phone chip has the same arrangement as diamond and is grown as a huge, nearly flawless single crystal. Peel one sheet off graphite and you have graphene, which won the 2010 Nobel Prize in Physics. Drug makers use X-ray crystallography to see the shapes of proteins and design medicines to fit them. Copper can be drawn into wire because its layers of atoms can slide without breaking apart."),
        ),
    ))

    override val lesson: Lesson by lazy {
        Lesson(
            listOf(
                LessonStep(
                    say = Bi("食盐晶体里，每个 Na⁺ 周围紧贴着几个 Cl⁻？", "In a salt crystal, how many Cl⁻ ions touch each Na⁺?"),
                    options = listOf(
                        Bi("4 个", "4"),
                        Bi("6 个", "6"),
                        Bi("8 个", "8"),
                    ),
                    answer = 1,
                ),
                LessonStep(
                    say = Bi("捏晶格里带光圈的 Na⁺（或面板「Na⁺ → 配位」），数它的邻居。", "Pinch the haloed Na⁺ in the lattice (or “Na⁺ → neighbours” below) and count its neighbours."),
                    target = "Probe0",
                    done = { crystalIdx == 0 && probeOn() && ready },
                    autoplay = { if (crystalIdx != 0) onTap("SpecNaCl"); lightProbe() },
                    reveal = true,
                    insight = Bi("6 个 Cl⁻，相距 282 pm，围成正八面体；Na⁺/Cl⁻ 半径比 0.56 正好放下 6 个。", "6 Cl⁻ at 282 pm form an octahedron; the Na⁺/Cl⁻ radius ratio, 0.56, fits exactly 6."),
                ),
                LessonStep(
                    say = Bi("金刚石最硬，铅笔芯（石墨）软得能写字。它们是什么做的？", "Diamond is the hardest; pencil “lead” (graphite) is soft enough to write. What are they made of?"),
                    options = listOf(
                        Bi("都是碳", "Both carbon"),
                        Bi("笔芯是铅", "Pencil: lead"),
                        Bi("两种元素", "Two elements"),
                    ),
                    answer = 0,
                ),
                LessonStep(
                    say = Bi("捏「金刚石」标本，再捏一个带光圈的 C 原子：它连着几个邻居？", "Pinch the diamond specimen, then a haloed C atom: how many neighbours does it hold?"),
                    target = "SpecDiamond",
                    done = { crystalIdx == 2 && coordOn && ready },
                    autoplay = { onTap("SpecDiamond"); lightProbe() },
                    reveal = true,
                    insight = Bi("都是碳！金刚石里每个 C 用 4 根 154 pm 键连成三维网，没有薄弱面 → 最硬。", "Both carbon! In diamond each C holds 4 bonds (154 pm) in a 3-D net with no weak plane → hardest."),
                ),
                LessonStep(
                    say = Bi("捏「石墨」标本，再捏住顶层左右拖（或面板「顶层 → 滑动」）。", "Pinch the graphite specimen, then pinch-drag its top layer sideways (or “Top layer → slide”)."),
                    target = "SpecGraphite",
                    done = { crystalIdx == 3 && slideTotal > 200f },
                    autoplay = { onTap("SpecGraphite"); onTap("TopLayer") },
                    insight = Bi("层内 142 pm 牢牢相连，层间 335 pm 只靠弱力 → 一层层滑开，铅笔就这样写字。", "Layers are bonded tight (142 pm) but held 335 pm apart by weak forces → they slide off: that's how pencils write."),
                ),
            ),
            challenge = Challenge(
                say = Bi("找出“最挤”的晶体：哪一种里每个原子紧贴 12 个邻居？捏标本，再看配位。", "Find the most crowded crystal: in which does every atom touch 12 neighbours? Pinch specimens, check coordination."),
                progress = {
                    if (!coordOn) tr("先打开配位：捏一个带光圈的原子", "Turn on coordination: pinch a haloed atom")
                    else tr("%s：配位数 %d · 目标 12", "%s: coordination %d · goal 12").format(nm(crystalIdx), litCn())
                },
                done = { ready && litCn() == 12 },
                autoplay = { onTap("SpecCu"); lightProbe() },
            ),
        )
    }

    // ================================================================ interaction

    override val tapTargets: List<TapTarget> get() {
        val out = arrayListOf(
            TapTarget("SpecNaCl", "食盐 → 晶格", "Salt → lattice") { crystalIdx == 0 },
            TapTarget("SpecCsCl", "氯化铯 → 晶格", "CsCl → lattice") { crystalIdx == 1 },
            TapTarget("SpecDiamond", "金刚石 → 晶格", "Diamond → lattice") { crystalIdx == 2 },
            TapTarget("SpecGraphite", "石墨 → 晶格", "Graphite → lattice") { crystalIdx == 3 },
            TapTarget("SpecCu", "铜 → 晶格", "Copper → lattice") { crystalIdx == 4 },
        )
        val cr = crystal
        for (k in 0 until cr.classCount) {
            val l = classLabel(cr, k)
            out += TapTarget("Probe$k", "$l → 配位", "$l → neighbours") { coordOn && probeK == k }
        }
        out += TapTarget("Chisel", "劈刀 → 劈开", "Chisel → cleave") { isCleaved }
        if (cr.hex) out += TapTarget("TopLayer", "顶层 → 滑动", "Top layer → slide")
        out += TapTarget("Trackball", "旋转球 → 摆正", "Trackball → reset")
        return out
    }

    override fun isTarget(name: String) = name.startsWith("Spec") || name.startsWith("Probe") || name == "Chisel" || name == "TopLayer" || name == "Trackball"

    fun selectCrystal(ci: Int) {
        if (crystalIdx != ci) crystalIdx = ci
        cleaveState = 0; cleaveT = 0f; slip = 0f; sep = 0f; cleavePending = false
        slideX = 0f; slideZ = 0f; slideAnimLeft = 0f; slideTotal = 0f
        if (probeK >= Crystals.ALL[ci].classCount) probeK = 0
        val sp = spotPos(ci); loadFrom = P3(sp.x, sp.y + 0.05f, sp.z)
        userRot = viewOf(ci)
        loadT = if (walkIn) 1f else 0f
        shownKey = -1
    }

    override fun onTap(name: String) {
        lastMsg = when {
            name.startsWith("Spec") -> {
                val ci = SPEC_ID.indexOf(name).coerceAtLeast(0)
                selectCrystal(ci); update(0f)
                tr("%s的晶格从标本里长出来：放大 %s", "%s: its lattice grows out of the specimen, magnified %s").format(nm(ci), magnification())
            }
            name.startsWith("Probe") -> {
                val k = name.removePrefix("Probe").toIntOrNull() ?: 0
                val cr = crystal
                if (k >= cr.classCount) tr("这种晶体只有一种格位", "This crystal has only one kind of site")
                else if (coordOn && probeK == k) { coordOn = false; tr("关闭配位显示", "Coordination off") }
                else {
                    if (cleaveState != 0) rejoin()
                    probeK = k; coordOn = true
                    val sh = cr.shell(k)
                    tr("%s：%d 个最近邻（%.1f pm）围成%s", "%s: %d nearest neighbours (%.1f pm) form a %s").format(classLabel(cr, k), sh.size, cr.nnDistance(k), polyName(sh.size))
                }
            }
            name == "Chisel" -> when {
                crystal.id != "nacl" -> { selectCrystal(0); cleavePending = true; tr("先载入食盐，再沿 {100} 面劈开", "Loading salt, then cleaving it along {100}") }
                cleaveState == 0 || cleaveState == 3 -> { startCleave(); tr("劈：离子层错开半格 → 同号离子相斥 → 沿 {100} 面裂开", "Strike: layers slip half a spacing → like ions repel → split on {100}") }
                else -> { rejoin(); tr("合拢（真实晶体劈开后合不回去）", "Put back together (a real crystal would not rejoin)") }
            }
            name == "TopLayer" -> {
                // one lattice constant across the line of sight (whichever in-plane axis faces the viewer more sideways)
                val k = geo.k; val a = model.crystal.a.toFloat() * k
                val wx = userRot.rotate(floatArrayOf(1f, 0f, 0f)); val wz = userRot.rotate(floatArrayOf(0f, 0f, 1f))
                val useX = abs(wx[0]) >= abs(wz[0])
                val sgn = if ((if (useX) wx[0] else wz[0]) >= 0f) 1f else -1f
                val dur = 0.6f
                slideVX = if (useX) sgn * a / dur else 0f; slideVZ = if (useX) 0f else sgn * a / dur
                if (abs(slideX + slideVX * dur) > 2.5f * a || abs(slideZ + slideVZ * dur) > 2.5f * a) { slideVX = -slideVX; slideVZ = -slideVZ }
                slideAnimLeft = dur
                Sfx.play(Sfx.WHOOSH, 0.2f, 1.7f, latt.pos.x / 0.9f)                            // sound: graphite's top layer slides (weak interlayer forces)
                tr("顶层滑过 %.0f pm（一个晶格常数）：层间力很弱；铅笔在纸上留下一道", "Top layer slides %.0f pm (one lattice constant): weak interlayer forces; the pencil leaves a line").format(model.crystal.a)
            }
            name == "Trackball" -> { userRot = viewOf(crystalIdx); fitTarget(); tr("视角摆正", "View reset") }
            else -> ""
        }
    }

    private fun rejoin() { if (cleaveState == 1 || cleaveState == 2) { cleaveState = 3; cleaveT = 0f } }

    override fun tapMessage(name: String): String? = lastMsg.ifEmpty { null }

    override fun isDragTarget(name: String) = name == "Trackball" || name.startsWith("Probe") || name == "TopLayer"
    override fun onDragStart(name: String) { if (name == "TopLayer") sliding = true else dragging = true }
    override fun onDrag(name: String, dx: Float, dy: Float, dz: Float) {
        if (name == "TopLayer") {
            // hand motion -> the layer's plane (lattice local y = c axis), in model metres
            val l = userRot.conj().rotate(floatArrayOf(dx, dy, dz)); val s = max(0.05f, latt.scale.x)
            moveLayer(l[0] / s, l[2] / s)
            return
        }
        userRot = Q.axisAngle(0f, 1f, 0f, dx * DRAG_GAIN) * Q.axisAngle(1f, 0f, 0f, -dy * DRAG_GAIN) * userRot
    }
    override fun onDragEnd(name: String) { if (name == "TopLayer") sliding = false else { dragging = false; fitTarget() } }

    // ================================================================ review states

    private fun settle(frames: Int = 150) { repeat(frames) { update(1f / 60f) } }

    override fun reviewVariants(): Map<String, () -> Unit> = mapOf(
        "space" to { selectCrystal(0); supercell = 2; spaceFill = true; cellOn = false; coordOn = false; loadT = 1f; settle() },
        "cell" to { selectCrystal(0); supercell = 1; spaceFill = true; cellOn = true; coordOn = false; loadT = 1f; settle() },
        "cscl" to { selectCrystal(1); supercell = 2; spaceFill = false; cellOn = false; coordOn = true; probeK = 0; loadT = 1f; settle() },
        "diamond" to { selectCrystal(2); supercell = 2; spaceFill = false; cellOn = false; coordOn = true; probeK = 1; loadT = 1f; settle() },
        "graphite" to { selectCrystal(3); supercell = 2; spaceFill = false; cellOn = false; coordOn = false; loadT = 1f; settle(10); onTap("TopLayer"); settle(60); onTap("TopLayer"); settle(60) },
        "cleave_slip" to { selectCrystal(0); supercell = 2; spaceFill = false; cellOn = false; coordOn = false; loadT = 1f; settle(10); onTap("Chisel"); settle(48) },
        "cleave" to { selectCrystal(0); supercell = 2; spaceFill = false; cellOn = false; coordOn = false; loadT = 1f; settle(10); onTap("Chisel"); settle(130) },
        "cu" to { selectCrystal(4); supercell = 2; spaceFill = true; cellOn = true; coordOn = false; loadT = 1f; settle() },
        "walkin" to { selectCrystal(0); supercell = 2; spaceFill = false; cellOn = false; coordOn = false; walkIn = true; loadT = 1f; settle() },
    )

    // ================================================================ panel

    @Composable
    override fun Panel() {
        LabPanel(readout) {
            LabRow {
                SegmentControl {
                    SegmentItem(selected = !spaceFill, onClick = { spaceFill = false }, title = { Text(tr("球棍模型", "Ball & stick")) })
                    SegmentItem(selected = spaceFill, onClick = { spaceFill = true }, title = { Text(tr("空间填充", "Space-filling")) })
                }
                Spacer(Modifier.width(8.dp))
                SegmentControl {
                    for (k in 1..3) SegmentItem(selected = supercell == k, onClick = { supercell = k }, title = { Text("$k×$k×$k") })
                }
                Spacer(Modifier.width(8.dp))
                SegmentControl {
                    SPEED_LABELS.forEachIndexed { k, l -> SegmentItem(selected = speedIdx == k, onClick = { speedIdx = k }, title = { Text(l) }) }
                }
            }
            LabRow {
                LabSwitch(tr("晶胞", "Unit cell"), cellOn) { cellOn = it; if (it && cleaveState != 0) rejoin() }
                LabSwitch(tr("配位", "Coordination"), coordOn) { coordOn = it; if (it && cleaveState != 0) rejoin() }
                LabSwitch(tr("走进晶格", "Walk in"), walkIn) { walkIn = it; if (!it) { loadT = 1f } }
            }
        }
    }

    // ================================================================ helpers

    private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

    /** Unit cylinder (along +Y) node from a to b (lattice-local coordinates), radius r. */
    private fun placeSeg(n: Node, a: FloatArray, b: FloatArray, r: Float) {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val dz = b[2] - a[2]
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        val p = P3((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2); val q = Q.fromTo(0f, 1f, 0f, dx, dy, dz); val sc = P3(r, l, r)
        if (n.pos != p) n.pos = p; if (n.rot != q) n.rot = q; if (n.scale != sc) n.scale = sc
    }
    private fun placeSegW(n: Node, a: P3, b: P3, r: Float) = placeSeg(n, floatArrayOf(a.x, a.y, a.z), floatArrayOf(b.x, b.y, b.z), r)

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    /** Rotation whose columns are the images of the local x, y, z axes. */
    private fun quatFromBasis(x: FloatArray, y: FloatArray, z: FloatArray): Q {
        val m00 = x[0]; val m01 = y[0]; val m02 = z[0]; val m10 = x[1]; val m11 = y[1]; val m12 = z[1]; val m20 = x[2]; val m21 = y[2]; val m22 = z[2]
        val tr = m00 + m11 + m22
        val q = when {
            tr > 0f -> { val s = sqrt(tr + 1f) * 2f; Q((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s) }
            m00 > m11 && m00 > m22 -> { val s = sqrt(1f + m00 - m11 - m22) * 2f; Q(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s) }
            m11 > m22 -> { val s = sqrt(1f + m11 - m00 - m22) * 2f; Q((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s) }
            else -> { val s = sqrt(1f + m22 - m00 - m11) * 2f; Q((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s) }
        }
        val l = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w).coerceAtLeast(1e-12f)
        return Q(q.x / l, q.y / l, q.z / l, q.w / l)
    }
}
