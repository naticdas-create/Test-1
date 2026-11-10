package com.advancedvr.gesture

import com.advancedvr.core.Vec3
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Mão virtual SEM rastreamento: pose procedural de 21 pontos (mesma topologia do MediaPipe),
 * interpolada entre "aberta" e "pinça". A ponta do polegar+indicador fica sobre o cursor.
 * Unidades locais: comprimento da palma (punho -> base do médio) = 1.
 */
class VirtualHand {
    var pinch = 0f
        private set

    fun update(pressed: Boolean, dt: Float) {
        val target = if (pressed) 1f else 0f
        pinch += (target - pinch) * min(1f, dt * 20f)
    }

    /** Pontos no plano da vista: origem no ponto de pinça; right/up = eixos da câmera; escala em metros. */
    fun points(anchor: Vec3, right: Vec3, up: Vec3, palmLen: Float): Array<Vec3> {
        val t = pinch
        val l = FloatArray(42) { OPEN[it] + (PINCH[it] - OPEN[it]) * t }
        val mx = (l[8] + l[16]) / 2f
        val my = (l[9] + l[17]) / 2f
        val a = TILT
        val ca = cos(a); val sa = sin(a)
        return Array(21) { i ->
            val x = l[i * 2] - mx
            val y = l[i * 2 + 1] - my
            val rx = x * ca - y * sa
            val ry = x * sa + y * ca
            anchor + right * (rx * palmLen) + up * (ry * palmLen)
        }
    }

    companion object {
        const val TILT = 0.32f
        // x,y por landmark: 0 punho | 1-4 polegar | 5-8 indicador | 9-12 médio | 13-16 anelar | 17-20 mínimo
        val OPEN = floatArrayOf(
            0f, 0f,
            -0.35f, 0.20f, -0.62f, 0.45f, -0.82f, 0.70f, -0.96f, 0.95f,
            -0.28f, 0.95f, -0.33f, 1.30f, -0.36f, 1.55f, -0.38f, 1.78f,
            0f, 1.00f, 0f, 1.40f, 0f, 1.68f, 0f, 1.92f,
            0.26f, 0.95f, 0.30f, 1.30f, 0.32f, 1.55f, 0.34f, 1.75f,
            0.50f, 0.85f, 0.57f, 1.10f, 0.60f, 1.28f, 0.62f, 1.45f
        )
        val PINCH = floatArrayOf(
            0f, 0f,
            -0.35f, 0.20f, -0.55f, 0.50f, -0.62f, 0.85f, -0.50f, 1.15f,
            -0.28f, 0.95f, -0.42f, 1.25f, -0.50f, 1.38f, -0.50f, 1.17f,
            0f, 1.00f, 0.02f, 1.28f, 0.0f, 1.40f, -0.05f, 1.42f,
            0.26f, 0.95f, 0.30f, 1.22f, 0.28f, 1.34f, 0.22f, 1.36f,
            0.50f, 0.85f, 0.54f, 1.05f, 0.52f, 1.15f, 0.47f, 1.17f
        )
    }
}
