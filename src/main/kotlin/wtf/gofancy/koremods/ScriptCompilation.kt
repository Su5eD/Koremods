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
import wtf.gofancy.koremods.script.KoremodsKtsScript
import java.io.File
import java.io.Serializable
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.impl.internalScriptingRunSuspend
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

private val LOGGER: Logger = createLogger("ScriptCompilation")

/**
 * Read the Source Code contents of pack scripts.
 *
 * @param packs the Script Packs to read the contents of
 * @return a list of script packs containing their scripts' [SourceCode]
 */
fun readScriptSources(packs: Collection<RawScriptPack<Path>>): List<RawScriptPack<SourceCode>> {
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

/**
 * Read the contents of a script from a [Path] and return its [SourceCode].
 *
 * @param identifier the script's name
 * @param path path to the script's source code file
 * @return [SourceCode] representation of the script file's contents
 */
fun readScriptSource(identifier: Identifier, path: Path): SourceCode {
    LOGGER.debug("Reading source for script $identifier")
    return if (path.fileSystem == FileSystems.getDefault()) FileScriptSource(File(path.pathString))
    else PathScriptSource(path)
}

/**
 * Read the bytecode contents of compiled scripts from script packs.
 *
 * @param packs the Script Packs to read the contents of
 */
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

/**
 * Process and compile scripts from each script pack,
 * returning a new list of packs with the compiled results.
 *
 * @param packs script packs to compile
 * @param libraries file names of additional libraries to use during compilation
 * @return a list of script packs with the resulting compile contents
 */
internal fun compileScriptPacks(packs: Collection<RawScriptPack<SourceCode>>, libraries: Array<out String> = emptyArray()): List<RawScriptPack<CompiledScript>> {
    LOGGER.info("Compiling script packs")

    return packs.map { pack ->
        val compiledScripts = pack.scripts.map compile@{ script ->
            val compiled = script.compileScriptResult(libraries)
            return@compile RawScript(script.identifier, compiled)
        }
        return@map RawScriptPack(pack.namespace, pack.path, compiledScripts)
    }
}

/**
 * Compile a Source script with additional configuration and return the compilation result.
 *
 * @param libraries file names of additional libraries to use during compilation
 * @return the compiled script result
 */
fun RawScript<SourceCode>.compileScriptResult(libraries: Array<out String>): CompiledScript {
    return compileScriptResult(identifier, source) {
        jvm {
            dependenciesFromCurrentContext(libraries = libraries)
        }
    }
}

/**
 * Compile a source script and ensure the process was successful.
 *
 * @param identifier the script's name
 * @param source source code to compile
 * @param configBuilder additional configuration
 * @return the compilation result
 * @throws ScriptEvaluationException if compilation fails
 */
fun compileScriptResult(identifier: Identifier, source: SourceCode, configBuilder: ScriptCompilationConfiguration.Builder.() -> Unit): CompiledScript {
    val result = LOGGER.measureMillis(Level.DEBUG, "Compiling script $identifier") {
        compileScript(identifier, source, configBuilder)
    }
    return when (result) {
        is ResultWithDiagnostics.Success -> result.value
        is ResultWithDiagnostics.Failure -> {
            LOGGER.logResultErrors(result)
            throw ScriptEvaluationException("Failed to compile script $identifier. See the log for more information")
        }
    }
}

/**
 * Invoke the kotlin script compiler.
 *
 * @param identifier the script's name
 * @param source source code to compile
 * @param configBuilder additional configuration
 * @return compiler diagnostic result
 */
@Suppress("DEPRECATION_ERROR")
fun compileScript(identifier: Identifier, source: SourceCode, configBuilder: ScriptCompilationConfiguration.Builder.() -> Unit): ResultWithDiagnostics<CompiledScript> {
    LOGGER.info("Compiling script $identifier")
    val compiler = JvmScriptCompiler()
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<KoremodsKtsScript>(body = configBuilder)
    return internalScriptingRunSuspend { compiler.invoke(source, compilationConfiguration) }
}

/**
 * A Script Source that points to a java NIO [Path].
 *
 * @param path path to the script's source code file
 */
class PathScriptSource(private val path: Path) : ExternalSourceCode, Serializable {
    override val externalLocation: URL = path.toUri().toURL()

    override val text: String by lazy(path::readText)
    override val name: String = path.fileName.toString()
    override val locationId: String = path.toString()

    override fun equals(other: Any?): Boolean =
        this === other || (other as? PathScriptSource)?.let { path == it.path && text == it.text } == true

    override fun hashCode(): Int = path.absolute().hashCode() + text.hashCode() * 23
}
