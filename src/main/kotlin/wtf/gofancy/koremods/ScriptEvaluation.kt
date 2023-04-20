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

package wtf.gofancy.koremods

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.launch.KoremodsLaunch
import wtf.gofancy.koremods.launch.KoremodsLaunchPlugin
import wtf.gofancy.koremods.script.KoremodsKtsScript
import java.io.InputStream
import java.nio.file.Path
import java.security.ProtectionDomain
import java.util.jar.JarInputStream
import kotlin.io.path.inputStream
import kotlin.reflect.KClass
import kotlin.script.experimental.api.*
import kotlin.script.experimental.impl.internalScriptingRunSuspend
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

private val LOGGER: Logger = createLogger("ScriptCompilation")

/**
 * An exception thrown when an error is encountered during the script evaluation process.
 * 
 * @param msg the detail message
 * @param cause the cause (A `null` value is permitted, and indicates that the cause is nonexistent or unknown.)
 */
class ScriptEvaluationException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

/**
 * Evaluate a set of raw, compiled koremods scripts by loading their classes into the JVM
 * and retrieving their list of transformers.
 * 
 * @param compiledPacks the script packs to evaluate
 * @return a list of evaluated koremods script packs
 */
internal fun evalScriptPacks(compiledPacks: Collection<RawScriptPack<CompiledScript>>): List<KoremodsScriptPack> {
    return compiledPacks.map { pack ->
        val processed = pack.scripts.map { script ->
            val transformers = evalTransformers(script.identifier, script.source)
            KoremodsScript(script.identifier, transformers)
        }
        return@map KoremodsScriptPack(pack.namespace, pack.path, processed)
    }
}

/**
 * Evaluate a compiled script by loading its classes and retrieving its defined transformers.
 * Ensures the list of transformers is not empty, otherwise throwing an exception.
 * 
 * @param identifier the unique identified of the script
 * @param script the compiled script to evaluate
 * @return the script's [TransformerHandler] containing its transformers
 * @throws RuntimeException if the script defines no transformers 
 */
private fun evalTransformers(identifier: Identifier, script: CompiledScript): TransformerHandler {
    val handler = LOGGER.measureMillis(Level.DEBUG, "Evaluating script $identifier") {
        val engineLogger = createLogger("${identifier.namespace}/${identifier.name}")
        evalTransformers(identifier, script, engineLogger)
    }
    return if (handler.getTransformers().isNotEmpty()) handler
    else throw RuntimeException("Script $identifier does not define any transformers")
}

/**
 * Evaluate a compiled script by loading its classes and retrieving its defined transformers.
 * 
 * @param identifier the unique identified of the script
 * @param script the compiled script to evaluate
 * @return the script's [TransformerHandler] containing its transformers
 */
fun evalTransformers(identifier: Identifier, script: CompiledScript, logger: Logger): TransformerHandler {
    when (val eval = evalScript(identifier, script, logger)) {
        is ResultWithDiagnostics.Success -> {
            when (val result = eval.value.returnValue) {
                is ResultValue.Value -> throw ScriptEvaluationException("Script $identifier returned a value instead of Unit")
                is ResultValue.Unit -> return (result.scriptInstance as KoremodsKtsScript).transformerHandler
                is ResultValue.Error -> throw ScriptEvaluationException("Exception in script $identifier", result.error)
                // this shouldn't ever happen
                ResultValue.NotEvaluated -> throw ScriptEvaluationException("An unknown error has occured while evaluating script $identifier")
            }
        }

        is ResultWithDiagnostics.Failure -> {
            LOGGER.logResultErrors(eval)
            throw ScriptEvaluationException("Failed to evaluate script $identifier. See the log for more information")
        }
    }
}

/**
 * Evaluate a compiled script by loading its classes and return the evaluation result.
 * 
 * @param identifier the unique identified of the script
 * @param script the compiled script to evaluate
 * @param logger logger object to supply to the script instance
 * @return the script evaluation result
 */
