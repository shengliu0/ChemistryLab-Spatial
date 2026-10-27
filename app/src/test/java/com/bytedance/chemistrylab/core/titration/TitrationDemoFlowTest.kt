package com.bytedance.chemistrylab.core.titration

import com.bytedance.chemistrylab.demos.titration.IonWindowKinds
import com.bytedance.chemistrylab.demos.titration.TitrationDemo
import com.bytedance.chemistrylab.lab.Lang
import com.bytedance.chemistrylab.lab.spec.Mat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** The demo driven headless like a user: the opening drip reaches the endpoint, the 30 s rule, drag, micro counts. */
class TitrationDemoFlowTest {
    private val d = TitrationDemo
    private val cache = File("build/review-cache").apply { mkdirs() }
    private fun run(seconds: Float) = repeat((seconds * 60).toInt()) { d.update(1f / 60f) }
    private fun alpha(): Float = (d.liquidMat as Mat.Unlit).alpha

    @Test
    fun openingDripTurnsTheFlaskAtTheRightDropAndTheVerdictFollows() {
        Lang.set(false)
        d.buildSpec(cache)
        assertTrue("opens mid-titration", d.mixedDrops in 480..482)
        assertTrue("colourless at the start", alpha() < 0.01f)
        // drip (1 drop/s) until the flask has turned
        var t = 0f; var firstPink = -1
        while (t < 90f && firstPink < 0) { run(0.5f); t += 0.5f; if (alpha() > 0.05f) firstPink = d.mixedDrops }
        println("HCl + phenolphthalein, 1 drop/s from 24.00 mL: the flask turned at %.2f mL after %.1f s".format(firstPink * 0.05, t))
        // pH(25.00) = 7.00 (colourless), pH(25.05) = 10.00: the drop after equivalence turns it (it may still be easing in)
        assertTrue(firstPink in 501..502)
        // shut the stopcock (pinch it while it runs), wait: 30 s rule -> verdict
        d.onTap("Stopcock")
        run(3f)
        val v = d.mixedDrops * 0.05
        run(31f)
        println("held %.1f s; readout:\n%s".format(d.holdSeconds, d.readoutText))
        assertTrue(d.holdSeconds >= 30f)
        assertTrue(d.readoutText.contains("颜色保持 30 s 不褪"))
        assertTrue(d.readoutText.contains("%.2f mL".format(v)))
    }

    @Test
    fun stopcockTapAddsOneDropAndDragSetsTheRate() {
        d.buildSpec(cache)
        d.setupFor(Acid.HCL, Indicator.PHENOLPHTHALEIN, 0)
        d.onTap("Stopcock"); run(1.5f)
        assertEquals(1, d.outDrops)
        // drag down 7.5 cm = half of the full travel -> RMAX/4 = 0.25 mL/s; keeps running after release
        d.onDragStart("Stopcock"); d.onDrag("Stopcock", 0f, -0.075f, 0f); d.onDragEnd("Stopcock")
        assertEquals(0.25f, d.flow, 1e-6f)
        val before = d.outDrops; run(4f)
        val rate = (d.outDrops - before) * 0.05 / 4.0
        println("dragged to %.2f mL/s, measured %.3f mL/s".format(d.flow, rate))
        assertEquals(0.25, rate, 0.03)
        // dragging up shuts it; a tap while running shuts it too
        d.onDragStart("Stopcock"); d.onDrag("Stopcock", 0f, 0.5f, 0f); d.onDragEnd("Stopcock")
        assertEquals(0f, d.flow, 0f)
        d.onDragStart("Stopcock"); d.onDrag("Stopcock", 0f, -0.2f, 0f); d.onDragEnd("Stopcock")
        assertEquals(1.0f, d.flow, 1e-6f)
        d.onTap("Stopcock"); assertEquals(0f, d.flow, 0f)
    }

    @Test
    fun moleculeWindowReachesTheFlaskCountsThroughReactions() {
        for ((acid, drops) in listOf(Acid.HCL to 400, Acid.ACETIC to 200, Acid.ACETIC to 498)) {
            d.buildSpec(cache)
            d.setupFor(acid, Indicator.PHENOLPHTHALEIN, drops)
            run(1f)
            // add 1 mL and let the window catch up
            d.addDrops(20); run(14f)
            val tg = d.microTargets
            val shown = IntArray(6) { d.ionsShown(it) }
            println("$acid at %.2f mL: targets $tg, shown H3O ${shown[0]} OH ${shown[1]} Na ${shown[2]} Cl ${shown[3]} HA ${shown[4]} A ${shown[5]}".format(d.mixedDrops * 0.05))
            assertEquals(tg.h3o, shown[IonWindowKinds.H3O]); assertEquals(tg.oh, shown[IonWindowKinds.OH]); assertEquals(tg.na, shown[IonWindowKinds.NA])
            assertEquals(tg.cl, shown[IonWindowKinds.CL]); assertEquals(tg.ha, shown[IonWindowKinds.HA]); assertTrue(abs(tg.a - shown[IonWindowKinds.A]) <= 0)
        }
    }
}
