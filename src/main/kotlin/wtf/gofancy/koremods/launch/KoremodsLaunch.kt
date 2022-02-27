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

package wtf.gofancy.koremods.launch

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import wtf.gofancy.koremods.KoremodsDiscoverer
import wtf.gofancy.koremods.api.KoremodsLaunchPlugin
import wtf.gofancy.koremods.api.SplashScreen
import wtf.gofancy.koremods.parseMainConfig
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch
import java.net.URL
import kotlin.io.path.div

@Suppress("unused")
object KoremodsLaunch {
    private val LOGGER: Logger = LogManager.getLogger("Koremods.Launch")
    
    fun launch(prelaunch: KoremodsPrelaunch, discoveryUrls: Array<URL>, libraries: Array<String>, launchPlugin: KoremodsLaunchPlugin?) {
        LOGGER.info("Launching Koremods instance")
        
        KoremodsBlackboard.cacheDir = prelaunch.cacheDir
        KoremodsBlackboard.scriptContextClassLoader = javaClass.classLoader
        
        val configPath = prelaunch.configDir / KoremodsBlackboard.CONFIG_FILE
        val config = parseMainConfig(configPath)
        var splash: SplashScreen? = null
        val contexts = mutableSetOf(
            getLoggerContext(KoremodsPrelaunch::class.java.classLoader),
            getLoggerContext(KoremodsBlackboard.scriptContextClassLoader),
        )
        
        if (launchPlugin != null) {
            val callback: (Level, String) -> Unit
            val os = System.getProperty("os.name").lowercase()
            
            if (config.enableSplashScreen && launchPlugin.shouldEnableSplashScreen() && !os.contains("mac")) {
                LOGGER.info("Creating splash screen")
                splash = launchPlugin.createSplashScreen(prelaunch)
                callback = splash::log

                contexts.add(getLoggerContext(splash::class.java.classLoader))
            } else {
                callback = launchPlugin::appendLogMessage
            }
            
            LOGGER.debug("Injecting splash screen log appenders")
            contexts.forEach { ctx -> injectSplashLogger(ctx, callback) }
        }
        
        try {
            KoremodsDiscoverer.INSTANCE = KoremodsDiscoverer(prelaunch.mainJarFile.name, *libraries).apply { 
                discoverKoremods(prelaunch.modsDir, discoveryUrls)
            }
            
            LOGGER.fatal("Discovering Koremods finished successfully")
            splash?.close(true)
        } catch (t: Throwable) {
            LOGGER.fatal("An error has occured while discovering Koremods")
            splash?.close(false)
            throw t
        }
    }
    
    private fun getLoggerContext(classLoader: ClassLoader): LoggerContext {
        return LogManager.getContext(classLoader, false) as LoggerContext
    }
}

internal fun injectSplashLogger(context: LoggerContext, callback: (Level, String) -> Unit) {
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
