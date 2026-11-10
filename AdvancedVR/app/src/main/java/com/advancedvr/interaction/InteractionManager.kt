package com.advancedvr.interaction

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.advancedvr.VrServices
import com.advancedvr.core.FingerGesture
import com.advancedvr.core.Mx
import com.advancedvr.core.Vec3
import com.advancedvr.gesture.VirtualHand
import com.advancedvr.render.Batch
import com.advancedvr.window.Hit
import com.advancedvr.window.Region
import com.advancedvr.window.VrWindow
import com.advancedvr.window.WinMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Interação SEM rastreamento de mãos.
 *  - Modo VR (estéreo): o cursor é o olhar (centro da vista); tocar a tela = pinça (selecionar/segurar/arrastar).
 *  - Modo tela (mono): o cursor é o dedo na tela.
 * Uma mão virtual é desenhada por cima das janelas, na posição do cursor, e fecha em pinça ao tocar.
 * Estados: IDLE -> CONTENT (clique/rolagem) | GRAB (arrasta pela alça) | RESIZE (cantos) | CONSUMED.
 */
class InteractionManager(private val svc: VrServices) {
    enum class PState { IDLE, CONTENT, GRAB, RESIZE, CONSUMED }

    class Ptr {
        var active = false; var pressed = false
        var state = PState.IDLE
        var nx = 0f; var ny = 0f
        var hover: Hit? = null
        var win: VrWindow? = null
        var downTime = 0L
        var lastPx = -1f; var lastPy = -1f
        var cooldownUntil = 0L
        var dist = 2f; var offset = Vec3(); var touchY0 = 0f; var curScale = 1f
        var w0 = 0f; var h0 = 0f
        var emptyDownAt = 0L
    }

    val ptr = Ptr()
    val hand = VirtualHand()
    private var lastEmptyTap = 0L

    @Volatile var touchNx = 0f
    @Volatile var touchNy = 0f
    @Volatile var touchDown = false

