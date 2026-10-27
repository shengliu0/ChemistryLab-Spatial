package com.bytedance.chemistrylab.core.crystal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The crystallography the demo shows, checked against closed forms and measured data. Every number below is
 * COMPUTED from the generated lattice (basis + real lattice constants), never typed in.
 */
class CrystalCoreTest {
    private val nacl = Crystals.NACL
    private val cscl = Crystals.CSCL
    private val dia = Crystals.DIAMOND
    private val gra = Crystals.GRAPHITE
    private val cu = Crystals.CU_FCC

    @Test
    fun densityFromTheUnitCellMatchesMeasurement() {
        // rho = Z M / (N_A V). Measured: CRC Handbook (NaCl 2.165, CsCl 3.988, diamond 3.515, Cu 8.96), graphite 2.26
        // (single crystal, Kelly 1981). Expected closed forms, e.g. NaCl 4 x 58.443 / (6.02214e23 x (564.02e-10 cm)^3).
        val closed = mapOf(
            "nacl" to 4 * 58.44277 / (AVOGADRO * Math.pow(564.02e-10, 3.0)),
            "cscl" to 1 * 168.35845 / (AVOGADRO * Math.pow(412.3e-10, 3.0)),
            "diamond" to 8 * 12.011 / (AVOGADRO * Math.pow(356.71e-10, 3.0)),
            "graphite" to 4 * 12.011 / (AVOGADRO * sqrt(3.0) / 2 * Math.pow(246.4e-10, 2.0) * 670.8e-10),
            "cu" to 4 * 63.546 / (AVOGADRO * Math.pow(361.49e-10, 3.0)))
        for (c in Crystals.ALL) {
            val rho = c.density()
            val dev = (rho - c.measuredDensity) / c.measuredDensity
            println("DENSITY %-8s Z=%d (%d atoms) M=%.3f g/mol V=%.4e pm³ -> ρ = %.4f g/cm³, measured %.3f (%+.2f %%)".format(
                c.id, c.zFormula, c.zAtoms, c.formulaMass, c.cellVolume, rho, c.measuredDensity, 100 * dev))
            assertEquals(closed.getValue(c.id), rho, 1e-9 * rho)
            assertTrue("${c.id} density off by ${100 * dev} %", abs(dev) < 0.01)
        }
        // the numbers quoted in the brief
        assertEquals(2.165, nacl.density(), 0.005)
        assertEquals(3.51, dia.density(), 0.01)
        assertEquals(8.93, cu.density(), 0.01)
        assertEquals(2.26, gra.density(), 0.01)
    }

    @Test
    fun zPerCellByCountingAndBySharing() {
        // Z from the half-open cell, and from the closed cell with the corner/edge/face sharing rule (what the
        // unit-cell highlight shows: 8 x 1/8 + 6 x 1/2 = 4 ...). Both must agree for every crystal and every model size.
        val want = mapOf("nacl" to (4 to 8), "cscl" to (1 to 2), "diamond" to (8 to 8), "graphite" to (4 to 4), "cu" to (4 to 4))
        for (c in Crystals.ALL) {
            val (zf, za) = want.getValue(c.id)
            assertEquals(zf, c.zFormula); assertEquals(za, c.zAtoms)
            for (n in 1..3) {
                val m = Model(c, n)
                val s = m.zBySharing()
                if (n == 2) println("Z %-8s %d formula units = %d atoms; sharing rule on the closed cell: %s".format(c.id, zf, za,
                    (0 until c.classCount).joinToString(" | ") { k -> m.sharingTerms(k).joinToString(" + ") { (cnt, f) -> "$cnt×%.4g".format(f) } } + " = %.3f".format(s)))
                assertEquals("${c.id} n=$n", za.toDouble(), s, 1e-9)
            }
        }
        // the textbook decompositions
        val m = Model(nacl, 1)
        assertEquals(listOf(8 to 0.125, 6 to 0.5), m.sharingTerms(1))     // Cl-: corners + faces
        assertEquals(listOf(12 to 0.25, 1 to 1.0), m.sharingTerms(0))     // Na+: edges + body centre
        // hexagonal prism: the 60° vertical edges hold 1/6 of an atom, the 120° edges 1/3 (two layers each)
        val g = Model(gra, 2)
        assertEquals(listOf(4 to 1.0 / 6, 4 to 1.0 / 3), g.sharingTerms(0))   // alpha atoms on the edges: 2 atoms
        assertEquals(listOf(2 to 1.0), g.sharingTerms(1))                     // beta atoms inside: 2 atoms
    }

