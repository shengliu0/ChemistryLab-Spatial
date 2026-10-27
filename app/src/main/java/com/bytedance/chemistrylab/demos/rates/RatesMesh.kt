// Geometry and textures for the reaction-rates bench: a conical (Erlenmeyer) flask lathed from its
// profile, the liquid as a truncated cone, flat chart ribbons, a paper card with a bold X, ball
// molecules with a coloured "reactive end", and seven-segment digits baked ONCE (the stopwatches and
// thermostats swap digit materials per frame; no per-frame bakes). Same conventions as render/MeshGen:
// plain arrays, CCW outward winding, no SDK types.

package com.bytedance.chemistrylab.demos.rates

import com.bytedance.chemistrylab.render.Image
import com.bytedance.chemistrylab.render.MeshData
import com.bytedance.chemistrylab.render.MeshGen
import com.bytedance.chemistrylab.render.ProcTex
import com.bytedance.chemistrylab.render.Rgb
import com.bytedance.chemistrylab.render.V3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal object RatesMesh {

    /** Surface of revolution about +Y through the (r, y) [profile] points (open, normals outward). */
    fun lathe(profile: FloatArray, seg: Int): MeshData {
        val m = profile.size / 2
        val nv = m * (seg + 1)
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2)
        for (i in 0 until m) {
            val r = profile[i * 2]; val y = profile[i * 2 + 1]
            val i0 = maxOf(0, i - 1); val i1 = minOf(m - 1, i + 1)
            val dr = profile[i1 * 2] - profile[i0 * 2]; val dy = profile[i1 * 2 + 1] - profile[i0 * 2 + 1]
            val l = sqrt(dr * dr + dy * dy).coerceAtLeast(1e-9f)
            val nr = dy / l; val ny = -dr / l                        // outward normal of the profile
            for (s in 0..seg) {
                val a = 2.0 * PI * s / seg; val c = cos(a).toFloat(); val sn = sin(a).toFloat()
                val k = i * (seg + 1) + s
                pos[k * 3] = r * c; pos[k * 3 + 1] = y; pos[k * 3 + 2] = r * sn
                nrm[k * 3] = nr * c; nrm[k * 3 + 1] = ny; nrm[k * 3 + 2] = nr * sn
                uv[k * 2] = s.toFloat() / seg; uv[k * 2 + 1] = i.toFloat() / (m - 1)
            }
        }
        val idx = IntArray((m - 1) * seg * 6); var t = 0
        for (i in 0 until m - 1) for (s in 0 until seg) {
            val a = i * (seg + 1) + s; val b = a + seg + 1
            idx[t++] = a; idx[t++] = b; idx[t++] = a + 1
            idx[t++] = a + 1; idx[t++] = b; idx[t++] = b + 1
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Closed truncated cone along +Y from y = 0 (radius [r0]) to y = [h] (radius [r1]). */
    fun frustum(r0: Float, r1: Float, h: Float, seg: Int): MeshData {
        val side = lathe(floatArrayOf(r0, 0f, r1, h), seg)
        val bottom = disc(r0, 0f, -1f, seg); val top = disc(r1, h, 1f, seg)
        return merge(listOf(side, bottom, top))
    }

    private fun disc(r: Float, y: Float, ny: Float, seg: Int): MeshData {
        val pos = FloatArray((seg + 2) * 3); val nrm = FloatArray((seg + 2) * 3); val uv = FloatArray((seg + 2) * 2)
        pos[1] = y; nrm[1] = ny; uv[0] = 0.5f; uv[1] = 0.5f
        for (s in 0..seg) {
            val a = 2.0 * PI * s / seg; val k = s + 1
            pos[k * 3] = (r * cos(a)).toFloat(); pos[k * 3 + 1] = y; pos[k * 3 + 2] = (r * sin(a)).toFloat()
            nrm[k * 3 + 1] = ny; uv[k * 2] = 0.5f + 0.5f * cos(a).toFloat(); uv[k * 2 + 1] = 0.5f + 0.5f * sin(a).toFloat()
        }
        val idx = IntArray(seg * 3); var t = 0
        for (s in 0 until seg) { idx[t++] = 0; if (ny > 0) { idx[t++] = s + 2; idx[t++] = s + 1 } else { idx[t++] = s + 1; idx[t++] = s + 2 } }
        return MeshData(pos, nrm, uv, idx)
    }

    fun merge(parts: List<MeshData>): MeshData {
        val nv = parts.sumOf { it.vertexCount }; val ni = parts.sumOf { it.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v = 0; var t = 0
        for (p in parts) {
            System.arraycopy(p.pos, 0, pos, v * 3, p.pos.size); System.arraycopy(p.nrm, 0, nrm, v * 3, p.nrm.size)
            System.arraycopy(p.uv, 0, uv, v * 2, p.uv.size)
            for (k in p.idx.indices) idx[t + k] = p.idx[k] + v
            v += p.vertexCount; t += p.idx.size
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** A copy of [m] scaled per axis then moved (normals renormalised). */
    fun placed(m: MeshData, tx: Float, ty: Float, tz: Float, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): MeshData {
        val pos = FloatArray(m.pos.size); val nrm = FloatArray(m.nrm.size)
        for (k in 0 until m.vertexCount) {
            pos[k * 3] = m.pos[k * 3] * sx + tx; pos[k * 3 + 1] = m.pos[k * 3 + 1] * sy + ty; pos[k * 3 + 2] = m.pos[k * 3 + 2] * sz + tz
            var nx = m.nrm[k * 3] / sx; var ny = m.nrm[k * 3 + 1] / sy; var nz = m.nrm[k * 3 + 2] / sz
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f); nx /= l; ny /= l; nz /= l
            nrm[k * 3] = nx; nrm[k * 3 + 1] = ny; nrm[k * 3 + 2] = nz
        }
        return MeshData(pos, nrm, m.uv.copyOf(), m.idx.copyOf())
    }

    /**
     * Erlenmeyer flask, outer glass profile (r, y) from the base: rounded heel, straight cone to the
     * shoulder, neck, flared lip. [rb] base radius, [hs] shoulder height, [rn] neck radius, [ht] lip height.
     */
    fun flaskProfile(rb: Float, hs: Float, rn: Float, ht: Float): FloatArray {
        val pts = ArrayList<Float>()
        fun p(r: Float, y: Float) { pts += r; pts += y }
        p(0.0f, 0f); p(rb - 0.010f, 0f)
        for (k in 1..4) { val a = PI / 2 * k / 4; p((rb - 0.010f + 0.010f * sin(a)).toFloat(), (0.010f * (1 - cos(a))).toFloat()) }
        p(rb - 0.0015f, 0.016f)
        p(rn + 0.004f, hs - 0.006f)
        p(rn, hs + 0.006f)
        p(rn, ht - 0.006f)
        p(rn + 0.004f, ht - 0.002f); p(rn + 0.004f, ht); p(rn - 0.001f, ht)
        return pts.toFloatArray()
    }

    // ---------------------------------------------------------------- ribbons (chart curves)

    fun ribbon(n: Int): MeshData {
        val pos = FloatArray(n * 2 * 3); val nrm = FloatArray(n * 2 * 3); val uv = FloatArray(n * 2 * 2)
        for (k in 0 until n * 2) { nrm[k * 3 + 2] = 1f; uv[k * 2] = (k / 2).toFloat() / (n - 1); uv[k * 2 + 1] = (k % 2).toFloat() }
        val idx = IntArray((n - 1) * 6); var t = 0
        for (i in 0 until n - 1) {
            val a = i * 2; val b = a + 2
            idx[t++] = a; idx[t++] = b; idx[t++] = a + 1
            idx[t++] = a + 1; idx[t++] = b; idx[t++] = b + 1
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Rewrite a ribbon in place along (xs, ys) at depth [z], half width [hw]. */
    fun ribbonInto(md: MeshData, xs: FloatArray, ys: FloatArray, z: Float, hw: Float) {
        val n = xs.size
        var px = 0f; var py = 1f
        for (i in 0 until n) {
            val i0 = maxOf(0, i - 1); val i1 = minOf(n - 1, i + 1)
            val tx = xs[i1] - xs[i0]; val ty = ys[i1] - ys[i0]
            val l = sqrt(tx * tx + ty * ty)
            if (l > 1e-6f) { px = -ty / l; py = tx / l }
            val o = i * 6
            md.pos[o] = xs[i] - px * hw; md.pos[o + 1] = ys[i] - py * hw; md.pos[o + 2] = z
            md.pos[o + 3] = xs[i] + px * hw; md.pos[o + 4] = ys[i] + py * hw; md.pos[o + 5] = z
        }
    }

    /** Unit quad in the XY plane facing +Z. */
    fun quad(): MeshData = MeshData(
        floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    // ---------------------------------------------------------------- molecules

    /** 4 × 2 colour strip (sRGB) for constant-UV molecules: texel centres u = 0.125, 0.375, 0.625, 0.875. */
    fun strip(vararg c: Rgb): Image {
        val img = Image(4, 2)
        for (y in 0 until 2) for (x in 0 until 4) { val k = c[minOf(x, c.size - 1)]; img.px[y * 4 + x] = ProcTex.argb(1f, k.r, k.g, k.b) }
        return img
    }

    private fun ball(x: Float, r: Float, u: Float, lat: Int, lon: Int): MeshData {
        val m = MeshGen.sphere(r, lat, lon, MeshGen.SphereUv.EQUIRECT)
        for (k in 0 until m.vertexCount) { m.pos[k * 3] += x; m.uv[k * 2] = u; m.uv[k * 2 + 1] = 0.5f }
        return m
    }

    /** Molecule A: body sphere (strip texel 0) with its reactive atom (texel 1) on the body +x side. */
    fun moleculeA(rBody: Float, rEnd: Float): MeshData =
        merge(listOf(ball(0f, rBody, 0.125f, 10, 16), ball(rBody * 0.92f, rEnd, 0.375f, 7, 12)))

    /** Molecule B: one sphere (strip texel 0). */
    fun moleculeB(r: Float): MeshData = ball(0f, r, 0.125f, 10, 16)

    // ---------------------------------------------------------------- textures

    /** Soft round sprite (white, gaussian alpha) for flashes and steam. */
    fun blob(n: Int = 64, sharp: Float = 3.2f): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r2 = dx * dx + dy * dy
            val a = if (r2 >= 1f) 0f else exp(-sharp * r2) * (1 - r2)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /** A ring sprite (the reaction "burst"). */
    fun ringSprite(n: Int = 64): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy)
            val a = exp(-((r - 0.78f) / 0.09f).pow(2)) * (if (r < 1f) 1f else 0f)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /**
     * A paper label wrapped on the cone wall [rOf](h) between angles [a0]..[a1] (radians about +Y; the back
     * of the flask is 3π/2) and heights [h0]..[h1], [off] outside the glass. [inward] = the printed side,
     * facing the flask's axis (seen THROUGH the liquid from the front); otherwise the plain back.
     */
    fun conePatch(rOf: (Float) -> Float, a0: Float, a1: Float, h0: Float, h1: Float, na: Int, nh: Int, off: Float, inward: Boolean): MeshData {
        val nv = (na + 1) * (nh + 1)
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2)
        for (j in 0..nh) for (i in 0..na) {
            val a = a0 + (a1 - a0) * i / na; val h = h0 + (h1 - h0) * j / nh
            val r = rOf(h) + off
            val k = j * (na + 1) + i
            pos[k * 3] = r * cos(a); pos[k * 3 + 1] = h; pos[k * 3 + 2] = r * sin(a)
            val sg = if (inward) -1f else 1f
            nrm[k * 3] = sg * cos(a); nrm[k * 3 + 2] = sg * sin(a)
            uv[k * 2] = i.toFloat() / na; uv[k * 2 + 1] = 1f - j.toFloat() / nh
        }
        val idx = IntArray(na * nh * 6); var t = 0
        for (j in 0 until nh) for (i in 0 until na) {
            val p = j * (na + 1) + i; val q = p + 1; val c = p + na + 1; val d = c + 1
            // (q − p) × (c − p) points toward the axis on the back of the flask
            if (inward) { idx[t++] = p; idx[t++] = q; idx[t++] = c; idx[t++] = q; idx[t++] = d; idx[t++] = c }
            else { idx[t++] = p; idx[t++] = c; idx[t++] = q; idx[t++] = q; idx[t++] = c; idx[t++] = d }
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /**
     * The printed side of that label: white paper with a bold black X centred at height [hc], arms [half]
     * long and [ink] thick — drawn in true lengths on the cone (arc length × height), so it is not skewed.
     */
    fun crossOnCone(w: Int, hp: Int, rOf: (Float) -> Float, a0: Float, a1: Float, h0: Float, h1: Float, hc: Float, half: Float, ink: Float): Image {
        val img = Image(w, hp)
        val am = (a0 + a1) / 2
        for (y in 0 until hp) for (x in 0 until w) {
            val a = a0 + (a1 - a0) * (x + 0.5f) / w
            val h = h1 - (h1 - h0) * (y + 0.5f) / hp
            val sx = rOf(h) * (a - am); val ty = h - hc
            val d = min(segDist(sx, ty, -half, -half, half, half), segDist(sx, ty, -half, half, half, -half))
            val px = (h1 - h0) / hp                                   // one texel in metres (for the soft edge)
            val inkA = ((ink / 2 + px - d) / (2 * px)).coerceIn(0f, 1f)
            val grain = 0.95f + 0.05f * ProcTex.fbm(V3(x * 0.18f, y * 0.18f, 0.3f), 2, 5)
            val pr = 0.96f * grain; val pg = 0.95f * grain; val pb = 0.92f * grain
            val c = 0.06f
            img.px[y * w + x] = ProcTex.argb(1f, pr + (c - pr) * inkA, pg + (c - pg) * inkA, pb + (c - pb) * inkA)
        }
        return img
    }

    // ---------------------------------------------------------------- seven-segment digits

    /** Segments a..g as (x0, y0, x1, y1) in a 0.6 × 1 cell, y up. */
    private val SEG = arrayOf(
        floatArrayOf(0.10f, 1.00f, 0.50f, 1.00f), floatArrayOf(0.56f, 0.94f, 0.56f, 0.56f), floatArrayOf(0.56f, 0.44f, 0.56f, 0.06f),
        floatArrayOf(0.10f, 0.00f, 0.50f, 0.00f), floatArrayOf(0.04f, 0.44f, 0.04f, 0.06f), floatArrayOf(0.04f, 0.94f, 0.04f, 0.56f),
        floatArrayOf(0.10f, 0.50f, 0.50f, 0.50f))
    private val DIGIT = mapOf('0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd",
        '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g", ' ' to "", 'C' to "adef", '°' to "abfg")

    private fun advance(ch: Char) = if (ch == '.') 0.22f else 0.78f

    /** Bake [s] (digits, '.', '-', ' ') as lit seven-segment strokes: white on black (draw with ADD). */
    fun sevenSeg(s: String, hPx: Int = 48, ghost: Float = 0f): Image {
        val cap = hPx * 0.70f; val pad = hPx * 0.15f
        val wCells = s.sumOf { advance(it).toDouble() }.toFloat()
        val w = max(4, (wCells * cap + 2 * pad).toInt() + 1)
        val img = Image(w, hPx)
        val segsOn = ArrayList<FloatArray>(); val segsOff = ArrayList<FloatArray>()
        var x0 = pad
        val base = hPx - pad
        for (ch in s) {
            if (ch == '.') { segsOn += floatArrayOf(x0 + 0.06f * cap, base, x0 + 0.06f * cap, base); x0 += advance(ch) * cap; continue }
            val on = DIGIT[ch] ?: ""
            for ((k, sg) in SEG.withIndex()) {
                val seg = floatArrayOf(x0 + sg[0] * cap, base - sg[1] * cap, x0 + sg[2] * cap, base - sg[3] * cap)
                if (('a' + k) in on) segsOn += seg else segsOff += seg
            }
            x0 += advance(ch) * cap
        }
        val r = cap * 0.075f
        for (y in 0 until hPx) for (x in 0 until w) {
            val px = x + 0.5f; val py = y + 0.5f
            var dOn = Float.MAX_VALUE; var dOff = Float.MAX_VALUE
            for (sg in segsOn) dOn = min(dOn, segDist(px, py, sg[0], sg[1], sg[2], sg[3]))
            if (ghost > 0f) for (sg in segsOff) dOff = min(dOff, segDist(px, py, sg[0], sg[1], sg[2], sg[3]))
            val a = ((r + 0.8f - dOn) / 1.6f).coerceIn(0f, 1f)
            val g = ((r + 0.8f - dOff) / 1.6f).coerceIn(0f, 1f) * ghost
            val v = max(a, g).toDouble().pow(1 / 2.2).toFloat()
            img.px[y * w + x] = ProcTex.argb(1f, v, v, v)
        }
        return img
    }

    /** Width / height of the [sevenSeg] image of [s] (for sizing its quad). */
    fun aspect(s: String): Float { val cap = 0.70f; return (s.sumOf { advance(it).toDouble() }.toFloat() * cap + 0.30f) }

    private fun segDist(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0; val dy = y1 - y0; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-9f) 0f else (((px - x0) * dx + (py - y0) * dy) / l2).coerceIn(0f, 1f)
        val ex = x0 + t * dx - px; val ey = y0 + t * dy - py
        return sqrt(ex * ex + ey * ey)
    }

    /** linear -> sRGB (one channel). */
    fun srgb(v: Double): Float {
        val c = v.coerceIn(0.0, 1.0)
        return (if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055).toFloat()
    }
}
