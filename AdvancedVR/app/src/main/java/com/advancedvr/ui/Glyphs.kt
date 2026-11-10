package com.advancedvr.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

enum class Glyph {
    COMPASS, BRIEFCASE, GALLERY, SHARE, YOUTUBE, GRID, GEAR, FOLDER, GLOBE, BELL, WIFI, BATTERY,
    GOOGLE, ANDROID, KEYBOARD, CLOSE, MINUS, MAXIMIZE, PIN, MORE, BACK, FORWARD, RELOAD, SEARCH, PLUS, HOME
}

/** Ícones vetoriais desenhados em Canvas (sem depender de arquivos de imagem). */
object Glyphs {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    fun draw(c: Canvas, g: Glyph, cx: Float, cy: Float, r: Float, color: Int, battery: Float = 1f) {
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (g) {
            Glyph.CLOSE -> { c.drawLine(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, p); c.drawLine(cx + r * 0.55f, cy - r * 0.55f, cx - r * 0.55f, cy + r * 0.55f, p) }
            Glyph.MINUS -> c.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, p)
            Glyph.PLUS -> { c.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, p); c.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.55f, p) }
            Glyph.MAXIMIZE -> { rf.set(cx - r * 0.55f, cy - r * 0.45f, cx + r * 0.55f, cy + r * 0.45f); c.drawRoundRect(rf, r * 0.12f, r * 0.12f, p) }
            Glyph.PIN -> { c.drawCircle(cx, cy - r * 0.15f, r * 0.34f, p); c.drawLine(cx, cy + r * 0.2f, cx, cy + r * 0.65f, p) }
            Glyph.MORE -> { p.style = Paint.Style.FILL; for (i in -1..1) c.drawCircle(cx + i * r * 0.5f, cy, r * 0.11f, p) }
            Glyph.BACK -> { path.reset(); path.moveTo(cx + r * 0.25f, cy - r * 0.55f); path.lineTo(cx - r * 0.3f, cy); path.lineTo(cx + r * 0.25f, cy + r * 0.55f); c.drawPath(path, p) }
            Glyph.FORWARD -> { path.reset(); path.moveTo(cx - r * 0.25f, cy - r * 0.55f); path.lineTo(cx + r * 0.3f, cy); path.lineTo(cx - r * 0.25f, cy + r * 0.55f); c.drawPath(path, p) }
            Glyph.RELOAD -> {
                rf.set(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f); c.drawArc(rf, -60f, 290f, false, p)
                path.reset(); path.moveTo(cx + r * 0.62f, cy - r * 0.55f); path.lineTo(cx + r * 0.5f, cy - r * 0.15f); path.lineTo(cx + r * 0.1f, cy - r * 0.3f); c.drawPath(path, p)
            }
            Glyph.SEARCH -> { c.drawCircle(cx - r * 0.1f, cy - r * 0.1f, r * 0.42f, p); c.drawLine(cx + r * 0.2f, cy + r * 0.2f, cx + r * 0.6f, cy + r * 0.6f, p) }
            Glyph.HOME -> { path.reset(); path.moveTo(cx - r * 0.6f, cy); path.lineTo(cx, cy - r * 0.55f); path.lineTo(cx + r * 0.6f, cy); path.moveTo(cx - r * 0.4f, cy - r * 0.1f); path.lineTo(cx - r * 0.4f, cy + r * 0.5f); path.lineTo(cx + r * 0.4f, cy + r * 0.5f); path.lineTo(cx + r * 0.4f, cy - r * 0.1f); c.drawPath(path, p) }
            Glyph.COMPASS -> {
                p.strokeWidth = r * 0.14f; c.drawCircle(cx, cy, r * 0.62f, p)
                p.style = Paint.Style.FILL
                path.reset(); path.moveTo(cx + r * 0.3f, cy - r * 0.3f); path.lineTo(cx + r * 0.1f, cy + r * 0.1f); path.lineTo(cx - r * 0.3f, cy + r * 0.3f); path.lineTo(cx - r * 0.1f, cy - r * 0.1f); path.close(); c.drawPath(path, p)
            }
            Glyph.BRIEFCASE -> {
                p.style = Paint.Style.FILL; rf.set(cx - r * 0.62f, cy - r * 0.32f, cx + r * 0.62f, cy + r * 0.5f); c.drawRoundRect(rf, r * 0.14f, r * 0.14f, p)
                p.style = Paint.Style.STROKE; rf.set(cx - r * 0.25f, cy - r * 0.58f, cx + r * 0.25f, cy - r * 0.2f); c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p)
            }
            Glyph.GALLERY -> {
                rf.set(cx - r * 0.62f, cy - r * 0.5f, cx + r * 0.62f, cy + r * 0.5f); c.drawRoundRect(rf, r * 0.14f, r * 0.14f, p)
                path.reset(); path.moveTo(cx - r * 0.55f, cy + r * 0.35f); path.lineTo(cx - r * 0.15f, cy - r * 0.05f); path.lineTo(cx + r * 0.1f, cy + r * 0.2f); path.lineTo(cx + r * 0.3f, cy); path.lineTo(cx + r * 0.55f, cy + r * 0.3f); c.drawPath(path, p)
                p.style = Paint.Style.FILL; c.drawCircle(cx + r * 0.28f, cy - r * 0.25f, r * 0.1f, p)
            }
            Glyph.SHARE -> {
                path.reset(); path.moveTo(cx - r * 0.55f, cy + r * 0.45f); path.quadTo(cx - r * 0.5f, cy - r * 0.2f, cx + r * 0.2f, cy - r * 0.2f); c.drawPath(p.let { path }, p)
                path.reset(); path.moveTo(cx + r * 0.05f, cy - r * 0.62f); path.lineTo(cx + r * 0.6f, cy - r * 0.2f); path.lineTo(cx + r * 0.05f, cy + r * 0.22f); c.drawPath(path, p)
            }
            Glyph.YOUTUBE -> {
                rf.set(cx - r * 0.65f, cy - r * 0.45f, cx + r * 0.65f, cy + r * 0.45f); c.drawRoundRect(rf, r * 0.25f, r * 0.25f, p)
                p.style = Paint.Style.FILL; path.reset(); path.moveTo(cx - r * 0.15f, cy - r * 0.22f); path.lineTo(cx + r * 0.25f, cy); path.lineTo(cx - r * 0.15f, cy + r * 0.22f); path.close(); c.drawPath(path, p)
            }
            Glyph.GRID -> { p.style = Paint.Style.FILL; for (i in -1..1) for (j in -1..1) c.drawCircle(cx + i * r * 0.42f, cy + j * r * 0.42f, r * 0.1f, p) }
            Glyph.GEAR -> {
                c.drawCircle(cx, cy, r * 0.2f, p); c.drawCircle(cx, cy, r * 0.5f, p)
                for (k in 0 until 8) { val a = Math.toRadians(k * 45.0); c.drawLine(cx + (Math.cos(a) * r * 0.5).toFloat(), cy + (Math.sin(a) * r * 0.5).toFloat(), cx + (Math.cos(a) * r * 0.68).toFloat(), cy + (Math.sin(a) * r * 0.68).toFloat(), p) }
            }
            Glyph.FOLDER -> {
                path.reset(); path.moveTo(cx - r * 0.62f, cy - r * 0.35f); path.lineTo(cx - r * 0.2f, cy - r * 0.35f); path.lineTo(cx - r * 0.05f, cy - r * 0.18f); path.lineTo(cx + r * 0.62f, cy - r * 0.18f)
                path.lineTo(cx + r * 0.62f, cy + r * 0.45f); path.lineTo(cx - r * 0.62f, cy + r * 0.45f); path.close(); c.drawPath(path, p)
            }
            Glyph.GLOBE -> {
                c.drawCircle(cx, cy, r * 0.6f, p); rf.set(cx - r * 0.25f, cy - r * 0.6f, cx + r * 0.25f, cy + r * 0.6f); c.drawOval(rf, p)
                c.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, p)
            }
            Glyph.BELL -> {
                path.reset(); path.moveTo(cx - r * 0.5f, cy + r * 0.35f); path.quadTo(cx - r * 0.4f, cy + r * 0.2f, cx - r * 0.4f, cy - r * 0.1f)
                path.quadTo(cx - r * 0.4f, cy - r * 0.6f, cx, cy - r * 0.6f); path.quadTo(cx + r * 0.4f, cy - r * 0.6f, cx + r * 0.4f, cy - r * 0.1f)
                path.quadTo(cx + r * 0.4f, cy + r * 0.2f, cx + r * 0.5f, cy + r * 0.35f); path.close(); c.drawPath(path, p)
                c.drawLine(cx - r * 0.12f, cy + r * 0.6f, cx + r * 0.12f, cy + r * 0.6f, p)
            }
            Glyph.WIFI -> { for (i in 1..3) { rf.set(cx - r * 0.25f * i, cy + r * 0.3f - r * 0.25f * i, cx + r * 0.25f * i, cy + r * 0.3f + r * 0.25f * i); c.drawArc(rf, -135f, 90f, false, p) }; p.style = Paint.Style.FILL; c.drawCircle(cx, cy + r * 0.3f, r * 0.08f, p) }
            Glyph.BATTERY -> {
                rf.set(cx - r * 0.7f, cy - r * 0.3f, cx + r * 0.6f, cy + r * 0.3f); c.drawRoundRect(rf, r * 0.1f, r * 0.1f, p)
                p.style = Paint.Style.FILL; c.drawRect(cx + r * 0.64f, cy - r * 0.1f, cx + r * 0.74f, cy + r * 0.1f, p)
                rf.set(cx - r * 0.6f, cy - r * 0.2f, cx - r * 0.6f + r * 1.1f * battery.coerceIn(0.05f, 1f), cy + r * 0.2f); c.drawRoundRect(rf, r * 0.05f, r * 0.05f, p)
            }
            Glyph.GOOGLE -> { p.style = Paint.Style.FILL; p.textSize = r * 1.5f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true; c.drawText("G", cx, cy + r * 0.52f, p); p.isFakeBoldText = false }
            Glyph.ANDROID -> {
                rf.set(cx - r * 0.5f, cy - r * 0.3f, cx + r * 0.5f, cy + r * 0.5f); c.drawArc(rf, 180f, 180f, true, p.also { it.style = Paint.Style.FILL })
                p.style = Paint.Style.STROKE; c.drawLine(cx - r * 0.3f, cy - r * 0.35f, cx - r * 0.45f, cy - r * 0.6f, p); c.drawLine(cx + r * 0.3f, cy - r * 0.35f, cx + r * 0.45f, cy - r * 0.6f, p)
            }
            Glyph.KEYBOARD -> {
                rf.set(cx - r * 0.7f, cy - r * 0.42f, cx + r * 0.7f, cy + r * 0.42f); c.drawRoundRect(rf, r * 0.12f, r * 0.12f, p)
                p.style = Paint.Style.FILL
                for (i in -2..2) c.drawCircle(cx + i * r * 0.26f, cy - r * 0.15f, r * 0.05f, p)
                for (i in -1..1) c.drawCircle(cx + i * r * 0.26f, cy + r * 0.02f, r * 0.05f, p)
                c.drawRect(cx - r * 0.35f, cy + r * 0.16f, cx + r * 0.35f, cy + r * 0.24f, p)
            }
        }
    }
}
