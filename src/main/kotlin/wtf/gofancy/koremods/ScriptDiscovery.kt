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
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.streams.toList

data class Identifier internal constructor(val namespace: String, val name: String) {
    override fun toString(): String = "$namespace:$name"
}

private data class RawScriptPack<T>(val namespace: String, val path: Path, val scripts: List<RawScript<T>>)
private data class RawScript<T>(val identifier: Identifier, val source: T)

data class KoremodScriptPack(val namespace: String, val path: Path, val scripts: List<KoremodScript>)
data class KoremodScript(val identifier: Identifier, val handler: TransformerHandler)

private val LOGGER: Logger = KoremodsBlackboard.createLogger("Discoverer")
private val SCRIPT_SCAN: Marker = MarkerManager.getMarker("SCRIPT_SCAN")

class KoremodsDiscoverer(private vararg val libraries: String) {
    companion object {
        var INSTANCE: KoremodsDiscoverer? = null
    }
    
    private val scriptNameRegex = "^[a-zA-Z0-9]*\$".toRegex()
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
        val rawScriptPacks = scanPaths(paths)
        if (rawScriptPacks.isNotEmpty()) {
            val eval = evalScripts(rawScriptPacks)
            scriptPacks = ImmutableList.copyOf(eval)
        }
    }
    
    private fun scanPaths(paths: Iterable<Path>): List<RawScriptPack<String>> {
        LOGGER.debug("Scanning classpath for Koremod modules")

        return paths.mapNotNull { path ->
            if (path.isDirectory()) {
                LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.relativeTo(path.parent.parent.parent)}")
                
                val conf = path.resolve(KoremodsBlackboard.CONFIG_FILE_LOCATION).toFile()
                if (conf.exists()) {
                    return@mapNotNull readConfig(path, conf.inputStream()) {
                        val scriptPath = path / it
                        if (scriptPath.exists()) scriptPath.inputStream()
                        else null
                    }
                }
            } else if (path.extension == "jar" || path.extension == "zip") {
                LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.name}")
                
                val zip = ZipFile(path.toFile())
                zip.getEntry(KoremodsBlackboard.CONFIG_FILE_LOCATION)?.let { entry ->
                    val inputStream = zip.getInputStream(entry)
                    return@mapNotNull readConfig(path, inputStream) {
                        zip.getEntry(it)?.let(zip::getInputStream)
                    }
                }
            }
            
            return@mapNotNull null
        }
    }
    
    private fun readConfig(parent: Path, istream: InputStream, locator: (String) -> InputStream?): RawScriptPack<String>? {
        val reader = istream.bufferedReader()
        
        val config: KoremodModConfig = parseConfig(reader)
        reader.close()
        LOGGER.info("Loading scripts for module ${config.namespace}")
        
        if (config.scripts.isEmpty()) {
            LOGGER.error("Module ${config.namespace} defines a koremod without any scripts")
            return null
        }
        
        val scripts = locateScripts(config.namespace, locator, config.scripts)
        
        return if (scripts.isEmpty()) null
        else RawScriptPack(config.namespace, parent, scripts)
    }

    private fun locateScripts(namespace: String, locator: (String) -> InputStream?, paths: List<String>): List<RawScript<String>> {
        return paths
            .mapNotNull { path ->
                val nameWithExt = path.substringAfterLast('/')
                val index = nameWithExt.indexOf(".core.kts")
                if (index == -1) {
                    LOGGER.error("Script $nameWithExt has an invalid extension, expected 'core.kts'")
                    return@mapNotNull null
                }
                
                val name = nameWithExt.substring(0, index)
                if (!name.matches(scriptNameRegex)) {
                    LOGGER.error("Script name '$name' does not match the regex $scriptNameRegex")
                    return@mapNotNull null
                }
                val identifier = Identifier(namespace, name)
                
                LOGGER.debug("Reading script $identifier")
                locator(path)?.let { ins ->
                    val lines = ins.bufferedReader().readLines()
                    if (lines.isEmpty()) LOGGER.error("Script $identifier could not be read")
                    return@mapNotNull RawScript(identifier, lines.joinToString(separator = "\n"))
                }
                
                LOGGER.error("Could not read script $name")
                return@mapNotNull null
            }
    }

    private fun evalScripts(sourcePacks: List<RawScriptPack<String>>): List<KoremodScriptPack> {
        val threads = sourcePacks.sumOf { it.scripts.size }
        val threadFactory = Executors.defaultThreadFactory()
        val executors = Executors.newFixedThreadPool(threads) { runnable ->
            threadFactory.newThread(runnable).apply { 
                if (KoremodsBlackboard.scriptContextClassLoader != null) {
                    contextClassLoader = KoremodsBlackboard.scriptContextClassLoader
                }
            }
        }

        val futurePacks: List<RawScriptPack<Future<TransformerHandler>>> = sourcePacks.map { pack ->
            val futureScripts = pack.scripts.map { script ->
                val future = executors.submit(Callable {
                    evalScript(script.identifier, script.source)
                })
                
                RawScript(script.identifier, future)
            }
            
            RawScriptPack(pack.namespace, pack.path, futureScripts)
        }
        
        executors.shutdown()
        executors.awaitTermination(30, TimeUnit.SECONDS)
        
        return futurePacks.map {
            val processed = it.scripts.map { (identifier, future) -> 
                KoremodScript(identifier, future.get())
            }
            
            return@map KoremodScriptPack(it.namespace, it.path, processed)
        }
    }

    private fun evalScript(identifier: Identifier, source: String): TransformerHandler {
        LOGGER.debug("Evaluating script $identifier")
        
        val handler = LOGGER.measureTime(Level.DEBUG, "Evaluating script $identifier") {
            val engineLogger = KoremodsBlackboard.createLogger("${identifier.namespace}/${identifier.name}")
            evalTransformers(identifier, source.toScriptSource(), engineLogger, libraries)
        }
        if (handler.getTransformers().isEmpty()) LOGGER.error("Script $identifier does not define any transformers")

        return handler
    }
    
    fun getFlatTransformers(): List<Transformer<*>> {
        return scriptPacks
            .flatMap(KoremodScriptPack::scripts)
            .flatMap { it.handler.getTransformers() }
    }
}

internal fun <T> Logger.measureTime(level: Level, message: String, block: () -> T): T {
    val stopwatch = Stopwatch.createStarted()
    val result = block()
    stopwatch.stop()
    
    val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
    log(level, "$message took $time ms")
    
    return result
}
