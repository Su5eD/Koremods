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
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.script.KoremodsKtsScript
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.extension
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.impl.internalScriptingRunSuspend
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

private val LOGGER: Logger = KoremodsBlackboard.createLogger("ScriptCompilation")

internal fun compileScriptPacks(packs: Collection<RawScriptPack<SourceCode>>, libraries: Array<out String> = emptyArray()): List<RawScriptPack<CompiledScript>> {
    LOGGER.info("Compiling script packs")

    return packs.map { pack ->
        val compiledScripts = pack.scripts.map compile@{ script ->
            val compiled = compileScriptResult(script, libraries)
            return@compile RawScript(script.identifier, compiled)
        }
        return@map RawScriptPack(pack.namespace, pack.path, compiledScripts)
    }
}

fun compileScriptResult(script: RawScript<SourceCode>, libraries: Array<out String>): CompiledScript { // TODO RawScript instance method?
    return compileScriptResult(script.identifier, script.source) {
        jvm {
            dependenciesFromCurrentContext(libraries = libraries)
        }
    }
}

fun compileScriptResult(identifier: Identifier, source: SourceCode, configBuilder: ScriptCompilationConfiguration.Builder.() -> Unit): CompiledScript {
    val result = LOGGER.measureMillis(Level.DEBUG, "Compiling script $identifier") {
        compileScript(identifier, source, configBuilder)
    }
    return when (result) {
        is ResultWithDiagnostics.Success -> result.value
        is ResultWithDiagnostics.Failure -> {
            result.printErrors()
            throw ScriptEvaluationException("Failed to compile script $identifier. See the log for more information")
        }
    }
}

@Suppress("DEPRECATION_ERROR")
fun compileScript(identifier: Identifier, source: SourceCode, configBuilder: ScriptCompilationConfiguration.Builder.() -> Unit): ResultWithDiagnostics<CompiledScript> {
    LOGGER.info("Compiling script $identifier")

    val compiler = JvmScriptCompiler()
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<KoremodsKtsScript>(body = configBuilder)

    return internalScriptingRunSuspend { compiler.invoke(source, compilationConfiguration) }
}

internal fun readScriptSources(packs: Collection<RawScriptPack<Path>>): List<RawScriptPack<SourceCode>> {
    LOGGER.info("Reading script sources from packs")

    return packs.map { pack ->
        val sourceScripts = pack.scripts.map readScripts@{ script ->
            val source = readScriptSource(script.identifier, script.source)
            return@readScripts RawScript(script.identifier, source)
        }
        return@map RawScriptPack(pack.namespace, pack.path, sourceScripts)
    }
        .toList()
}

fun readScriptSource(identifier: Identifier, path: Path): SourceCode { // TODO Path source code
    LOGGER.debug("Reading source for script $identifier")
    
    val text = path.bufferedReader().readText()
    return if (text.isNotEmpty()) text.toScriptSource()
    else throw RuntimeException("Script $identifier could not be read")
}

internal fun readCompiledScripts(packs: Collection<RawScriptPack<Path>>): List<RawScriptPack<CompiledScript>> {
    LOGGER.info("Reading compiled scripts")

    return packs.map { pack ->
        val compiledScripts = pack.scripts.map readCompiled@{ script ->
            if (script.source.extension == "jar") {
                val compiled = script.source.loadScriptFromJar()
                return@readCompiled RawScript(script.identifier, compiled)
            } else {
                LOGGER.error("Script ${script.identifier} has invalid extension ${script.source.extension}")
                throw IllegalArgumentException("Invalid script extension '${script.source.extension}'")
            }
        }
        return@map RawScriptPack(pack.namespace, pack.path, compiledScripts)
    }
        .toList()
}

fun ResultWithDiagnostics.Failure.printErrors() {
    reports.forEach { report ->
        report.exception
            ?.let { LOGGER.catching(Level.ERROR, it) }
            ?: LOGGER.log(report.severity.toLogLevel(), report.message)
    }
}

fun ScriptDiagnostic.Severity.toLogLevel(): Level {
    return when (this) {
        ScriptDiagnostic.Severity.FATAL -> Level.FATAL
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
    }
}