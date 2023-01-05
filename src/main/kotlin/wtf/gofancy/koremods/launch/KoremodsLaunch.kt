/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2023 Garden of Fancy
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
import org.apache.logging.log4j.core.config.LoggerConfig
import wtf.gofancy.koremods.KoremodsLoader
import wtf.gofancy.koremods.LoaderMode
import wtf.gofancy.koremods.parseMainConfig
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.splash.KoremodsSplashScreen
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div

/**
 * Koremods launch entrypoint, used by frontends to initialize the Koremods discovery & loading process
 */
@Suppress("unused")
object KoremodsLaunch {
    private val LOGGER: Logger = KoremodsBlackboard.createLogger("Launch")

    /**
     * `KoremodsLoader` instance created by [launch].
     * Upon success, loaded script packs will be accessible by frontends by this property.
     * 
     * @see KoremodsLoader
     */
    var LOADER: KoremodsLoader? = null
        private set
    
    val PLUGIN: KoremodsLaunchPlugin =
        ServiceLoader.load(KoremodsLaunchPlugin::class.java, KoremodsLaunchPlugin::class.java.classLoader)
            .firstOrNull()
            ?.also { plugin -> LOGGER.info("Found launch plugin: ${plugin.javaClass.name}") }
            ?: DummyKoremodsLaunchPlugin

    /**
     * Used as the base classloader for Koremods scripts.
     *
     * @see wtf.gofancy.koremods.script.KoremodsScriptEvaluationConfiguration
     */
    var scriptContextClassLoader: ClassLoader? = null
        private set

    /**
     * Configures the Koremods scripting environment and initializes the loading process.
     * A splash screen is created if it's enabled in the [config][wtf.gofancy.koremods.KoremodsConfig]
     * and compatible with the current environment.
     *
     * @param loaderMode the LoaderMode to use with the [LOADER] instance
     * @param configDir path to the directory containing the main koremods config file
     * @param discoveryDir optional directory containing koremods script packs
     * @param discoveryPaths additional paths to search for Koremods script packs
     */
    fun launch(loaderMode: LoaderMode, configDir: Path, discoveryDir: Path?, discoveryPaths: Iterable<Path>) {
        if (LOADER != null) throw IllegalStateException("Koremods has already been launched")
        
        LOGGER.info("Launching Koremods instance")

        scriptContextClassLoader = javaClass.classLoader

        val configPath = configDir / KoremodsBlackboard.CONFIG_FILE
        val config = parseMainConfig(configPath)
        val splash: KoremodsSplashScreen?
        val contexts = mutableSetOf(
            getLoggerContext(Thread.currentThread().contextClassLoader),
            getLoggerContext(scriptContextClassLoader!!),
        )

        val callback: (Level, String) -> Unit
        val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

        if (config.enableSplashScreen && PLUGIN.splashScreenAvailable && !isMacOS) {
            LOGGER.info("Creating splash screen")
            splash = createSplashScreen()
            callback = splash::log

            contexts.add(getLoggerContext(splash.javaClass.classLoader))
            splash.startOnThread()
        } else {
            splash = null
            callback = PLUGIN::appendLogMessage
        }

        LOGGER.debug("Injecting splash screen log appenders")
        contexts.forEach { ctx -> injectKoremodsLogAppender(ctx, callback) }

        try {
            LOADER = KoremodsLoader(loaderMode).apply {
                if (discoveryDir != null) loadKoremods(discoveryDir, discoveryPaths)
                else loadKoremods(discoveryPaths)
            }

            LOGGER.info("Discovering script packs finished successfully")
            splash?.close(true)
        } catch (t: Throwable) {
            LOGGER.fatal("An error has occured while discovering script packs")
            splash?.close(false)
            throw t
        }
    }
}

/**
 * Injects a new [KoremodsLogAppender] to the specified [context], delegating all
 * append calls to the [callback] function
 *
 * @param context The logger context to inject into
 * @param callback Log appender callback
 *
 * @see KoremodsLogAppender
 */
internal fun injectKoremodsLogAppender(context: LoggerContext, callback: (Level, String) -> Unit) {
    val config = context.configuration

    val appender = KoremodsLogAppender("KoremodsAppender", null, callback)
    appender.start()
    config.addAppender(appender)

    val loggerConfig = LoggerConfig.createLogger(
        true, Level.ALL, "KoremodsLogger",
        "true", emptyArray(), null, config, null
    )
    loggerConfig.addAppender(appender, Level.ALL, null)
    config.addLogger(KoremodsBlackboard.LOGGER_GROUP, loggerConfig)

    context.updateLoggers()
}

private fun getLoggerContext(classLoader: ClassLoader): LoggerContext {
    return LogManager.getContext(classLoader, false) as LoggerContext
}

private fun createSplashScreen(): KoremodsSplashScreen {
    val logger = KoremodsBlackboard.createLogger("Splash")
    return KoremodsSplashScreen(logger)
}
