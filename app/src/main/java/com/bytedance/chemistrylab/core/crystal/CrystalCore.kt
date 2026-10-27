// CrystalCore — exact crystallography of five classic structures, from real lattice constants.
//
// Everything the demo SHOWS is computed here from the generated lattice, nothing is a lookup:
//   * the conventional unit cell (basis in fractional coordinates) of rock salt NaCl, caesium chloride CsCl,
//     diamond, AB (Bernal) graphite and fcc copper;
//   * nearest-neighbour distance and coordination number of every site, by searching the infinite lattice
//     (all images within +-2 cells) for the closest atoms;
//   * Z (atoms / formula units per cell) by counting the half-open cell, AND by the textbook sharing rule on the
//     closed cell (corner 1/8, edge 1/4, face 1/2 for cubes; 60/120 deg edges 1/6, 1/3 for the hexagonal cell);
//   * density  rho = Z M / (N_A V_cell);  packing fraction  phi = sum (4/3) pi r^3 / V_cell;
//   * the coordination polyhedron (convex hull of the neighbour shell) and the displayed finite model.
//
// DATA (room temperature):
//   lattice constants  NaCl a = 564.02 pm, CsCl a = 412.3 pm, diamond a = 356.71 pm, Cu a = 361.49 pm
//                      (R. W. G. Wyckoff, Crystal Structures vol. 1, 2nd ed., Wiley 1963; CRC Handbook of
//                      Chemistry and Physics, "Lattice constants of the elements"); graphite a = 246.4 pm,
//                      c = 670.8 pm (Trucano & Chen, Nature 258, 136 (1975)).
//   atomic masses      IUPAC 2013 conventional values; N_A exact (SI 2019).
//   measured density   CRC Handbook (97th ed.): NaCl 2.165, CsCl 3.988, diamond 3.515, Cu 8.96 g/cm^3;
//                      graphite single crystal 2.26 g/cm^3 (B. T. Kelly, Physics of Graphite, 1981).
//   ionic radii        R. D. Shannon, Acta Cryst. A32, 751 (1976): Na+ (CN 6) 102 pm, Cs+ (CN 8) 174 pm,
//                      Cl- (CN 6) 181 pm. Covalent / metallic crystals use the contact radius d_nn / 2
//                      (C in diamond 77.2 pm, C in graphite 71.1 pm, Cu 127.8 pm) — the hard-sphere model
//                      that gives the packing fractions pi/(3 sqrt 2) = 0.7405 (fcc) and pi sqrt3 / 16 = 0.3401.
//
// FRAME. Cartesian coordinates are in pm, +Y is the "up" axis of the display: for cubic crystals x, y, z are
// the cube axes; for graphite y is the c axis (layers horizontal), a1 lies along +x and a2 at 120 deg in x-z.

package com.bytedance.chemistrylab.core.crystal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Avogadro constant (exact since the 2019 SI redefinition). */
const val AVOGADRO = 6.02214076e23
private val SQ3 = sqrt(3.0)

/** A chemical species of a crystal. [shannonPm] = effective ionic radius (0 = not an ion). */
class Element(val symbol: String, val label: String, val massGmol: Double, val shannonPm: Double = 0.0, val charge: Int = 0)

enum class Bonding { IONIC, COVALENT, METALLIC }

/** An atom of the conventional cell: element index, site class (symmetry-distinct site), fractional position. */
class BasisAtom(val el: Int, val cls: Int, val f1: Double, val f2: Double, val f3: Double)

