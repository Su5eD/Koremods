package dev.su5ed.koremods.script

import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

internal class CoremodScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    jvm {
        baseClassLoader(FilteredClassLoader())
        loadDependencies(false)
    }
    
    refineConfigurationBeforeEvaluate { context ->
        val cl = context.evaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]
        if (cl is FilteredClassLoader) {
            context.compiledScript.compilationConfiguration[ScriptCompilationConfiguration.jvm.restrictions]
                ?.let(cl::allow)
        }
        
        context.evaluationConfiguration.asSuccess()
    }
})

class ClassNotAvailableInSandboxException(message: String) : RuntimeException(message)

internal class FilteredClassLoader : ClassLoader() {
    private val restrictions = mutableListOf(
        "dev.su5ed.koremods.script.CoremodKtsScript",
        "dev.su5ed.koremods.dsl.",
        "java.lang",
        "java.util",
        "kotlin.",
        "org.objectweb.asm.",
        "codes.som.anthony.koffee.",
        "org.apache.logging.log4j."
    )
    
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (name.contains(".") && restrictions.isNotEmpty() && restrictions.none(name::startsWith)) 
            throw ClassNotAvailableInSandboxException(name)
        
        return super.loadClass(name, resolve)
    }
    
    fun allow(paths: Iterable<String>) {
        restrictions.addAll(paths)
    }
}
