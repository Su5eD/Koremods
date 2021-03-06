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

import wtf.gofancy.koremods.splash.Uniform
import org.lwjgl.opengl.GL30.*

abstract class RenderBase : Render {
    abstract val vertexShader: String?
    abstract val fragmentShader: String?
    
    abstract val uniforms: List<Uniform>

    protected var VAO: Int = 0
        private set
    protected var VBO: Int = 0
        private set
    protected var EBO: Int = 0
        private set
    protected var shader: Int = 0
        private set

    override fun bake(VAO: Int, VBO: Int, EBO: Int, shaderFactory: (String?, String?) -> Int) {
        this.VAO = VAO
        this.VBO = VBO
        this.EBO = EBO
        shader = shaderFactory(vertexShader, fragmentShader)
        
        glUseProgram(shader)
        uniforms.forEach { it.init(shader) }
        glUseProgram(0)
    }

    override fun startDrawing() {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        
        glBindVertexArray(VAO)
        glUseProgram(shader)
        
        uniforms.forEach(Uniform::tick)
    }

    override fun endDrawing() {
        glUseProgram(0)
        
        glBindVertexArray(0)
    }

    override fun windowClosing() {}

    override fun shouldCloseWindow(): Boolean = true

    override fun onWindowClose() {
        glDeleteVertexArrays(VAO)
        glDeleteBuffers(VBO)
        glDeleteBuffers(EBO)
    }
}
