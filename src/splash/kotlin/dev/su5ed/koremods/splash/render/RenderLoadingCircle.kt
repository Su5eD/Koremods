package dev.su5ed.koremods.splash.render

import dev.su5ed.koremods.splash.*
import org.lwjgl.opengl.GL30.*

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
