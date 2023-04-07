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

package wtf.gofancy.koremods.script

import wtf.gofancy.koremods.launch.KoremodsLaunch
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.util.filterByAnnotationType

/**
 * A list of import expressions which are implicitly added to Koremods scripts
 */
private val DEFAULT_IMPORTS: List<String> = listOf(
    "wtf.gofancy.koremods.script.ImportScript",
    "wtf.gofancy.koremods.dsl.*",
    "org.objectweb.asm.tree.ClassNode",
    "org.objectweb.asm.tree.MethodNode",
    "org.objectweb.asm.tree.FieldNode",
    "org.objectweb.asm.Opcodes.*"
)

/**
 * Koremods Script compilation configuration
 */
internal class KoremodsScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(DEFAULT_IMPORTS.plus(KoremodsLaunch.PLUGIN.defaultImports))
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    compilerOptions(listOf("-jvm-target", "11"))
    refineConfiguration {
        onAnnotations(ImportScript::class, handler = KoremodsScriptConfigurator)
    }
})

/**
 * Processes Kotlin Script source annotations prior to compilation
 */
object KoremodsScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val diagnostics = mutableListOf<ScriptDiagnostic>()
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
            ?.takeIf(List<*>::isNotEmpty)
            ?: return context.compilationConfiguration.asSuccess()

        val scriptDir = (context.script as? FileBasedScriptSource)?.file?.parentFile
        val importedSources = mutableMapOf<String, Pair<File, String>>()
        annotations
            .filterByAnnotationType<ImportScript>()
            .forEach { scriptAnnotation ->
                scriptAnnotation.annotation.paths.forEach { sourceName ->
                    val file = (scriptDir?.resolve(sourceName) ?: File(sourceName)).normalize()
                    val prevImport = importedSources.put(file.absolutePath, file to sourceName)
                    if (prevImport != null) {
                        diagnostics.add(
                            ScriptDiagnostic(
                                ScriptDiagnostic.unspecifiedError, "Duplicate imports: \"${prevImport.second}\" and \"$sourceName\"",
                                sourcePath = context.script.locationId, location = scriptAnnotation.location?.locationInText
                            )
                        )
                    }
                }
            }

        return if (diagnostics.isNotEmpty()) ResultWithDiagnostics.Failure(diagnostics)
        else context.compilationConfiguration.with {
            if (importedSources.isNotEmpty()) {
                importScripts.append(importedSources.values.map { FileScriptSource(it.first) })
            }
        }.asSuccess()
    }
}
