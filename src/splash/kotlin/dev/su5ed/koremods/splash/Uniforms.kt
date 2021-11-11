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
