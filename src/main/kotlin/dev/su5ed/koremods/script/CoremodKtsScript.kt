@file:Suppress("unused")

package dev.su5ed.koremods.script

import dev.su5ed.koremods.dsl.TransformerBuilder
import dev.su5ed.koremods.dsl.TransformerHandler
import org.apache.logging.log4j.Logger
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "core.kts",
    compilationConfiguration = CoremodScriptCompilationConfiguration::class,
    evaluationConfiguration = CoremodScriptEvaluationConfiguration::class
)
abstract class CoremodKtsScript(val logger: Logger) {
    val transformerHandler = TransformerHandler()
    
    fun transformers(configuration: TransformerBuilder.() -> Unit) {
        transformerHandler.transformers(configuration)
    }
}
