package com.advancedvr.apps

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.advancedvr.VrServices
import com.advancedvr.ui.AppButton
import com.advancedvr.ui.Glyph
import com.advancedvr.ui.Glyphs
import com.advancedvr.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dock inferior (relógio, bateria, Wi-Fi, notificações + atalhos de apps), no estilo do Quest. */
class DockContent(private val svc: VrServices) : WindowContent {
    private val buttons = HashMap<String, AppButton>()
    private var status: StatusView? = null
    private val refresh = Runnable { }

    private inner class StatusView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private var battery = 1f
        private var wifi = false
        private val tick = object : Runnable {
            override fun run() { update(); invalidate(); postDelayed(this, 15000) }
        }

        init { isClickable = true }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); tick.run() }
        override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

        private fun update() {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) battery = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1).toFloat()
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            wifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(150, 100)

        override fun onDraw(c: Canvas) {
            p.color = Ui.TEXT; p.textSize = 32f; p.isFakeBoldText = true; p.textAlign = Paint.Align.LEFT
            c.drawText(fmt.format(Date()), 8f, 42f, p)
            Glyphs.draw(c, Glyph.BATTERY, 28f, 76f, 20f, Ui.SUB.toInt(), battery)
            Glyphs.draw(c, Glyph.WIFI, 72f, 76f, 20f, if (wifi) Ui.TEXT else Ui.SUB)
            Glyphs.draw(c, Glyph.BELL, 114f, 76f, 20f, Ui.TEXT)
            if (svc.notifications.items.isNotEmpty()) { p.color = 0xFFFF4B4B.toInt(); c.drawCircle(126f, 62f, 6f, p) }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                if (e.x > 95f) svc.wm.openApp("notifications") else svc.wm.openApp("settings")
            }
            return true
        }
    }

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(22, 0, 22, 0) }
        val sv = StatusView(ctx); status = sv
        root.addView(sv)
        root.addView(Ui.spacer(ctx, 8))
        fun app(id: String, g: Glyph, c1: Int, c2: Int, onClick: () -> Unit) {
            val b = AppButton(ctx, g, c1, c2, 76)
            b.setOnClickListener { onClick() }
            buttons[id] = b
            root.addView(b, LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 0, 6, 0) })
        }
        fun open(id: String) = { svc.wm.openApp(id); Unit }
        for (id in listOf("browser", "files", "gallery", "youtube")) {
            val s = AppRegistry.get(id)!!
            app(id, s.glyph, s.c1, s.c2, open(id))
        }
        root.addView(View(ctx).apply { setBackgroundColor(Ui.DIV) }, LinearLayout.LayoutParams(2, 60).apply { setMargins(10, 0, 10, 0) })
        for (id in listOf("google", "settings")) {
            val s = AppRegistry.get(id)!!
            app(id, s.glyph, s.c1, s.c2, open(id))
        }
        app("keyboard", Glyph.KEYBOARD, 0xFF4B5563.toInt(), 0xFF2F3640.toInt()) { svc.wm.toggleKeyboard() }
        app("apps", Glyph.GRID, 0xFF3A3F46.toInt(), 0xFF24272C.toInt(), open("apps"))

        svc.wm.onChanged = { svc.main.post { refreshRunning() } }
        refreshRunning()
        return root
    }

    private fun refreshRunning() {
        for ((id, b) in buttons) b.running = svc.wm.windows.any { it.appId == id && !it.closing }
        status?.invalidate()
    }
}
