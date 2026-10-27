package com.bytedance.chemistrylab.core.crystal

import com.bytedance.chemistrylab.core.P3
import com.bytedance.chemistrylab.demos.crystal.CrystalDemo
import com.bytedance.chemistrylab.lab.spec.Cam
import com.bytedance.chemistrylab.lab.spec.SoftRender
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in close-up stills of the crystal bench for iterating on the look (the review's viewer camera stands 2.5 m
 * away). Run with  -Dreview.only=crystal_look  ->  app/build/review/crystal_look/ (PNG). Skipped otherwise.
 */
class CrystalLookTest {
    @Test
    fun closeUps() {
        assumeTrue(System.getProperty("review.only") == "crystal_look")
        val out = File("build/review/crystal_look").apply { mkdirs() }
        val cache = File("build/review-cache").apply { mkdirs() }
        val d = CrystalDemo
        val root = d.runHeadless(cache, 2f)
        root.walk { if (it.name.startsWith("Halo_")) it.visible = false }
        val lattice = Cam(P3(0.55f, 0.32f, 1.45f), P3(0.26f, 0.10f, -0.05f), 48f, "lattice")
        val tray = Cam(P3(-0.42f, 0.10f, 0.95f), P3(-0.48f, -0.15f, 0.05f), 45f, "tray")
        val wide = Cam(P3(0.0f, 0.30f, 1.9f), P3(-0.05f, 0.02f, 0f), 52f, "wide")
        for (c in listOf(lattice, tray, wide)) SoftRender.writePng(SoftRender.render(root, c, 1280, 800, 2), File(out, "default_${c.name}.png"))
        for ((name, apply) in d.reviewVariants()) {
            apply(); repeat(20) { d.update(1f / 60f) }
            root.walk { if (it.name.startsWith("Halo_")) it.visible = false }
            val cam = if (name == "walkin") Cam(P3(0.3f, 0.2f, 1.9f), P3(0f, 0.02f, 0f), 60f, "walk") else lattice
            SoftRender.writePng(SoftRender.render(root, cam, 1280, 800, 2), File(out, "$name.png"))
            if (name == "cleave" || name == "graphite") SoftRender.writePng(SoftRender.render(root, tray, 1280, 800, 2), File(out, "${name}_tray.png"))
            println("LOOK $name: ${d.readoutText.replace("\n", " | ")}")
        }
    }
}
