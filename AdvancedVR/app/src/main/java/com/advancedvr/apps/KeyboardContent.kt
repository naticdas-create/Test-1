package com.advancedvr.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.advancedvr.VrServices
import com.advancedvr.ui.Ui

/** Teclado virtual próprio para VR: teclas grandes, feedback visual, shift, números/símbolos, espaço, apagar, enter. */
class KeyboardContent(private val svc: VrServices) : WindowContent {
    override fun createView(ctx: Context): View = KeyboardView(ctx, svc)
}

private enum class KT { CHAR, SHIFT, BACK, ENTER, SPACE, SYM, HIDE }
private class Key(val label: String, val type: KT, val weight: Float = 1f) { val r = RectF() }

private class KeyboardView(ctx: Context, private val svc: VrServices) : View(ctx) {
    private var shift = false
    private var symbols = false
    private var pressed: Key? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rows: List<List<Key>> = emptyList()

    private fun chars(s: String) = s.map { Key(it.toString(), KT.CHAR) }

    private fun build() {
        rows = if (!symbols) listOf(
            chars("1234567890"),
            chars("qwertyuiop"),
            chars("asdfghjkl") + Key("⌫", KT.BACK, 1.4f),
            listOf(Key("⇧", KT.SHIFT, 1.4f)) + chars("zxcvbnm") + Key("↵", KT.ENTER, 1.4f),
            listOf(Key("?123", KT.SYM, 1.6f), Key(",", KT.CHAR), Key("espaço", KT.SPACE, 5f), Key(".", KT.CHAR), Key("/", KT.CHAR), Key("⌄", KT.HIDE, 1.4f))
        ) else listOf(
            chars("1234567890"),
            chars("@#$%&*-+()"),
            chars("!\"':;?=_~") + Key("⌫", KT.BACK, 1.4f),
            chars("<>[]{}\\|^") + Key("↵", KT.ENTER, 1.4f),
            listOf(Key("ABC", KT.SYM, 1.6f), Key(",", KT.CHAR), Key("espaço", KT.SPACE, 5f), Key(".", KT.CHAR), Key("/", KT.CHAR), Key("⌄", KT.HIDE, 1.4f))
        )
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val pad = 8f; val gap = 7f
        val rh = (height - pad * 2 - gap * (rows.size - 1)) / rows.size
        rows.forEachIndexed { ri, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val avail = width - pad * 2 - gap * (row.size - 1)
            var x = pad
            for (k in row) {
                val w = avail * k.weight / total
                k.r.set(x, pad + ri * (rh + gap), x + w, pad + ri * (rh + gap) + rh)
                x += w + gap
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { if (rows.isEmpty()) build() else layoutKeys() }

    override fun onDraw(c: Canvas) {
        c.drawColor(Ui.PANEL)
        if (rows.isEmpty()) build()
        paint.textAlign = Paint.Align.CENTER
        for (row in rows) for (k in row) {
            val isP = k === pressed
            paint.color = when {
                isP -> Ui.ACCENT
                k.type == KT.CHAR || k.type == KT.SPACE -> 0xFF30343A.toInt()
                k.type == KT.SHIFT && shift -> 0xFF4C6FBF.toInt()
                else -> 0xFF262A30.toInt()
            }
            val r = if (isP) RectF(k.r.left - 2, k.r.top - 4, k.r.right + 2, k.r.bottom) else k.r
            c.drawRoundRect(r, 14f, 14f, paint)
            paint.color = Ui.TEXT
            paint.textSize = if (k.label.length > 1) 24f else 34f
            val label = if (k.type == KT.CHAR && shift && !symbols) k.label.uppercase() else k.label
            c.drawText(label, k.r.centerX(), k.r.centerY() + paint.textSize * 0.34f, paint)
        }
    }

    private fun keyAt(x: Float, y: Float): Key? { for (row in rows) for (k in row) if (k.r.contains(x, y)) return k; return null }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { pressed = keyAt(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP -> {
                val k = pressed
                pressed = null
                if (k != null && k === keyAt(e.x, e.y)) fire(k)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    private fun fire(k: Key) {
        when (k.type) {
            KT.CHAR -> { svc.keyboard.commit(if (shift && !symbols) k.label.uppercase() else k.label); if (shift) { shift = false } }
            KT.SPACE -> svc.keyboard.commit(" ")
            KT.BACK -> svc.keyboard.backspace()
            KT.ENTER -> svc.keyboard.enter()
            KT.SHIFT -> shift = !shift
            KT.SYM -> { symbols = !symbols; build() }
            KT.HIDE -> svc.wm.toggleKeyboard()
        }
    }
}

/** Notificação flutuante presa à visão. */
class ToastContent(private val title: String, private val text: String) : WindowContent {
    override fun createView(ctx: Context): View {
        val box = Ui.vbox(ctx).apply { setPadding(28, 14, 28, 14); setBackgroundColor(0xF01B1D21.toInt()); gravity = android.view.Gravity.CENTER_VERTICAL }
        box.addView(Ui.text(ctx, title, 24f, Ui.TEXT, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        box.addView(Ui.text(ctx, text, 20f, Ui.SUB).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        return box
    }
}
