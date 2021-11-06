package dev.su5ed.koremods

import com.google.common.base.Stopwatch
import dev.su5ed.koremods.dsl.Transformer
import dev.su5ed.koremods.dsl.TransformerHandler
import dev.su5ed.koremods.script.evalTransformers
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
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
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.script.experimental.host.toScriptSource
import kotlin.streams.toList

private data class SourceScriptPack(val modid: String, val file: File, val scripts: List<RawScript<String>>)
private data class FutureScriptPack(val modid: String, val scripts: List<RawScript<Future<TransformerHandler>>>)
private data class RawScript<T>(val name: String, val source: T)

data class KoremodScriptPack(val modid: String, val scripts: List<KoremodScript>)
data class KoremodScript(val name: String, val handler: TransformerHandler)

object KoremodDiscoverer {
    lateinit var transformers: List<KoremodScriptPack>
    private val logger = LogManager.getLogger("KoremodDiscoverer")
    
    fun discoverKoremods(dir: Path, classpath: Array<URL>) {
        val paths = Files.walk(dir, 1)
            .filter { it.name != dir.name }
            .toList()
        val classPaths = classpath
            .map(URL::toURI)
            .filter { it.scheme == "file" }
            .map(Paths::get)
        
        discoverKoremods(paths + classPaths)
    }
    
    fun discoverKoremods(paths: Collection<Path>) {
        val modScripts = scanPaths(paths)
        
        val sum = modScripts.sumOf { it.scripts.size }
        transformers = if (sum > 0) evalScripts(modScripts, sum) else emptyList()
    }
    
    private fun scanPaths(paths: Iterable<Path>): List<SourceScriptPack> {
        val scriptPacks: MutableList<SourceScriptPack> = mutableListOf()
        paths.forEach { path ->
            val file = path.toFile()
            if (file.isDirectory) {
                val conf = path.resolve("META-INF/koremods.conf").toFile()
                if (conf.exists()) {
                    scriptPacks.add(readConfig(file, conf.inputStream()) {
                        val scriptFile = File(file, it)
                        if (scriptFile.exists()) scriptFile.inputStream()
                        else null
                    })
                }
            }
            else if (path.extension == "jar" || path.extension == "zip") {
                val zip = ZipFile(file)
                zip.getEntry("META-INF/koremods.conf")?.let { entry ->
                    val istream = zip.getInputStream(entry)
                    scriptPacks.add(readConfig(file, istream) {
                        zip.getEntry(it)?.let(zip::getInputStream)
                    })
                }
            }
        }
        return scriptPacks
    }
    
    private fun readConfig(parent: File, istream: InputStream, locator: (String) -> InputStream?): SourceScriptPack {
        val reader = istream.bufferedReader()
        
        val config: KoremodModConfig = parseConfig(reader)
        reader.close()
        logger.info("Loading scripts for mod ${config.modid}")
                            
        val scripts = locateScripts(locator, config.scripts)
        
        return SourceScriptPack(config.modid, parent, scripts)
    }

    private fun locateScripts(locator: (String) -> InputStream?, scripts: Map<String, String>): List<RawScript<String>> {
        return scripts
            .mapNotNull { (name, path) ->
                logger.debug("Reading script $name")
                
                locator(path)?.let { ins ->
                    val lines = ins.bufferedReader().readLines()
                    if (lines.isEmpty()) logger.error("Script $name not found")
                    return@mapNotNull RawScript(name, lines.joinToString(separator = "\n"))
                }
                
                logger.error("Could not read script $name")
                return@mapNotNull null
            }
    }

    private fun evalScripts(sourcePacks: List<SourceScriptPack>, threads: Int): List<KoremodScriptPack> {
        val executors = Executors.newFixedThreadPool(threads)

        val futurePacks: List<FutureScriptPack> = sourcePacks.map { pack ->
            if (pack.scripts.isEmpty()) logger.error("Mod ${pack.file.name} defines a koremods config without any scripts")
            
            val futureScripts = pack.scripts.map { src ->
                val future = executors.submit(Callable { evalScript(pack.modid, pack.file, src.name, src.source) })
                RawScript(src.name, future)
            }
            
            FutureScriptPack(pack.modid, futureScripts)
        }
        
        logger.measureTime(Level.DEBUG, "Evaluating all scripts") {
            executors.shutdown()
            executors.awaitTermination(15, TimeUnit.SECONDS)
        }
        
        return futurePacks.map {
            val processed = it.scripts.map { (name, future) -> 
                KoremodScript(name, future.get())
            }
            KoremodScriptPack(it.modid, processed)
        }
    }

    private fun evalScript(modid: String, file: File, name: String, source: String): TransformerHandler {
        logger.debug("Evaluating script $name")
        
        val handler = logger.measureTime(Level.DEBUG, "Evaluating script $name") {
            val engineLogger = LogManager.getLogger("Koremods.$modid/$name")
            evalTransformers(name, source.toScriptSource(), engineLogger, listOf(file))
        }
        if (handler.getTransformers().isEmpty()) logger.error("Script $name does not define any transformers")

        return handler
    }
    
    fun isInitialized(): Boolean = ::transformers.isInitialized
    
    fun getFlatTransformers(): List<Transformer> {
        return transformers
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
