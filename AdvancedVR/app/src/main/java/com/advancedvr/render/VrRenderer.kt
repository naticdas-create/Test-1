package com.advancedvr.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import com.advancedvr.VrServices
import com.advancedvr.core.Mx
import com.advancedvr.core.Vec3
import com.advancedvr.window.VrWindow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Renderer estéreo (side-by-side) com correção de lente, passthrough da câmera,
 * painéis curvos com sombra e camada de sobreposição (mãos/raios/cursores) por cima de tudo.
 */
class VrRenderer(private val svc: VrServices) : GLSurfaceView.Renderer {

    private var W = 1
    private var H = 1
    private var fbW = 2
    private var fbH = 2
    private var fbo = 0
    private var fbTex = 0
    private var fbScale = 0f

    private lateinit var winProg: Prog
    private lateinit var envProg: Prog
    private lateinit var camProg: Prog
    private lateinit var batchProg: Prog
    private lateinit var distProg: Prog
    private lateinit var quadBuf: java.nio.FloatBuffer
    private lateinit var meshBuf: java.nio.FloatBuffer
    private val meshCols = 40

    private val batch = Batch()
    private val head = FloatArray(16)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val rot3 = FloatArray(9)

    // Câmera (passthrough)
    private var camTex = 0
    private var camSt: SurfaceTexture? = null
    private val camMat = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    @Volatile private var camFrame = false
    private var camHasFrame = false
    @Volatile private var pendingCamRequest: SurfaceRequest? = null

    private var lastNs = 0L
    private var ready = false

    /** Entregue ao CameraX Preview. A criação da textura é adiada para a thread GL. */
    val surfaceProvider = Preview.SurfaceProvider { req -> pendingCamRequest = req }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        winProg = GlUtil.program(Shaders.WIN_VS, Shaders.WIN_FS)
        envProg = GlUtil.program(Shaders.ENV_VS, Shaders.ENV_FS)
        camProg = GlUtil.program(Shaders.CAM_VS, Shaders.CAM_FS)
        batchProg = GlUtil.program(Shaders.BATCH_VS, Shaders.BATCH_FS)
        distProg = GlUtil.program(Shaders.DIST_VS, Shaders.DIST_FS)
        quadBuf = GlUtil.floatBuf(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val m = FloatArray((meshCols + 1) * 4)
        for (i in 0..meshCols) {
            val u = i / meshCols.toFloat()
            m[i * 4] = u; m[i * 4 + 1] = 1f      // topo (v=1)
            m[i * 4 + 2] = u; m[i * 4 + 3] = 0f  // base (v=0)
        }
        meshBuf = GlUtil.floatBuf(m)
        fbo = 0; fbTex = 0; fbScale = 0f
        ready = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        W = width; H = height
        fbScale = 0f // força recriação do FBO
    }

