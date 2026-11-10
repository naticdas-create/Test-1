package com.advancedvr.window

import android.graphics.PorterDuff
import android.os.Build
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import android.widget.EditText
import android.webkit.WebView
import android.view.inputmethod.EditorInfo
import com.advancedvr.VrServices
import com.advancedvr.apps.AppRegistry
import com.advancedvr.apps.DockContent
import com.advancedvr.apps.KeyboardContent
import com.advancedvr.apps.ToastContent
import com.advancedvr.apps.WindowContent
import com.advancedvr.core.GestureAction
import com.advancedvr.core.Mx
import com.advancedvr.core.Vec3
import com.advancedvr.ui.Ui
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Gerencia o ciclo de vida das janelas: abrir/fechar/minimizar/maximizar/organizar/fixar + captura das Views para texturas. */
class VrWindowManager(private val svc: VrServices, private val host: FrameLayout) {
    val windows = CopyOnWriteArrayList<VrWindow>()
    val releaseQueue = ConcurrentLinkedQueue<VrWindow>()
    @Volatile var focused: VrWindow? = null
    @Volatile var textTarget: VrWindow? = null
    var onChanged: (() -> Unit)? = null

    private var nextId = 1
    private var running = false
    private var tickCount = 0
    private var kbAuto = false
    private val metrics = svc.ctx.resources.displayMetrics

    // ------------------------------------------------------------------ geometria

    private fun headAhead(): Pair<Vec3, Vec3> {
        val m = FloatArray(16); svc.head.getHeadMatrix(m)
        var f = Vec3(-m[8], 0f, -m[10])
        f = if (f.length() < 0.2f) Vec3(0f, 0f, -1f) else f.normalized()
        return Pair(f, Vec3(-f.z, 0f, f.x)) // frente, direita
    }

    private fun spawnPos(dist: Float, dx: Float, dy: Float): Vec3 {
        val (f, r) = headAhead()
        return f * dist + r * dx + Vec3(0f, dy, 0f)
    }

    private fun sizePx(w: VrWindow) {
        val maxW = metrics.widthPixels.coerceAtLeast(800)
        val maxH = metrics.heightPixels.coerceAtLeast(500)
        w.pxW = (w.widthM * Ui.PPM).roundToInt().coerceIn(120, maxW)
        w.pxH = (w.heightM * Ui.PPM).roundToInt().coerceIn(80, maxH)
        w.widthM = w.pxW / Ui.PPM
        w.heightM = w.pxH / Ui.PPM
    }

    private fun create(kind: WinKind, appId: String, title: String, content: WindowContent, wM: Float, hM: Float, pos: Vec3): VrWindow {
        val win = VrWindow(nextId++, kind, appId, title, content, wM, hM)
        win.position = pos
        sizePx(win)
        val frame = WindowFrameView(svc.ctx, win, content.createView(svc.ctx), svc)
        win.frame = frame
        host.addView(frame, FrameLayout.LayoutParams(win.pxW, win.pxH))
        windows.add(win)
        onChanged?.invoke()
        return win
    }

    // ------------------------------------------------------------------ abrir / fechar

    fun openApp(id: String, newInstance: Boolean = false, args: Map<String, String> = emptyMap()): VrWindow? {
        val spec = AppRegistry.get(id) ?: return null
        if (!(newInstance && spec.multi)) {
            windows.firstOrNull { it.appId == id && !it.closing }?.let { restore(it); focus(it); return it }
        }
        val n = windows.count { it.kind == WinKind.APP && !it.closing }
        val pos = spawnPos(1.95f, ((n % 3) - 1) * 0.22f, 0.12f - (n % 3) * 0.06f)
        val w = create(WinKind.APP, id, spec.title, spec.create(svc, args), spec.widthM, spec.heightM, pos)
        focus(w)
        return w
    }

    fun openDock() {
        if (windows.any { it.kind == WinKind.DOCK && !it.closing }) return
        create(WinKind.DOCK, "dock", "Dock", DockContent(svc), 1.4f, 0.17f, spawnPos(1.4f, 0f, -0.72f))
    }

    fun toggleDock() {
        val d = windows.firstOrNull { it.kind == WinKind.DOCK && !it.closing }
        if (d != null) close(d) else openDock()
    }

    fun close(w: VrWindow) { w.closing = true; if (focused === w) focused = null; onChanged?.invoke() }

