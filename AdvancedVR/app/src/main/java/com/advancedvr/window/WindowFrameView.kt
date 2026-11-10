package com.advancedvr.window

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.advancedvr.VrServices
import com.advancedvr.ui.Glyph
import com.advancedvr.ui.Glyphs
import com.advancedvr.ui.Ui

/** Raiz da janela: conteúdo + barra de título (X, minimizar, título, maximizar, fixar na visão). */
class WindowFrameView(ctx: Context, val win: VrWindow, content: View, svc: VrServices) : LinearLayout(ctx) {
    @Volatile var dirty = true

    init {
        orientation = VERTICAL
        setBackgroundColor(if (win.kind == WinKind.APP || win.kind == WinKind.KEYBOARD) Ui.PANEL else 0xF01B1D21.toInt())
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        if (win.hasTitleBar) addView(TitleBar(ctx, win, svc), LayoutParams(LayoutParams.MATCH_PARENT, Ui.BAR_PX))
    }

    override fun onDescendantInvalidated(child: View, target: View) { dirty = true; super.onDescendantInvalidated(child, target) }
    override fun invalidate() { dirty = true; super.invalidate() }
}

private class TitleBar(ctx: Context, private val win: VrWindow, private val svc: VrServices) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btn = Ui.BAR_PX
    private var pressedIdx = -1

    // índices: 0 fechar, 1 minimizar, 2 maximizar, 3 fixar na visão
    private fun rectX(i: Int): Float = if (i < 2) 12f + i * (btn + 4f) else width - 12f - (4 - i) * (btn + 4f) + 4f

    private fun hit(x: Float): Int {
        for (i in 0..3) { val l = rectX(i); if (x >= l && x <= l + btn) return i }
        return -1
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Ui.BAR)
        for (i in 0..3) {
            val l = rectX(i)
            if (pressedIdx == i) { paint.color = 0x33FFFFFF; c.drawCircle(l + btn / 2f, height / 2f, btn / 2f - 4f, paint) }
            val g = when (i) { 0 -> Glyph.CLOSE; 1 -> Glyph.MINUS; 2 -> Glyph.MAXIMIZE; else -> Glyph.PIN }
            val col = if (i == 3 && win.mode == WinMode.HEAD_LOCKED) Ui.ACCENT else 0xFFE6E8EB.toInt()
            Glyphs.draw(c, g, l + btn / 2f, height / 2f, btn * 0.34f, col)
        }
        paint.color = Ui.SUB; paint.textSize = 22f; paint.textAlign = Paint.Align.CENTER
        c.drawText(win.title, width / 2f, height / 2f + 8f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedIdx = hit(e.x); invalidate() }
            MotionEvent.ACTION_UP -> {
                val i = hit(e.x)
                if (i == pressedIdx) when (i) {
                    0 -> svc.wm.close(win)
                    1 -> svc.wm.minimize(win)
                    2 -> svc.wm.toggleMaximize(win)
                    3 -> svc.wm.toggleHeadLock(win)
                }
                pressedIdx = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedIdx = -1; invalidate() }
        }
        return true
    }
}
