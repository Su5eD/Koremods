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

package wtf.gofancy.koremods

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
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

private val LOGGER: Logger = KoremodsBlackboard.createLogger("ScriptCompilation")

class ScriptEvaluationException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

internal fun evalScriptPacks(compiledPacks: Collection<RawScriptPack<CompiledScript>>): List<KoremodsScriptPack> {
    return compiledPacks.map { pack ->
        val processed = pack.scripts.map { script ->
            val transformers = evalTransformers(script.identifier, script.source)
            KoremodsScript(script.identifier, transformers)
        }
        return@map KoremodsScriptPack(pack.namespace, pack.path, processed)
    }
}

private fun evalTransformers(identifier: Identifier, script: CompiledScript): TransformerHandler {
    val handler = LOGGER.measureMillis(Level.DEBUG, "Evaluating script $identifier") {
        val engineLogger = KoremodsBlackboard.createLogger("${identifier.namespace}/${identifier.name}")
        evalTransformers(identifier, script, engineLogger)
    }
    return if (handler.getTransformers().isNotEmpty()) handler 
    else throw RuntimeException("Script $identifier does not define any transformers")
}

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


@Suppress("DEPRECATION_ERROR")
fun evalScript(identifier: Identifier, script: CompiledScript, logger: Logger): ResultWithDiagnostics<EvaluationResult> {
    LOGGER.info("Evaluating script $identifier")

    val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<KoremodsKtsScript> {
        constructorArgs(identifier, logger)
    }

    return internalScriptingRunSuspend { BasicJvmScriptEvaluator().invoke(script, evaluationConfiguration) }
}

fun Path.loadScriptFromJar(): CompiledScript {
    val (className, entries) = inputStream().use { istream ->
        JarInputStream(istream).use jistream@{
            val className = it.manifest.mainAttributes.getValue("Main-Class")
                ?: throw IllegalArgumentException("No Main-Class manifest attribute")
            return@jistream Pair(className, it.readEntries())
        }
    }
    return KJvmCompiledScriptLoadedFromJar(className, entries)
}

fun JarInputStream.readEntries(): Map<String, ByteArray> {
    return generateSequence(::getNextJarEntry)
        .associate { Pair(it.name, readAllBytes()) }
}

internal class KJvmCompiledScriptLoadedFromJar(private val scriptClassFQName: String, private val entries: Map<String, ByteArray>) : CompiledScript {
    private var loadedScript: KJvmCompiledScript? = null

    private fun getScriptOrFail(): KJvmCompiledScript = loadedScript ?: throw RuntimeException("Compiled script is not loaded yet")

    override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<KClass<*>> {
        if (loadedScript == null) {
            val actualEvaluationConfiguration = scriptEvaluationConfiguration ?: ScriptEvaluationConfiguration()
            val baseClassLoader = actualEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]
                ?: Thread.currentThread().contextClassLoader
            val classLoader = MemoryClassLoader(entries, baseClassLoader)
            loadedScript = createScriptFromClassLoader(scriptClassFQName, classLoader)
        }
        return getScriptOrFail().getClass(scriptEvaluationConfiguration)
    }

    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = getScriptOrFail().compilationConfiguration

    override val sourceLocationId: String?
        get() = getScriptOrFail().sourceLocationId

    override val otherScripts: List<CompiledScript>
        get() = getScriptOrFail().otherScripts

    override val resultField: Pair<String, KotlinType>?
        get() = getScriptOrFail().resultField
}

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