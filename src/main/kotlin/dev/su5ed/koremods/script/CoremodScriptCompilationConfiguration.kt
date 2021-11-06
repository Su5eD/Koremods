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
