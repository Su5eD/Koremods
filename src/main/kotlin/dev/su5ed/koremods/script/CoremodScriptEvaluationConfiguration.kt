package dev.su5ed.koremods.script

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class CoremodScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(true)
    
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

class ClassNotAvailableInSandboxException : RuntimeException()

class FilteredClassLoader : ClassLoader() {
    private val restrictions = mutableListOf(
        "dev.su5ed.koremods.script.CoremodKtsScript",
        "dev.su5ed.koremods.dsl.",
        "java.lang",
        "java.util",
        "kotlin.",
        "org.objectweb.asm."
    )
    
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (name.contains(".") && restrictions.isNotEmpty() && restrictions.none(name::startsWith)) 
            throw ClassNotAvailableInSandboxException()
        
        return super.loadClass(name, resolve)
    }
    
    fun allow(paths: Iterable<String>) {
        restrictions.addAll(paths)
    }
}
