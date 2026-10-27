package com.bytedance.chemistrylab.core.crystal

import com.bytedance.chemistrylab.demos.crystal.CrystalDemo
import com.bytedance.chemistrylab.lab.spec.Node
import com.bytedance.chemistrylab.lab.spec.SoftRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Device budget of the crystal bench (measured on the Swan: ~20 us per re-uploaded vertex, keep visible nodes
 * < ~600). Worst-case states are built headless and their VISIBLE mesh nodes counted; in steady state (lattice
 * spinning) one frame may only move transforms: no mesh edits, no material swaps.
 */
class CrystalBudgetTest {
    private val cache = File("build/review-cache").apply { mkdirs() }

    private fun visibleMeshNodes(root: Node): Int { var n = 0; SoftRender.forEachMesh(root) { _, _ -> n++ }; return n }

    @Test
    fun worstCaseStatesStayUnder600VisibleNodes() {
        val d = CrystalDemo
        val root = d.runHeadless(cache, 0.5f)
        fun settle(k: Int = 60) = repeat(k) { d.update(1f / 60f) }
        val states = linkedMapOf<String, () -> Unit>(
            "NaCl 3x3x3 ball+stick, coordination + cell" to { d.selectCrystal(0); d.supercell = 3; d.spaceFill = false; d.coordOn = true; d.cellOn = true; settle() },
            "NaCl 3x3x3 cleaving (repulsion glows)" to { d.selectCrystal(0); d.supercell = 3; d.coordOn = false; d.cellOn = false; settle(); d.onTap("Chisel"); settle(66) },
            "NaCl 3x3x3 space-filling" to { d.selectCrystal(0); d.supercell = 3; d.spaceFill = true; settle() },
            "graphite 3 ball+stick + coordination" to { d.selectCrystal(3); d.supercell = 3; d.spaceFill = false; d.coordOn = true; settle() },
            "diamond 3x3x3 coordination + cell" to { d.selectCrystal(2); d.supercell = 3; d.coordOn = true; d.cellOn = true; settle() },
            "Cu 3x3x3 walk-in" to { d.selectCrystal(4); d.supercell = 3; d.coordOn = false; d.cellOn = false; d.walkIn = true; settle() },
        )
        for ((name, apply) in states) {
            apply(); d.ensureHalos()
            val n = visibleMeshNodes(root)
            println("BUDGET %-45s visible mesh nodes %d".format(name, n))
            assertTrue("$name: $n visible nodes", n < 600)
        }
        d.walkIn = false
    }

    @Test
    fun labelsAreShortAndInsideTheVolume() {
        val d = CrystalDemo
        d.runHeadless(cache, 0.5f)
        for (en in listOf(false, true)) {
            com.bytedance.chemistrylab.lab.Lang.set(en)
            for ((name, apply) in d.reviewVariants()) {
                apply(); repeat(6) { d.update(1f / 60f) }
                assertEquals(6, d.labels.size)
                for (l in d.labels.filter { it.visible && it.text.isNotEmpty() }) {
                    if (!en) println("LABEL %-12s %-40s at (%.2f, %.2f, %.2f)".format(name, l.text, l.pos.x, l.pos.y, l.pos.z))
                    assertTrue("$name '${l.text}' at ${l.pos}", kotlin.math.abs(l.pos.x) < 1.0f && l.pos.y in -0.55f..0.55f && kotlin.math.abs(l.pos.z) < 1.0f)
                    assertTrue("$name label too long: '${l.text}'", l.text.length <= 44)
                }
            }
        }
        com.bytedance.chemistrylab.lab.Lang.set(false)
        d.walkIn = false
    }

    @Test
    fun aSteadyFrameOnlyMovesTransforms() {
        val d = CrystalDemo
        val root = d.runHeadless(cache, 1f)
        d.selectCrystal(0); d.supercell = 2; d.speedIdx = 2; d.coordOn = true
        repeat(180) { d.update(1f / 60f) }
        val all = ArrayList<Node>(); root.walk { all += it }
        val tf = all.map { it.tfVersion }; val mesh = all.map { it.meshVersion }; val mat = all.map { it.matVersion }; val vis = all.map { it.visVersion }
        d.update(1f / 60f)
        val dTf = all.indices.count { all[it].tfVersion != tf[it] }
        val dMesh = all.indices.count { all[it].meshVersion != mesh[it] }
        val dMat = all.indices.count { all[it].matVersion != mat[it] }
        val dVis = all.indices.count { all[it].visVersion != vis[it] }
        println("STEADY FRAME (%d nodes in the tree): %d transforms, %d meshes, %d materials, %d visibility changes; moved: %s".format(
            all.size, dTf, dMesh, dMat, dVis, all.indices.filter { all[it].tfVersion != tf[it] }.joinToString { all[it].name }))
        assertEquals(0, dMesh); assertEquals(0, dMat)
        assertTrue("too many transforms per frame: $dTf", dTf <= 16)
    }
}
