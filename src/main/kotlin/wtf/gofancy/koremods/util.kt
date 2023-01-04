/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2023 Garden of Fancy
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

package wtf.gofancy.koremods

import com.google.common.base.Stopwatch
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * Measure and log code execution time in milliseconds.
 *
 * @param level the log level
 * @param message the action performed
 * @param block the function to run
 */
fun <T> Logger.measureMillis(level: Level, message: String, block: () -> T): T {
    val stopwatch = Stopwatch.createStarted()
    val result = block()
    val time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)
    log(level, "$message took $time ms")
    return result
}

/**
 * Dump errors from a [ResultWithDiagnostics] to a Log4J [Logger].
 */
fun Logger.logResultErrors(result: ResultWithDiagnostics.Failure) {
    result.reports.forEach { report ->
        report.exception
            ?.let { catching(Level.ERROR, it) }
            ?: log(report.severity.toLogLevel(), report.message)
    }
}

private fun ScriptDiagnostic.Severity.toLogLevel(): Level {
    return when (this) {
        ScriptDiagnostic.Severity.FATAL -> Level.FATAL
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
    }
}