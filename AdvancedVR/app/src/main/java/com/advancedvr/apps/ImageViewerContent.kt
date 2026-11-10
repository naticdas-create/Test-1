package com.advancedvr.apps

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.advancedvr.ui.Ui

/** Visualizador de imagem (uri content:// ou caminho de arquivo). */
class ImageViewerContent(private val src: String) : WindowContent {
    override fun createView(ctx: Context): View {
        val iv = ImageView(ctx).apply { setBackgroundColor(0xFF000000.toInt()); scaleType = ImageView.ScaleType.FIT_CENTER }
        Thread {
            try {
                val uri = if (src.startsWith("/")) Uri.fromFile(java.io.File(src)) else Uri.parse(src)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / s > 2000 || o.outHeight / s > 1200) s *= 2
                val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
                iv.post { iv.setImageBitmap(b) }
            } catch (_: Exception) { }
        }.start()
        return iv
    }
}
