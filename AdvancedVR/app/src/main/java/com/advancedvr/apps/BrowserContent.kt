package com.advancedvr.apps

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.text.InputType
import android.util.TypedValue
import com.advancedvr.VrServices
import com.advancedvr.ui.Glyph
import com.advancedvr.ui.Glyphs
import com.advancedvr.ui.Ui
import android.graphics.Canvas
import android.graphics.Paint

/** Navegador real baseado em WebView: pesquisa, endereços, navegação, várias janelas independentes. */
class BrowserContent(private val svc: VrServices, private val startUrl: String?) : WindowContent {
    override val softwareCapture = true
    private var web: WebView? = null
    private var addr: EditText? = null

    private val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private class IconBtn(ctx: Context, val g: Glyph, val onClick: () -> Unit) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true; setOnClickListener { onClick() } }
        override fun onDraw(c: Canvas) {
            if (isPressed) { p.color = 0x33FFFFFF; c.drawCircle(width / 2f, height / 2f, width / 2.2f, p) }
            Glyphs.draw(c, g, width / 2f, height / 2f, width * 0.3f, 0xFFE6E8EB.toInt())
        }
    }

    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.PANEL) }
        val bar = Ui.hbox(ctx).apply { setPadding(10, 6, 10, 6); setBackgroundColor(Ui.PANEL2) }
        fun btn(g: Glyph, f: () -> Unit) = IconBtn(ctx, g, f).also { bar.addView(it, LinearLayout.LayoutParams(60, 60)) }
        btn(Glyph.BACK) { web?.goBack() }
        btn(Glyph.FORWARD) { web?.goForward() }
        btn(Glyph.RELOAD) { web?.reload() }
        btn(Glyph.HOME) { goHome() }

        val a = EditText(ctx).apply {
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f)
            setTextColor(Ui.TEXT); setHintTextColor(Ui.SUB)
            hint = "Pesquisar ou digitar endereço"
            background = Ui.round(Ui.FIELD, 30f)
            setPadding(26, 0, 26, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_ENTER) { if (ev.action == KeyEvent.ACTION_UP) go(text.toString()); true } else false
            }
        }
        addr = a
        bar.addView(a, LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(8, 0, 8, 0) })
        btn(Glyph.PLUS) { svc.wm.openApp("browser", newInstance = true) }

        val w = WebView(ctx)
        web = w
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true; loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (svc.settings.desktopMode) userAgentString = desktopUA
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = false
            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) { if (a.hasFocus().not() && !url.startsWith("https://start.local")) a.setText(url) }
        }
        w.webChromeClient = object : WebChromeClient() {
            private var custom: View? = null
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                custom = view; root.addView(view, FrameLayout.LayoutParams(-1, -1)); col.visibility = View.INVISIBLE
            }
            override fun onHideCustomView() { custom?.let { root.removeView(it) }; custom = null; col.visibility = View.VISIBLE }
        }
        col.addView(bar, LinearLayout.LayoutParams(-1, 72))
        col.addView(w, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        if (startUrl.isNullOrBlank()) goHome() else go(startUrl)
        return root
    }

    private fun goHome() {
        addr?.setText("")
        web?.loadDataWithBaseURL("https://start.local/", START_PAGE, "text/html", "utf-8", null)
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        web?.loadUrl(url)
        addr?.clearFocus()
    }

    override fun onBack(): Boolean { val w = web ?: return false; return if (w.canGoBack()) { w.goBack(); true } else false }
    override fun onDestroy() { web?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }; web = null }

    companion object {
        private const val START_PAGE = """<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>body{margin:0;background:#15171b;color:#f2f4f7;font-family:sans-serif}.w{max-width:900px;margin:40px auto;padding:0 20px}
form{display:flex;gap:10px}input{flex:1;padding:16px 22px;border-radius:30px;border:0;background:#2b2e34;color:#fff;font-size:22px}
h3{color:#9ba1aa;font-weight:500;margin-top:36px}.g{display:flex;flex-wrap:wrap;gap:18px}
a{display:flex;flex-direction:column;align-items:center;width:110px;color:#f2f4f7;text-decoration:none;font-size:16px}
a i{width:76px;height:76px;border-radius:20px;display:flex;align-items:center;justify-content:center;font-size:34px;font-style:normal;margin-bottom:8px}</style></head>
<body><div class='w'><form action='https://www.google.com/search'><input name='q' placeholder='Pesquisar na web'></form>
<h3>Popular</h3><div class='g'>
<a href='https://www.google.com'><i style='background:#4a4f57'>G</i>Google</a>
<a href='https://www.youtube.com'><i style='background:#e0264a'>▶</i>YouTube</a>
<a href='https://pt.wikipedia.org'><i style='background:#6b7280'>W</i>Wikipédia</a>
<a href='https://www.twitch.tv'><i style='background:#7b3fd0'>T</i>Twitch</a>
<a href='https://www.reddit.com'><i style='background:#e07a00'>R</i>Reddit</a>
<a href='https://www.bing.com'><i style='background:#1b5fd6'>B</i>Bing</a>
<a href='https://news.google.com'><i style='background:#2f6bff'>N</i>Notícias</a></div></div></body></html>"""
    }
}
