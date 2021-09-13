package dev.su5ed.koremods.script

import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class CoremodScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(true)
    jvm {
        baseClassLoader(FilteredClassLoader())
        loadDependencies(false)
    }
})

class ClassNotAvailableInSandboxException : RuntimeException()

val restrictions = listOf(
    "dev.su5ed.koremods.dsl.",
    "dev.su5ed.koremods.script.CoremodKtsScript",
    "java.lang",
    "java.util",
    "kotlin.",
)

class FilteredClassLoader : ClassLoader() {
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (name.contains(".") && restrictions.isNotEmpty() && restrictions.none(name::startsWith)) 
            throw ClassNotAvailableInSandboxException()
        
        return super.loadClass(name, resolve)
    }
}
