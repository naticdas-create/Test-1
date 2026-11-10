package com.advancedvr.core

import android.content.Context

enum class GestureAction(val label: String) {
    NONE("Nenhuma"),
    TOGGLE_DOCK("Abrir/fechar menu"),
    BACK("Voltar"),
    TOGGLE_KEYBOARD("Teclado"),
    OPEN_SETTINGS("Configurações"),
    OPEN_APPS("Aplicativos"),
    CLOSE_WINDOW("Fechar janela"),
    MINIMIZE_WINDOW("Minimizar janela"),
    MAXIMIZE_WINDOW("Maximizar janela"),
    RECENTER("Recentralizar")
}

enum class FingerGesture(val label: String) {
    PALM_BUTTON("Tecla Volume +"),
    MIDDLE_PINCH("Tecla Volume −"),
    RING_PINCH("Toque duplo no vazio"),
    PINKY_PINCH("Toque longo no vazio")
}

class VrSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("advanced_vr", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun f(k: String, d: Float) = p.getFloat(k, d)
    private fun putB(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    private fun putF(k: String, v: Float) = p.edit().putFloat(k, v).apply()

    var passthrough: Boolean get() = b("passthrough", true); set(v) = putB("passthrough", v)
    var stereo: Boolean get() = b("stereo", true); set(v) = putB("stereo", v)
    var showHands: Boolean get() = b("showHands", true); set(v) = putB("showHands", v)
    var gesturesEnabled: Boolean get() = b("gestures", true); set(v) = putB("gestures", v)
    var handleOnTop: Boolean get() = b("handleTop", false); set(v) = putB("handleTop", v)
    var desktopMode: Boolean get() = b("desktop", true); set(v) = putB("desktop", v)

    var ipdMm: Float get() = f("ipd", 63f); set(v) = putF("ipd", v)
    var lensK1: Float get() = f("k1", 0.22f); set(v) = putF("k1", v)
    var lensK2: Float get() = f("k2", 0.12f); set(v) = putF("k2", v)
    var renderScale: Float get() = f("scale", 0.85f); set(v) = putF("scale", v)
    var fovY: Float get() = f("fov", 85f); set(v) = putF("fov", v)

    fun action(g: FingerGesture): GestureAction {
        val def = when (g) {
            FingerGesture.PALM_BUTTON -> GestureAction.TOGGLE_DOCK
            FingerGesture.MIDDLE_PINCH -> GestureAction.BACK
            FingerGesture.RING_PINCH -> GestureAction.TOGGLE_KEYBOARD
            FingerGesture.PINKY_PINCH -> GestureAction.OPEN_SETTINGS
        }
        val name = p.getString("g_" + g.name, def.name) ?: def.name
        return runCatching { GestureAction.valueOf(name) }.getOrDefault(def)
    }

    fun setAction(g: FingerGesture, a: GestureAction) {
        p.edit().putString("g_" + g.name, a.name).apply()
    }
}
