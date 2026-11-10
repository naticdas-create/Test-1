package com.advancedvr.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Botão de app arredondado com gradiente, glifo branco e indicador de "em execução" (como no dock do Quest). */
class AppButton(
    ctx: Context, private val glyph: Glyph, private val c1: Int, private val c2: Int, private val sizePx: Int
) : View(ctx) {
    var running = false
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx + 12)

    override fun onDraw(c: Canvas) {
        rect.set(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        paint.shader = LinearGradient(0f, 0f, 0f, sizePx.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, sizePx * 0.26f, sizePx * 0.26f, paint)
        paint.shader = null
        if (isPressed) { paint.color = 0x44FFFFFF; c.drawRoundRect(rect, sizePx * 0.26f, sizePx * 0.26f, paint) }
        Glyphs.draw(c, glyph, sizePx / 2f, sizePx / 2f, sizePx * 0.36f, 0xFFFFFFFF.toInt())
        if (running) {
            paint.color = 0xFFD0D4DA.toInt()
            c.drawRoundRect(sizePx * 0.38f, sizePx + 3f, sizePx * 0.62f, sizePx + 8f, 3f, 3f, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val r = super.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) invalidate()
        return r
    }
}
