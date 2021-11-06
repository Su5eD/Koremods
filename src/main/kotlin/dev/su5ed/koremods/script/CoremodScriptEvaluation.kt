package dev.su5ed.koremods.script

import dev.su5ed.koremods.dsl.TransformerHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.withUpdatedClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

private val evalLogger: Logger = LogManager.getLogger("KoremodEvaluation")

fun evalTransformers(name: String, source: SourceCode, log: Logger, classpath: Collection<File>): TransformerHandler {
    when (val eval = evalScript(source, log, classpath)) {
        is ResultWithDiagnostics.Success -> {
            when (val result = eval.value.returnValue) {
                is ResultValue.Value-> throw IllegalStateException("Script $name returned a value instead of Unit")
                is ResultValue.Unit -> return result.scriptInstance
                    .cast<CoremodKtsScript>()
                    .transformerHandler
                is ResultValue.Error -> throw RuntimeException("Exception in script $name", result.error)
            }
        }
        is ResultWithDiagnostics.Failure -> {
            eval.reports.forEach { report -> evalLogger.log(report.severity.toLogLevel(), report.message) }
            throw ScriptEvaluationException("Failed to evaluate script $name. See the log for more information")
        }
    }
    
    throw ScriptEvaluationException("An unknown error has occured while evaluating script $name")
}

class ScriptEvaluationException(msg: String) : RuntimeException(msg)

fun evalScript(source: SourceCode, logger: Logger, classpath: Collection<File> = emptyList()): ResultWithDiagnostics<EvaluationResult> {
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<CoremodKtsScript>()
        .withUpdatedClasspath(classpath)
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
