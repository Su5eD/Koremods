package dev.su5ed.koremods

import com.google.common.base.Stopwatch
import dev.su5ed.koremods.dsl.Transformer
import dev.su5ed.koremods.script.getKoremodEngine
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.script.Invocable
import kotlin.io.path.extension

data class KoremodScript(val name: String, val transformers: List<Transformer>)

object KoremodDiscoverer {
    internal lateinit var transformers: Map<String, List<KoremodScript>>
    private val logger = LogManager.getLogger("KoremodDiscoverer")
    
    fun discoverKoremods(modsDir: Path) {
        val modScripts = mutableMapOf<String, Map<String, String>>()
        Files.walk(modsDir, 1)
            .filter { it.extension == "jar" || it.extension == "zip" }
            .map { path -> ZipFile(path.toFile()) }
            .forEach { zip ->
                zip.getEntry("META-INF/koremods.conf")?.let { entry ->
                    val istream = zip.getInputStream(entry)
                    val reader = istream.bufferedReader()
        
                    val config = parseConfig(reader)
                    reader.close()
                    logger.info("Loading scripts for mod ${config.modid}")
        
                    val scripts = locateScripts(zip, config.scripts)
                    modScripts[config.modid] = scripts
                }
            }
                
        if (modScripts.isNotEmpty()) transformers = evalScripts(modScripts)
    }

    private fun locateScripts(zip: ZipFile, scripts: Map<String, String>): Map<String, String> {
        return scripts
            .mapValues { (name, path) ->
                zip.getEntry(path)?.let { script ->
                    logger.debug("Reading script $name")

                    val ins = zip.getInputStream(script)
                    val lines = ins.bufferedReader().readLines()
                    return@mapValues lines.joinToString(separator = "\n")
                } ?: run {
                    logger.error("Could not read script $name")
                    return@mapValues ""
                }
            }
            .filterValues(String::isNotEmpty)
    }

    private fun evalScripts(scripts: Map<String, Map<String, String>>): Map<String, List<KoremodScript>> {
        val executors = Executors.newFixedThreadPool(scripts.values.sumOf(Map<*, *>::size))

        val futures: Map<String, Map<String, Future<List<Transformer>>>> = scripts.mapValues { (_, scripts) ->
            scripts.mapValues { (name, source) ->
                executors.submit(Callable { evalScript(name, source) })
            }
        }

        val stopwatch = Stopwatch.createStarted()

        executors.shutdown()
        executors.awaitTermination(10, TimeUnit.SECONDS)

        stopwatch.stop()
        val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        logger.debug("All scripts evaluated in $time ms")
        
        return futures.mapValues { (_, scripts) ->
            scripts.map { (name, future) -> 
                KoremodScript(name, future.get())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun evalScript(name: String, source: String): List<Transformer> {
        logger.debug("Evaluating script $name")
        val stopwatch = Stopwatch.createStarted()

        val engine = getKoremodEngine(logger) // TODO Logger
        engine.eval(source)

        val transformers = (engine as Invocable).invokeFunction("getTransformers") as List<Transformer>
        if (transformers.isEmpty()) logger.warn("Script $name does not define any transformers")

        stopwatch.stop()
        val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        logger.debug("Script $name evaluated in $time ms")

        return transformers
    }
    
    internal fun isInitialized(): Boolean = ::transformers.isInitialized
}

fun initScriptEngine(logger: Logger) {
    logger.info("Preloading KTS scripting engine")
    val stopwatch = Stopwatch.createStarted()
    
    val engine = getKoremodEngine(logger)
    engine.eval("transformers {}")
                
    stopwatch.stop()
    val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
    logger.debug("Script engine initialized in $time ms")
}
