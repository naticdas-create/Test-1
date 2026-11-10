package com.advancedvr.core

import kotlin.math.tan

/**
 * Parâmetros da projeção de UM olho. Compartilhados entre renderer, rastreamento de mãos
 * e interação para que a mão apareça exatamente onde a câmera a vê (mapa imagem -> NDC).
 */
class ViewParams {
    @Volatile var tanX = 1f
    @Volatile var tanY = 1f
    @Volatile var aspect = 1f
    @Volatile var cropX = 1f
    @Volatile var cropY = 1f
    @Volatile var imageAspect = 16f / 9f

    fun update(eyeAspect: Float, fovYDeg: Float, imgAspect: Float) {
        aspect = eyeAspect
        tanY = tan(Math.toRadians(fovYDeg.toDouble() / 2.0)).toFloat()
        tanX = tanY * eyeAspect
        imageAspect = imgAspect
        if (eyeAspect < imgAspect) { cropY = 1f; cropX = eyeAspect / imgAspect }
        else { cropX = 1f; cropY = imgAspect / eyeAspect }
    }

    /** x normalizado da imagem da câmera (0..1, esquerda->direita) para NDC do olho. */
    fun nx(x: Float) = (x - 0.5f) / cropX * 2f

    /** y normalizado da imagem (0..1, topo->base) para NDC do olho (cima positivo). */
    fun ny(y: Float) = -(y - 0.5f) / cropY * 2f
}
