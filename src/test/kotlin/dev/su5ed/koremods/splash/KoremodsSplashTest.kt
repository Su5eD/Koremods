package dev.su5ed.koremods.splash

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import kotlin.concurrent.thread

private val fakeLog = listOf(
    "Initializing Koremods",
    "Hello world",
    "Setting up environment",
    "Creating directories",
    "Discovering scripts",
    "More useless text",
    "Located script foo",
    "Located script bar",
    "Evaluating scripts",
    "Actually evaluating scripts",
    "Compiling scrips for the first time, this may take a while"
)

private val LOG: Logger = LogManager.getLogger("KoremodsSplash")

fun main() {
    val splash = initSplashScreen()
    splash.injectSplashLogger(LogManager.getContext(false) as LoggerContext)
    
    thread(name = "SplashLog") { 
        var fakeLogIndex = 0
        
        while (fakeLogIndex < fakeLog.size) {
            Thread.sleep(250)
            LOG.info(fakeLog[fakeLogIndex++])
        }
    }
    
    Thread.sleep(9000)
    LOG.info("Done")
    splash.close()
}