@Suppress("DEPRECATION_ERROR")
fun evalScript(identifier: Identifier, script: CompiledScript, logger: Logger): ResultWithDiagnostics<EvaluationResult> {
    LOGGER.info("Evaluating script $identifier")

    val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<KoremodsKtsScript> {
        constructorArgs(identifier, logger)
    }

    return internalScriptingRunSuspend { BasicJvmScriptEvaluator().invoke(script, evaluationConfiguration) }
}

/**
 * Load a compiled script from a [Path] that points to a valid jar file.
 * 
 * @receiver a [Path] that points to a valid jar file.
 * @return an instance of the loaded script
 */
fun Path.loadScriptFromJar(): CompiledScript {
    val className = inputStream().use { istream ->
        JarInputStream(istream).use jistream@{
            return@jistream it.manifest.mainAttributes.getValue("Main-Class")
                ?: throw IllegalArgumentException("No Main-Class manifest attribute")
        }
    }
    return KJvmCompiledScriptLoadedFromJar(className, this)
}

/**
 * Read the byte contents of all entries in a [JarInputStream].
 * 
 * @return a map of name to byte contents of all JIS entries
 */
fun JarInputStream.readEntries(): Map<String, ByteArray> {
    return generateSequence(::getNextJarEntry)
        .associate { Pair(it.name, readAllBytes()) }
}

/**
 * Represents a [CompiledScript] loaded from a [Path]. The script will prefer the [ClassLoader] created by the
 * active [KoremodsLaunchPlugin] if available.
 * 
 * @property scriptClassFQName the fully qualified name of the script's class
 * @property path path to the compiled script jar
 */
internal class KJvmCompiledScriptLoadedFromJar(private val scriptClassFQName: String, private val path: Path) : CompiledScript {
    private var loadedScript: KJvmCompiledScript? = null

    private fun getScriptOrFail(): KJvmCompiledScript = loadedScript ?: throw RuntimeException("Compiled script is not loaded yet")

    override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<KClass<*>> {
        if (loadedScript == null) {
            val actualEvalConfig = scriptEvaluationConfiguration ?: ScriptEvaluationConfiguration()
            val baseClassLoader = actualEvalConfig[ScriptEvaluationConfiguration.jvm.baseClassLoader]
                ?: Thread.currentThread().contextClassLoader
            val classLoader = KoremodsLaunch.PLUGIN.createCompiledScriptClassLoader(path, baseClassLoader)
                ?: createScriptMemoryClassLoader(baseClassLoader)
            loadedScript = createScriptFromClassLoader(scriptClassFQName, classLoader)
        }
        return getScriptOrFail().getClass(scriptEvaluationConfiguration)
    }

    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = getScriptOrFail().compilationConfiguration

    override val sourceLocationId: String?
        get() = loadedScript?.sourceLocationId

    override val otherScripts: List<CompiledScript>
        get() = getScriptOrFail().otherScripts

    override val resultField: Pair<String, KotlinType>?
        get() = getScriptOrFail().resultField

    private fun createScriptMemoryClassLoader(parent: ClassLoader?): ClassLoader {
        val entries: Map<String, ByteArray> = path.inputStream()
            .use { istream -> JarInputStream(istream).use(JarInputStream::readEntries) }
        return MemoryClassLoader(entries, parent)
    }
}

/**
 * Loads classes from bytes stored in program memory. Used by [KJvmCompiledScriptLoadedFromJar] to load compiled script classes.
 * 
 * @property resources a map of jar entry paths to entry byte contents
 * @param parent optional parent [ClassLoader]
 */
internal class MemoryClassLoader(private val resources: Map<String, ByteArray>, parent: ClassLoader?) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*> {
        val resource = name.replace('.', '/') + ".class"

        return resources[resource]?.let { bytes ->
            val protectionDomain = ProtectionDomain(null, null)
            return defineClass(name, bytes, 0, bytes.size, protectionDomain)
        }
            ?: throw ClassNotFoundException(name)
    }

    override fun getResourceAsStream(name: String): InputStream? {
        return resources[name]?.inputStream()
    }
}