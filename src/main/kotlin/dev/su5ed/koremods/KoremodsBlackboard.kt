package dev.su5ed.koremods

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File

object KoremodsBlackboard {
    const val LOGGER_PACKAGE = "Koremods"
    
    var cacheDir: File? = null
    var scriptContextClassLoader: ClassLoader? = null
    
    fun createLogger(name: String): Logger {
        return LogManager.getLogger("$LOGGER_PACKAGE.$name")
    }
}
