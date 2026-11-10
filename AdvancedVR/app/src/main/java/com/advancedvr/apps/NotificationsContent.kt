package com.advancedvr.apps

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.advancedvr.VrServices
import com.advancedvr.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsContent(private val svc: VrServices) : WindowContent {
    override fun createView(ctx: Context): View {
        val root = Ui.vbox(ctx).apply { setBackgroundColor(Ui.PANEL); setPadding(24, 18, 24, 12) }
        val head = Ui.hbox(ctx)
        head.addView(Ui.text(ctx, "Notificações", 30f, Ui.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.button(ctx, "Limpar") { svc.notifications.clear() })
        root.addView(head)
        val list = Ui.vbox(ctx)
        root.addView(ScrollView(ctx).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 14 })
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        fun fill() {
            list.removeAllViews()
            if (svc.notifications.items.isEmpty()) list.addView(Ui.text(ctx, "Sem notificações.", 22f, Ui.SUB))
            for (n in svc.notifications.items) {
                val card = Ui.vbox(ctx).apply { background = Ui.round(Ui.PANEL2, 22f); setPadding(22, 14, 22, 14) }
                card.addView(Ui.text(ctx, n.title, 24f, Ui.TEXT, true))
                if (n.text.isNotBlank()) card.addView(Ui.text(ctx, n.text, 20f, Ui.SUB))
                card.addView(Ui.text(ctx, fmt.format(Date(n.time)) + " · " + n.source, 17f, Ui.SUB))
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
            }
        }
        fill()
        svc.notifications.listener = { fill() }
        return root
    }
    override fun onDestroy() { svc.notifications.listener = null }
}
