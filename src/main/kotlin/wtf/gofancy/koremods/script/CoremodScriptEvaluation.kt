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

package wtf.gofancy.koremods.script

import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import org.jetbrains.kotlin.utils.addToStdlib.cast
import wtf.gofancy.koremods.Identifier
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.withUpdatedClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

private val LOGGER: Logger = KoremodsBlackboard.createLogger("Evaluation")

fun evalTransformers(identifier: Identifier, source: SourceCode, log: Logger, classpath: Collection<File> = emptyList()): TransformerHandler {
    when (val eval = evalScript(source, log, classpath)) {
        is ResultWithDiagnostics.Success -> {
            when (val result = eval.value.returnValue) {
                is ResultValue.Value-> throw IllegalStateException("Script $identifier returned a value instead of Unit")
                is ResultValue.Unit -> return result.scriptInstance
                    .cast<CoremodKtsScript>()
                    .transformerHandler
                is ResultValue.Error -> throw RuntimeException("Exception in script $identifier", result.error)
                // this shouldn't ever happen
                ResultValue.NotEvaluated -> throw ScriptEvaluationException("An unknown error has occured while evaluating script $identifier")
            }
        }
        is ResultWithDiagnostics.Failure -> {
            eval.reports.forEach { report ->
                report.exception
                    ?.let { LOGGER.catching(Level.ERROR, it) }
                    ?: LOGGER.log(report.severity.toLogLevel(), report.message)
            }
            throw ScriptEvaluationException("Failed to evaluate script $identifier. See the log for more information")
        }
    }
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
