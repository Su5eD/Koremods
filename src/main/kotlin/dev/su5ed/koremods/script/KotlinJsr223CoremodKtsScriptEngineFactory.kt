package dev.su5ed.koremods.script

import org.apache.logging.log4j.Logger
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import javax.script.ScriptEngine
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.jvmhost.createJvmScriptDefinitionFromTemplate
import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

class KotlinJsr223CoremodKtsScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {
    private val scriptDefinition = createJvmScriptDefinitionFromTemplate<CoremodKtsScript>()

    override fun getExtensions(): List<String> =
        listOf(scriptDefinition.compilationConfiguration[ScriptCompilationConfiguration.fileExtension]!!)

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223ScriptEngineImpl(
            this,
            scriptDefinition.compilationConfiguration,
            scriptDefinition.evaluationConfiguration
        ) { ctx -> ScriptArgsWithTypes(arrayOf(ctx.getAttribute("logger")), arrayOf(Logger::class)) }
}
