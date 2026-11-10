package com.advancedvr.tracking

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rastreamento de cabeça (3DoF) via GAME_ROTATION_VECTOR (giroscópio + acelerômetro, sem magnetômetro).
 * Sem ARCore. Entrega a matriz cabeça->mundo no sistema GL (y para cima, -z à frente).
 */
class HeadTracker(private val ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val lock = Any()
    private val r9 = FloatArray(9)
    private val remap = FloatArray(9)
    private val h = FloatArray(9) // matriz 3x3 row-major (cabeça->mundo GL), antes do yaw offset
    private val head = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    @Volatile private var yawOffset = 0f
    private var needsInitialCenter = true
    val available: Boolean get() = sensor != null

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) } }
    fun stop() { sm.unregisterListener(this) }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        (ctx as? Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_90

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(r9, e.values)
        if (displayRotation() == Surface.ROTATION_270)
            SensorManager.remapCoordinateSystem(r9, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remap)
        else
            SensorManager.remapCoordinateSystem(r9, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remap)
        synchronized(lock) {
            // ENU (x=leste,y=norte,z=cima) -> GL (x=leste,y=cima,z=-norte)
            for (c in 0..2) {
                h[0 * 3 + c] = remap[0 * 3 + c]
                h[1 * 3 + c] = remap[2 * 3 + c]
                h[2 * 3 + c] = -remap[1 * 3 + c]
            }
            if (needsInitialCenter) { needsInitialCenter = false; recenterLocked() }
            val phi = -yawOffset
            val cs = cos(phi); val sn = sin(phi)
            // M = Ry(phi) * H ; Ry = [[c,0,s],[0,1,0],[-s,0,c]]
            for (c in 0..2) {
                val r0 = cs * h[c] + sn * h[6 + c]
                val r1 = h[3 + c]
                val r2 = -sn * h[c] + cs * h[6 + c]
                head[c * 4 + 0] = r0
                head[c * 4 + 1] = r1
                head[c * 4 + 2] = r2
            }
            head[3] = 0f; head[7] = 0f; head[11] = 0f
            head[12] = 0f; head[13] = 0f; head[14] = 0f; head[15] = 1f
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    private fun recenterLocked() {
        val fx = -h[2]
        val fz = -h[8]
        yawOffset = atan2(-fx, -fz)
    }

    /** Define a direção atual como "frente" (yaw). */
    fun recenter() { synchronized(lock) { recenterLocked() } }

    fun getHeadMatrix(out: FloatArray) { synchronized(lock) { System.arraycopy(head, 0, out, 0, 16) } }

    /** Ângulo de yaw da direção do olhar (0 = olhando para -z). */
    fun yaw(): Float {
        val m = FloatArray(16); getHeadMatrix(m)
        return atan2(m[8], m[10])
    }
}
