package com.advancedvr.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Tema visual (inspirado no Horizon OS) e helpers de construção de Views. Unidades em pixels da janela VR. */
object Ui {
    const val PPM = 640f            // pixels por metro nas janelas
    const val BAR_M = 0.085f        // altura da barra de título (m)
    val BAR_PX = (BAR_M * PPM).roundToInt()

    val PANEL = 0xFF1B1D21.toInt()
    val PANEL2 = 0xFF24272C.toInt()
    val BAR = 0xFF121316.toInt()
    val FIELD = 0xFF2B2E34.toInt()
    val ACCENT = 0xFF2F6BFF.toInt()
    val TEXT = 0xFFF2F4F7.toInt()
    val SUB = 0xFF9BA1AA.toInt()
    val DIV = 0xFF30343A.toInt()

    fun round(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    fun text(ctx: Context, s: String, size: Float = 24f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun button(ctx: Context, label: String, accent: Boolean = false, onClick: () -> Unit): TextView =
        text(ctx, label, 24f, TEXT, true).apply {
            gravity = Gravity.CENTER
            setPadding(28, 14, 28, 14)
            background = round(if (accent) ACCENT else FIELD, 40f)
            setOnClickListener { onClick() }
        }

    fun hbox(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun vbox(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun spacer(ctx: Context, w: Int, h: Int = 1): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(w, h) }

    fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
