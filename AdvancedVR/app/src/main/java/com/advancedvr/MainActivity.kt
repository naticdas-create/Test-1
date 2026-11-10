package com.advancedvr

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.advancedvr.interaction.InteractionManager
import com.advancedvr.render.VrRenderer
import com.advancedvr.ui.VrBus
import com.advancedvr.window.VrWindowManager

class VrGLSurfaceView(ctx: android.content.Context, private val svc: VrServices) : GLSurfaceView(ctx) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = svc.interaction.onTouch(e, width, height)
}

class MainActivity : ComponentActivity() {
    private lateinit var svc: VrServices
    private lateinit var glView: VrGLSurfaceView
    private lateinit var renderer: VrRenderer
    private var cameraStarted = false

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.CAMERA] == true) startCamera()
        else svc.notifications.post("Câmera negada", "Passthrough desativado; fundo virtual em uso.", "Sistema")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        svc = VrServices(this)
        VrBus.notifications = svc.notifications
        svc.interaction = InteractionManager(svc)

        val root = FrameLayout(this)
        val host = FrameLayout(this) // hospeda as Views das janelas (cobertas pelo GLSurfaceView)
        root.addView(host, ViewGroup.LayoutParams(-1, -1))
        svc.wm = VrWindowManager(svc, host)

        renderer = VrRenderer(svc)
        glView = VrGLSurfaceView(this, svc).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glView, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        // Início: dock + navegador (como na referência do Quest)
        svc.wm.openDock()
        svc.wm.openApp("browser")
        svc.notifications.post("Advanced VR", "Olhe para um botão e toque na tela para selecionar. Volume + abre/fecha o menu.", "Sistema")

        requestPermissions()
    }

    private fun requestPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.CAMERA)
        list.add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        if (list.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startCamera()
        else permLauncher.launch(list.toTypedArray())
    }

    /** Câmera traseira apenas como passthrough (sem rastreamento de mãos). */
    private fun startCamera() {
        if (cameraStarted || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        cameraStarted = true
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            try {
                val provider = f.get()
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build())
                    .setTargetRotation(Surface.ROTATION_90)
                    .build()
                preview.setSurfaceProvider(renderer.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                cameraStarted = false
                svc.notifications.post("Câmera indisponível", e.message ?: "", "Sistema")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        svc.head.start(); glView.onResume(); svc.wm.start()
    }

    override fun onPause() {
        svc.wm.stop(); glView.onPause(); svc.head.stop()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { svc.interaction.onVolumeKey(true); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { svc.interaction.onVolumeKey(false); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!svc.wm.back()) svc.notifications.post("Voltar", "Nada para voltar na janela atual.", "Sistema", toast = false)
    }
}