    fun minimize(w: VrWindow) { w.state = WinState.MINIMIZED; if (focused === w) focused = null; onChanged?.invoke() }

    fun restore(w: VrWindow) {
        if (w.state == WinState.MINIMIZED) { w.state = WinState.NORMAL }
        w.frame?.visibility = View.VISIBLE
        onChanged?.invoke()
    }

    fun focus(w: VrWindow) {
        focused = w
        if (w.kind == WinKind.APP) textTarget = w
    }

    fun toggleMaximize(w: VrWindow) {
        if (w.state == WinState.MAXIMIZED) {
            val g = w.savedGeom
            if (g != null) { w.widthM = g[0]; w.heightM = g[1]; w.position = Vec3(g[2], g[3], g[4]) }
            w.state = WinState.NORMAL
        } else {
            w.savedGeom = floatArrayOf(w.widthM, w.heightM, w.position.x, w.position.y, w.position.z)
            val a = w.widthM / w.heightM
            var nw = 2.6f; var nh = nw / a
            if (nh > 1.5f) { nh = 1.5f; nw = nh * a }
            w.widthM = nw; w.heightM = nh
            w.position = spawnPos(1.9f, 0f, 0.05f)
            w.state = WinState.MAXIMIZED
        }
        w.animScale = 0.88f
        relayout(w)
    }

    fun toggleHeadLock(w: VrWindow) {
        val m = FloatArray(16); svc.head.getHeadMatrix(m)
        val p = w.position
        if (w.mode == WinMode.WORLD) {
            // mundo -> espaço da cabeça (transposta da rotação)
            w.position = Vec3(
                m[0] * p.x + m[1] * p.y + m[2] * p.z,
                m[4] * p.x + m[5] * p.y + m[6] * p.z,
                m[8] * p.x + m[9] * p.y + m[10] * p.z
            )
            w.mode = WinMode.HEAD_LOCKED
            svc.notifications.post("Janela presa à visão", w.title, "Sistema", toast = false)
        } else {
            w.position = Mx.dir(m, p)
            w.mode = WinMode.WORLD
        }
        onChanged?.invoke()
    }

    /** Recalcula pixels após redimensionar: relayout real do conteúdo + nova superfície de textura. */
    fun relayout(w: VrWindow) {
        sizePx(w)
        val f = w.frame ?: return
        f.layoutParams = FrameLayout.LayoutParams(w.pxW, w.pxH)
        f.requestLayout()
        w.surfaceVersion++
    }

    fun back(): Boolean {
        val w = focused ?: return false
        return w.content.onBack()
    }

    fun recenter() {
        val dock = windows.firstOrNull { it.kind == WinKind.DOCK }
        val ref = dock ?: windows.firstOrNull { it.kind == WinKind.APP } ?: return
        val (f, _) = headAhead()
        val headYaw = atan2(-f.x, -f.z)
        val refYaw = atan2(-ref.position.x, -ref.position.z)
        val d = headYaw - refYaw
        val c = cos(d); val s = sin(d)
        for (w in windows) if (w.mode == WinMode.WORLD) {
            val p = w.position
            w.position = Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
        }
    }

    fun arrange() {
        val apps = windows.filter { it.kind == WinKind.APP && !it.closing && it.state != WinState.MINIMIZED }
        if (apps.isEmpty()) return
        val (f, _) = headAhead()
        val base = atan2(-f.x, -f.z)
        val step = 0.62f
        apps.forEachIndexed { i, w ->
            val a = base + (i - (apps.size - 1) / 2f) * step
            w.position = Vec3(-sin(a) * 2.0f, 0.05f, -cos(a) * 2.0f)
            if (w.state == WinState.MAXIMIZED) { w.state = WinState.NORMAL }
        }
    }

