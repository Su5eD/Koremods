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
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.impl.internalScriptingRunSuspend
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

private val LOGGER: Logger = KoremodsBlackboard.createLogger("ScriptCompilation")

internal fun compileScriptPacks(packs: Collection<RawScriptPack<String>>, libraries: Array<out String> = emptyArray()): List<RawScriptPack<CompiledScript>> {
    LOGGER.info("Compiling script packs")

    return packs.map { pack ->
        val compiledScripts = pack.scripts.map compile@{ script ->
            val compiled = compileScriptResult(script.identifier, script.source.toScriptSource(), libraries)
            return@compile RawScript(script.identifier, compiled)
        }
        return@map RawScriptPack(pack.namespace, pack.path, compiledScripts)
    }
}

fun compileScriptResult(identifier: Identifier, source: SourceCode, libraries: Array<out String>): CompiledScript {
    val result = LOGGER.measureMillis(Level.DEBUG, "Compiling script $identifier") {
        compileScript(identifier, source, libraries)
    }
    return when(result) {
        is ResultWithDiagnostics.Success -> result.value
        is ResultWithDiagnostics.Failure -> {
            result.printErrors()
            throw ScriptEvaluationException("Failed to compile script $identifier. See the log for more information")
        }
    }
}

@Suppress("DEPRECATION_ERROR")
fun compileScript(identifier: Identifier, source: SourceCode, libraries: Array<out String>): ResultWithDiagnostics<CompiledScript> {
    LOGGER.info("Compiling script $identifier")

    val compiler = JvmScriptCompiler()
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<KoremodsKtsScript> {
        jvm {
            dependenciesFromCurrentContext(libraries = libraries)
        }
    }

    return internalScriptingRunSuspend { compiler.invoke(source, compilationConfiguration) }
}

internal fun readScriptSources(packs: Collection<RawScriptPack<Path>>): List<RawScriptPack<String>> {
    return packs.map { pack ->
        val sourceScripts = pack.scripts.map readScripts@{ script ->
            val source = readScriptSource(script.identifier, script.source)
            return@readScripts RawScript(script.identifier, source)
        }
        return@map RawScriptPack(pack.namespace, pack.path, sourceScripts)
    }
        .toList()
}

internal fun readScriptSource(identifier: Identifier, path: Path): String {
    val lines = path.bufferedReader().readLines()
    return if (lines.isNotEmpty()) lines.joinToString(separator = "\n")
    else throw RuntimeException("Script $identifier could not be read")
}

internal fun ResultWithDiagnostics.Failure.printErrors() {
    reports.forEach { report ->
        report.exception
            ?.let { LOGGER.catching(Level.ERROR, it) }
            ?: LOGGER.log(report.severity.toLogLevel(), report.message)
    }
}

internal fun ScriptDiagnostic.Severity.toLogLevel(): Level {
    return when(this) {
        ScriptDiagnostic.Severity.FATAL -> Level.FATAL
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
    }
}