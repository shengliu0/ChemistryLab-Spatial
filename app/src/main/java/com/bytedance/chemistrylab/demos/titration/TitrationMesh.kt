// Geometry and textures for the titration bench (kept in this package on purpose): merged static parts
// (node budget), lathe glassware (Erlenmeyer flask, burette taper), the liquid frustum, ball molecules
// in CPK colours (one mesh per species, atoms coloured through constant UVs into an 8-texel strip), a
// soft sprite, and the 7-segment layout of the pH meter. Plain arrays, no SDK types.

package com.bytedance.chemistrylab.demos.titration

import com.bytedance.chemistrylab.render.BuoyMesh
import com.bytedance.chemistrylab.render.Image
import com.bytedance.chemistrylab.render.MeshData
import com.bytedance.chemistrylab.render.MeshGen
import com.bytedance.chemistrylab.render.ProcTex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal object TitrationMesh {

    /** One placed copy of a mesh: scaled per axis, rotated about Z by [rz], then translated. */
    class Part(val mesh: MeshData, val tx: Float, val ty: Float, val tz: Float,
               val sx: Float = 1f, val sy: Float = 1f, val sz: Float = 1f, val rz: Float = 0f)

    fun merge(parts: List<Part>): MeshData {
        var nv = 0; var ni = 0
        for (p in parts) { nv += p.mesh.vertexCount; ni += p.mesh.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v0 = 0; var i0 = 0
        for (p in parts) {
            val m = p.mesh; val c = cos(p.rz); val s = sin(p.rz)
            for (k in 0 until m.vertexCount) {
                val o = (v0 + k) * 3
                val x = m.pos[k * 3] * p.sx; val y = m.pos[k * 3 + 1] * p.sy
                pos[o] = x * c - y * s + p.tx; pos[o + 1] = x * s + y * c + p.ty; pos[o + 2] = m.pos[k * 3 + 2] * p.sz + p.tz
                var nx = m.nrm[k * 3] / p.sx; var ny = m.nrm[k * 3 + 1] / p.sy; val nz = m.nrm[k * 3 + 2] / p.sz
                val rx = nx * c - ny * s; val ry = nx * s + ny * c; nx = rx; ny = ry
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f)
                nrm[o] = nx / l; nrm[o + 1] = ny / l; nrm[o + 2] = nz / l
                uv[(v0 + k) * 2] = m.uv[k * 2]; uv[(v0 + k) * 2 + 1] = m.uv[k * 2 + 1]
            }
            for (k in m.idx.indices) idx[i0 + k] = m.idx[k] + v0
            v0 += m.vertexCount; i0 += m.idx.size
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Surface of revolution about +Y through (y, r) pairs (open, no caps). */
    fun lathe(ys: FloatArray, rs: FloatArray, sides: Int): MeshData {
        val path = FloatArray(ys.size * 3) { k -> if (k % 3 == 1) ys[k / 3] else 0f }
        return BuoyMesh.taper(path, rs, sides)
    }

    /** Closed liquid frustum from y = 0 (radius [r0]) to [h] (radius [r1]), with top and bottom discs. */
    fun frustum(r0: Float, r1: Float, h: Float, sides: Int = 40): MeshData {
        val side = lathe(floatArrayOf(0f, h), floatArrayOf(r0, r1), sides)
        return merge(listOf(Part(side, 0f, 0f, 0f), Part(disc(r1, sides, true), 0f, h, 0f), Part(disc(r0, sides, false), 0f, 0f, 0f)))
    }

    /** Flat disc in the XZ plane facing +Y ([up]) or −Y. */
    fun disc(r: Float, sides: Int, up: Boolean): MeshData {
        val pos = FloatArray((sides + 2) * 3); val nrm = FloatArray((sides + 2) * 3); val uv = FloatArray((sides + 2) * 2)
        val ny = if (up) 1f else -1f
        nrm[1] = ny; uv[0] = 0.5f; uv[1] = 0.5f
        for (s in 0..sides) {
            val a = 2.0 * PI * s / sides; val v = s + 1
            pos[v * 3] = (r * cos(a)).toFloat(); pos[v * 3 + 2] = (r * sin(a)).toFloat()
            nrm[v * 3 + 1] = ny; uv[v * 2] = 0.5f + 0.5f * cos(a).toFloat(); uv[v * 2 + 1] = 0.5f + 0.5f * sin(a).toFloat()
        }
        val idx = IntArray(sides * 3)
        for (s in 0 until sides) { val b = s * 3; idx[b] = 0; if (up) { idx[b + 1] = s + 2; idx[b + 2] = s + 1 } else { idx[b + 1] = s + 1; idx[b + 2] = s + 2 } }
        return MeshData(pos, nrm, uv, idx)
    }

    /** A ring (closed tube) of radius [r] in the XZ plane. */
    fun ring(r: Float, wire: Float, seg: Int): MeshData {
        val p = FloatArray(seg * 3) { k -> val a = 2.0 * PI * (k / 3) / seg; when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() } }
        return MeshGen.tube(p, wire, 6, true)
    }

    /** Unit quad in the XY plane facing +Z (billboards, numerals). */
    fun quad(): MeshData = MeshData(
        floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Soft round sprite (white, gaussian alpha). */
    fun blob(n: Int = 64, sharp: Float = 3.0f): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r2 = dx * dx + dy * dy
            val a = if (r2 >= 1f) 0f else exp(-sharp * r2) * (1 - r2)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /** Vertical colour strip (1 x n): row 0 = top. [rgb] gives sRGB for t = 0 (bottom) .. 1 (top). */
    fun strip(n: Int, rgb: (Float) -> FloatArray): Image {
        val img = Image(1, n)
        for (y in 0 until n) { val c = rgb(1f - (y + 0.5f) / n); img.px[y] = ProcTex.argb(1f, c[0], c[1], c[2]) }
        return img
    }

    // ---------------------------------------------------------------- molecules (CPK)

    /** Texel centres of [atomTex] (8 x 2): O, H, C, Na, Cl, white. */
    const val U_O = 0.0625f; const val U_H = 0.1875f; const val U_C = 0.3125f; const val U_NA = 0.4375f; const val U_CL = 0.5625f

    fun atomTex(): Image {
        val img = Image(8, 2)
        val c = intArrayOf(ProcTex.argb(1f, 0.95f, 0.22f, 0.17f), ProcTex.argb(1f, 0.96f, 0.96f, 0.98f), ProcTex.argb(1f, 0.30f, 0.31f, 0.34f),
            ProcTex.argb(1f, 0.67f, 0.40f, 0.98f), ProcTex.argb(1f, 0.25f, 0.92f, 0.30f), ProcTex.argb(1f, 1f, 1f, 1f),
            ProcTex.argb(1f, 1f, 1f, 1f), ProcTex.argb(1f, 1f, 1f, 1f))
        for (y in 0 until 2) for (x in 0 until 8) img.px[y * 8 + x] = c[x]
        return img
    }

    private fun atom(x: Float, y: Float, z: Float, r: Float, u: Float, lat: Int = 8, lon: Int = 14): MeshData {
        val m = MeshGen.sphere(r, lat, lon, MeshGen.SphereUv.EQUIRECT)
        for (k in 0 until m.vertexCount) { m.pos[k * 3] += x; m.pos[k * 3 + 1] += y; m.pos[k * 3 + 2] += z; m.uv[k * 2] = u; m.uv[k * 2 + 1] = 0.5f }
        return m
    }
    private fun join(parts: List<MeshData>) = merge(parts.map { Part(it, 0f, 0f, 0f) })

    const val R_O = 0.0105f; const val R_H = 0.0068f; const val R_C = 0.0108f

    /** H₃O⁺: trigonal pyramid, r(O–H) 0.98 Å, H–O–H ≈ 113°. [s] metres per Å. */
    fun hydronium(s: Float): MeshData {
        val parts = arrayListOf(atom(0f, 0f, 0f, R_O, U_O))
        val th = Math.toRadians(105.0)
        for (k in 0 until 3) {
            val ph = 2 * PI * k / 3 + 0.5
            val d = 0.98f * s
            parts += atom((d * sin(th) * cos(ph)).toFloat(), (d * cos(th)).toFloat(), (d * sin(th) * sin(ph)).toFloat(), R_H, U_H)
        }
        return join(parts)
    }

    /** OH⁻: r(O–H) 0.97 Å along +x. */
    fun hydroxide(s: Float) = join(listOf(atom(-0.25f * s, 0f, 0f, R_O, U_O), atom(0.72f * s, 0f, 0f, R_H, U_H)))

    /** H₂O: r 0.96 Å, 104.5°, H's toward −y. */
    fun water(s: Float): MeshData {
        val a = Math.toRadians(52.25); val d = 0.96f * s
        return join(listOf(atom(0f, 0.12f * s, 0f, R_O, U_O),
            atom((d * sin(a)).toFloat(), (0.12f * s - d * cos(a)).toFloat(), 0f, R_H, U_H),
            atom((-d * sin(a)).toFloat(), (0.12f * s - d * cos(a)).toFloat(), 0f, R_H, U_H)))
    }

    fun sodium() = atom(0f, 0f, 0f, 0.0125f, U_NA, 12, 20)
    fun chloride() = atom(0f, 0f, 0f, 0.0185f, U_CL, 12, 20)

    /**
     * Acetic acid (acid = true) or acetate, planar, Å (gas-phase structure, rounded): C–C 1.52, C=O 1.21,
     * C–O 1.36, O–H 0.97; the carboxyl group toward +x, centred.
     */
    fun acetic(s: Float, acid: Boolean): MeshData {
        val at = ArrayList<FloatArray>()           // x, y, z, kind (0 O, 1 H, 2 C)
        at += floatArrayOf(-0.76f, 0f, 0f, 2f); at += floatArrayOf(0.76f, 0f, 0f, 2f)
        if (acid) { at += floatArrayOf(1.40f, 1.04f, 0f, 0f); at += floatArrayOf(1.42f, -1.12f, 0f, 0f); at += floatArrayOf(2.36f, -0.98f, 0f, 1f) }
        else { at += floatArrayOf(1.38f, 1.08f, 0f, 0f); at += floatArrayOf(1.38f, -1.08f, 0f, 0f) }
        at += floatArrayOf(-1.14f, 1.02f, 0f, 1f); at += floatArrayOf(-1.14f, -0.51f, 0.89f, 1f); at += floatArrayOf(-1.14f, -0.51f, -0.89f, 1f)
        val cx = if (acid) 0.45f else 0.30f
        return join(at.map { a ->
            val k = a[3].toInt()
            atom((a[0] - cx) * s, a[1] * s, a[2] * s, when (k) { 0 -> R_O; 1 -> R_H; else -> R_C }, when (k) { 0 -> U_O; 1 -> U_H; else -> U_C })
        })
    }

    /** Merge a capsule (stir bar) along X: cylinder + two spheres. */
    fun capsule(len: Float, r: Float): MeshData {
        val cyl = MeshGen.cylinder(r, len, 20)
        return merge(listOf(Part(cyl, 0f, 0f, 0f, rz = (PI / 2).toFloat()),
            Part(MeshGen.sphere(r, 8, 16, MeshGen.SphereUv.EQUIRECT), len / 2, 0f, 0f),
            Part(MeshGen.sphere(r, 8, 16, MeshGen.SphereUv.EQUIRECT), -len / 2, 0f, 0f)))
    }

    // ---------------------------------------------------------------- 7-segment display

    /** Segment bits a..g for 0-9 (bit 0 = a); index 10 = 'P', 11 = 'H', 12 = '-'. */
    val SEG = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F, 0x73, 0x76, 0x40)

    /** Centre (x, y), size (w, h) of segment [k] (a..g) in a digit [w] wide, [h] tall, stroke [t]. */
    fun segRect(k: Int, w: Float, h: Float, t: Float): FloatArray = when (k) {
        0 -> floatArrayOf(0f, h / 2, w - t, t)
        1 -> floatArrayOf(w / 2, h / 4, t, h / 2 - t)
        2 -> floatArrayOf(w / 2, -h / 4, t, h / 2 - t)
        3 -> floatArrayOf(0f, -h / 2, w - t, t)
        4 -> floatArrayOf(-w / 2, -h / 4, t, h / 2 - t)
        5 -> floatArrayOf(-w / 2, h / 4, t, h / 2 - t)
        else -> floatArrayOf(0f, 0f, w - t, t)
    }

    /** linear -> sRGB (one channel). */
    fun srgb(v: Double): Float {
        val c = v.coerceIn(0.0, 1.0)
        return (if (c <= 0.0031308) 12.92 * c else 1.055 * Math.pow(c, 1 / 2.4) - 0.055).toFloat()
    }
}
