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

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationKeys
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.filterByAnnotationType

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Allow(vararg val paths: String)

internal val JvmScriptCompilationConfigurationKeys.restrictions by PropertiesCollection.key<MutableList<String>>(mutableListOf())

internal class CoremodScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        dependenciesFromClassloader(
            classLoader = CoremodKtsScript::class.java.classLoader,
            wholeClasspath = true
        )
        defaultImports( // TODO
            "org.objectweb.asm.Opcodes.*",
            "dev.su5ed.koremods.script.Allow",
            "org.objectweb.asm.tree.ClassNode",
            "org.objectweb.asm.tree.MethodNode",
            "org.objectweb.asm.tree.FieldNode"
        )
    }
    refineConfiguration {
        onAnnotations(Allow::class, handler = CoremodScriptConfigurator)
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})

object CoremodScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        return processAnnotations(context)
    }
    
    private fun processAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotation = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
            ?.filterByAnnotationType<Allow>()
            ?.map(ScriptSourceAnnotation<Allow>::annotation)
            ?.firstOrNull()
            ?: return context.compilationConfiguration.asSuccess()
                
        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            jvm.restrictions(annotation.paths.toMutableList())
        }.asSuccess()
    }
}
