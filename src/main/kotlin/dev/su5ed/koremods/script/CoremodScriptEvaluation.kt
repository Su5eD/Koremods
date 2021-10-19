package dev.su5ed.koremods.script

import dev.su5ed.koremods.dsl.TransformerHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

private val evalLogger: Logger = LogManager.getLogger("KoremodEvaluation")

fun evalTransformers(name: String, source: SourceCode, log: Logger): TransformerHandler? {
    when (val eval = evalScript(source, log)) {
        is ResultWithDiagnostics.Success -> {
            when (val result = eval.value.returnValue) {
                is ResultValue.Value-> throw IllegalStateException("Script $name returned a value instead of Unit")
                is ResultValue.Unit -> return result.scriptInstance
                    .cast<CoremodKtsScript>()
                    .transformerHandler
                is ResultValue.Error -> evalLogger.error("Evaluation of script $name resulted in an exception", result.error)
            }
        }
        is ResultWithDiagnostics.Failure -> {
            evalLogger.error("Failed to evaluate script $name")
            eval.reports.forEach { report -> evalLogger.log(report.severity.toLogLevel(), report.message) }
        }
    }
    
    return null
}

fun evalScript(source: SourceCode, logger: Logger): ResultWithDiagnostics<EvaluationResult> {
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<CoremodKtsScript>()
    val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<CoremodKtsScript> {
        constructorArgs(logger)
    }
    
    return BasicJvmScriptingHost().eval(source, compilationConfiguration, evaluationConfiguration)
}

private fun ScriptDiagnostic.Severity.toLogLevel(): Level {
    return when(this) {
        ScriptDiagnostic.Severity.FATAL -> Level.FATAL
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
    }
}