class Crystal(
    val id: String,
    val formula: String,
    val hex: Boolean,
    val a: Double,
    val c: Double,
    val elements: List<Element>,
    val basis: List<BasisAtom>,
    val atomsPerFormula: Int,
    val bonding: Bonding,
    /** Measured (macroscopic) density, g/cm^3. */
    val measuredDensity: Double,
    /** Element of each site class (class -> element index). */
    val classElement: IntArray,
) {
    val classCount get() = classElement.size

    /** Fractional -> Cartesian (pm). */
    fun cart(f1: Double, f2: Double, f3: Double): DoubleArray =
        if (!hex) doubleArrayOf(a * f1, a * f2, a * f3)
        else doubleArrayOf(a * (f1 - 0.5 * f2), c * f3, a * f2 * SQ3 / 2)

    /** Cartesian (pm) -> fractional. */
    fun frac(x: Double, y: Double, z: Double): DoubleArray =
        if (!hex) doubleArrayOf(x / a, y / a, z / a)
        else { val f2 = z * 2 / (a * SQ3); doubleArrayOf(x / a + 0.5 * f2, f2, y / c) }

    /** Unit-cell volume, pm^3. */
    val cellVolume: Double get() = if (!hex) a * a * a else SQ3 / 2 * a * a * c

    /** Atoms in one cell, counted in the half-open cell [0,1)^3 of the basis. */
    val zAtoms: Int get() = basis.size
    /** Formula units per cell (NaCl: 8 atoms = 4 NaCl). */
    val zFormula: Int get() = basis.size / atomsPerFormula
    /** Molar mass of one formula unit, g/mol. */
    val formulaMass: Double get() = basis.sumOf { elements[it.el].massGmol } / zFormula

    /** rho = Z M / (N_A V), g/cm^3 (1 pm^3 = 1e-30 cm^3). */
    fun density(): Double = zFormula * formulaMass / (AVOGADRO * cellVolume * 1e-30)

    /** First basis atom of site class [cls]. */
    fun basisOf(cls: Int): BasisAtom = basis.first { it.cls == cls }

    /** All atoms of the infinite crystal within [range] cells of the origin cell (element, pm position). */
    fun images(range: Int = 2): List<Pair<Int, DoubleArray>> {
        val out = ArrayList<Pair<Int, DoubleArray>>()
        for (i in -range..range) for (j in -range..range) for (k in -range..range) for (b in basis)
            out += b.el to cart(b.f1 + i, b.f2 + j, b.f3 + k)
        return out
    }

    /**
     * The first coordination shell of site class [cls], from the infinite lattice: every atom at the minimum
     * distance (within 1 %). Returned as (element, vector from the site, pm).
     */
    fun shell(cls: Int): List<Pair<Int, DoubleArray>> = synchronized(shellCache) { shellCache.getOrPut(cls) { searchShell(cls) } }
    private val shellCache = HashMap<Int, List<Pair<Int, DoubleArray>>>()

    private fun searchShell(cls: Int): List<Pair<Int, DoubleArray>> {
        val b = basisOf(cls); val p = cart(b.f1, b.f2, b.f3)
        val all = images().map { (e, q) -> e to doubleArrayOf(q[0] - p[0], q[1] - p[1], q[2] - p[2]) }
            .filter { len(it.second) > 1e-6 }
        val dmin = all.minOf { len(it.second) }
        return all.filter { len(it.second) <= dmin * 1.01 }
    }

    /** Nearest-neighbour distance from site class [cls], pm. */
    fun nnDistance(cls: Int = 0): Double = shell(cls).minOf { len(it.second) }

    /** Coordination number of site class [cls]. */
    fun coordination(cls: Int = 0): Int = shell(cls).size

    private val radiusCache = HashMap<Int, Double>()

    /** Space-filling radius (pm): Shannon ionic radius for ions, else the contact radius d_nn / 2. */
    fun radius(el: Int): Double = synchronized(radiusCache) {
        radiusCache.getOrPut(el) {
            val e = elements[el]
            if (bonding == Bonding.IONIC && e.shannonPm > 0) e.shannonPm
            else nnDistance(classElement.indexOfFirst { it == el }.coerceAtLeast(0)) / 2
        }
    }

    /** Packing fraction: volume of the spheres in one cell / cell volume. */
    fun packingFraction(): Double = basis.sumOf { 4.0 / 3.0 * PI * cube(radius(it.el)) } / cellVolume

    /** r(cation) / r(anion) for ionic crystals (0 otherwise). */
    fun radiusRatio(): Double {
        if (bonding != Bonding.IONIC) return 0.0
        val cat = elements.first { it.charge > 0 }; val an = elements.first { it.charge < 0 }
        return cat.shannonPm / an.shannonPm
    }

    companion object {
        internal fun len(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        private fun cube(x: Double) = x * x * x

        /** Classic radius-ratio rule (Pauling): the coordination number a cation of that relative size can hold. */
        fun cnFromRadiusRatio(r: Double): Int = when {
            r < 0.155 -> 2; r < 0.225 -> 3; r < 0.414 -> 4; r < 0.732 -> 6; r < 1.0 -> 8; else -> 12
        }
    }
}

/** The five crystals of the demo. */
object Crystals {
    private val NA = Element("Na", "Na⁺", 22.98977, shannonPm = 102.0, charge = 1)
    private val CL = Element("Cl", "Cl⁻", 35.453, shannonPm = 181.0, charge = -1)
    private val CS = Element("Cs", "Cs⁺", 132.90545, shannonPm = 174.0, charge = 1)
    private val C = Element("C", "C", 12.011)
    private val CU = Element("Cu", "Cu", 63.546)

    private val FCC = listOf(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.5, 0.5, 0.0), doubleArrayOf(0.5, 0.0, 0.5), doubleArrayOf(0.0, 0.5, 0.5))

    /** Rock salt: Cl- on an fcc lattice (corners + faces), Na+ in every octahedral hole (edges + body centre). */
    val NACL = Crystal("nacl", "NaCl", false, 564.02, 564.02, listOf(NA, CL),
        FCC.map { BasisAtom(1, 1, it[0], it[1], it[2]) } + FCC.map { BasisAtom(0, 0, (it[0] + 0.5) % 1.0, it[1], it[2]) },
        2, Bonding.IONIC, 2.165, intArrayOf(0, 1))

    /** Caesium chloride: Cl- at the cube corners, Cs+ at the body centre (two interpenetrating simple cubes). */
    val CSCL = Crystal("cscl", "CsCl", false, 412.3, 412.3, listOf(CS, CL),
        listOf(BasisAtom(1, 1, 0.0, 0.0, 0.0), BasisAtom(0, 0, 0.5, 0.5, 0.5)),
        2, Bonding.IONIC, 3.988, intArrayOf(0, 1))

    /** Diamond: fcc (class 0) + the same fcc shifted by (1/4,1/4,1/4) (class 1): every atom tetrahedral. */
    val DIAMOND = Crystal("diamond", "C", false, 356.71, 356.71, listOf(C),
        FCC.map { BasisAtom(0, 0, it[0], it[1], it[2]) } + FCC.map { BasisAtom(0, 1, it[0] + 0.25, it[1] + 0.25, it[2] + 0.25) },
        1, Bonding.COVALENT, 3.515, intArrayOf(0, 0))

    /**
     * Graphite, P6_3/mmc: layers at z = 1/4 (A) and 3/4 (B). Class 0 (alpha) = (0,0): an atom directly above and
     * below in the next layers; class 1 (beta) = (1/3,2/3) in A and (2/3,1/3) in B: above a hexagon centre.
     */
    val GRAPHITE = Crystal("graphite", "C", true, 246.4, 670.8, listOf(C),
        listOf(BasisAtom(0, 0, 0.0, 0.0, 0.25), BasisAtom(0, 1, 1.0 / 3, 2.0 / 3, 0.25),
            BasisAtom(0, 0, 0.0, 0.0, 0.75), BasisAtom(0, 1, 2.0 / 3, 1.0 / 3, 0.75)),
        1, Bonding.COVALENT, 2.26, intArrayOf(0, 0))

    /** Copper: face-centred cubic, cubic close packing. */
    val CU_FCC = Crystal("cu", "Cu", false, 361.49, 361.49, listOf(CU),
        FCC.map { BasisAtom(0, 0, it[0], it[1], it[2]) }, 1, Bonding.METALLIC, 8.96, intArrayOf(0))

    val ALL = listOf(NACL, CSCL, DIAMOND, GRAPHITE, CU_FCC)
}

