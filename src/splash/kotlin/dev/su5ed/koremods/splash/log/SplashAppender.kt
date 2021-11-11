package dev.su5ed.koremods.splash.log

import dev.su5ed.koremods.api.SplashScreen
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender

class SplashAppender(name: String, filter: Filter?, private val splash: SplashScreen) : AbstractAppender(name, filter, null, true) {
    companion object {
        fun createAppender(name: String, filter: Filter?, splash: SplashScreen): SplashAppender {
            return SplashAppender(name, filter, splash)
        }
    }
    
    override fun append(event: LogEvent) {
        if (!splash.isClosing()) splash.log(event.message.formattedMessage)
    }
}
