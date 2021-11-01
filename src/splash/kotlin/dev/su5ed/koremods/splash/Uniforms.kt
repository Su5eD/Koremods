package dev.su5ed.koremods.splash

import dev.su5ed.koremods.splash.math.Matrix4f
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
        
        glUniform2f(location, WINDOW_SIZE.first.toFloat(), WINDOW_SIZE.second.toFloat())
    }
}

class UniformFade : UniformBase("fade") {
    private var startMillis: Long = 0
    var reverse: Boolean = false

    override fun init(shaderProgram: Int) {
        super.init(shaderProgram)
        reset()
    }
    
    fun reset() {
        startMillis = System.currentTimeMillis()
    }

    fun update(length: Float): Float { 
        val delta = (System.currentTimeMillis() - startMillis) / length
        val fade = if (reverse) 1 - delta else delta
        val actualFade = fade.coerceIn(0f, 1f)
        
        glUniform1f(location, actualFade)
        return actualFade
    }
}

object UniformView : UniformBase("view") {
    fun update(matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val buf = stack.mallocFloat(4 * 4)
            matrix.toBuffer(buf)
            glUniformMatrix4fv(location, false, buf)
        }
    }
}

object UniformTextColor : UniformBase("textColor") {
    fun update(textColor: Triple<Float, Float, Float>) {
        glUniform3f(location, textColor.first, textColor.second, textColor.third)
    }
}
