@file:Suppress("unused")

package dev.su5ed.koremods.script

import dev.su5ed.koremods.dsl.Transformer
import dev.su5ed.koremods.dsl.TransformerBuilder
import dev.su5ed.koremods.dsl.TransformerHandler
import org.apache.logging.log4j.Logger
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

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

fun evalTransformers(source: SourceCode, logger: Logger): List<Transformer>? {
    return evalScript(source, logger).valueOrNull()
        ?.returnValue
        ?.scriptInstance
        ?.cast<CoremodKtsScript>()
        ?.getTransformers()
}

fun evalScript(source: SourceCode, logger: Logger): ResultWithDiagnostics<EvaluationResult> {
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<CoremodKtsScript>()
    val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<CoremodKtsScript> {
        constructorArgs(logger)
    }
    
    return BasicJvmScriptingHost().eval(source, compilationConfiguration, evaluationConfiguration)
}