// ====================================================================== coordination polyhedron

/** Convex hull of a coordination shell: polygonal faces (vertex index loops, CCW seen from outside) and edges. */
class Polyhedron(val faces: List<IntArray>, val edges: List<IntArray>) {
    val vertexCount: Int get() = faces.flatMap { it.toList() }.distinct().size

    companion object {
        /**
         * Hull of points [p] around the origin (the central atom). Brute force over all triples (shells have
         * <= 12 points): a triple spans a face plane if every point lies on one side; coplanar points of that plane
         * form one polygon (squares of the cuboctahedron stay squares). A flat shell (graphite: 3 neighbours in the
         * plane of the atom) gives a single polygon.
         */
        fun hull(p: List<DoubleArray>): Polyhedron {
            val n = p.size
            if (n < 3) return Polyhedron(emptyList(), emptyList())
            val s = p.maxOf { Crystal.len(it) }
            val q = p.map { doubleArrayOf(it[0] / s, it[1] / s, it[2] / s) }
            val eps = 1e-6
            // flat shell (all points in one plane through the origin)
            var nrm: DoubleArray? = null
            loop@ for (i in 0 until n) for (j in i + 1 until n) {
                val c = cross(q[i], q[j]); if (Crystal.len(c) > 1e-3) { nrm = c; break@loop }
            }
            if (nrm != null && q.all { abs(dot(it, nrm)) < 1e-6 * Crystal.len(nrm) }) {
                val loop = sortedLoop((0 until n).toList(), q, norm(nrm))
                return Polyhedron(listOf(loop), edgesOf(listOf(loop)))
            }
            val planes = ArrayList<DoubleArray>()          // (nx, ny, nz, d), outward unit normal
            for (i in 0 until n) for (j in i + 1 until n) for (k in j + 1 until n) {
                var c = cross(sub(q[j], q[i]), sub(q[k], q[i]))
                val l = Crystal.len(c); if (l < 1e-9) continue
                c = doubleArrayOf(c[0] / l, c[1] / l, c[2] / l)
                var d = dot(c, q[i])
                var pos = 0; var neg = 0
                for (m in 0 until n) { val sd = dot(c, q[m]) - d; if (sd > eps) pos++ else if (sd < -eps) neg++ }
                if (pos > 0 && neg > 0) continue
                if (pos > 0) { c = doubleArrayOf(-c[0], -c[1], -c[2]); d = -d }
                if (planes.none { abs(it[0] - c[0]) < 1e-6 && abs(it[1] - c[1]) < 1e-6 && abs(it[2] - c[2]) < 1e-6 && abs(it[3] - d) < 1e-6 })
                    planes += doubleArrayOf(c[0], c[1], c[2], d)
            }
            val faces = planes.map { pl ->
                val on = (0 until n).filter { abs(dot(pl, q[it]) - pl[3]) < 1e-6 }
                sortedLoop(on, q, doubleArrayOf(pl[0], pl[1], pl[2]))
            }
            return Polyhedron(faces, edgesOf(faces))
        }

        /** Indices [idx] ordered counter-clockwise around [axis] (seen from the tip of the axis). */
        private fun sortedLoop(idx: List<Int>, q: List<DoubleArray>, axis: DoubleArray): IntArray {
            val cx = idx.sumOf { q[it][0] } / idx.size; val cy = idx.sumOf { q[it][1] } / idx.size; val cz = idx.sumOf { q[it][2] } / idx.size
            val u = norm(if (abs(axis[0]) < 0.9) cross(axis, doubleArrayOf(1.0, 0.0, 0.0)) else cross(axis, doubleArrayOf(0.0, 1.0, 0.0)))
            val v = cross(axis, u)
            return idx.sortedBy { i -> val d = doubleArrayOf(q[i][0] - cx, q[i][1] - cy, q[i][2] - cz); atan2(dot(d, v), dot(d, u)) }.toIntArray()
        }

        private fun edgesOf(faces: List<IntArray>): List<IntArray> {
            val out = LinkedHashSet<Long>()
            for (f in faces) for (k in f.indices) {
                val a = f[k]; val b = f[(k + 1) % f.size]
                if (f.size == 2 && k == 1) continue
                out += min(a, b).toLong() * 1000 + max(a, b)
            }
            return out.map { intArrayOf((it / 1000).toInt(), (it % 1000).toInt()) }
        }

        internal fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        internal fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
        internal fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        internal fun norm(a: DoubleArray): DoubleArray { val l = max(Crystal.len(a), 1e-300); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
    }
}

// ====================================================================== the displayed finite model

/** How much of an atom lies inside the highlighted unit cell, and how its cut-out is oriented. */
enum class Cut { FULL, HALF, QUARTER, EIGHTH, WEDGE60, WEDGE120 }

/**
 * A cell member: atom [atom], [fraction] of it inside the cell, the cut-out shape and its orientation
 * ([dirs] = inward cube axes as signed axis codes +-1..3 for cubic cells; for the hexagonal cell [startDeg] =
 * azimuth (from +x toward +z) where the wedge begins, or the inward face normal's azimuth for a HALF).
 */
class CellMember(val atom: Int, val fraction: Double, val cut: Cut, val dirs: IntArray, val startDeg: Double = 0.0)

class ModelAtom(val el: Int, val cls: Int, val f: DoubleArray, val p: DoubleArray, val layer: Int)

/**
 * The finite piece of crystal that is displayed: [n] = 1..3 ("supercell" size). Cubic crystals are closed
 * n x n x n blocks of conventional cells (boundary atoms included, as in every textbook picture); graphite is a
 * round flake of (n + 2) layers. Positions [ModelAtom.p] are in pm, centred on the model.
 */
class Model(val crystal: Crystal, val n: Int) {
    val atoms: List<ModelAtom>
    /** Atom index pairs at the nearest-neighbour distance (bonds of the ball-and-stick picture). */
    val bonds: IntArray
    /** The part that moves: NaCl's right-hand half when it is cleaved, graphite's top layer when it slides. */
    val partB: BooleanArray
    /** Unit-cell corners (8, pm): bottom face 0..3 then top face 4..7, in loop order. */
    val cellCorners: List<DoubleArray>
    val cellMembers: List<CellMember>
    /** One probe atom per site class (whose coordination the user can light up). */
    val probes: IntArray
    /** Cleave plane (NaCl): x of the plane between the two halves (pm, model frame). */
    val cleaveX: Double
    /** Largest |p| + space-filling radius (pm): the model's bounding sphere. */
    val boundRadius: Double
    val centre: DoubleArray

