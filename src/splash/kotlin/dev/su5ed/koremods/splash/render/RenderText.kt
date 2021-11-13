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

import dev.su5ed.koremods.splash.*
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.opengl.GL30.*
import java.util.concurrent.ConcurrentLinkedQueue

class RenderText : RenderBase() {
    companion object {
        private val COLOR_WHITE = Triple(1f, 1f, 1f)
        private val COLOR_BLACK = Triple(0f, 0f, 0f)
    }
    
    private val indices = intArrayOf(
        0, 1, 3,    // first triangle
        1, 2, 3     // second triangle
    )
    private val splashLog: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    
    override val vertexShader: String = "font"
    override val fragmentShader: String = "font"
    override val uniforms: List<Uniform> = listOf(UniformView, UniformTextColor)
    
    private var logShader: Int = 0
    private val fontUbuntu = TrueType("ubuntu_regular.ttf", 14, WINDOW_SIZE)

    override fun getWindowHints(): Map<Int, Int> = mapOf(
        GLFW_VISIBLE to GLFW_FALSE
    )

    override fun initWindow(window: Long, monitor: Long) {
        fontUbuntu.initWindow(monitor)
    }

    override fun bake(VAO: Int, VBO: Int, EBO: Int, shaderFactory: (String?, String?) -> Int) {
        super.bake(VAO, VBO, EBO, shaderFactory)
        
        logShader = shaderFactory(vertexShader, "logFont")
    }

    override fun initRender() {
        fontUbuntu.initFont()
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        
        glBindVertexArray(VAO)
        
        // position attribute
        val posAttrib = glGetAttribLocation(shader, "aPos")
        glEnableVertexAttribArray(posAttrib)
        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
        
        // texture coord attribute
        val texCoordAttrib = glGetAttribLocation(shader, "aTexCoord")
        glEnableVertexAttribArray(texCoordAttrib)
        glVertexAttribPointer(texCoordAttrib, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, (2 * Float.SIZE_BYTES).toLong())
        
        bufferIndices(EBO, indices, GL_STATIC_DRAW)
        
        glBindVertexArray(0)
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
    }
    
    fun log(message: String) {
        splashLog.add(message)
    }

    override fun draw(window: Long) {
        glBlendFunc(GL_DST_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        fontUbuntu.renderText("Copyright \u00a9 2021 Garden of Fancy. All Rights Reserved.", COLOR_BLACK, VBO, 230f)
        
        glBlendFunc(GL_SRC_ALPHA, GL_DST_ALPHA)
        glUseProgram(logShader)
        
        splashLog.reversed().forEachIndexed { index, s ->
            fontUbuntu.renderText(s, COLOR_WHITE, VBO, 200 - index * 14f)
        }
    }

    override fun endDrawing() {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        super.endDrawing()
    }

    override fun onWindowClose() {
        fontUbuntu.free()
        super.onWindowClose()
    }
}
