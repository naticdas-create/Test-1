package com.advancedvr.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.advancedvr.VrServices
import com.advancedvr.ui.Ui
import java.io.File

/** Explorador de arquivos real (java.io.File). Pastas navegáveis; imagens abrem em janela própria. */
class FilesContent(private val svc: VrServices) : WindowContent {
    private var dir: File = Environment.getExternalStorageDirectory()
    private val items = ArrayList<File>()
    private var pathView: TextView? = null
    private var adapter: BaseAdapter? = null
    private val imgExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    override fun createView(ctx: Context): View {
        val root = Ui.vbox(ctx).apply { setBackgroundColor(Ui.PANEL); setPadding(20, 14, 20, 10) }
        val top = Ui.hbox(ctx)
        top.addView(Ui.button(ctx, "↑ Subir") { up() })
        val pv = Ui.text(ctx, "", 22f, Ui.SUB).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START }
        pathView = pv
        top.addView(pv, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 16 })
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            top.addView(Ui.button(ctx, "Acesso total") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        root.addView(top)
        val list = ListView(ctx).apply { divider = Ui.round(Ui.DIV, 0f); dividerHeight = 1; selector = Ui.round(0, 0f) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10 })
        adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val f = items[p]
                val row = Ui.hbox(ctx).apply { setPadding(10, 18, 10, 18) }
                row.addView(Ui.text(ctx, if (f.isDirectory) "📁" else "📄", 26f), LinearLayout.LayoutParams(60, -2))
                row.addView(Ui.text(ctx, f.name, 23f).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                if (f.isFile) row.addView(Ui.text(ctx, size(f.length()), 19f, Ui.SUB))
                row.isClickable = true
                row.setOnClickListener { open(f) }
                return row
            }
        }
        list.adapter = adapter
        load(dir)
        return root
    }

    private fun size(b: Long) = when { b > 1 shl 20 -> "%.1f MB".format(b / 1048576f); b > 1024 -> "%.0f KB".format(b / 1024f); else -> "$b B" }

    private fun load(d: File) {
        var target = d
        var list = target.listFiles()
        if (list == null) { target = svc.ctx.getExternalFilesDir(null) ?: svc.ctx.filesDir; list = target.listFiles() ?: emptyArray() }
        dir = target
        items.clear()
        items.addAll(list.filter { !it.name.startsWith(".") }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        pathView?.text = dir.absolutePath
        adapter?.notifyDataSetChanged()
    }

    private fun up() { dir.parentFile?.let { load(it) } }

    private fun open(f: File) {
        when {
            f.isDirectory -> load(f)
            f.extension.lowercase() in imgExt -> svc.wm.openApp("viewer", true, mapOf("uri" to f.absolutePath))
            else -> svc.notifications.post(f.name, "Tipo de arquivo sem visualizador (${size(f.length())})", "Arquivos")
        }
    }

    override fun onBack(): Boolean { val p = dir.parentFile ?: return false; return if (dir != Environment.getExternalStorageDirectory()) { load(p); true } else false }
}