    init {
        val cr = crystal
        val raw = ArrayList<ModelAtom>()
        val eps = 1e-6
        if (!cr.hex) {
            centre = cr.cart(n / 2.0, n / 2.0, n / 2.0)
            for (i in -1..n) for (j in -1..n) for (k in -1..n) for (b in cr.basis) {
                val f = doubleArrayOf(b.f1 + i, b.f2 + j, b.f3 + k)
                if (f.any { it < -eps || it > n + eps }) continue
                val c = cr.cart(f[0], f[1], f[2])
                raw += ModelAtom(b.el, b.cls, f, doubleArrayOf(c[0] - centre[0], c[1] - centre[1], c[2] - centre[2]), 0)
            }
        } else {
            val layers = n + 2
            val rFlake = (1.25 + 0.6 * n) * cr.a
            val ax = cr.cart(0.5, 0.5, 0.0)                     // flake axis through the highlighted cell's centre
            val yMid = (layers - 1) * cr.c / 4 + cr.c / 4         // layer l sits at c (l/2 + 1/4)
            centre = doubleArrayOf(ax[0], yMid, ax[2])
            val m = (rFlake / cr.a).toInt() + 3
            for (l in 0 until layers) {
                val k = l / 2; val top = l % 2 == 1
                for (b in cr.basis) {
                    if ((b.f3 > 0.5) != top) continue
                    for (i in -m..m) for (j in -m..m) {
                        val f = doubleArrayOf(b.f1 + i, b.f2 + j, b.f3 + k)
                        val c = cr.cart(f[0], f[1], f[2])
                        val dx = c[0] - ax[0]; val dz = c[2] - ax[2]
                        if (dx * dx + dz * dz > rFlake * rFlake) continue
                        raw += ModelAtom(b.el, b.cls, f, doubleArrayOf(c[0] - centre[0], c[1] - centre[1], c[2] - centre[2]), l)
                    }
                }
            }
            // prune dangling atoms (fewer than 2 in-plane neighbours) until the flake edge is clean
            val dnn = cr.nnDistance(0)
            while (true) {
                val keep = raw.filter { a -> raw.count { b -> b !== a && b.layer == a.layer && dist(a.p, b.p) < dnn * 1.02 } >= 2 }
                if (keep.size == raw.size) break
                raw.clear(); raw.addAll(keep)
            }
        }
        if (!cr.hex && cr.bonding == Bonding.COVALENT) {
            // diamond: a closed block has corner / edge atoms whose 4 bonds all point OUT of the block. They would
            // float unconnected; keep them only where the highlighted cell needs them for the count.
            val lo = cellLo(n); val dnn = cr.nnDistance(0)
            fun inCell(a: ModelAtom) = (0 until 3).all { val u = a.f[it] - lo[it]; u > -eps && u < 1 + eps }
            val keep = raw.filter { a -> inCell(a) || raw.any { b -> b !== a && dist(a.p, b.p) < dnn * 1.02 } }
            raw.clear(); raw.addAll(keep)
        }
        atoms = raw
        val na = atoms.size

        // bonds: every pair at the nearest-neighbour distance
        val dnn = cr.nnDistance(0)
        val bl = ArrayList<Int>()
        for (i in 0 until na) for (j in i + 1 until na) if (dist(atoms[i].p, atoms[j].p) < dnn * 1.02) { bl += i; bl += j }
        bonds = bl.toIntArray()

        // the moving part
        partB = BooleanArray(na)
        var cx = 0.0
        when {
            cr.id == "nacl" -> {
                val cutF = floor(n / 2.0 * 2) / 2 + 0.2           // between two (100) layers near the middle
                for (i in 0 until na) partB[i] = atoms[i].f[0] > cutF
                val xa = atoms.filter { it.f[0] <= cutF }.maxOf { it.p[0] }; val xb = atoms.filter { it.f[0] > cutF }.minOf { it.p[0] }
                cx = (xa + xb) / 2
            }
            cr.hex -> { val top = atoms.maxOf { it.layer }; for (i in 0 until na) partB[i] = atoms[i].layer == top }
        }
        cleaveX = cx

        // the highlighted unit cell
        val members = ArrayList<CellMember>()
        if (!cr.hex) {
            val lo = cellLo(n)
            val corners01 = listOf(intArrayOf(0, 0, 0), intArrayOf(1, 0, 0), intArrayOf(1, 0, 1), intArrayOf(0, 0, 1),
                intArrayOf(0, 1, 0), intArrayOf(1, 1, 0), intArrayOf(1, 1, 1), intArrayOf(0, 1, 1))
            cellCorners = corners01.map { c -> val q = cr.cart((lo[0] + c[0]).toDouble(), (lo[1] + c[1]).toDouble(), (lo[2] + c[2]).toDouble())
                doubleArrayOf(q[0] - centre[0], q[1] - centre[1], q[2] - centre[2]) }
            for ((idx, at) in atoms.withIndex()) {
                val dirs = ArrayList<Int>(); var inside = true
                for (ax in 0 until 3) {
                    val u = at.f[ax] - lo[ax]
                    if (u < -eps || u > 1 + eps) { inside = false; break }
                    if (abs(u) < eps) dirs += ax + 1 else if (abs(u - 1) < eps) dirs += -(ax + 1)
                }
                if (!inside) continue
                val cut = when (dirs.size) { 0 -> Cut.FULL; 1 -> Cut.HALF; 2 -> Cut.QUARTER; else -> Cut.EIGHTH }
                members += CellMember(idx, 1.0 / (1 shl dirs.size), cut, dirs.toIntArray())
            }
        } else {
            val layers = atoms.maxOf { it.layer } + 1
            val kc = (layers - 2) / 2                            // cell layers 2kc, 2kc+1: never the top layer
            val corners01 = listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(0.0, 1.0))
            cellCorners = (0..1).flatMap { h -> corners01.map { c -> val q = cr.cart(c[0], c[1], (kc + h).toDouble())
                doubleArrayOf(q[0] - centre[0], q[1] - centre[1], q[2] - centre[2]) } }
            for ((idx, at) in atoms.withIndex()) {
                val u = at.f[0]; val v = at.f[1]; val w = at.f[2] - kc
                if (u < -eps || u > 1 + eps || v < -eps || v > 1 + eps || w < -eps || w > 1 + eps) continue
                val onU = abs(u) < eps || abs(u - 1) < eps; val onV = abs(v) < eps || abs(v - 1) < eps
                val m = when {
                    onU && onV -> {
                        // a vertical edge of the 60/120 deg prism: the wedge between the two cell edges meeting there
                        val (start, span) = when {
                            abs(u) < eps && abs(v) < eps -> 0.0 to 120.0
                            abs(u - 1) < eps && abs(v) < eps -> 120.0 to 60.0
                            abs(u - 1) < eps -> 180.0 to 120.0
                            else -> 300.0 to 60.0
                        }
                        CellMember(idx, span / 360.0, if (span > 90) Cut.WEDGE120 else Cut.WEDGE60, IntArray(0), start)
                    }
                    onU || onV -> {
                        // a vertical face: half, inward normal in the plane
                        val nAz = when { abs(u) < eps -> 30.0; abs(u - 1) < eps -> 210.0; abs(v) < eps -> 90.0; else -> 270.0 }
                        CellMember(idx, 0.5, Cut.HALF, IntArray(0), nAz)
                    }
                    else -> CellMember(idx, 1.0, Cut.FULL, IntArray(0))
                }
                members += m
            }
        }
        cellMembers = members