    @Test
    fun coordinationNumbersFromTheGeneratedLattice() {
        val want = mapOf("nacl" to listOf(6, 6), "cscl" to listOf(8, 8), "diamond" to listOf(4, 4), "graphite" to listOf(3, 3), "cu" to listOf(12))
        for (c in Crystals.ALL) {
            val cn = (0 until c.classCount).map { c.coordination(it) }
            println("CN %-8s %s (nearest neighbours: %s)".format(c.id, cn, (0 until c.classCount).joinToString { k ->
                c.shell(k).groupBy { c.elements[it.first].label }.map { (e, l) -> "${l.size} $e" }.joinToString("+") }))
            assertEquals(c.id, want.getValue(c.id), cn)
        }
        // ionic: the nearest neighbours are all of the OTHER ion
        for (c in listOf(nacl, cscl)) for (k in 0..1) assertTrue(c.shell(k).all { it.first != c.classElement[k] })
    }

    @Test
    fun nearestNeighbourDistancesAreTheClosedForms() {
        val cases = listOf(
            Triple(nacl, nacl.a / 2, 282.0), Triple(cscl, cscl.a * sqrt(3.0) / 2, 357.1),
            Triple(dia, dia.a * sqrt(3.0) / 4, 154.4), Triple(gra, gra.a / sqrt(3.0), 142.3), Triple(cu, cu.a / sqrt(2.0), 255.6))
        for ((c, exact, quoted) in cases) {
            val d = c.nnDistance(0)
            println("NN %-8s d = %.3f pm (closed form %.3f, quoted %.1f)".format(c.id, d, exact, quoted))
            assertEquals(exact, d, 1e-9)
            assertEquals(quoted, d, 0.1)          // the brief quotes rounded values
            for (k in 0 until c.classCount) assertEquals(exact, c.nnDistance(k), 1e-9)
        }
        // graphite: the layers are c/2 = 335.4 pm apart, 2.36 x the in-plane bond
        val ys = Model(gra, 2).atoms.map { it.p[1] }.distinct().sorted()
        val gap = ys[1] - ys[0]
        println("graphite interlayer spacing %.1f pm = %.2f × the C–C bond".format(gap, gap / gra.nnDistance()))
        assertEquals(335.4, gap, 1e-6)
    }

    @Test
    fun packingFractions() {
        val fcc = PI / (3 * sqrt(2.0)); val diamond = PI * sqrt(3.0) / 16
        for (c in Crystals.ALL) println("PACKING %-8s φ = %.4f (radii %s pm)".format(c.id, c.packingFraction(),
            c.elements.indices.joinToString { "${c.elements[it].label} %.1f".format(c.radius(it)) }))
        assertEquals(fcc, cu.packingFraction(), 1e-12); assertEquals(0.7405, cu.packingFraction(), 5e-5)
        assertEquals(diamond, dia.packingFraction(), 1e-12); assertEquals(0.3401, dia.packingFraction(), 5e-5)
        // ionic radii (Shannon 1976) are consistent with the lattice: r+ + r- = d_nn within 1 %
        for (c in listOf(nacl, cscl)) {
            val sum = c.elements.sumOf { it.shannonPm }
            println("IONIC %s r+ + r- = %.0f pm vs d_nn %.1f pm (%+.2f %%)".format(c.id, sum, c.nnDistance(), 100 * (sum / c.nnDistance() - 1)))
            assertEquals(1.0, sum / c.nnDistance(), 0.01)
        }
        assertEquals(0.653, nacl.packingFraction(), 0.001)
        assertEquals(0.669, cscl.packingFraction(), 0.001)
        assertEquals(0.171, gra.packingFraction(), 0.001)
    }

    @Test
    fun radiusRatioPredictsTheCoordination() {
        for (c in listOf(nacl, cscl)) {
            val r = c.radiusRatio(); val cn = Crystal.cnFromRadiusRatio(r)
            println("RADIUS RATIO %s r+/r- = %.3f -> CN %d (lattice: %d)".format(c.id, r, cn, c.coordination(0)))
            assertEquals(c.coordination(0), cn)
        }
    }

    @Test
    fun coordinationPolyhedra() {
        // octahedron, cube, tetrahedron, triangle, cuboctahedron: V, E, F and Euler V - E + F = 2 for solids
        val want = mapOf("nacl" to Triple(6, 12, 8), "cscl" to Triple(8, 12, 6), "diamond" to Triple(4, 6, 4),
            "graphite" to Triple(3, 3, 1), "cu" to Triple(12, 24, 14))
        for (c in Crystals.ALL) for (k in 0 until c.classCount) {
            val p = Polyhedron.hull(c.shell(k).map { it.second })
            val v = p.vertexCount; val e = p.edges.size; val f = p.faces.size
            if (k == 0) println("POLY %-8s V=%d E=%d F=%d faces %s".format(c.id, v, e, f, p.faces.groupBy { it.size }.map { (s, l) -> "${l.size}×${s}-gon" }))
            assertEquals("${c.id}/$k", want.getValue(c.id), Triple(v, e, f))
            if (f > 1) assertEquals(2, v - e + f)
        }
    }

