package dev.su5ed.koremods

import com.google.common.base.Stopwatch
import dev.su5ed.koremods.dsl.Transformer
import dev.su5ed.koremods.script.evalScript
import dev.su5ed.koremods.script.evalTransformers
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

data class KoremodScript(val name: String, val transformers: List<Transformer>)

object KoremodDiscoverer {
    lateinit var transformers: Map<String, List<KoremodScript>>
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
        val modScripts = mutableMapOf<String, Map<String, String>>()
        
        scanPaths(paths, modScripts)
        
        val sum = modScripts.values.sumOf(Map<*, *>::size)
        transformers = if (sum > 0) evalScripts(modScripts, sum) else emptyMap()
    }
    
    private fun scanPaths(paths: Iterable<Path>, modScripts: MutableMap<String, Map<String, String>>) {
        paths.forEach { path ->
            val file = path.toFile()
            if (file.isDirectory) {
                val conf = path.resolve("META-INF/koremods.conf").toFile()
                if (conf.exists()) readConfig(conf.inputStream(), modScripts) {
                    val scriptFile = File(file, it)
                    return@readConfig if (scriptFile.exists()) scriptFile.inputStream()
                    else null
                }
            }
            else if (path.extension == "jar" || path.extension == "zip") {
                val zip = ZipFile(file)
                zip.getEntry("META-INF/koremods.conf")?.let { entry ->
                    val istream = zip.getInputStream(entry)
                    readConfig(istream, modScripts) {
                        zip.getEntry(it)?.let(zip::getInputStream)
                    }
                }
            }
        }
    }
    
    private fun readConfig(istream: InputStream, modScripts: MutableMap<String, Map<String, String>>, locator: (String) -> InputStream?) {
        val reader = istream.bufferedReader()
                            
        val config = parseConfig(reader)
        reader.close()
        logger.info("Loading scripts for mod ${config.modid}")
                            
        val scripts = locateScripts(locator, config.scripts)
        modScripts[config.modid] = scripts
    }

    private fun locateScripts(locator: (String) -> InputStream?, scripts: Map<String, String>): Map<String, String> {
        return scripts
            .mapValues { (name, path) ->
                logger.debug("Reading script $name")
                
                locator(path)?.let { ins ->
                    val lines = ins.bufferedReader().readLines()
                    if (lines.isEmpty()) logger.error("Script $name not found")
                    return@mapValues lines.joinToString(separator = "\n")
                }
                
                logger.error("Could not read script $name")
                return@mapValues ""
            }
            .filterValues(String::isNotEmpty)
    }

    private fun evalScripts(scripts: Map<String, Map<String, String>>, threads: Int): Map<String, List<KoremodScript>> {
        val executors = Executors.newFixedThreadPool(threads)

        val futures: Map<String, Map<String, Future<List<Transformer>>>> = scripts.mapValues { (modid, scripts) ->
            if (scripts.isEmpty()) logger.error("Mod $modid provides a koremods config file without defining any scripts")
            
            scripts.mapValues { (name, source) ->
                executors.submit(Callable { evalScript(modid, name, source) })
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
    private fun evalScript(modid: String, name: String, source: String): List<Transformer> {
        logger.debug("Evaluating script $name")
        val stopwatch = Stopwatch.createStarted()
        
        val engineLogger = LogManager.getLogger("Koremods.$modid/$name")
        val transformers = evalTransformers(source.toScriptSource(), engineLogger)!!
        if (transformers.isEmpty()) logger.error("Script $name does not define any transformers")

        stopwatch.stop()
        val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        logger.debug("Script $name evaluated in $time ms")

        return transformers
    }
    
    fun isInitialized(): Boolean = ::transformers.isInitialized
    
    fun getFlatTransformers(): List<Transformer> {
        return transformers
            .flatMap(Map.Entry<String, List<KoremodScript>>::value)
            .flatMap(KoremodScript::transformers)
    }
}

fun preloadScriptEngine(logger: Logger) {
    // TODO PRELOAD marker
    logger.info("Preloading KTS scripting engine")
    val stopwatch = Stopwatch.createStarted()
    
    evalScript("transformers {}".toScriptSource(), logger)
                
    stopwatch.stop()
    val time = stopwatch.elapsed(TimeUnit.MILLISECONDS)
    logger.debug("Script engine preloaded in $time ms")
}
