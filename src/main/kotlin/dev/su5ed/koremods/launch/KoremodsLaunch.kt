/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
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

package dev.su5ed.koremods.launch

import dev.su5ed.koremods.KoremodsDiscoverer
import dev.su5ed.koremods.api.SplashBlackboard
import dev.su5ed.koremods.api.SplashScreen
import dev.su5ed.koremods.parseMainConfig
import dev.su5ed.koremods.prelaunch.AppenderCallback
import dev.su5ed.koremods.prelaunch.KoremodsBlackboard
import dev.su5ed.koremods.prelaunch.KoremodsPrelaunch
import dev.su5ed.koremods.prelaunch.SplashScreenFactory
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.div

@Suppress("unused")
class KoremodsLaunch {
    
    fun launch(prelaunch: KoremodsPrelaunch, cacheDir: File, configDir: Path, modsDir: Path, discoveryUrls: Array<URL>, splashFactory: SplashScreenFactory?, appenderCallback: AppenderCallback?) {
        KoremodsBlackboard.cacheDir = cacheDir
        KoremodsBlackboard.scriptContextClassLoader = KoremodsPrelaunch.dependencyClassLoader
        
        val configPath = configDir / KoremodsBlackboard.CONFIG_FILE
        val config = parseMainConfig(configPath)
        var splash: SplashScreen? = null
        val callback: AppenderCallback?
        val contexts = mutableSetOf(
            getLoggerContext(KoremodsPrelaunch::class.java.classLoader),
            getLoggerContext(KoremodsPrelaunch.dependencyClassLoader),
        )
        
        if (config.enableSplashScreen && splashFactory != null) {
            SplashBlackboard.loggerFactory = Function(KoremodsBlackboard::createLogger)
            
            splash = splashFactory.createSplashScreen(prelaunch)!!
            callback = AppenderCallback(splash::log)
            
            contexts.add(getLoggerContext(splash::class.java.classLoader))
        } else {
            callback = appenderCallback
        }
        
        if (callback != null) contexts.forEach { ctx -> injectSplashLogger(ctx, callback) }
        
        try {
            KoremodsDiscoverer.discoverKoremods(modsDir, discoveryUrls)
            
            splash?.close(true)
        } catch (t: Throwable) {
            splash?.close(false)
            throw t
        }
    }
    
    private fun getLoggerContext(classLoader: ClassLoader): LoggerContext {
        return LogManager.getContext(classLoader, false) as LoggerContext
    }
}

internal fun injectSplashLogger(context: LoggerContext, callback: AppenderCallback) {
    val config: Configuration = context.configuration

    val appender = KoremodsLogAppender("KoremodsAppender", null, callback)
    appender.start()
    config.addAppender(appender)

    val loggerConfig: LoggerConfig = LoggerConfig.createLogger(
        true, Level.ALL, "KoremodsLogger",
        "true", emptyArray(), null, config, null
    )
    loggerConfig.addAppender(appender, Level.ALL, null)
    config.addLogger(KoremodsBlackboard.NAME, loggerConfig)

    context.updateLoggers()
}
