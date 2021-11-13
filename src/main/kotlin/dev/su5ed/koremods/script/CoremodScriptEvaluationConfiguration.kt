/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
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

package dev.su5ed.koremods.script

import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

internal class CoremodScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    jvm {
        baseClassLoader(FilteredClassLoader())
        loadDependencies(false)
    }
    
    refineConfigurationBeforeEvaluate { context ->
        val cl = context.evaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]
        if (cl is FilteredClassLoader) {
            context.compiledScript.compilationConfiguration[ScriptCompilationConfiguration.jvm.restrictions]
                ?.let(cl::allow)
        }
        
        context.evaluationConfiguration.asSuccess()
    }
})

class ClassNotAvailableInSandboxException(message: String) : RuntimeException(message)

internal class FilteredClassLoader : ClassLoader(Thread.currentThread().contextClassLoader) {
    private val restrictions = mutableListOf(
        "dev.su5ed.koremods.script.CoremodKtsScript",
        "dev.su5ed.koremods.dsl.",
        "java.lang",
        "java.util",
        "kotlin.",
        "org.objectweb.asm.",
        "codes.som.anthony.koffee.",
        "org.apache.logging.log4j."
    )
    
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (name.contains(".") && restrictions.isNotEmpty() && restrictions.none(name::startsWith)) 
            throw ClassNotAvailableInSandboxException(name)
        
        return super.loadClass(name, resolve)
    }
    
    fun allow(paths: Iterable<String>) {
        restrictions.addAll(paths)
    }
}
