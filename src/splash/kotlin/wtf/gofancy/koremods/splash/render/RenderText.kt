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

import org.apache.logging.log4j.Level
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.opengl.GL30.*
import wtf.gofancy.koremods.splash.*
import java.util.concurrent.ConcurrentLinkedQueue

class RenderText : RenderBase() {
    companion object {
        private val COLOR_WHITE = Triple(1f, 1f, 1f)
        private val COLOR_BLACK = Triple(0f, 0f, 0f)
        private val COLOR_YELLOW = Triple(0.9f, 0.9f, 0f)
        private val COLOR_NEON_BLUE = Triple(21 / 255f, 242 / 255f, 253 / 255f)

        private const val COPYRIGHT_NOTICE = "Copyright \u00a9 2021 Garden of Fancy. All Rights Reserved."
    }
    
    private val indices = intArrayOf(
        0, 1, 3,    // first triangle
        1, 2, 3     // second triangle
    )
    private val splashLog: ConcurrentLinkedQueue<Pair<Level, String>> = ConcurrentLinkedQueue()
    
    override val vertexShader: String = "font"
    override val fragmentShader: String = "font"
    override val uniforms: List<Uniform> = listOf(UniformView, UniformTextColor)
    
    private var logShader: Int = 0
    private val fontUbuntu = TrueType("ubuntu_regular.ttf", 14f, KoremodsSplashScreen.WINDOW_SIZE)

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
    
    fun log(level: Level, message: String) {
        splashLog.add(Pair(level, message))
    }

    override fun draw(window: Long) {
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        
        fontUbuntu.renderText(COPYRIGHT_NOTICE, COLOR_BLACK, VBO, 230f)
        
        glUseProgram(logShader)
        splashLog.reversed().forEachIndexed { index, (level, message) ->
            fontUbuntu.renderText(message, getLevelColor(level), VBO, 200 - index * fontUbuntu.fontHeight)
        }
    }

    override fun onWindowClose() {
        fontUbuntu.free()
        super.onWindowClose()
    }
    
    private fun getLevelColor(level: Level): Triple<Float, Float, Float> {
        return when(level) {
            Level.ERROR -> COLOR_NEON_BLUE
            Level.WARN -> COLOR_YELLOW
            else -> COLOR_WHITE
        }
    }
}
