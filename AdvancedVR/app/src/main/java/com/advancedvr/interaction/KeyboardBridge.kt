package com.advancedvr.interaction

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.advancedvr.VrServices
import kotlin.math.max
import kotlin.math.min

/** Envia texto/teclas do teclado virtual para o campo focado da janela-alvo (EditText real ou campo dentro de WebView). */
class KeyboardBridge(private val svc: VrServices) {

    private fun focusedView() = svc.wm.textTarget?.frame?.findFocus()

    fun commit(s: String) = svc.main.post {
        val f = focusedView() ?: return@post
        if (f is EditText) {
            val a = max(0, f.selectionStart); val b = max(0, f.selectionEnd)
            f.text.replace(min(a, b), max(a, b), s)
        } else {
            val ic = f.onCreateInputConnection(EditorInfo())
            if (ic != null) ic.commitText(s, 1)
            else @Suppress("DEPRECATION") f.dispatchKeyEvent(KeyEvent(SystemClock.uptimeMillis(), s, KeyCharacterMap.VIRTUAL_KEYBOARD, 0))
        }
    }

    fun key(code: Int) = svc.main.post {
        val f = focusedView() ?: return@post
        val t = SystemClock.uptimeMillis()
        f.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        f.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    fun backspace() = key(KeyEvent.KEYCODE_DEL)
    fun enter() = key(KeyEvent.KEYCODE_ENTER)
}
