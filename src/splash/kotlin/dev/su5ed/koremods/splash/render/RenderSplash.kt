package dev.su5ed.koremods.splash.render

import dev.su5ed.koremods.splash.Uniform
import dev.su5ed.koremods.splash.bufferIndices
import dev.su5ed.koremods.splash.bufferVertices
import dev.su5ed.koremods.splash.loadImage
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL30.*

open class RenderSplash : RenderBase() {
    companion object {
        private const val TEXTURE = "koremods_splash.png"
    }
    
    private val vertices = floatArrayOf(
         // positions    // colors           // texture coords
         1.0f,   1.0f,   1.0f, 0.0f, 0.0f,   1.0f, 1.0f,    // top right
         1.0f,  -1.0f,   0.0f, 1.0f, 0.0f,   1.0f, 0.0f,    // bottom right
        -1.0f,  -1.0f,   0.0f, 0.0f, 1.0f,   0.0f, 0.0f,    // bottom left
        -1.0f,   1.0f,   1.0f, 1.0f, 0.0f,   0.0f, 1.0f     // top left 
    )
    private val indices = intArrayOf(
        0, 1, 3, // first triangle
        1, 2, 3  // second triangle
    )
    
    override val vertexShader: String = "splash"
    override val fragmentShader: String = "splash"
    override val uniforms: List<Uniform> = emptyList()
    
    private var texture: Int = 0
    
    override fun getWindowHints(): Map<Int, Int> = mapOf(
        GLFW_RESIZABLE to GLFW_FALSE,
        GLFW_DECORATED to GLFW_FALSE,
        GLFW_VISIBLE to GLFW_FALSE,
        GLFW_TRANSPARENT_FRAMEBUFFER to GLFW_TRUE
    )

    override fun initWindow(window: Long, monitor: Long) {}

    override fun initRender() {
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        glBindVertexArray(VAO)
        
        // position attribute
        val posAttrib = glGetAttribLocation(shader, "aPos")
        glEnableVertexAttribArray(posAttrib)
        glVertexAttribPointer(posAttrib, 2, GL_FLOAT, false, 7 * Float.SIZE_BYTES, 0)
        
        // color attribute
        val colorAttrib = glGetAttribLocation(shader, "aColor")
        glEnableVertexAttribArray(colorAttrib)
        glVertexAttribPointer(colorAttrib, 3, GL_FLOAT, false, 7 * Float.SIZE_BYTES, (2 * Float.SIZE_BYTES).toLong())
        
        // texture coord attribute
        val texCoordAttrib = glGetAttribLocation(shader, "aTexCoord")
        glEnableVertexAttribArray(texCoordAttrib)
        glVertexAttribPointer(texCoordAttrib, 2, GL_FLOAT, false, 7 * Float.SIZE_BYTES, (5 * Float.SIZE_BYTES).toLong())
        
        bufferIndices(EBO, indices, GL_STATIC_DRAW)
        bufferVertices(VBO, vertices, GL_STATIC_DRAW)
        
        glBindVertexArray(0)
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glDisableVertexAttribArray(2)
        
        texture = loadTexture()
    }

    override fun draw(window: Long) {
        glBindTexture(GL_TEXTURE_2D, texture)
        
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
    }

    private fun loadTexture(): Int {
        return loadImage(TEXTURE, true) { _, width, height, image ->
            val texId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, texId)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(), height.get(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image)
            glGenerateMipmap(GL_TEXTURE_2D)

            return@loadImage texId
        }
    }
}