    fun perform(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.TOGGLE_DOCK -> toggleDock()
            GestureAction.BACK -> back()
            GestureAction.TOGGLE_KEYBOARD -> toggleKeyboard()
            GestureAction.OPEN_SETTINGS -> openApp("settings")
            GestureAction.OPEN_APPS -> openApp("apps")
            GestureAction.CLOSE_WINDOW -> focused?.takeIf { it.kind == WinKind.APP }?.let { close(it) }
            GestureAction.MINIMIZE_WINDOW -> focused?.takeIf { it.kind == WinKind.APP }?.let { minimize(it) }
            GestureAction.MAXIMIZE_WINDOW -> focused?.takeIf { it.kind == WinKind.APP }?.let { toggleMaximize(it) }
            GestureAction.RECENTER -> { svc.head.recenter(); recenter() }
        }
    }

    // ------------------------------------------------------------------ teclado e toasts

    fun toggleKeyboard() {
        val k = windows.firstOrNull { it.kind == WinKind.KEYBOARD && !it.closing }
        if (k != null) { close(k); kbAuto = false } else { showKeyboard(); kbAuto = false }
    }

    fun showKeyboard() {
        if (windows.any { it.kind == WinKind.KEYBOARD && !it.closing }) return
        val t = textTarget
        val pos = if (t != null) t.position + Vec3(0f, -(t.heightM / 2f + 0.42f), 0f) * 1f else spawnPos(1.5f, 0f, -0.45f)
        val p = if (t != null) Vec3(pos.x, pos.y, pos.z).let { v -> v - (v.normalized() * 0.2f) } else pos
        create(WinKind.KEYBOARD, "keyboard", "Teclado", KeyboardContent(svc), 1.5f, 0.52f, p)
    }

    fun showToast(title: String, text: String) {
        windows.filter { it.kind == WinKind.TOAST && !it.closing }.forEach { it.closing = true }
        val w = create(WinKind.TOAST, "toast", title, ToastContent(title, text), 0.95f, 0.16f, Vec3(0f, 0.42f, -1.3f))
        w.mode = WinMode.HEAD_LOCKED
        svc.main.postDelayed({ w.closing = true }, 3500)
    }

    /** Após um clique: se um campo de texto ficou focado, abre o teclado; se não, fecha o teclado automático. */
    fun onContentTouched(w: VrWindow) {
        if (w.kind != WinKind.APP) return
        svc.main.postDelayed({
            val f = w.frame?.findFocus()
            val editable = f is EditText || (f is WebView && f.onCreateInputConnection(EditorInfo()) != null)
            val kb = windows.firstOrNull { it.kind == WinKind.KEYBOARD && !it.closing }
            if (editable && kb == null) { showKeyboard(); kbAuto = true }
            else if (!editable && kb != null && kbAuto) { close(kb); kbAuto = false }
        }, 260)
    }

    // ------------------------------------------------------------------ raycast e captura

    fun raycast(o: Vec3, d: Vec3): Hit? {
        var best: Hit? = null
        for (w in windows) {
            if (!w.visible || w.closing) continue
            val h = w.intersect(o, d) ?: continue
            if (best == null || h.t < best.t) best = h
        }
        return best
    }

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(cb)
    }

    fun stop() { running = false }

    private val cb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            tick()
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun tick() {
        tickCount++
        val topHandle = svc.settings.handleOnTop
        for (w in windows) {
            if (w.closing && w.animScale < 0.02f) { finalizeClose(w); continue }
            w.handleTop = topHandle
            if (w.state == WinState.MINIMIZED && w.animScale < 0.02f) {
                if (w.frame?.visibility == View.VISIBLE) w.frame?.visibility = View.INVISIBLE
                continue
            }
            if (tickCount % 2 == 0) capture(w)
        }
    }

    private fun capture(w: VrWindow) {
        val f = w.frame ?: return
        if (f.width != w.pxW || f.height != w.pxH) return
        if (!f.dirty && tickCount % 30 != 0) return
        if (w.posted.get() >= 2) return
        synchronized(w.lock) {
            val s = w.surface ?: return
            if (!s.isValid) return
            try {
                f.dirty = false
                val c = if (w.content.softwareCapture || Build.VERSION.SDK_INT < 23) s.lockCanvas(null) else s.lockHardwareCanvas()
                c.drawColor(0, PorterDuff.Mode.CLEAR)
                f.draw(c)
                s.unlockCanvasAndPost(c)
                w.posted.incrementAndGet()
            } catch (_: Exception) { }
        }
    }

    private fun finalizeClose(w: VrWindow) {
        host.removeView(w.frame)
        windows.remove(w)
        releaseQueue.add(w)
        try { w.content.onDestroy() } catch (_: Exception) { }
        if (focused === w) focused = null
        if (textTarget === w) textTarget = null
        onChanged?.invoke()
    }
}