    @Test
    fun displayedModelsStayWithinTheNodeBudget() {
        // closed n x n x n blocks: NaCl (2n+1)^3 ions, 3*2n*(2n+1)^2 bonds; CsCl (n+1)^3 + n^3; fcc (n+1)^3 + 3n^2(n+1)
        for (n in 1..3) {
            val m = Model(nacl, n)
            assertEquals((2 * n + 1).let { it * it * it }, m.atoms.size)
            assertEquals(3 * 2 * n * (2 * n + 1) * (2 * n + 1), m.bonds.size / 2)
            assertEquals((n + 1) * (n + 1) * (n + 1) + n * n * n, Model(cscl, n).atoms.size)
            assertEquals((n + 1) * (n + 1) * (n + 1) + 3 * n * n * (n + 1), Model(cu, n).atoms.size)
            if (n == 1) assertEquals(18, Model(dia, 1).atoms.size)           // the textbook diamond cell: 8 + 6 + 4
            // diamond: every B atom keeps its 4 bonds (16 n^3); unbonded block corners are dropped except in the cell
            val d = Model(dia, n)
            assertEquals(16 * n * n * n, d.bonds.size / 2)
            val bonded = BooleanArray(d.atoms.size).also { b -> for (x in d.bonds) b[x] = true }
            val inCell = d.cellMembers.map { it.atom }.toSet()
            assertTrue(d.atoms.indices.all { bonded[it] || it in inCell })
            assertEquals(4 * n * n * n, d.atoms.count { it.cls == 1 })
        }
        for (c in Crystals.ALL) for (n in 1..3) {
            val m = Model(c, n)
            val moving = m.partB.count { it }
            println("MODEL %-8s n=%d: %3d atoms, %3d bonds, moving part %3d, cell members %2d, probes %s".format(c.id, n, m.atoms.size, m.bonds.size / 2,
                moving, m.cellMembers.size, m.probes.joinToString { i -> "${c.elements[m.atoms[i].el].label}(%s)".format(m.atoms[i].f.joinToString(",") { "%.3g".format(it) }) }))
            assertTrue("${c.id} n=$n: ${m.atoms.size} atoms", m.atoms.size <= 343)
            assertTrue(moving <= 160)
            for (p in m.probes) assertTrue(!m.partB[p])
            // the probe's shell (infinite crystal) is the class's coordination
            for ((k, p) in m.probes.withIndex()) assertEquals(c.coordination(k), m.shellOf(p).size)
        }
        // graphite flakes have no dangling atoms: every atom has >= 2 in-plane bonds
        for (n in 1..3) {
            val m = Model(gra, n); val cnt = IntArray(m.atoms.size)
            for (k in m.bonds.indices step 2) { cnt[m.bonds[k]]++; cnt[m.bonds[k + 1]]++ }
            assertTrue(cnt.all { it >= 2 })
        }
    }

    @Test
    fun saltCleavesBecauseASlippedLayerFacesLikeCharges() {
        // Across the {100} cleave plane every ion faces an ion of OPPOSITE charge; slip the half by one ion spacing
        // (a/2) and every facing pair is LIKE-charged: the repulsion that makes salt brittle and split on a plane.
        for (n in 1..3) {
            val m = Model(nacl, n)
            val before = m.slipPairs(0.0); val after = m.slipPairs(0.5)
            val unlike = before.count { (i, j) -> m.atoms[i].el != m.atoms[j].el }
            val like = after.count { (i, j) -> m.atoms[i].el == m.atoms[j].el }
            println("CLEAVE n=$n: facing pairs before slip %d (unlike %d), after a/2 slip %d (like %d), plane at x = %.1f pm".format(
                before.size, unlike, after.size, like, m.cleaveX))
            assertEquals(before.size, unlike); assertEquals(after.size, like)
            assertEquals((2 * n + 1) * (2 * n + 1), before.size)
            assertEquals(2 * n * (2 * n + 1), after.size)
            // the plane lies half an ion spacing (a/4 = 141 pm) from the ions on either side
            val xs = m.atoms.map { it.p[0] }
            assertEquals(nacl.a / 4, xs.filter { it > m.cleaveX }.minOf { it - m.cleaveX }, 1e-9)
        }
    }
}
