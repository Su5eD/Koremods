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

import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.script.KOREMODS_SCRIPT_EXTENSION
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.script.experimental.api.CompiledScript
import kotlin.streams.toList

data class Identifier internal constructor(val namespace: String, val name: String) {
    override fun toString(): String = "$namespace:$name"
}

internal data class RawScriptPack<T>(val namespace: String, val path: Path, val scripts: List<RawScript<T>>)
internal data class RawScript<T>(val identifier: Identifier, val source: T)

data class KoremodScriptPack(val namespace: String, val path: Path, val scripts: List<KoremodScript>)
data class KoremodScript(val identifier: Identifier, val handler: TransformerHandler)

private val LOGGER: Logger = KoremodsBlackboard.createLogger("Discoverer")
private val SCRIPT_SCAN: Marker = MarkerManager.getMarker("SCRIPT_SCAN")
val SCRIPT_NAME_PATTERN = "^[a-zA-Z0-9]*\$".toRegex()

sealed class DiscoveryMode(internal val extension: String)
class CompileDiscovery(internal vararg val libraries: String) : DiscoveryMode(KOREMODS_SCRIPT_EXTENSION)
object EvalDiscovery : DiscoveryMode("jar")

class KoremodsDiscoverer(private val mode: DiscoveryMode) {
    var scriptPacks: List<KoremodScriptPack> = emptyList()
        private set

    fun discoverKoremods(dir: Path, additionalPaths: Array<URL>) {
        val paths = Files.walk(dir, 1)
            .filter { !it.isDirectory() && it.name != dir.name }
            .toList()
        val additional = additionalPaths
            .map(URL::toURI)
            .filter { it.scheme == "file" }
            .map(Paths::get)

        discoverKoremods(paths + additional)
    }

    fun discoverKoremods(paths: Iterable<Path>) {
        val located = scanPaths(paths)
        
        val compiled = if (mode is CompileDiscovery) {
            val sources = readScriptSources(located)
            compileScriptPacks(sources, mode.libraries)
        } else {
            readCompiledScripts(located)
        }
        
        val evaluated = evalScriptPacks(compiled)
        scriptPacks = ImmutableList.copyOf(evaluated)
    }

    private fun scanPaths(paths: Iterable<Path>): List<RawScriptPack<Path>> {
        LOGGER.debug("Scanning classpath for Koremods script packs")

        return paths.mapNotNull { path ->
            if (path.isDirectory()) {
                LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.relativeTo(path.parent.parent.parent)}")

                val conf = path.resolve(KoremodsBlackboard.CONFIG_FILE_LOCATION)
                if (conf.exists()) {
                    return@mapNotNull readConfig(path, conf, path)
                }
            } else if (path.extension == "jar" || path.extension == "zip") {
                LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.name}")
                
                val zipFs = FileSystems.newFileSystem(path, null)
                val conf = zipFs.getPath(KoremodsBlackboard.CONFIG_FILE_LOCATION)
                if (conf.exists()) {
                    return@mapNotNull readConfig(path, conf, zipFs.getPath(""))
                }
            }

            return@mapNotNull null
        }
    }

    private fun readConfig(parent: Path, configPath: Path, rootPath: Path): RawScriptPack<Path>? {
        val config: KoremodModConfig = configPath.bufferedReader().use(::parseConfig)
        LOGGER.info("Loading scripts for pack ${config.namespace}")

        if (config.scripts.isEmpty()) {
            LOGGER.error("Script pack ${config.namespace} defines a koremod configuration without any scripts")
            return null
        }

        val scripts = locateScripts(config.namespace, config.scripts, rootPath)
        
        return if (scripts.isNotEmpty()) RawScriptPack(config.namespace, parent, scripts) else null
    }

    private fun locateScripts(namespace: String, scripts: List<String>, rootPath: Path): List<RawScript<Path>> {
        return scripts
            .map { script ->
                val nameWithExt = script.substringAfterLast('/')
                val index = nameWithExt.indexOf(".$KOREMODS_SCRIPT_EXTENSION")
                if (index == -1) {
                    val extension = script.substringAfterLast('.')
                    LOGGER.error("Script $nameWithExt has an invalid extension '$extension', expected '.core.kts'")
                    throw IllegalArgumentException("Invalid script extension '$extension'")
                }

                val name = nameWithExt.substring(0, index)
                if (!name.matches(SCRIPT_NAME_PATTERN)) {
                    LOGGER.error("Script name '$name' does not match the pattern /$SCRIPT_NAME_PATTERN/")
                    throw IllegalArgumentException("Invalid script name '$name'")
                }
                
                val adjustedPath = script.replace(KOREMODS_SCRIPT_EXTENSION, mode.extension)
                val identifier = Identifier(namespace, name)
                LOGGER.debug("Reading script $identifier")
                val scriptPath = rootPath.resolve(adjustedPath)
                if (scriptPath.notExists()) {
                    throw IllegalArgumentException("Script $identifier file $adjustedPath not found")
                }
                
                return@map RawScript(identifier, scriptPath)
            }
    }
    
    private fun readCompiledScripts(packs: Collection<RawScriptPack<Path>>): List<RawScriptPack<CompiledScript>> {
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

    fun getAllTransformers(): List<Transformer<*>> {
        return scriptPacks
            .flatMap(KoremodScriptPack::scripts)
            .flatMap { it.handler.getTransformers() }
    }
}

internal fun <T> Logger.measureMillis(level: Level, message: String, block: () -> T): T {
    val stopwatch = Stopwatch.createStarted()
    val result = block()
    val time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
    log(level, "$message took $time ms")
    return result
}
