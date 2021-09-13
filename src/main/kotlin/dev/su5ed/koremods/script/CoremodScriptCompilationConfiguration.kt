package dev.su5ed.koremods.script

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.jsr223.importAllBindings
import kotlin.script.experimental.jvmhost.jsr223.jsr223

class CoremodScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        try {
            restrictions(listOf(
                "dev.su5ed.koremods.dsl.",
                "dev.su5ed.koremods.script.CoremodKtsScript",
                "java.lang",
                "java.util",
                "kotlin.",
            ))
        } catch (ignored: Throwable) {}
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
