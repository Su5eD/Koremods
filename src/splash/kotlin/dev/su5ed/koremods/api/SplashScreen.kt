package dev.su5ed.koremods.api

import org.apache.logging.log4j.core.LoggerContext

interface SplashScreen {
    fun awaitInit()
    
    fun injectSplashLogger(context: LoggerContext)
    
    fun log(message: String)
    
    fun close()
    
    fun isClosing(): Boolean
}
