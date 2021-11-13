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

package dev.su5ed.koremods.splash

import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack

fun createVertexShader(name: String, shaderProgram: Int) {
    createShader("$name.vert", GL_VERTEX_SHADER, shaderProgram)
}

fun createFragmentShader(name: String, shaderProgram: Int) {
    createShader("$name.frag", GL_FRAGMENT_SHADER, shaderProgram)
}

fun createShader(name: String, type: Int, shaderProgram: Int) {
    val source = getContextResourceAsStream("shaders/$name").bufferedReader().readText()
    
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    
    // Check for errors
    MemoryStack.stackPush().use { stack ->
        val success = stack.mallocInt(1)
        glGetShaderiv(shader, GL_COMPILE_STATUS, success)
        if (success.get() != GL_TRUE) {
            val infoLog = glGetShaderInfoLog(shader, 512)
            error(infoLog)
        }
    }
    
    glAttachShader(shaderProgram, shader)
    glDeleteShader(shader)
}
