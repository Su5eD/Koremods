package dev.su5ed.koremods.script

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.jsr223.importAllBindings
import kotlin.script.experimental.jvmhost.jsr223.jsr223

class CoremodScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        restrictions.put(listOf(
            "dev.su5ed.koremods.api.",
            "dev.su5ed.koremods.script.CoremodKtsScript",
            "java.lang",
            "java.util",
            "kotlin.",
        ))
        dependenciesFromClassloader("Koremods", "kotlin-stdlib", "kotlin-reflect")
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    jsr223 {
        importAllBindings(true)
    }
})
