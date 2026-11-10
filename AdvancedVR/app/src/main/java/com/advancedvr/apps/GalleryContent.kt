package com.advancedvr.apps

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import com.advancedvr.VrServices
import com.advancedvr.ui.Ui

/** Galeria real (MediaStore). Toque em uma imagem para abri-la em uma nova janela. */
class GalleryContent(private val svc: VrServices) : WindowContent {
    private val uris = ArrayList<Uri>()
    private val cache = LruCache<Uri, Bitmap>(80)

    override fun createView(ctx: Context): View {
        val root = Ui.vbox(ctx).apply { setBackgroundColor(Ui.PANEL); setPadding(16, 16, 16, 8) }
        val msg = Ui.text(ctx, "Carregando…", 24f, Ui.SUB)
        root.addView(msg)
        val grid = GridView(ctx).apply { numColumns = 4; horizontalSpacing = 8; verticalSpacing = 8; selector = Ui.round(0, 0f) }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val adapter = object : BaseAdapter() {
            override fun getCount() = uris.size
            override fun getItem(p: Int) = uris[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val iv = (cv as? ImageView) ?: ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Ui.PANEL2); layoutParams = AbsListViewLP(300) }
                val u = uris[p]
                iv.tag = u
                val c = cache.get(u)
                if (c != null) iv.setImageBitmap(c) else {
                    iv.setImageDrawable(null)
                    svc.io.execute {
                        val b = thumb(ctx, u) ?: return@execute
                        cache.put(u, b)
                        svc.main.post { if (iv.tag == u) iv.setImageBitmap(b) }
                    }
                }
                iv.setOnClickListener { svc.wm.openApp("viewer", true, mapOf("uri" to u.toString())) }
                return iv
            }
        }
        grid.adapter = adapter
        svc.io.execute {
            val found = ArrayList<Uri>()
            try {
                ctx.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )?.use { c ->
                    while (c.moveToNext() && found.size < 400)
                        found.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)))
                }
            } catch (_: Exception) { }
            svc.main.post {
                uris.addAll(found); adapter.notifyDataSetChanged()
                msg.text = if (found.isEmpty()) "Nenhuma imagem encontrada (verifique a permissão de mídia)." else ""
                if (found.isNotEmpty()) msg.visibility = View.GONE
            }
        }
        return root
    }

    @Suppress("FunctionName")
    private fun AbsListViewLP(h: Int) = android.widget.AbsListView.LayoutParams(-1, h)

    private fun thumb(ctx: Context, u: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(u, Size(320, 320), null)
        else ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
    } catch (_: Exception) { null }
}
