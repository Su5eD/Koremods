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
import wtf.gofancy.koremods.LOGGER_GROUP
import wtf.gofancy.koremods.LoaderMode
import wtf.gofancy.koremods.createLogger
import java.nio.file.Path
import java.util.*

/**
 * Koremods launch entrypoint, used by frontends to initialize the Koremods discovery & loading process
 */
@Suppress("unused")
object KoremodsLaunch {
    private val LOGGER: Logger = createLogger("Launch")

    /**
     * [KoremodsLoader] instance created by [launch].
     * Upon success, loaded script packs will be accessible by frontends by this property.
     *
     * @see KoremodsLoader
     */
    var LOADER: KoremodsLoader? = null
        private set

    /**
     * Global [KoremodsLaunchPlugin] service instance located using the [ServiceLoader].
     * If none can be found on the classpath, [DummyKoremodsLaunchPlugin] will be used instead.
     *
     * @see KoremodsLaunchPlugin
     */
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
     *
     * @param loaderMode the LoaderMode to use with the [LOADER] instance
     * @param discoveryDir optional directory containing koremods script packs
     * @param discoveryPaths additional paths to search for Koremods script packs
     */
    fun launch(loaderMode: LoaderMode, discoveryDir: Path?, discoveryPaths: Iterable<Path>) {
        if (LOADER != null) throw IllegalStateException("Koremods has already been launched")

        LOGGER.info("Launching Koremods instance")

        scriptContextClassLoader = javaClass.classLoader

        val contexts = mutableSetOf(
            getLoggerContext(Thread.currentThread().contextClassLoader),
            getLoggerContext(scriptContextClassLoader!!),
        )

        LOGGER.debug("Injecting plugin screen log appenders")
        contexts.forEach { ctx -> injectKoremodsLogAppender(ctx, PLUGIN::appendLogMessage) }

        LOADER = KoremodsLoader(loaderMode).apply {
            if (discoveryDir != null) loadKoremods(discoveryDir, discoveryPaths)
            else loadKoremods(discoveryPaths)
        }
        LOGGER.info("Discovering script packs finished successfully")
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
    config.addLogger(LOGGER_GROUP, loggerConfig)

    context.updateLoggers()
}

/**
 * Create a new [org.apache.logging.log4j.spi.LoggerContext] for the given classloader
 * and cast it to its implementation [LoggerContext] type.
 * 
 * @param classLoader the context owner
 * @return the newly created logger context cast to the implementation type
 */
private fun getLoggerContext(classLoader: ClassLoader): LoggerContext {
    return LogManager.getContext(classLoader, false) as LoggerContext
}
