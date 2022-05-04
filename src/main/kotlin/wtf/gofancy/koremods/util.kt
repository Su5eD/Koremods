package wtf.gofancy.koremods

import com.google.common.base.Stopwatch
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

fun <T> Logger.measureMillis(level: Level, message: String, block: () -> T): T {
    val stopwatch = Stopwatch.createStarted()
    val result = block()
    val time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
    log(level, "$message took $time ms")
    return result
}

fun Logger.logResultErrors(result: ResultWithDiagnostics.Failure) {
    result.reports.forEach { report ->
        report.exception
            ?.let { catching(Level.ERROR, it) }
            ?: log(report.severity.toLogLevel(), report.message)
    }
}

fun ScriptDiagnostic.Severity.toLogLevel(): Level {
    return when (this) {
        ScriptDiagnostic.Severity.FATAL -> Level.FATAL
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
    }
}