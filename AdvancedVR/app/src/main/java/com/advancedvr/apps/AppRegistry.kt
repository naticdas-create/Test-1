package com.advancedvr.apps

import android.content.Context
import android.view.View
import com.advancedvr.VrServices
import com.advancedvr.ui.Glyph

/** Conteúdo de uma janela: uma View Android real (WebView, ListView, etc.). */
interface WindowContent {
    /** true => captura via canvas de software (necessário para WebView). */
    val softwareCapture: Boolean get() = false
    fun createView(ctx: Context): View
    fun onBack(): Boolean = false
    fun onDestroy() {}
}

class AppSpec(
    val id: String, val title: String, val glyph: Glyph, val c1: Int, val c2: Int,
    val widthM: Float, val heightM: Float, val multi: Boolean = false, val listed: Boolean = true,
    val create: (VrServices, Map<String, String>) -> WindowContent
)

object AppRegistry {
    val all: List<AppSpec> = listOf(
        AppSpec("browser", "Navegador", Glyph.COMPASS, 0xFF2D8CFF.toInt(), 0xFF1B5FD6.toInt(), 1.6f, 1.0f, multi = true) { s, a -> BrowserContent(s, a["url"]) },
        AppSpec("youtube", "YouTube", Glyph.YOUTUBE, 0xFFFF5A72.toInt(), 0xFFE0264A.toInt(), 1.7f, 1.0f) { s, _ -> BrowserContent(s, "https://www.youtube.com") },
        AppSpec("google", "Google", Glyph.GOOGLE, 0xFF5A6068.toInt(), 0xFF3A3F46.toInt(), 1.6f, 1.0f) { s, _ -> BrowserContent(s, "https://www.google.com") },
        AppSpec("gallery", "Galeria", Glyph.GALLERY, 0xFFB06AF0.toInt(), 0xFF7B3FD0.toInt(), 1.4f, 1.0f) { s, _ -> GalleryContent(s) },
        AppSpec("files", "Arquivos", Glyph.BRIEFCASE, 0xFFFFA030.toInt(), 0xFFE07A00.toInt(), 1.3f, 0.95f) { s, _ -> FilesContent(s) },
        AppSpec("settings", "Configurações", Glyph.GEAR, 0xFF6B7280.toInt(), 0xFF454B55.toInt(), 1.15f, 1.05f) { s, _ -> SettingsContent(s) },
        AppSpec("apps", "Aplicativos", Glyph.GRID, 0xFF4B5563.toInt(), 0xFF2F3640.toInt(), 1.55f, 1.0f, listed = false) { s, _ -> AppsContent(s) },
        AppSpec("notifications", "Notificações", Glyph.BELL, 0xFF4B5563.toInt(), 0xFF2F3640.toInt(), 0.95f, 1.0f, listed = false) { s, _ -> NotificationsContent(s) },
        AppSpec("viewer", "Imagem", Glyph.GALLERY, 0xFF4B5563.toInt(), 0xFF2F3640.toInt(), 1.5f, 1.0f, multi = true, listed = false) { _, a -> ImageViewerContent(a["uri"] ?: "") }
    )

    fun get(id: String): AppSpec? = all.firstOrNull { it.id == id }
}
