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
