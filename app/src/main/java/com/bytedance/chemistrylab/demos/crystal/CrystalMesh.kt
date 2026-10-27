// Geometry and small textures for the crystal bench: cut-away atoms for the unit-cell count (half, quarter,
// eighth spheres; 60/120 deg wedges for the hexagonal cell), merged bond meshes (one mesh per lattice part, built
// only when the crystal or the supercell changes), the specimens (stepped salt crystal, brilliant-cut diamond,
// layered graphite, pencil), a slide arrow and the sparkle / glow sprites.

package com.bytedance.chemistrylab.demos.crystal

import com.bytedance.chemistrylab.core.crystal.Cut
import com.bytedance.chemistrylab.render.Image
import com.bytedance.chemistrylab.render.MeshData
import com.bytedance.chemistrylab.render.MeshGen
import com.bytedance.chemistrylab.render.ProcTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Growable mesh builder (flat or smooth vertices). */
internal class MB {
    private val p = ArrayList<Float>(); private val n = ArrayList<Float>(); private val t = ArrayList<Float>(); private val ix = ArrayList<Int>()
    val count get() = p.size / 3

    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f): Int {
        p += x; p += y; p += z; n += nx; n += ny; n += nz; t += u; t += w; return count - 1
    }
    fun tri(a: Int, b: Int, c: Int) { ix += a; ix += b; ix += c }

    /** Flat-shaded triangle; wound so its normal points away from [ref] (convex solids) or along [want]. */
    fun flat(a: FloatArray, b: FloatArray, c: FloatArray, ref: FloatArray? = null, want: FloatArray? = null) {
        var nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1])
        var ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])
        var nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        val l = sqrt(nx * nx + ny * ny + nz * nz); if (l < 1e-12f) return
        nx /= l; ny /= l; nz /= l
        val dir = want ?: ref?.let { r -> floatArrayOf((a[0] + b[0] + c[0]) / 3 - r[0], (a[1] + b[1] + c[1]) / 3 - r[1], (a[2] + b[2] + c[2]) / 3 - r[2]) }
        val flip = dir != null && nx * dir[0] + ny * dir[1] + nz * dir[2] < 0
        if (flip) { nx = -nx; ny = -ny; nz = -nz }
        val i0 = v(a[0], a[1], a[2], nx, ny, nz); val i1 = v(b[0], b[1], b[2], nx, ny, nz); val i2 = v(c[0], c[1], c[2], nx, ny, nz)
        if (flip) tri(i0, i2, i1) else tri(i0, i1, i2)
    }

    /** Append [md] scaled by [s] (per axis) then moved by [o]. */
    fun add(md: MeshData, sx: Float, sy: Float, sz: Float, ox: Float, oy: Float, oz: Float) {
        val base = count
        for (k in 0 until md.vertexCount) {
            var nx = md.nrm[k * 3] / sx; var ny = md.nrm[k * 3 + 1] / sy; var nz = md.nrm[k * 3 + 2] / sz
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f); nx /= l; ny /= l; nz /= l
            v(md.pos[k * 3] * sx + ox, md.pos[k * 3 + 1] * sy + oy, md.pos[k * 3 + 2] * sz + oz, nx, ny, nz, md.uv[k * 2], md.uv[k * 2 + 1])
        }
        for (i in md.idx) ix += base + i
    }

    fun build() = MeshData(p.toFloatArray(), n.toFloatArray(), t.toFloatArray(), ix.toIntArray())
}

internal object CrystalMesh {

    // ------------------------------------------------------------------ cut-away atoms (unit radius)

    /**
     * The part of a unit sphere inside a cell corner/edge/face, closed by flat cut faces. Canonical regions:
     * HALF y >= 0; QUARTER x >= 0 and z >= 0; EIGHTH x, y, z >= 0; WEDGEφ azimuth (from +x toward +z) in [0, φ].
     */
    fun cutSphere(cut: Cut, lat: Int = 16, lon: Int = 32): MeshData {
        val half = (PI / 2).toFloat()
        return when (cut) {
            Cut.FULL -> MeshGen.sphere(1f, lat, lon, MeshGen.SphereUv.EQUIRECT)
            Cut.HALF -> patch(0f, 2 * PI.toFloat(), 0f, half, lat, lon)
            Cut.QUARTER -> patch(0f, half, -half, half, lat, lon)
            Cut.EIGHTH -> patch(0f, half, 0f, half, lat, lon)
            Cut.WEDGE60 -> patch(0f, (PI / 3).toFloat(), -half, half, lat, lon)
            Cut.WEDGE120 -> patch(0f, (2 * PI / 3).toFloat(), -half, half, lat, lon)
        }
    }

