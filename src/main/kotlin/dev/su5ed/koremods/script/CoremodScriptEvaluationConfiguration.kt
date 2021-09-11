package dev.su5ed.koremods.script

import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.scriptsInstancesSharing

class CoremodScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(true)
})
