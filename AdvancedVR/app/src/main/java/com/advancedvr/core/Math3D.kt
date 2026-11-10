package com.advancedvr.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Vetor 3D imutável. */
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) Vec3(0f, 0f, -1f) else Vec3(x / l, y / l, z / l)
    }
    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun dist(o: Vec3) = (this - o).length()
}

/** Operações sobre matrizes 4x4 column-major (formato android.opengl.Matrix). */
object Mx {
    fun point(m: FloatArray, v: Vec3) = Vec3(
        m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12],
        m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13],
        m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
    )

    fun dir(m: FloatArray, v: Vec3) = Vec3(
        m[0] * v.x + m[4] * v.y + m[8] * v.z,
        m[1] * v.x + m[5] * v.y + m[9] * v.z,
        m[2] * v.x + m[6] * v.y + m[10] * v.z
    )
}

const val RAD2DEG = (180.0 / PI).toFloat()

/** Filtro One Euro: remove tremor com baixa latência quando o movimento é rápido. */
class OneEuro(
    private val minCutoff: Float = 1.2f,
    private val beta: Float = 0.02f,
    private val dCutoff: Float = 1f
) {
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrev = -1L

    fun reset() { tPrev = -1L }

    fun filter(x: Float, tNanos: Long): Float {
        if (tPrev < 0) { tPrev = tNanos; xPrev = x; dxPrev = 0f; return x }
        val dt = max((tNanos - tPrev) / 1e9f, 1e-3f)
        tPrev = tNanos
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1 - aD) * dxPrev
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1 - a) * xPrev
        xPrev = xHat; dxPrev = dxHat
        return xHat
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }
}
