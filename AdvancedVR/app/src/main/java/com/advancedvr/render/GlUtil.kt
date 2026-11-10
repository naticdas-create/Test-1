package com.advancedvr.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Prog(val id: Int) {
    private val cache = HashMap<String, Int>()
    fun u(name: String): Int = cache.getOrPut("u$name") { GLES20.glGetUniformLocation(id, name) }
    fun a(name: String): Int = cache.getOrPut("a$name") { GLES20.glGetAttribLocation(id, name) }
}

object GlUtil {
    private const val TAG = "GlUtil"

    fun program(vs: String, fs: String): Prog {
        val v = shader(GLES20.GL_VERTEX_SHADER, vs)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val st = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Link error: $log")
        }
        return Prog(p)
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val st = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            Log.e(TAG, "Shader error: $log\n$src")
            GLES20.glDeleteShader(s)
            throw RuntimeException("Shader error: $log")
        }
        return s
    }

    fun floatBuf(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also {
            it.put(a); it.position(0)
        }

    fun oesTexture(): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }
}

object Shaders {
    // ---- Painéis (janelas) e sombras: malha curva calculada no vertex shader ----
    const val WIN_VS = """
attribute vec2 aUV;
uniform mat4 uMVP; uniform vec2 uWH; uniform float uR; uniform float uZ;
varying vec2 vUV;
void main(){
  vUV = aUV;
  float x = (aUV.x - 0.5) * uWH.x;
  float y = (aUV.y - 0.5) * uWH.y;
  vec3 p = vec3(x, y, uZ);
  if (uR > 0.0) { float a = x / uR; p.x = uR * sin(a); p.z = uR * (1.0 - cos(a)) + uZ; }
  gl_Position = uMVP * vec4(p, 1.0);
}"""

    const val WIN_FS = """
#extension GL_OES_EGL_image_external : require
precision highp float;
uniform samplerExternalOES uTex; uniform mat4 uTexMat;
uniform vec2 uWH; uniform vec2 uHalf; uniform float uRadius;
uniform float uMode; uniform float uAlpha; uniform float uBright; uniform float uSoft;
varying vec2 vUV;
float sdr(vec2 p, vec2 b, float r){ vec2 q = abs(p) - b + vec2(r); return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
void main(){
  vec2 p = (vUV - 0.5) * uWH;
  float d = sdr(p, uHalf, uRadius);
  if (uMode > 0.5) {
    float a = 1.0 - smoothstep(0.0, uSoft, d);
    gl_FragColor = vec4(0.0, 0.0, 0.0, a * a * uAlpha * 0.55);
    return;
  }
  float aa = 1.0 - smoothstep(-0.0015, 0.0015, d);
  vec4 c = texture2D(uTex, (uTexMat * vec4(vUV, 0.0, 1.0)).xy);
  gl_FragColor = vec4(c.rgb * uBright, c.a) * (aa * uAlpha);
}"""

    // ---- Fundo virtual (céu/horizonte/chão) ----
    const val ENV_VS = """
attribute vec2 aPos; varying vec2 vNdc;
void main(){ vNdc = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }"""

    const val ENV_FS = """
precision highp float;
uniform mat3 uRot; uniform vec2 uTan; varying vec2 vNdc;
void main(){
  vec3 d = normalize(uRot * vec3(vNdc * uTan, -1.0));
  float t = d.y;
  vec3 zen = vec3(0.03, 0.07, 0.17);
  vec3 hor = vec3(0.32, 0.40, 0.55);
  vec3 flo = vec3(0.07, 0.075, 0.09);
  vec3 col;
  if (t > 0.0) col = mix(hor, zen, pow(clamp(t * 1.4, 0.0, 1.0), 0.6));
  else col = mix(hor * 0.55, flo, smoothstep(0.0, -0.5, t));
  col += vec3(0.05, 0.07, 0.10) * exp(-abs(t) * 18.0);
  gl_FragColor = vec4(col, 1.0);
}"""

    // ---- Passthrough da câmera ----
    const val CAM_VS = """
attribute vec2 aPos; varying vec2 vUV;
void main(){ vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"""

    const val CAM_FS = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uCam; uniform mat4 uMat; uniform vec2 uCrop; uniform float uBright;
varying vec2 vUV;
void main(){
  vec2 uv = 0.5 + (vUV - 0.5) * uCrop;
  vec4 c = texture2D(uCam, (uMat * vec4(uv, 0.0, 1.0)).xy);
  gl_FragColor = vec4(c.rgb * uBright, 1.0);
}"""

    // ---- Geometria colorida (mãos, raios, alças, cursores) ----
    const val BATCH_VS = """
attribute vec3 aPos; attribute vec4 aCol; uniform mat4 uVP; varying vec4 vCol;
void main(){ vCol = aCol; gl_Position = uVP * vec4(aPos, 1.0); }"""

    const val BATCH_FS = """
precision mediump float; varying vec4 vCol;
void main(){ gl_FragColor = vec4(vCol.rgb * vCol.a, vCol.a); }"""

    // ---- Correção de distorção das lentes (barrel) ----
    const val DIST_VS = """
attribute vec2 aPos; varying vec2 vUV;
void main(){ vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"""

    const val DIST_FS = """
precision highp float;
uniform sampler2D uTex; uniform float uK1; uniform float uK2; uniform float uAspect;
varying vec2 vUV;
void main(){
  bool left = vUV.x < 0.5;
  vec2 e = vec2(left ? vUV.x * 2.0 : (vUV.x - 0.5) * 2.0, vUV.y);
  vec2 c = e * 2.0 - 1.0;
  c.x *= uAspect;
  float r2 = dot(c, c);
  float s = 1.0 + uK1 * r2 + uK2 * r2 * r2;
  c *= s;
  c.x /= uAspect;
  vec2 se = c * 0.5 + 0.5;
  if (se.x < 0.0 || se.y < 0.0 || se.x > 1.0 || se.y > 1.0) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
  se.x = left ? se.x * 0.5 : se.x * 0.5 + 0.5;
  gl_FragColor = texture2D(uTex, se);
}"""
}