        // probes: per class, the atom (not in the moving part) with the most neighbours present in the model,
        // nearest to a point a little front-left-up of the centre (stable, visible choice)
        val counts = IntArray(na)
        for (k in bonds.indices step 2) { counts[bonds[k]]++; counts[bonds[k + 1]]++ }
        val target = listOf(doubleArrayOf(-0.10, 0.08, 0.12), doubleArrayOf(-0.30, 0.25, 0.35)).map { o -> DoubleArray(3) { o[it] * cr.a } }
        probes = IntArray(cr.classCount) { cls ->
            val t = target[min(cls, 1)]
            (0 until na).filter { atoms[it].cls == cls && !partB[it] }
                .minWith(compareBy<Int>({ -counts[it] }, { dist(atoms[it].p, t) }))
        }
        boundRadius = atoms.maxOf { Crystal.len(it.p) + cr.radius(it.el) }
    }

    /** Neighbours of model atom [i] in the INFINITE crystal (vectors, pm): atom i is a lattice translate of its
     *  class's basis atom, so the shell vectors are the same. */
    fun shellOf(i: Int): List<Pair<Int, DoubleArray>> = crystal.shell(atoms[i].cls)

    /** Sum of cell-member fractions = Z (atoms per cell), by the sharing rule. */
    fun zBySharing(): Double = cellMembers.sumOf { it.fraction }

