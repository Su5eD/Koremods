package wtf.gofancy.koremods.api

import org.apache.logging.log4j.Level

interface SplashScreen {
    fun startOnThread()

    fun setTerminateOnClose(terminate: Boolean)

    fun log(level: Level, message: String)

    fun close(delay: Boolean)
}
