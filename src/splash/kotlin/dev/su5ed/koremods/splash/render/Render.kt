package dev.su5ed.koremods.splash.render

interface Render {
    fun getWindowHints(): Map<Int, Int>
    
    fun initWindow(window: Long, monitor: Long)
    
    fun bake(VAO: Int, VBO: Int, EBO: Int, shaderFactory: (String?, String?) -> Int)

    fun initRender()
    
    fun startDrawing()
    
    fun draw(window: Long)
    
    fun endDrawing()
    
    fun windowClosing()
    
    fun shouldCloseWindow(): Boolean
    
    fun onWindowClose()
}
