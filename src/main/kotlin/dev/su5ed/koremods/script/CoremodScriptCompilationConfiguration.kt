package dev.su5ed.koremods.script

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.jsr223.importAllBindings
import kotlin.script.experimental.jvmhost.jsr223.jsr223

class CoremodScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        dependenciesFromClassloader(
            "Koremods", "kotlin-stdlib", "kotlin-reflect",
            classLoader = CoremodKtsScript::class.java.classLoader
        )
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    jsr223 {
        importAllBindings(true)
    }
})
