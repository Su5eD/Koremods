/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2022 Garden of Fancy
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

package wtf.gofancy.koremods.splash.render

import org.lwjgl.opengl.GL30.*
import wtf.gofancy.koremods.splash.*
import wtf.gofancy.koremods.splash.bufferIndices
import wtf.gofancy.koremods.splash.bufferVertices

class RenderLoadingCircle : RenderBase() {
    private val vertices = floatArrayOf(
         1.0f,   1.0f,   // top right
         1.0f,  -1.0f,   // bottom right
        -1.0f,  -1.0f,   // bottom left
        -1.0f,   1.0f    // top left 
    )
    private val indices = intArrayOf(
        0, 1, 3,    // first triangle
        1, 2, 3     // second triangle
    )
    override val vertexShader: String = "loadingVertex"
    override val fragmentShader: String = "loadingCircle"
    override val uniforms: List<Uniform> = listOf(UniformTime, UniformResolution)

    override fun getWindowHints(): Map<Int, Int> = emptyMap()

    override fun initWindow(window: Long, monitor: Long) {}

    override fun initRender() {
        glBindVertexArray(VAO)
        
        // position attribute
        val posAttrib = glGetAttribLocation(shader, "aPos")
        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(posAttrib)
        
        bufferVertices(VBO, vertices, GL_STATIC_DRAW)
        bufferIndices(EBO, indices, GL_STATIC_DRAW)
        
        glBindVertexArray(0)
        glDisableVertexAttribArray(0)
        
        glUseProgram(shader)
    }

    override fun draw(window: Long) {
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
    }
}
