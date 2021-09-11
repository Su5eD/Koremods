package dev.su5ed.koremods.script

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "core.kts",
    compilationConfiguration = CoremodScriptCompilationConfiguration::class,
    evaluationConfiguration = CoremodScriptEvaluationConfiguration::class
)
abstract class CoremodKtsScript(val args: Array<String>)