    private fun dir(th: Float, ph: Float) = floatArrayOf(cos(ph) * cos(th), sin(ph), cos(ph) * sin(th))

    /** Sphere patch over azimuth [t0, t1] x elevation [p0, p1], plus the flat faces that close it. */
    private fun patch(t0: Float, t1: Float, p0: Float, p1: Float, lat: Int, lon: Int): MeshData {
        val mb = MB()
        val nt = max(2, (lon * (t1 - t0) / (2 * PI)).roundToInt()); val np = max(2, (lat * (p1 - p0) / PI).roundToInt())
        val grid = Array(nt + 1) { i -> IntArray(np + 1) { j ->
            val d = dir(t0 + (t1 - t0) * i / nt, p0 + (p1 - p0) * j / np)
            mb.v(d[0], d[1], d[2], d[0], d[1], d[2], i.toFloat() / nt, j.toFloat() / np)
        } }
        for (i in 0 until nt) for (j in 0 until np) {
            // outward = d/dphi x d/dtheta
            mb.tri(grid[i][j], grid[i][j + 1], grid[i + 1][j]); mb.tri(grid[i + 1][j], grid[i][j + 1], grid[i + 1][j + 1])
        }
        val o = floatArrayOf(0f, 0f, 0f)
        val full = t1 - t0 > 6.2f
        if (!full) for ((th, sgn) in listOf(t0 to 1f, t1 to -1f)) {
            // meridian face at azimuth th: outward normal points away from the patch interior
            val want = floatArrayOf(sgn * sin(th), 0f, -sgn * cos(th))
            for (j in 0 until np) mb.flat(o, dir(th, p0 + (p1 - p0) * j / np), dir(th, p0 + (p1 - p0) * (j + 1) / np), want = want)
        }
        if (p0 == 0f) {
            val want = floatArrayOf(0f, -1f, 0f)
            for (i in 0 until nt) mb.flat(o, dir(t0 + (t1 - t0) * i / nt, 0f), dir(t0 + (t1 - t0) * (i + 1) / nt, 0f), want = want)
        }
        return mb.build()
    }

    // ------------------------------------------------------------------ bonds

