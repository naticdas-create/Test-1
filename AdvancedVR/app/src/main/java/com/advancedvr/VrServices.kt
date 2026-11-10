package com.advancedvr

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.advancedvr.core.ViewParams
import com.advancedvr.core.VrSettings
import com.advancedvr.interaction.InteractionManager
import com.advancedvr.interaction.KeyboardBridge
import com.advancedvr.tracking.HeadTracker
import com.advancedvr.ui.NotificationCenter
import com.advancedvr.window.VrWindowManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Contêiner de serviços compartilhados entre os módulos. */
class VrServices(val ctx: Context) {
    val settings = VrSettings(ctx)
    val main = Handler(Looper.getMainLooper())
    val io: ExecutorService = Executors.newFixedThreadPool(2)
    val head = HeadTracker(ctx)
    val view = ViewParams()
    val notifications = NotificationCenter(this)
    val keyboard = KeyboardBridge(this)
    lateinit var wm: VrWindowManager
    lateinit var interaction: InteractionManager

    fun wmOrNull(): VrWindowManager? = if (::wm.isInitialized) wm else null
}
