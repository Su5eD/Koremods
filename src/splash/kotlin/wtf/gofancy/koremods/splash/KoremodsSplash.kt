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

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import wtf.gofancy.koremods.api.SplashScreen
import wtf.gofancy.koremods.splash.render.*
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal val WINDOW_SIZE = Pair(550, 250)
internal const val WINDOW_ICON = "logo.png"

class KoremodsSplashScreen(private val logger: Logger) : SplashScreen {
    companion object {
        private const val CLOSE_DELAY_MS = 1000
    }
    
    private val renderText = RenderText()
    private val renders = listOf(
        RenderSplash(),
        RenderLoadingCircle(),
        renderText,
        RenderFade()
    )
    
    private val initLatch = CountDownLatch(1)
    private var terminateOnClose: Boolean = false

    private var errorCallback: GLFWErrorCallback? = null
    private var window: Long = 0
    private var closeWindow = false
    private var endWindow = false
    private var closeDelayMillis: Long = 0
    
    private val cursorX = MemoryStack.stackMallocDouble(1)
    private val cursorY = MemoryStack.stackMallocDouble(1)
    private val winX: IntBuffer = MemoryStack.stackMallocInt(1)
    private val winY: IntBuffer = MemoryStack.stackMallocInt(1)
    private var mousePress = false

    override fun setTerminateOnClose(terminate: Boolean) {
        terminateOnClose = terminate
    }

    override fun startOnThread() {
        thread(name = "SplashRender", block = {
            try {
                initWindow()
                loop()
                glfwDestroyWindow(window)
            } catch(t: Throwable) {
                logger.catching(t)  
            } finally {
                if (terminateOnClose) glfwTerminate()
                errorCallback?.free()
            }
        })
        
        try {
            initLatch.await(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.catching(e)
        }
    }

    private fun initWindow() {
        errorCallback = glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err))
        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW")
        
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE)
        
        renders.map(Render::getWindowHints).forEach { it.forEach(::glfwWindowHint) }
        
        window = glfwCreateWindow(WINDOW_SIZE.first, WINDOW_SIZE.second, "Koremods loading", NULL, NULL)
        if (window == NULL) throw RuntimeException("Failed to create the GLFW window")
        
        val monitor = glfwGetPrimaryMonitor()
        if (monitor != NULL) {
            val vidmode = glfwGetVideoMode(monitor)!!
            glfwSetWindowPos(window, (vidmode.width() - WINDOW_SIZE.first) / 2, (vidmode.height() - WINDOW_SIZE.second) / 2)
        }
        setWindowIcon()
        glfwSetCursorPosCallback(window, ::cursorPosCallback)
        glfwSetMouseButtonCallback(window, ::mouseButtonCallback)
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        glfwSwapInterval(1)
        
        renders.forEach { it.initWindow(window, monitor) }
        
        glfwShowWindow(window)
        glfwPollEvents()
    }

    private fun initRender() {
        renders.forEach {
            val VAO: Int
            val VBO: Int
            val EBO: Int

            MemoryStack.stackPush().use { stack ->
                val vao = stack.mallocInt(1)
                glGenVertexArrays(vao)
                VAO = vao.get()

                val vbo = stack.mallocInt(1)
                glGenBuffers(vbo)
                VBO = vbo.get()

                val ebo = stack.mallocInt(1)
                glGenBuffers(ebo)
                EBO = ebo.get()
            }
            
            glBindVertexArray(VAO)
            
            glBindBuffer(GL_ARRAY_BUFFER, VBO)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO)
            
            glBindVertexArray(0)
            
            it.bake(VAO, VBO, EBO, ::createShaders)
            it.initRender()
        }
    }

    private fun loop() {
        initRender()
        initLatch.countDown()
        
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            glClear(GL_COLOR_BUFFER_BIT)
            
            renders.forEach {
                it.startDrawing()
                it.draw(window)
                it.endDrawing()
            }
            
            glfwSwapBuffers(window)
            
            if (closeWindow) {
                val delta = System.currentTimeMillis() - closeDelayMillis
                if (delta >= CLOSE_DELAY_MS) {
                    renders.forEach(Render::windowClosing)
                    closeWindow = false
                    endWindow = true
                }
            }
            if (endWindow && renders.all(Render::shouldCloseWindow)) break
        }
        
        renders.forEach(Render::onWindowClose)
    }
    
    private fun setWindowIcon() {
        loadImage(WINDOW_ICON, false) { stack, width, height, image ->
            val glfwImages = GLFWImage.mallocStack(1, stack)
        
            glfwImages.position(0)
            glfwImages.width(width.get(0))
            glfwImages.height(height.get(0))
            glfwImages.pixels(image)
            glfwImages.position(0)
            glfwSetWindowIcon(window, glfwImages)
        }
    }
    
    override fun close(delay: Boolean) {
        if (delay) {
            closeWindow = true
            closeDelayMillis = System.currentTimeMillis()
        } else {
            glfwSetWindowShouldClose(window, true)
        }
    }

    @Synchronized
    override fun log(level: Level, message: String) {
        renderText.log(level, message)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun cursorPosCallback(window: Long, x: Double, y: Double) {
        if (mousePress) {
            val offsetCursorX = (x - cursorX.get()).toInt()
            val offsetCursorY = (y - cursorY.get()).toInt()
            cursorX.rewind()
            cursorY.rewind()

            glfwGetWindowPos(window, winX, winY)
            glfwSetWindowPos(window, winX.get() + offsetCursorX, winY.get() + offsetCursorY)
            winX.rewind()
            winY.rewind()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
            mousePress = true

            glfwGetCursorPos(window, cursorX, cursorY)
        } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
            mousePress = false
        }
    }
}

private fun createShaders(vertex: String?, fragment: String?): Int {
    val program = glCreateProgram()
    
    vertex?.let { createVertexShader(it, program) }
    fragment?.let { createFragmentShader(it, program) }

    glLinkProgram(program)
    return program
}