    /** One mesh of open tubes (radius [r], [sides] facets) between the point pairs [seg] (x0,y0,z0,x1,y1,z1 ...). */
    fun bonds(seg: FloatArray, r: Float, sides: Int = 6): MeshData {
        val nb = seg.size / 6
        val pos = FloatArray(nb * sides * 2 * 3); val nrm = FloatArray(pos.size); val uv = FloatArray(nb * sides * 2 * 2)
        val idx = IntArray(nb * sides * 6)
        var v = 0; var t = 0
        for (b in 0 until nb) {
            val ax = seg[b * 6]; val ay = seg[b * 6 + 1]; val az = seg[b * 6 + 2]
            var dx = seg[b * 6 + 3] - ax; var dy = seg[b * 6 + 4] - ay; var dz = seg[b * 6 + 5] - az
            val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9f); dx /= l; dy /= l; dz /= l
            // two unit vectors across the axis
            var ex: Float; var ey: Float; var ez: Float
            if (abs(dx) < 0.9f) { ex = 0f; ey = dz; ez = -dy } else { ex = -dz; ey = 0f; ez = dx }
            val el = sqrt(ex * ex + ey * ey + ez * ez); ex /= el; ey /= el; ez /= el
            val fx = dy * ez - dz * ey; val fy = dz * ex - dx * ez; val fz = dx * ey - dy * ex
            val v0 = v
            for (end in 0..1) for (s in 0 until sides) {
                val a = 2 * PI.toFloat() * s / sides; val c = cos(a); val sn = sin(a)
                val nx = ex * c + fx * sn; val ny = ey * c + fy * sn; val nz = ez * c + fz * sn
                pos[v * 3] = ax + dx * l * end + nx * r; pos[v * 3 + 1] = ay + dy * l * end + ny * r; pos[v * 3 + 2] = az + dz * l * end + nz * r
                nrm[v * 3] = nx; nrm[v * 3 + 1] = ny; nrm[v * 3 + 2] = nz
                v++
            }
            for (s in 0 until sides) {
                val s1 = (s + 1) % sides
                val a0 = v0 + s; val a1 = v0 + s1; val b0 = v0 + sides + s; val b1 = v0 + sides + s1
                idx[t++] = a0; idx[t++] = b0; idx[t++] = a1; idx[t++] = a1; idx[t++] = b0; idx[t++] = b1
            }
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Fixed-capacity triangle soup for the coordination polyhedron's faces (edited in place on selection). */
    fun hullBuffer(cap: Int = 72): MeshData = MeshData(FloatArray(cap * 3), FloatArray(cap * 3).also { for (k in 0 until cap) it[k * 3 + 1] = 1f },
        FloatArray(cap * 2), IntArray(cap))

    // ------------------------------------------------------------------ specimens

    /**
     * One half of a halite (rock-salt) crystal, the left (sign -1) or right (+1) of the cleave plane x = 0: a
     * cube of edge [s] with terraced (stepped) cleavage faces on top and front, the way real halite breaks.
     */
    fun saltHalf(s: Float, sign: Float): MeshData {
        val mb = MB(); val box = MeshGen.box(1f, 1f, 1f)
        val h = s / 2
        fun b(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) {
            val a = minOf(x0 * sign, x1 * sign); val c = maxOf(x0 * sign, x1 * sign)
            mb.add(box, c - a, y1 - y0, z1 - z0, (a + c) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        }
        b(0f, h, 0f, s * 0.80f, -h, h * 0.70f)                                  // main block
        b(0f, h * 0.86f, s * 0.80f, s * 0.92f, -h, h * 0.30f)                   // terrace 1
        b(0f, h * 0.62f, s * 0.92f, s, -h, -h * 0.25f)                          // terrace 2
        b(0f, h * 0.92f, 0f, s * 0.62f, h * 0.70f, h)                           // front step
        b(0f, h * 0.70f, 0f, s * 0.36f, h, h * 1.10f)                           // lower front ledge
        return mb.build()
    }

    /**
     * A graphite lump: six stacked, slightly offset 7-sided plates (the layers real graphite shows), split into the
     * shiny and the dull plates (two materials), in metres, base at y = 0.
     */
    fun graphiteLump(): Pair<MeshData, MeshData> {
        val plate = MeshGen.cylinder(1f, 1f, 7)
        val shiny = MB(); val dull = MB()
        var y = 0.004f
        for (k in 0 until 6) {
            val h = 0.0065f + 0.001f * (k % 2)
            val rot = 0.35f * k; val c = cos(rot); val s = sin(rot)
            val sx = 0.034f - 0.0025f * abs(k - 2); val sz = 0.026f - 0.002f * abs(k - 2)
            val ox = 0.004f * sin(k * 2.1f); val oz = 0.004f * cos(k * 1.7f)
            val mb = if (k % 2 == 0) shiny else dull
            // scale, then turn about y, then move (the plate is a prism, so normals turn with it)
            val t = MeshData(FloatArray(plate.pos.size), FloatArray(plate.nrm.size), plate.uv, plate.idx)
            for (v in 0 until plate.vertexCount) {
                val x = plate.pos[v * 3] * sx; val z = plate.pos[v * 3 + 2] * sz
                t.pos[v * 3] = c * x + s * z; t.pos[v * 3 + 1] = plate.pos[v * 3 + 1] * h; t.pos[v * 3 + 2] = -s * x + c * z
                var nx = plate.nrm[v * 3] / sx; val ny = plate.nrm[v * 3 + 1] / h; var nz = plate.nrm[v * 3 + 2] / sz
                val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; nz /= l
                t.nrm[v * 3] = c * nx + s * nz; t.nrm[v * 3 + 1] = ny / l; t.nrm[v * 3 + 2] = -s * nx + c * nz
            }
            mb.add(t, 1f, 1f, 1f, ox, y + h / 2, oz)
            y += h
        }
        return shiny.build() to dull.build()
    }

    /** Round brilliant cut (girdle radius 1, girdle plane y = 0): table, star, kite, upper-girdle and pavilion facets. */
    fun brilliant(): MeshData {
        val mb = MB()
        fun pt(deg: Double, r: Double, y: Double) = floatArrayOf((r * cos(Math.toRadians(deg))).toFloat(), y.toFloat(), (r * sin(Math.toRadians(deg))).toFloat())
        val cen = floatArrayOf(0f, -0.2f, 0f)
        val gTop = 0.02; val gBot = -0.02
        val T = Array(8) { pt(22.5 + 45 * it, 0.56, 0.33) }
        val S = Array(8) { pt(45.0 * it, 0.80, 0.18) }
        val G = Array(16) { pt(22.5 * it, 1.0, gTop) }
        val B = Array(16) { pt(22.5 * it, 1.0, gBot) }
        val L = Array(8) { pt(45.0 * it + 22.5, 0.50, -0.46) }
        val culet = floatArrayOf(0f, -0.86f, 0f)
        val top = floatArrayOf(0f, 0.33f, 0f)
        for (k in 0 until 8) {
            mb.flat(top, T[k], T[(k + 1) % 8], cen)                          // table (fan)
            mb.flat(T[(k + 7) % 8], S[k], T[k], cen)                         // star
            mb.flat(T[k], S[k], G[2 * k + 1], cen); mb.flat(T[k], G[2 * k + 1], S[(k + 1) % 8], cen)   // kite (bezel)
            mb.flat(S[k], G[(2 * k + 15) % 16], G[2 * k], cen); mb.flat(S[k], G[2 * k], G[2 * k + 1], cen)   // upper girdle
        }
        for (j in 0 until 16) { mb.flat(G[j], B[j], G[(j + 1) % 16], cen); mb.flat(G[(j + 1) % 16], B[j], B[(j + 1) % 16], cen) }   // girdle band
        for (k in 0 until 8) {
            // lower girdle facets down to the pavilion main points, then the mains to the culet
            mb.flat(B[2 * k], B[2 * k + 1], L[k], cen); mb.flat(B[2 * k + 1], B[(2 * k + 2) % 16], L[k], cen)
            mb.flat(L[k], B[(2 * k + 2) % 16], L[(k + 1) % 8], cen)
            mb.flat(L[k], L[(k + 1) % 8], culet, cen)
        }
        return mb.build()
    }

    /** A flat double-headed arrow along x (length 1, lying in the x-z plane, facing up and down). */
    fun slideArrow(): MeshData {
        val mb = MB()
        val pts = listOf(-0.5f to 0f, -0.30f to 0.16f, -0.30f to 0.06f, 0.30f to 0.06f, 0.30f to 0.16f, 0.5f to 0f,
            0.30f to -0.16f, 0.30f to -0.06f, -0.30f to -0.06f, -0.30f to -0.16f)
        fun p(i: Int) = floatArrayOf(pts[i].first, 0f, pts[i].second)
        val up = floatArrayOf(0f, 1f, 0f)
        // heads + shaft as triangles
        for (tr in listOf(intArrayOf(0, 1, 9), intArrayOf(5, 6, 4), intArrayOf(2, 3, 8), intArrayOf(3, 7, 8))) {
            mb.flat(p(tr[0]), p(tr[1]), p(tr[2]), want = up)
            mb.flat(p(tr[0]), p(tr[1]), p(tr[2]), want = floatArrayOf(0f, -1f, 0f))
        }
        return mb.build()
    }

    /** Unit quad in the XY plane facing +Z (billboards). */
    fun quad(): MeshData = MeshData(
        floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Closed horizontal ring path (for MeshGen.tube). */
    fun ring(r: Float, n: Int = 64): FloatArray = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() }
    }

    // ------------------------------------------------------------------ textures

    /** Soft round sprite: white, gaussian alpha. */
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

    /** Four-point sparkle star with a soft core (the diamond's glints). */
    fun star(n: Int = 64): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy)
            val ray = exp(-abs(dx) * 22f) * (1 - abs(dy)).coerceAtLeast(0f) + exp(-abs(dy) * 22f) * (1 - abs(dx)).coerceAtLeast(0f)
            val core = exp(-r * r * 30f)
            val a = (ray * 0.9f + core).coerceIn(0f, 1f) * (1 - r).coerceAtLeast(0f)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }
}
