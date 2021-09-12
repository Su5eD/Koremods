package dev.su5ed.koremods.script

import kotlin.script.experimental.annotations.KotlinScript

@Suppress("UNUSED_PARAMETER")
@KotlinScript(
    fileExtension = "core.kts",
    compilationConfiguration = CoremodScriptCompilationConfiguration::class,
    evaluationConfiguration = CoremodScriptEvaluationConfiguration::class
)
abstract class CoremodKtsScript(args: Array<String>)
