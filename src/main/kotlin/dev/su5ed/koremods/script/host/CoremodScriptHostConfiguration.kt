/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package dev.su5ed.koremods.script.host

import dev.su5ed.koremods.KoremodsBlackboard
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache

/**
 * STOLEN from kotlin-main-kts [scriptDef](https://github.com/JetBrains/kotlin/blob/4ad5f01324117335c122cbb062420b3d6145f827/libraries/tools/kotlin-main-kts/src/org/jetbrains/kotlin/mainKts/scriptDef.kt)
 */
private const val COMPILED_SCRIPTS_CACHE_DIR_PROPERTY = "dev.su5ed.koremods.core.kts.compiled.scripts.cache.dir"
private const val COMPILED_SCRIPTS_CACHE_DIR_ENV_VAR = "DEV_SU5ED_KOREMODS_COMPILED_SCRIPTS_CACHE_DIR"
private const val COMPILED_SCRIPTS_CACHE_DIR = "dev.su5ed.koremods.compiled.cache"
private const val COMPILED_SCRIPTS_CACHE_VERSION = 1

class CoremodScriptHostConfiguration : ScriptingHostConfiguration(
    {
        jvm {
            (KoremodsBlackboard.cacheDir ?: getDefaultCacheDir())
                ?.takeIf { it.exists() && it.isDirectory }
                ?.let { 
                    compilationCache(
                        CompiledScriptJarsCache { script, compilationConfigurtion ->
                            File(it, compiledScriptUniqueName(script, compilationConfigurtion) + ".jar")
                        }
                    )
                }
        }
    }
)

private fun getDefaultCacheDir(): File? {
    val cacheExtSetting = System.getProperty(COMPILED_SCRIPTS_CACHE_DIR_PROPERTY) ?: System.getenv(COMPILED_SCRIPTS_CACHE_DIR_ENV_VAR)
    return when {
        cacheExtSetting == null -> Directories(System.getProperties(), System.getenv()).cache
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { File(it, COMPILED_SCRIPTS_CACHE_DIR).apply(File::mkdir) }
        cacheExtSetting.isBlank() -> null
        else -> File(cacheExtSetting)
    }
        ?.takeIf { it.exists() && it.isDirectory }
}

private fun compiledScriptUniqueName(script: SourceCode, compilationConfigurtion: ScriptCompilationConfiguration): String {
    val digestWrapper = MessageDigest.getInstance("SHA-256")

    fun addToDigest(chunk: String) = with(digestWrapper) {
        val chunkBytes = chunk.toByteArray()
        update(chunkBytes.size.toByteArray())
        update(chunkBytes)
    }

    digestWrapper.update(COMPILED_SCRIPTS_CACHE_VERSION.toByteArray())
    addToDigest(script.text)
    compilationConfigurtion.notTransientData.entries
        .sortedBy { it.key.name }
        .forEach {
            addToDigest(it.key.name)
            addToDigest(it.value.toString())
        }
    return digestWrapper.digest().toHexString()
}

private fun ByteArray.toHexString(): String = joinToString("", transform = { "%02x".format(it) })

private fun Int.toByteArray() = ByteBuffer.allocate(Int.SIZE_BYTES).also { it.putInt(this) }.array()
