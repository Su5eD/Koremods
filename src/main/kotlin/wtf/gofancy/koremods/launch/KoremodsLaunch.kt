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

package wtf.gofancy.koremods.launch

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import wtf.gofancy.koremods.KoremodsDiscoverer
import wtf.gofancy.koremods.api.SplashScreen
import wtf.gofancy.koremods.parseMainConfig
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.div

@Suppress("unused")
class KoremodsLaunch {
    
    fun launch(prelaunch: KoremodsPrelaunch, cacheDir: File, configDir: Path, modsDir: Path, discoveryUrls: Array<URL>, launchPlugin: KoremodsLaunchPlugin?) {
        KoremodsBlackboard.cacheDir = cacheDir
        KoremodsBlackboard.scriptContextClassLoader = KoremodsPrelaunch.dependencyClassLoader
        
        val configPath = configDir / KoremodsBlackboard.CONFIG_FILE
        val config = parseMainConfig(configPath)
        var splash: SplashScreen? = null
        val contexts = mutableSetOf(
            getLoggerContext(KoremodsPrelaunch::class.java.classLoader),
            getLoggerContext(KoremodsPrelaunch.dependencyClassLoader),
        )
        
        if (launchPlugin != null) {
            val callback: (String) -> Unit
            val os = System.getProperty("os.name").lowercase()
            
            if (config.enableSplashScreen && launchPlugin.enableSplashScreen && !os.contains("mac")) {
                splash = launchPlugin.createSplashScreen(prelaunch)!!
                callback = splash::log

                contexts.add(getLoggerContext(splash::class.java.classLoader))
            } else {
                callback = launchPlugin::appendLogMessage
            }
            
            contexts.forEach { ctx -> injectSplashLogger(ctx, callback) }
        }
        
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

internal fun injectSplashLogger(context: LoggerContext, callback: (String) -> Unit) {
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