    fun onTouch(e: MotionEvent, w: Int, h: Int): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val stereo = svc.settings.stereo
                val half = if (stereo) w / 2f else w.toFloat()
                val lx = if (stereo) e.x % half else e.x
                touchNx = lx / half * 2f - 1f; touchNy = 1f - e.y / h * 2f; touchDown = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchDown = false
        }
        return true
    }

    /** Teclas de volume = atalhos configuráveis (substituem os "botões nos dedos"). */
    fun onVolumeKey(up: Boolean) = runGesture(if (up) FingerGesture.PALM_BUTTON else FingerGesture.MIDDLE_PINCH)

    // ------------------------------------------------------------------ quadro

    fun update(dt: Float, head: FloatArray) {
        val stereo = svc.settings.stereo
        val p = ptr
        val pressed = touchDown
        p.active = stereo || pressed
        if (stereo) { p.nx = 0f; p.ny = 0f } else { p.nx = touchNx; p.ny = touchNy }
        val down = pressed && !p.pressed
        val up = !pressed && p.pressed

        val dir = dirWorld(p.nx, p.ny, head)
        val dirH = dirHead(p.nx, p.ny)
        if (p.state != PState.CONTENT) p.hover = if (p.active) svc.wm.raycast(Vec3(), dir) else null

        if (down) onDown(p, dir, dirH)
        if (pressed) onMove(p, dt, dir, dirH)
        if (up) onUp(p)
        p.pressed = pressed
        hand.update(pressed, dt)
    }

    private fun dirHead(nx: Float, ny: Float): Vec3 {
        val v = svc.view
        return Vec3(nx * v.tanX, ny * v.tanY, -1f).normalized()
    }

    private fun dirWorld(nx: Float, ny: Float, head: FloatArray) = Mx.dir(head, dirHead(nx, ny)).normalized()

    private fun onDown(p: Ptr, dirW: Vec3, dirH: Vec3) {
        val now = SystemClock.uptimeMillis()
        if (now < p.cooldownUntil) { p.state = PState.CONSUMED; return }
        val hit = svc.wm.raycast(Vec3(), dirW)
        p.hover = hit
        if (hit == null) { p.state = PState.CONSUMED; p.emptyDownAt = now; return }
        val w = hit.win
        svc.wm.focus(w)
        p.win = w
        when (hit.region) {
            Region.BODY -> {
                p.state = PState.CONTENT
                p.downTime = now
                val px = hit.u.coerceIn(0f, 1f) * w.pxW; val py = hit.v.coerceIn(0f, 1f) * w.pxH
                p.lastPx = px; p.lastPy = py
                sendTouch(w, MotionEvent.ACTION_DOWN, px, py, now)
            }
            Region.HANDLE -> {
                p.state = PState.GRAB
                val dir = if (w.mode == WinMode.HEAD_LOCKED) dirH else dirW
                p.dist = w.position.length().coerceAtLeast(0.6f)
                p.offset = w.position - dir * p.dist
                p.touchY0 = touchNy; p.curScale = 1f
            }
            Region.GRIP_L, Region.GRIP_R -> {
                p.state = PState.RESIZE
                p.w0 = w.widthM; p.h0 = w.heightM
            }
        }
    }

    private fun onMove(p: Ptr, dt: Float, dirW: Vec3, dirH: Vec3) {
        val w = p.win ?: return
        when (p.state) {
            PState.CONTENT -> {
                val ph = w.planeHit(Vec3(), dirW) ?: return
                val u = (ph[1] / w.widthM + 0.5f).coerceIn(0f, 1f)
                val v = (0.5f - ph[2] / w.heightM).coerceIn(0f, 1f)
                val px = u * w.pxW; val py = v * w.pxH
                if (abs(px - p.lastPx) >= 1.5f || abs(py - p.lastPy) >= 1.5f) {
                    p.lastPx = px; p.lastPy = py
                    sendTouch(w, MotionEvent.ACTION_MOVE, px, py, p.downTime)
                }
            }
            PState.GRAB -> {
                val dir = if (w.mode == WinMode.HEAD_LOCKED) dirH else dirW
                // Arrastar o dedo para cima/baixo na tela afasta/aproxima a janela (profundidade real)
                val target = exp((touchNy - p.touchY0) * 0.9f)
                p.curScale += (target - p.curScale) * min(1f, dt * 8f)
                val d = (p.dist * p.curScale).coerceIn(0.7f, 7f)
                var t = dir * d + p.offset
                t = Vec3(t.x, t.y.coerceIn(-2.2f, 2.8f), t.z)
                w.position = w.position.lerp(t, 1f - exp(-dt * 16f))
            }
            PState.RESIZE -> {
                val ph = w.planeHit(Vec3(), dirW) ?: return
                val k = (abs(ph[1]) / (p.w0 / 2f + 0.05f)).coerceIn(0.45f, 2.5f)
                w.widthM = p.w0 * k; w.heightM = p.h0 * k
            }
            else -> {}
        }
    }

    private fun onUp(p: Ptr) {
        val w = p.win
        val now = SystemClock.uptimeMillis()
        when (p.state) {
            PState.CONTENT -> if (w != null) {
                sendTouch(w, MotionEvent.ACTION_UP, p.lastPx, p.lastPy, p.downTime)
                svc.wm.onContentTouched(w)
                p.cooldownUntil = now + 200
            }
            PState.RESIZE -> if (w != null) svc.main.post { svc.wm.relayout(w) }
            PState.CONSUMED -> if (p.win == null && p.emptyDownAt != 0L) {
                // toque no vazio: longo / duplo = atalhos configuráveis
                if (now - p.emptyDownAt > 600) runGesture(FingerGesture.PINKY_PINCH)
                else { if (now - lastEmptyTap < 350) { runGesture(FingerGesture.RING_PINCH); lastEmptyTap = 0 } else lastEmptyTap = now }
                p.emptyDownAt = 0L
            }
            else -> {}
        }
        p.state = PState.IDLE; p.win = null
    }

    private fun sendTouch(w: VrWindow, action: Int, x: Float, y: Float, downTime: Long) {
        svc.main.post {
            val f = w.frame ?: return@post
            val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            f.dispatchTouchEvent(ev)
            ev.recycle()
        }
    }

    private fun runGesture(g: FingerGesture) {
        val a = svc.settings.action(g)
        svc.main.post { svc.wm.perform(a) }
    }

    // ------------------------------------------------------------------ sobreposição (SEMPRE por cima das janelas)

    private fun toWorld(nx: Float, ny: Float, depth: Float, head: FloatArray): Vec3 {
        val v = svc.view
        return Mx.dir(head, Vec3(nx * v.tanX * depth, ny * v.tanY * depth, -depth))
    }

    fun buildOverlay(b: Batch, head: FloatArray) {
        val right = Vec3(head[0], head[1], head[2])
        val up = Vec3(head[4], head[5], head[6])
        val fwd = Vec3(-head[8], -head[9], -head[10])
        val p = ptr
        val focused = svc.wm.focused

        // Alças brancas (barra de movimentação) e cantos de redimensionamento
        for (w in svc.wm.windows) {
            if (!w.visible || !w.grabbable || w.closing || w.animScale < 0.6f) continue
            val n = w.normalWorld()
            val hy = w.handleY()
            val h = p.hover
            val hovered = p.active && h != null && h.win === w
            val onHandle = hovered && h!!.region == Region.HANDLE
            val onGrip = hovered && (h!!.region == Region.GRIP_L || h.region == Region.GRIP_R)
            val grabbed = p.state == PState.GRAB && p.win === w
            val resizing = p.state == PState.RESIZE && p.win === w
            val r = if (grabbed) 0.0105f else if (onHandle) 0.0095f else 0.0075f
            val a = Mx.point(w.model, Vec3(-0.17f, hy, 0.004f))
            val c = Mx.point(w.model, Vec3(0.17f, hy, 0.004f))
            b.capsule(a, c, r, n, if (onHandle || grabbed) 0xFFFFFFFF.toInt() else 0xE6F2F2F2.toInt())
            if (w.resizable && (onGrip || resizing || (hovered && w === focused))) {
                val hw = w.widthM / 2f; val hh = w.heightM / 2f; val g = 0.03f; val l = 0.06f
                for (sx in intArrayOf(-1, 1)) {
                    val cx = sx * (hw + g); val cy = -(hh + g)
                    val corner = Mx.point(w.model, Vec3(cx, cy, 0.004f))
                    b.capsule(corner, Mx.point(w.model, Vec3(cx - sx * l, cy, 0.004f)), 0.006f, n, 0xFFFFFFFF.toInt())
                    b.capsule(corner, Mx.point(w.model, Vec3(cx, cy + l, 0.004f)), 0.006f, n, 0xFFFFFFFF.toInt())
                }
            }
        }

        if (!p.active) return
        val hov = p.hover
        if (hov != null && p.state != PState.GRAB) {
            val n = hov.win.normalWorld()
            val c = hov.world + n * 0.012f
            val s0 = toWorld(p.nx, p.ny, 0.5f, head) + up * -0.07f + right * 0.03f
            b.ribbon(s0, c, 0.004f, fwd * -1f, 0x00FFFFFF, 0xCCFFFFFF.toInt())
            val r = if (p.pressed) 0.009f else 0.013f
            b.circle(c, right, up, r + 0.003f, 0xCC101216.toInt(), 18)
            b.circle(c, right, up, r, 0xFFFFFFFF.toInt(), 18)
            if (!p.pressed) b.circle(c, right, up, r * 0.45f, 0xFF2F6BFF.toInt(), 12)
        }
        if (svc.settings.showHands) drawHand(b, head, right, up, fwd)
    }

    private val chains = arrayOf(intArrayOf(1, 2, 3, 4), intArrayOf(5, 6, 7, 8), intArrayOf(9, 10, 11, 12), intArrayOf(13, 14, 15, 16), intArrayOf(17, 18, 19, 20))
    private val palmPoly = intArrayOf(0, 1, 5, 9, 13, 17)

    /** Mão virtual: contorno escuro + dedos em cinza, renderizada por último (sem teste de profundidade). */
    private fun drawHand(b: Batch, head: FloatArray, right: Vec3, up: Vec3, fwd: Vec3) {
        val p = ptr
        val anchor = toWorld(p.nx, p.ny, 0.6f, head)
        val palmLen = 0.13f
        val pts = hand.points(anchor, right, up, palmLen)
        val n = fwd * -1f
        val outline = 0xFF0C0E11.toInt()
        val fill = 0xFF555A62.toInt()
        val ro = palmLen * 0.02f

        fun pass(extra: Float, col: Int) {
            for ((ci, ch) in chains.withIndex()) {
                val r = palmLen * (if (ci == 0) 0.105f else if (ci == 4) 0.085f else 0.095f) + extra
                for (k in 0 until ch.size - 1) b.capsule(pts[ch[k]], pts[ch[k + 1]], r, n, col)
            }
            var cx = 0f; var cy = 0f; var cz = 0f
            for (i in palmPoly) { cx += pts[i].x; cy += pts[i].y; cz += pts[i].z }
            val c = Vec3(cx / palmPoly.size, cy / palmPoly.size, cz / palmPoly.size)
            for (k in palmPoly.indices) {
                val a = pts[palmPoly[k]]; val bb = pts[palmPoly[(k + 1) % palmPoly.size]]
                b.tri(c, a, bb, col)
                b.capsule(a, bb, palmLen * 0.09f + extra, n, col)
            }
        }
        pass(ro, outline)
        pass(0f, fill)
        if (hand.pinch > 0.8f) b.circle((pts[4] + pts[8]) * 0.5f, right, up, palmLen * 0.06f, 0xFFFFFFFF.toInt(), 12)
    }
}
