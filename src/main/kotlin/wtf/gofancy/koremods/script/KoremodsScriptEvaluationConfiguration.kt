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

import wtf.gofancy.koremods.launch.KoremodsLaunch
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

/**
 * A list of fully qualified class names and package prefixes allowed to be classloaded in Koremods Scripts
 *
 * @see FilteredClassLoader
 */
val ALLOWED_CLASSES: List<String> = listOf(
    "codes.som.koffee.",
    "java.lang.",
    "java.util.",
    "kotlin.",
    "org.apache.logging.log4j.",
    "org.objectweb.asm.",
    "wtf.gofancy.koremods.Identifier",
    "wtf.gofancy.koremods.script.KoremodsKtsScript",
    "wtf.gofancy.koremods.script.ImportScript",
    "wtf.gofancy.koremods.dsl.",
)

internal class KoremodsScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    jvm {
        baseClassLoader(FilteredClassLoader(ALLOWED_CLASSES, KoremodsLaunch.scriptContextClassLoader ?: Thread.currentThread().contextClassLoader))
        loadDependencies(false)
        scriptsInstancesSharing(true)
    }
})

/**
 * Thrown when a disallowed class is attempted to be loaded in a restricted sandbox environment
 *
 * @param message the detail message
 */
class ClassNotAvailableInSandboxException(message: String) : RuntimeException(message)

/**
 * Restricts loading classes to names matching [allowedClasses]. Used to isolate Koremods Script environments.
 *
 * @param allowedClasses A list of fully qualified class names and package prefixes allowed to be loaded by this classloader
 * @param parent the parent class loader for delegation
 */
internal class FilteredClassLoader(private val allowedClasses: List<String>, parent: ClassLoader?) : ClassLoader(parent) {
    /**
     * @throws [ClassNotAvailableInSandboxException] if an unavailable class is attempted to be loaded
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (name.contains(".") && (allowedClasses.isNotEmpty() && allowedClasses.none(name::startsWith)))
            throw ClassNotAvailableInSandboxException(name)

        return super.loadClass(name, resolve)
    }
}
