package com.advancedvr.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.advancedvr.VrServices
import com.advancedvr.ui.AppButton
import com.advancedvr.ui.Ui

/** Menu de aplicativos: apps do sistema VR + todos os apps Android instalados. */
class AppsContent(private val svc: VrServices) : WindowContent {
    private class Entry(val label: String, val spec: AppSpec?, val pkg: String?, val icon: Drawable?)

    private val all = ArrayList<Entry>()
    private val shown = ArrayList<Entry>()
    private var adapter: BaseAdapter? = null

    override fun createView(ctx: Context): View {
        val root = Ui.vbox(ctx).apply { setBackgroundColor(Ui.PANEL); setPadding(28, 22, 28, 12) }
        val search = EditText(ctx).apply {
            setSingleLine(); setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f)
            setTextColor(Ui.TEXT); setHintTextColor(Ui.SUB); hint = "Pesquisar aplicativos"
            background = Ui.round(Ui.FIELD, 32f); setPadding(30, 0, 30, 0)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 64))
        val grid = GridView(ctx).apply { numColumns = 6; verticalSpacing = 18; horizontalSpacing = 8; setPadding(0, 18, 0, 0); selector = Ui.round(0, 0f) }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        for (s in AppRegistry.all.filter { it.listed }) all.add(Entry(s.title, s, null, null))
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val e = shown[p]
                val cell = Ui.vbox(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 6, 0, 6) }
                if (e.spec != null) cell.addView(AppButton(ctx, e.spec.glyph, e.spec.c1, e.spec.c2, 92))
                else cell.addView(ImageView(ctx).apply { setImageDrawable(e.icon) }, LinearLayout.LayoutParams(92, 92))
                cell.addView(Ui.text(ctx, e.label, 19f, Ui.TEXT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER })
                cell.isClickable = true
                cell.setOnClickListener { launch(ctx, e) }
                return cell
            }
        }
        grid.adapter = adapter
        applyFilter("")
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // carrega apps instalados fora da thread principal
        svc.io.execute {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(i, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { Entry(it.loadLabel(pm).toString(), null, it.activityInfo.packageName, it.loadIcon(pm)) }
                .sortedBy { it.label.lowercase() }
            svc.main.post { all.addAll(list); applyFilter(search.text.toString()) }
        }
        return root
    }

    private fun applyFilter(q: String) {
        shown.clear()
        shown.addAll(all.filter { it.label.contains(q, true) })
        adapter?.notifyDataSetChanged()
    }

    private fun launch(ctx: Context, e: Entry) {
        if (e.spec != null) { svc.wm.openApp(e.spec.id); return }
        val intent = ctx.packageManager.getLaunchIntentForPackage(e.pkg ?: return) ?: return
        svc.notifications.post(e.label, "Abrindo app Android (sai do modo VR; use Voltar para retornar)", "Sistema")
        svc.main.postDelayed({ intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent) }, 600)
    }
}
