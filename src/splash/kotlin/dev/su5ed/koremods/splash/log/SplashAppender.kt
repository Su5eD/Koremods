package dev.su5ed.koremods.splash.log

import dev.su5ed.koremods.splash.KoremodsSplashScreen
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

class SplashAppender(name: String, filter: Filter?, private val splash: KoremodsSplashScreen) : AbstractAppender(name, filter, null, true, Property.EMPTY_ARRAY) {
    companion object {
        fun createAppender(name: String, filter: Filter?, splash: KoremodsSplashScreen): SplashAppender {
            return SplashAppender(name, filter, splash)
        }
    }
    
    override fun append(event: LogEvent) {
        splash.log(event.message.formattedMessage)
    }
}
