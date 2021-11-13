/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.su5ed.koremods.splash.render

import dev.su5ed.koremods.splash.Uniform
import dev.su5ed.koremods.splash.UniformFade
import dev.su5ed.koremods.splash.bufferIndices
import dev.su5ed.koremods.splash.bufferVertices
import org.lwjgl.opengl.GL30.*

class RenderFade : RenderBase() {
    companion object {
        private const val FADE_IN_MS = 250f
        private const val FADE_OUT_MS = 500f
    }
    
    private val vertices = floatArrayOf(
         1.0f,   1.0f,    // top right
         1.0f,  -1.0f,    // bottom right
        -1.0f,  -1.0f,    // bottom left
        -1.0f,   1.0f,    // top left
    )
    private val indices = intArrayOf(
        0, 1, 3,    // first triangle
        1, 2, 3     // second triangle
    )
    private val uniformFade = UniformFade()
    private var fadeTime = FADE_IN_MS
    private var currentFade: Float = 0.0f
    
    override val vertexShader: String = "fade"
    override val fragmentShader: String = "fade"
    override val uniforms: List<Uniform> = listOf(uniformFade)
    
    override fun getWindowHints(): Map<Int, Int> = emptyMap()

    override fun initWindow(window: Long, monitor: Long) {}

    override fun initRender() {
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        
        glBindVertexArray(VAO)

        // position attribute
        val posAttrib = glGetAttribLocation(shader, "aPos")
        glEnableVertexAttribArray(posAttrib)
        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)

        bufferVertices(VBO, vertices, GL_STATIC_DRAW)
        bufferIndices(EBO, indices, GL_STATIC_DRAW)

        glBindVertexArray(0)
        glDisableVertexAttribArray(0)
    }

    override fun draw(window: Long) {
        currentFade = uniformFade.update(fadeTime)
        
        glBlendFunc(GL_ZERO, GL_SRC_ALPHA)
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun windowClosing() {
        uniformFade.reverse = true
        fadeTime = FADE_OUT_MS
    }

    override fun shouldCloseWindow(): Boolean = uniformFade.reverse && currentFade <= 0f
}