    private fun makeFbo() {
        if (fbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0); GLES20.glDeleteTextures(1, intArrayOf(fbTex), 0) }
        fbScale = svc.settings.renderScale
        fbW = max(2, ((W * fbScale).toInt()) and 1.inv())
        fbH = max(2, (H * fbScale).toInt())
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); fbTex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fbW, fbH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); fbo = f[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fbTex, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    // ------------------------------------------------------------------ recursos

    private fun handleCamera() {
        val req = pendingCamRequest
        if (req != null) {
            pendingCamRequest = null
            if (camSt == null) {
                camTex = GlUtil.oesTexture()
                camSt = SurfaceTexture(camTex).also { it.setOnFrameAvailableListener { camFrame = true } }
            }
            val st = camSt!!
            st.setDefaultBufferSize(req.resolution.width, req.resolution.height)
            val surface = Surface(st)
            req.provideSurface(surface, ContextCompat.getMainExecutor(svc.ctx)) { surface.release() }
        }
        if (camFrame) {
            camFrame = false
            try {
                camSt?.updateTexImage()
                camSt?.getTransformMatrix(camMat)
                camHasFrame = true
            } catch (_: Exception) { }
        }
    }

    private fun ensureWindowSurface(w: VrWindow) {
        if (w.builtVersion == w.surfaceVersion && w.st != null) return
        synchronized(w.lock) {
            w.surface?.release(); w.surface = null
            w.st?.release(); w.st = null
            if (w.texId != 0) GLES20.glDeleteTextures(1, intArrayOf(w.texId), 0)
            w.texId = GlUtil.oesTexture()
            val st = SurfaceTexture(w.texId)
            st.setDefaultBufferSize(max(1, w.pxW), max(1, w.pxH))
            st.setOnFrameAvailableListener { w.frameReady.set(true) }
            w.st = st
            w.posted.set(0)
            w.hasFrame = false
            w.surface = Surface(st)
            w.builtVersion = w.surfaceVersion
        }
    }

    private fun releaseWindow(w: VrWindow) {
        synchronized(w.lock) {
            w.surface?.release(); w.surface = null
            w.st?.release(); w.st = null
            if (w.texId != 0) { GLES20.glDeleteTextures(1, intArrayOf(w.texId), 0); w.texId = 0 }
        }
    }

    // ------------------------------------------------------------------ quadro

    override fun onDrawFrame(gl: GL10?) {
        if (!ready) return
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNs = now
        val s = svc.settings

        while (true) { releaseWindow(svc.wm.releaseQueue.poll() ?: break) }
        handleCamera()
        if (fbo == 0 || fbScale != s.renderScale) makeFbo()

        svc.head.getHeadMatrix(head)

        // Parâmetros de projeção de um olho
        val stereo = s.stereo
        val eyeAspect = if (stereo) (fbW / 2f) / fbH else W.toFloat() / H
        svc.view.update(eyeAspect, s.fovY, 16f / 9f)

        val wins = svc.wm.windows
        for (w in wins) {
            w.step(dt)
            ensureWindowSurface(w)
            if (w.frameReady.getAndSet(false)) {
                try {
                    w.st?.updateTexImage()
                    w.st?.getTransformMatrix(w.texMat)
                    w.posted.set(0)
                    w.hasFrame = true
                } catch (_: Exception) { }
            }
            w.updateModel(head)
        }

        svc.interaction.update(dt, head)
        batch.clear()
        svc.interaction.buildOverlay(batch, head)

        val sorted = wins.filter { it.visible && it.hasFrame }.sortedByDescending { it.distance }

        if (stereo) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            for (eye in 0..1) drawEye(eye, eye * (fbW / 2), fbW / 2, fbH, sorted)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, W, H)
            drawDistortion(eyeAspect)
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawEye(-1, 0, W, H, sorted)
        }
    }

    private fun drawEye(eye: Int, vx: Int, vw: Int, vh: Int, wins: List<VrWindow>) {
        val s = svc.settings
        GLES20.glViewport(vx, 0, vw, vh)
        val off = when (eye) { 0 -> -1f; 1 -> 1f; else -> 0f } * s.ipdMm / 2000f
        val ep = Mx.dir(head, Vec3(off, 0f, 0f))
        for (c in 0..2) for (r in 0..2) view[c * 4 + r] = head[r * 4 + c]
        view[3] = 0f; view[7] = 0f; view[11] = 0f; view[15] = 1f
        view[12] = 0f; view[13] = 0f; view[14] = 0f
        Matrix.translateM(view, 0, -ep.x, -ep.y, -ep.z)
        Matrix.perspectiveM(proj, 0, s.fovY, vw.toFloat() / vh, 0.05f, 60f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawBackground(vw.toFloat() / vh)
        drawWindows(wins)
        batch.draw(batchProg, vp)
    }

    private fun drawBackground(aspect: Float) {
        if (svc.settings.passthrough && camHasFrame) {
            val p = camProg
            GLES20.glUseProgram(p.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
            GLES20.glUniform1i(p.u("uCam"), 0)
            GLES20.glUniformMatrix4fv(p.u("uMat"), 1, false, camMat, 0)
            GLES20.glUniform2f(p.u("uCrop"), svc.view.cropX, svc.view.cropY)
            GLES20.glUniform1f(p.u("uBright"), 0.92f)
            drawQuad(p)
        } else {
            val p = envProg
            GLES20.glUseProgram(p.id)
            rot3[0] = head[0]; rot3[1] = head[1]; rot3[2] = head[2]
            rot3[3] = head[4]; rot3[4] = head[5]; rot3[5] = head[6]
            rot3[6] = head[8]; rot3[7] = head[9]; rot3[8] = head[10]
            GLES20.glUniformMatrix3fv(p.u("uRot"), 1, false, rot3, 0)
            GLES20.glUniform2f(p.u("uTan"), svc.view.tanX, svc.view.tanY)
            drawQuad(p)
        }
    }

    private fun drawQuad(p: Prog) {
        val a = p.a("aPos")
        quadBuf.position(0)
        GLES20.glEnableVertexAttribArray(a)
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(a)
    }

    private fun drawWindows(wins: List<VrWindow>) {
        if (wins.isEmpty()) return
        val p = winProg
        GLES20.glUseProgram(p.id)
        val aUV = p.a("aUV")
        meshBuf.position(0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, meshBuf)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(p.u("uTex"), 0)
        val focused = svc.wm.focused
        val verts = (meshCols + 1) * 2
        for (w in wins) {
            Matrix.multiplyMM(mvp, 0, vp, 0, w.model, 0)
            GLES20.glUniformMatrix4fv(p.u("uMVP"), 1, false, mvp, 0)
            val r = if (w.curved) VrWindow.CURVE_R else 0f
            val alpha = (w.animScale * 1.5f).coerceIn(0f, 1f)
            GLES20.glUniform1f(p.u("uR"), r)
            GLES20.glUniform2f(p.u("uHalf"), w.widthM / 2f, w.heightM / 2f)
            GLES20.glUniform1f(p.u("uRadius"), w.cornerRadius)
            GLES20.glUniform1f(p.u("uAlpha"), alpha)
            // sombra suave atrás do painel
            val m = 0.14f
            GLES20.glUniform2f(p.u("uWH"), w.widthM + 2 * m, w.heightM + 2 * m)
            GLES20.glUniform1f(p.u("uMode"), 1f)
            GLES20.glUniform1f(p.u("uSoft"), m)
            GLES20.glUniform1f(p.u("uZ"), -0.025f)
            GLES20.glUniform1f(p.u("uBright"), 1f)
            GLES20.glUniformMatrix4fv(p.u("uTexMat"), 1, false, w.texMat, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, w.texId)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, verts)
            // painel
            GLES20.glUniform2f(p.u("uWH"), w.widthM, w.heightM)
            GLES20.glUniform1f(p.u("uMode"), 0f)
            GLES20.glUniform1f(p.u("uZ"), 0f)
            GLES20.glUniform1f(p.u("uBright"), if (w === focused || w.kind != com.advancedvr.window.WinKind.APP) 1f else 0.86f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, verts)
        }
        GLES20.glDisableVertexAttribArray(aUV)
    }

    private fun drawDistortion(eyeAspect: Float) {
        val s = svc.settings
        val p = distProg
        GLES20.glUseProgram(p.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbTex)
        GLES20.glUniform1i(p.u("uTex"), 0)
        GLES20.glUniform1f(p.u("uK1"), s.lensK1)
        GLES20.glUniform1f(p.u("uK2"), s.lensK2)
        GLES20.glUniform1f(p.u("uAspect"), eyeAspect)
        drawQuad(p)
    }
}