    /** Per class: (count of members by cut kind) — for the "8 x 1/8 + 6 x 1/2" text. */
    fun sharingTerms(cls: Int): List<Pair<Int, Double>> =
        cellMembers.filter { atoms[it.atom].cls == cls }.groupBy { it.fraction }.map { (f, l) -> l.size to f }.sortedBy { it.second }

    /**
     * NaCl cleave: after the moving half slips by one ion spacing (a/2) along +y, which atom of the static half
     * does each facing atom of the moving half sit against? Pairs (moving atom, static atom).
     */
    fun slipPairs(slipFrac: Double = 0.5): List<Pair<Int, Int>> {
        if (crystal.id != "nacl") return emptyList()
        val fB = atoms.indices.filter { partB[it] }.minOf { atoms[it].f[0] }
        val fA = atoms.indices.filter { !partB[it] }.maxOf { atoms[it].f[0] }
        val out = ArrayList<Pair<Int, Int>>()
        for (i in atoms.indices) {
            if (!partB[i] || abs(atoms[i].f[0] - fB) > 1e-6) continue
            val f = atoms[i].f
            val j = atoms.indices.firstOrNull { !partB[it] && abs(atoms[it].f[0] - fA) < 1e-6 &&
                abs(atoms[it].f[1] - (f[1] + slipFrac)) < 1e-6 && abs(atoms[it].f[2] - f[2]) < 1e-6 } ?: continue
            out += i to j
        }
        return out
    }

    companion object {
        /** Lower corner (in cells) of the highlighted cubic cell: the central cell, or front-top-left for n = 2. */
        fun cellLo(n: Int) = intArrayOf((n - 1) / 2, n / 2, n / 2)
        fun dist(a: DoubleArray, b: DoubleArray): Double { val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]; return sqrt(dx * dx + dy * dy + dz * dz) }
    }
}
