package dev.su5ed.koremods.splash.render

import dev.su5ed.koremods.splash.Uniform
import dev.su5ed.koremods.splash.UniformFade
import dev.su5ed.koremods.splash.bufferIndices
import dev.su5ed.koremods.splash.bufferVertices
import org.lwjgl.opengl.GL30.*

class RenderFade : RenderBase() {
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
    private var fadeTime: Float = 500f
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
        uniformFade.reset()
        uniformFade.reverse = true
        fadeTime = 750f
    }

    override fun shouldCloseWindow(): Boolean = uniformFade.reverse && currentFade <= 0f
}
