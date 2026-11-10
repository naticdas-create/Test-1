package com.advancedvr.window

import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.view.Surface
import com.advancedvr.apps.WindowContent
import com.advancedvr.core.RAD2DEG
import com.advancedvr.core.Mx
import com.advancedvr.core.Vec3
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

enum class WinState { NORMAL, MAXIMIZED, MINIMIZED }
enum class WinMode { WORLD, HEAD_LOCKED }
enum class WinKind { APP, DOCK, KEYBOARD, TOAST }
enum class Region { BODY, HANDLE, GRIP_L, GRIP_R }

class Hit(
    val win: VrWindow, val region: Region, val t: Float,
    val lx: Float, val ly: Float, val u: Float, val v: Float, val world: Vec3
)

/**
 * Janela espacial. Dimensões em metros (widthM x heightM incluem a barra de título).
 * O conteúdo é uma View Android real renderizada para uma SurfaceTexture (textura OES).
 */
class VrWindow(
    val id: Int,
    val kind: WinKind,
    val appId: String,
    var title: String,
    val content: WindowContent,
    @Volatile var widthM: Float,
    @Volatile var heightM: Float
) {
    companion object {
        const val CURVE_R = 2.6f
    }

    @Volatile var position = Vec3(0f, 0f, -2f)
    @Volatile var mode = WinMode.WORLD
    @Volatile var state = WinState.NORMAL
    @Volatile var closing = false
    @Volatile var handleTop = false
    var animScale = 0f

    val hasTitleBar = kind == WinKind.APP
    val grabbable = kind != WinKind.TOAST
    val resizable = kind == WinKind.APP
    val curved = kind == WinKind.APP
    val pitchFactor = if (kind == WinKind.APP) 0f else 0.85f
    val cornerRadius = if (kind == WinKind.APP) 0.035f else 0.05f

    val model = FloatArray(16)
    val inv = FloatArray(16)
    var distance = 2f

    // Pixels do conteúdo
    @Volatile var pxW = 0
    @Volatile var pxH = 0
    @Volatile var frame: WindowFrameView? = null

    // Superfície de captura (GL <-> UI)
    val lock = Any()
    @Volatile var surface: Surface? = null
    var st: SurfaceTexture? = null
    var texId = 0
    val texMat = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    val frameReady = AtomicBoolean(false)
    val posted = AtomicInteger(0)
    @Volatile var hasFrame = false
    @Volatile var surfaceVersion = 0
    var builtVersion = -1

    var savedGeom: FloatArray? = null

    val visible: Boolean get() = !(state == WinState.MINIMIZED && animScale < 0.01f)

    fun step(dt: Float) {
        val target = if (closing || state == WinState.MINIMIZED) 0f else 1f
        val k = min(1f, dt * 14f)
        animScale += (target - animScale) * k
        if (abs(target - animScale) < 0.003f) animScale = target
    }

    fun handleY(): Float {
        val hh = heightM / 2f
        return if (handleTop) hh + 0.05f else -(hh + 0.05f)
    }

    /** Calcula model/inv. Janelas WORLD encaram a cabeça (yaw); HEAD_LOCKED seguem a orientação da cabeça. */
    fun updateModel(head: FloatArray) {
        val p = position
        if (mode == WinMode.WORLD) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, p.x, p.y, p.z)
            val yaw = atan2(-p.x, -p.z) * RAD2DEG
            val hd = sqrt(p.x * p.x + p.z * p.z)
            val pitch = atan2(p.y, hd) * RAD2DEG * pitchFactor
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
            distance = p.length()
        } else {
            System.arraycopy(head, 0, model, 0, 16)
            model[12] = 0f; model[13] = 0f; model[14] = 0f
            Matrix.translateM(model, 0, p.x, p.y, p.z)
            distance = p.length()
        }
        val s = animScale.coerceAtLeast(0.001f)
        Matrix.scaleM(model, 0, s, s, s)
        Matrix.invertM(inv, 0, model, 0)
    }

    /** Interseção com o painel (curvo = cilindro, plano caso contrário). Retorna [t, lx, ly] em metros locais. */
    fun planeHit(o: Vec3, d: Vec3): FloatArray? {
        if (animScale < 0.5f || closing) return null
        val lo = Mx.point(inv, o)
        val ld = Mx.dir(inv, d)
        if (ld.z >= -1e-6f) return null
        val t: Float
        val lx: Float
        val ly: Float
        if (curved) {
            val r = CURVE_R
            val a = ld.x * ld.x + ld.z * ld.z
            if (a < 1e-9f) return null
            val b = 2f * (lo.x * ld.x + (lo.z - r) * ld.z)
            val c = lo.x * lo.x + (lo.z - r) * (lo.z - r) - r * r
            val disc = b * b - 4f * a * c
            if (disc < 0f) return null
            t = (-b + sqrt(disc)) / (2f * a)
            if (t <= 0f) return null
            val px = lo.x + t * ld.x
            val pz = lo.z + t * ld.z
            lx = r * atan2(px, r - pz)
            ly = lo.y + t * ld.y
        } else {
            t = -lo.z / ld.z
            if (t <= 0f) return null
            lx = lo.x + t * ld.x
            ly = lo.y + t * ld.y
        }
        return floatArrayOf(t, lx, ly)
    }

    fun intersect(o: Vec3, d: Vec3): Hit? {
        if (kind == WinKind.TOAST) return null
        val ph = planeHit(o, d) ?: return null
        val lx = ph[1]; val ly = ph[2]
        val hw = widthM / 2f; val hh = heightM / 2f
        val hy = handleY()
        val region = when {
            abs(lx) <= hw && abs(ly) <= hh -> Region.BODY
            grabbable && abs(lx) <= 0.30f && abs(ly - hy) <= 0.045f -> Region.HANDLE
            resizable && lx in (hw - 0.01f)..(hw + 0.12f) && ly in (-hh - 0.12f)..(-hh + 0.01f) -> Region.GRIP_R
            resizable && lx in (-hw - 0.12f)..(-hw + 0.01f) && ly in (-hh - 0.12f)..(-hh + 0.01f) -> Region.GRIP_L
            else -> return null
        }
        return Hit(this, region, ph[0], lx, ly, lx / widthM + 0.5f, 0.5f - ly / heightM, o + d * ph[0])
    }

    fun normalWorld(): Vec3 = Mx.dir(model, Vec3(0f, 0f, 1f)).normalized()
}
