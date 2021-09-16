package dev.su5ed.koremods.script

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationKeys
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.jsr223.importAllBindings
import kotlin.script.experimental.jvmhost.jsr223.jsr223
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
        defaultImports(
            "org.objectweb.asm.Opcodes.*",
            "dev.su5ed.koremods.script.Allow"
        )
    }
    refineConfiguration {
        onAnnotations(Allow::class, handler = CoremodScriptConfigurator())
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    jsr223 {
        importAllBindings(true)
    }
})

private class CoremodScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
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
