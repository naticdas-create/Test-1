package com.advancedvr.apps

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.advancedvr.VrServices
import com.advancedvr.core.FingerGesture
import com.advancedvr.core.GestureAction
import com.advancedvr.ui.Ui

/** Configurações reais do sistema VR (persistidas em SharedPreferences). */
class SettingsContent(private val svc: VrServices) : WindowContent {
    override fun createView(ctx: Context): View {
        val s = svc.settings
        val box = Ui.vbox(ctx).apply { setPadding(30, 20, 30, 30) }
        fun title(t: String) = box.addView(Ui.text(ctx, t, 24f, Ui.ACCENT, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22; bottomMargin = 6 })
        fun sw(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val row = Ui.hbox(ctx)
            row.addView(Ui.text(ctx, label, 23f), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Switch(ctx).apply { isChecked = get(); setOnCheckedChangeListener { _, v -> set(v) } })
            box.addView(row, LinearLayout.LayoutParams(-1, 64))
        }
        fun seek(label: String, min: Float, max: Float, get: () -> Float, set: (Float) -> Unit, unit: String = "") {
            val lbl = Ui.text(ctx, "", 23f)
            fun upd() { lbl.text = "$label: ${"%.2f".format(get())}$unit" }
            upd()
            val sb = SeekBar(ctx).apply {
                this.max = 100
                progress = (((get() - min) / (max - min)) * 100).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) { if (user) { set(min + (max - min) * p / 100f); upd() } }
                    override fun onStartTrackingTouch(b: SeekBar?) {}
                    override fun onStopTrackingTouch(b: SeekBar?) {}
                })
            }
            box.addView(lbl, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
            box.addView(sb, LinearLayout.LayoutParams(-1, 52))
        }

        title("Tela e passthrough")
        sw("Passthrough da câmera (fundo)", { s.passthrough }, { s.passthrough = it })
        sw("Modo VR estéreo (lentes)", { s.stereo }, { s.stereo = it })
        seek("Distância interpupilar", 55f, 72f, { s.ipdMm }, { s.ipdMm = it }, " mm")
        seek("Campo de visão", 60f, 110f, { s.fovY }, { s.fovY = it }, "°")
        seek("Distorção da lente (k1)", 0f, 0.6f, { s.lensK1 }, { s.lensK1 = it })
        seek("Distorção da lente (k2)", 0f, 0.4f, { s.lensK2 }, { s.lensK2 = it })
        seek("Resolução de renderização", 0.5f, 1f, { s.renderScale }, { s.renderScale = it })

        title("Interface")
        sw("Mostrar mão virtual", { s.showHands }, { s.showHands = it })
        sw("Barra branca em cima da janela", { s.handleOnTop }, { s.handleOnTop = it })
        sw("Navegador em modo desktop (novas janelas)", { s.desktopMode }, { s.desktopMode = it })
        box.addView(Ui.button(ctx, "Recentralizar visão") { svc.wm.perform(GestureAction.RECENTER) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14 })
        box.addView(Ui.button(ctx, "Organizar janelas") { svc.wm.arrange() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })

        title("Atalhos (toque para alternar a ação)")
        sw("Atalhos habilitados", { s.gesturesEnabled }, { s.gesturesEnabled = it })
        for (g in FingerGesture.values()) {
            val b = Ui.button(ctx, "") { }
            fun label() { b.text = "${g.label}  →  ${s.action(g).label}" }
            label()
            b.setOnClickListener {
                val all = GestureAction.values()
                s.setAction(g, all[(s.action(g).ordinal + 1) % all.size]); label()
            }
            box.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
        }

        title("Sistema")
        box.addView(Ui.button(ctx, "Permitir notificações do Android") {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, LinearLayout.LayoutParams(-1, -2))
        box.addView(Ui.text(ctx, "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\nRastreamento de cabeça: " +
            (if (svc.head.available) "giroscópio OK" else "sem sensor"), 20f, Ui.SUB), LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14 })
        return ScrollView(ctx).apply { setBackgroundColor(Ui.PANEL); addView(box) }
    }
}
