@file:Suppress("unused")

package dev.su5ed.koremods.script

import dev.su5ed.koremods.dsl.TransformerBuilder
import dev.su5ed.koremods.dsl.TransformerHandler
import org.apache.logging.log4j.Logger
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import kotlin.script.experimental.annotations.KotlinScript

@Suppress("UNUSED_PARAMETER")
@KotlinScript(
    fileExtension = "core.kts",
    compilationConfiguration = CoremodScriptCompilationConfiguration::class,
    evaluationConfiguration = CoremodScriptEvaluationConfiguration::class
)
abstract class CoremodKtsScript(val logger: Logger) {
    private val transformerHandler = TransformerHandler()
    
    fun transformers(configuration: TransformerBuilder.() -> Unit) {
        transformerHandler.transformers(configuration)
    }
    
    fun getTransformers() = transformerHandler.getTransformers()
}

fun getKoremodEngine(logger: Logger): ScriptEngine {
    val engine = ScriptEngineManager().getEngineByExtension("core.kts")
        ?: throw RuntimeException("Could not initialize engine")
    
    engine.put("logger", logger)
    
    return engine
}
