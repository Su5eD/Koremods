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

package wtf.gofancy.koremods.splash

import wtf.gofancy.koremods.splash.math.Matrix4f
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack

interface Uniform {
    fun init(shaderProgram: Int)
    
    fun tick() {}
}

abstract class UniformBase(private val name: String) : Uniform {
    var location: Int = 0
        private set

    override fun init(shaderProgram: Int) {
        location = glGetUniformLocation(shaderProgram, name)
    }
}

object UniformTime : UniformBase("time") {
    override fun tick() {
        val timeValue = glfwGetTime().toFloat()
        glUniform1f(location, timeValue)
    }
}

object UniformResolution : UniformBase("resolution") {
    override fun init(shaderProgram: Int) {
        super.init(shaderProgram)
        
        glUniform2f(location, KoremodsSplashScreen.WINDOW_SIZE.first.toFloat(), KoremodsSplashScreen.WINDOW_SIZE.second.toFloat())
    }
}

class UniformFade : UniformBase("fade") {
    private var startMillis: Long = 0
    private var currentFade: Float = 0f
    private var peakFade: Float = 0f
    var reverse: Boolean = false
        set(value) {
            field = value
            reset()
            peakFade = currentFade
        }

    override fun init(shaderProgram: Int) {
        super.init(shaderProgram)
        reset()
    }
    
    private fun reset() {
        startMillis = System.currentTimeMillis()
    }

    fun update(length: Float): Float { 
        val delta = (System.currentTimeMillis() - startMillis) / length
        val fade = if (reverse) peakFade - delta else delta
        currentFade = fade.coerceIn(0f, 1f)
        
        glUniform1f(location, currentFade)
        return currentFade
    }
}

object UniformView : UniformBase("view") {
    private val buffer = MemoryStack.stackMallocFloat(4 * 4)
    
    fun update(matrix: Matrix4f) {
        buffer.clear()
        matrix.toBuffer(buffer)
        glUniformMatrix4fv(location, false, buffer)
    }
}

object UniformTextColor : UniformBase("textColor") {
    fun update(textColor: Triple<Float, Float, Float>) {
        glUniform3f(location, textColor.first, textColor.second, textColor.third)
    }
}
