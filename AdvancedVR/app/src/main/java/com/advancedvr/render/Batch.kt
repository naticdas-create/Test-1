package com.advancedvr.render

import android.opengl.GLES20
import com.advancedvr.core.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Acumulador de triângulos coloridos em espaço de mundo. Reconstruído a cada quadro. */
class Batch(private val maxVerts: Int = 30000) {
    private val data = FloatArray(maxVerts * 7)
    private val buf = ByteBuffer.allocateDirect(maxVerts * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var n = 0

    fun clear() { n = 0 }

    private fun v(p: Vec3, c: Int) {
        if (n >= maxVerts) return
        val i = n * 7
        data[i] = p.x; data[i + 1] = p.y; data[i + 2] = p.z
        data[i + 3] = ((c shr 16) and 255) / 255f
        data[i + 4] = ((c shr 8) and 255) / 255f
        data[i + 5] = (c and 255) / 255f
        data[i + 6] = ((c ushr 24) and 255) / 255f
        n++
    }

    fun tri(a: Vec3, b: Vec3, c: Vec3, col: Int) {
        if (n + 3 > maxVerts) return
        v(a, col); v(b, col); v(c, col)
    }

    fun triC(a: Vec3, ca: Int, b: Vec3, cb: Int, c: Vec3, cc: Int) {
        if (n + 3 > maxVerts) return
        v(a, ca); v(b, cb); v(c, cc)
    }

    fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, col: Int) { tri(a, b, c, col); tri(a, c, d, col) }

    /** Disco no plano definido pelos eixos unitários ax/ay. */
    fun circle(c: Vec3, ax: Vec3, ay: Vec3, r: Float, col: Int, seg: Int = 16) {
        var prev = c + ax * r
        for (i in 1..seg) {
            val a = (2.0 * PI * i / seg).toFloat()
            val p = c + ax * (cos(a) * r) + ay * (sin(a) * r)
            tri(c, prev, p, col)
            prev = p
        }
    }

    /** Cápsula (segmento com pontas arredondadas) voltada para a normal n. */
    fun capsule(a: Vec3, b: Vec3, r: Float, n: Vec3, col: Int) {
        val d = b - a
        if (d.length() < 1e-5f) {
            val ax = n.cross(Vec3(0f, 1f, 0f)).normalized()
            circle(a, ax, n.cross(ax).normalized(), r, col, 10); return
        }
        val u = d.normalized()
        val perp = n.cross(u).normalized()
        quad(a + perp * r, a - perp * r, b - perp * r, b + perp * r, col)
        circle(a, u, perp, r, col, 10)
        circle(b, u, perp, r, col, 10)
    }

    /** Fita com cores diferentes nas pontas (raio com fade). */
    fun ribbon(a: Vec3, b: Vec3, w: Float, n: Vec3, ca: Int, cb: Int) {
        val u = (b - a).normalized()
        val perp = n.cross(u).normalized() * (w / 2f)
        triC(a + perp, ca, a - perp, ca, b - perp, cb)
        triC(a + perp, ca, b - perp, cb, b + perp, cb)
    }

    fun draw(p: Prog, vp: FloatArray) {
        if (n == 0) return
        GLES20.glUseProgram(p.id)
        GLES20.glUniformMatrix4fv(p.u("uVP"), 1, false, vp, 0)
        buf.position(0)
        buf.put(data, 0, n * 7)
        val aPos = p.a("aPos"); val aCol = p.a("aCol")
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 28, buf)
        buf.position(3)
        GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, 28, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, n)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aCol)
        buf.position(0)
    }
}
