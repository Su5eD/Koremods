package dev.su5ed.koremods.splash

import dev.su5ed.koremods.splash.log.SplashAppender
import dev.su5ed.koremods.splash.render.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.IntBuffer
import kotlin.concurrent.thread

internal val WINDOW_SIZE = Pair(550, 250)
internal const val WINDOW_ICON = "logo.png"

fun initSplashScreen(): KoremodsSplashScreen {
    return KoremodsSplashScreen()
        .also { splash -> thread(name = "KoremodSplash", block = splash::run) }
}

fun injectSplashAppender(logger: org.apache.logging.log4j.core.Logger, splash: KoremodsSplashScreen) {
    val ctx: LoggerContext = LogManager.getContext(false) as LoggerContext
    val config: Configuration = ctx.configuration
    
    val appender = SplashAppender.createAppender("KoremodsSplashAppender", null, splash)
    appender.start()
    config.addAppender(appender)
    
    logger.addAppender(appender)
    ctx.updateLoggers()
}

class KoremodsSplashScreen {
    private val renderText = RenderText()
    private val renders = listOf(
        RenderSplash(),
        RenderLoadingCircle(),
        renderText,
        RenderFade()
    )
    
    private var errorCallback: GLFWErrorCallback? = null
    private var window: Long = 0
    private var closeWindow = false
    
    private val cursorX = MemoryStack.stackMallocDouble(1)
    private val cursorY = MemoryStack.stackMallocDouble(1)
    private val winX: IntBuffer = MemoryStack.stackMallocInt(1)
    private val winY: IntBuffer = MemoryStack.stackMallocInt(1)
    private var mousePress = false

    fun run() {
        try {
            initWindow()
            loop()
            glfwDestroyWindow(window)
        } finally {
            glfwTerminate()
            errorCallback?.free()
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
        
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            glClear(GL_COLOR_BUFFER_BIT)
            
            renders.forEach {
                it.startDrawing()
                it.draw(window)
                it.endDrawing()
            }
            
            glfwSwapBuffers(window)
            
            if (closeWindow && renders.all(Render::shouldCloseWindow)) break
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
    
    fun closeWindow() {
        renders.forEach(Render::windowClosing)
        
        closeWindow = true
    }
    
    @Synchronized
    fun log(message: String) {
        renderText.log(message)
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
