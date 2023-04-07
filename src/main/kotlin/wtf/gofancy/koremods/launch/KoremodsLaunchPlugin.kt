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
import wtf.gofancy.koremods.dsl.ClassTransformerParams
import wtf.gofancy.koremods.dsl.FieldTransformerParams
import wtf.gofancy.koremods.dsl.MethodTransformerParams
import wtf.gofancy.koremods.dsl.TransformerPropertiesStore
import java.nio.file.Path

/**
 * A java service interface implemented by frontends for additional configuration of [wtf.gofancy.koremods.launch.KoremodsLaunch].
 */
interface KoremodsLaunchPlugin {

    /**
     * Whether the Koremods Splash screen is available in the current environment.
     * Does NOT toggle the state of the screen, that is done by [wtf.gofancy.koremods.KoremodsConfig.enableSplashScreen]
     */
    val splashScreenAvailable: Boolean

    /**
     * A list of fully qualified class names and package prefixes allowed to be classloaded in Koremods Scripts,
     * in addition to [wtf.gofancy.koremods.script.ALLOWED_CLASSES]
     */
    val allowedClasses: List<String>

    /**
     * A list of import expressions to be implicitly added to Koremods Scripts.
     * Syntax for these is the same as for regular `import` statements.
     */
    val defaultImports: List<String>

    /**
     * Fallback logger appender callback used for [KoremodsLogAppender] when the splash screen is disabled/not avaiable.
     *
     * @param level The logging Level
     * @param message the message string to be logger
     *
     * @see wtf.gofancy.koremods.createLogger
     */
    fun appendLogMessage(level: Level, message: String)

    /**
     * Create a custom ClassLoader that will be used to load compiled scripts from Script Packs.
     *
     * @param path Path to the script .jar file
     * @param parent The parent ClassLoader
     * @return A new ClassLoader, or `null` to use the default [wtf.gofancy.koremods.MemoryClassLoader] provided by Koremods.
     */
    fun createCompiledScriptClassLoader(path: Path, parent: ClassLoader?): ClassLoader? = null

    /**
     * Allows changing the input parameters of class Transformers built using [wtf.gofancy.koremods.dsl.TransformerBuilder]
     * before they're finalized.
     *
     * @param params Input transformer parameters
     * @return The processed parameters
     */
    fun mapClassTransformer(params: ClassTransformerParams, props: TransformerPropertiesStore): ClassTransformerParams = params

    /**
     * Allows changing the input parameters of method Transformers built using [wtf.gofancy.koremods.dsl.TransformerBuilder]
     * before they're finalized.
     *
     * @param params Input transformer parameters
     * @return The processed parameters
     */
    fun mapMethodTransformer(params: MethodTransformerParams, props: TransformerPropertiesStore): MethodTransformerParams = params

    /**
     * Allows changing the input parameters of field Transformers built using [wtf.gofancy.koremods.dsl.TransformerBuilder]
     * before they're finalized.
     *
     * @param params Input transformer parameters
     * @return The processed parameters
     */
    fun mapFieldTransformer(params: FieldTransformerParams, props: TransformerPropertiesStore): FieldTransformerParams = params
}
