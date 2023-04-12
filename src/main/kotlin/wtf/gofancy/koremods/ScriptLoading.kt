/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2023 Garden of Fancy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package wtf.gofancy.koremods

import com.google.common.collect.ImmutableList
import wtf.gofancy.koremods.Identifier.Companion.NAMESPACE_PATTERN
import wtf.gofancy.koremods.Identifier.Companion.NAME_PATTERN
import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.script.KOREMODS_SCRIPT_EXTENSION
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.toList

/**
 * Uniquely identifies objects within Koremods, such as scripts and transformers.
 * Both namespaces and names must follow a set of rules and are check against their respective patterns -
 * [NAMESPACE_PATTERN] for namespaces and [NAME_PATTERN] for names.
 * 
 * @param namespace the owner's globally unique name, used to group together objects belonging to the same entity
 * @param name the object's unique name within its [namespace]
 * @throws IllegalArgumentException if any of the two components don't match their pattern
 * @see NAMESPACE_PATTERN
 * @see NAME_PATTERN
 */
data class Identifier internal constructor(val namespace: String, val name: String) : Serializable {
    companion object {
        /**
         * Identifier [namespace]s follow a lower_snake_case format, and must:
         * - Start with a lowercase letter
         * - Only contain lowercase characters, numbers and underscores
         * - Be max. 64 characters long
         */
        val NAMESPACE_PATTERN = "^[a-z][a-z0-9_]{1,63}\$".toRegex()

        /**
         * Identifier [name]s follow a camelCase format, and must:
         * - Start with a lowercase letter
         * - Only contain alphanumeric characters
         * - Be max. 64 characters long
         */
        val NAME_PATTERN = "^[a-z][a-zA-Z0-9]{1,63}\$".toRegex()
    }

    init {
        if (!namespace.matches(NAMESPACE_PATTERN)) {
            throw IllegalArgumentException("Identifier namespace '$namespace' does not match the pattern /$NAMESPACE_PATTERN/")
        }
        if (!name.matches(NAME_PATTERN)) {
            throw IllegalArgumentException("Identifier name '$name' does not match the pattern /$NAME_PATTERN/")
        }
    }

    override fun toString(): String = "$namespace:$name"
}

/**
 * Represents a Koremods Script Pack in raw, unfinalized form with script sources.
 * 
 * @param T the script source type
 * @property namespace the pack's namespace
 * @property path path to the pack's root file
 * @property scripts list of the pack's raw scripts that also contain sources
 */
data class RawScriptPack<T> internal constructor(val namespace: String, val path: Path, val scripts: List<RawScript<T>>)

/**
 * Represents a Koremods Script in raw, unfinalized form with a source attached.
 * 
 * @param T the script source type
 * @property identifier the script's unique identifier
 * @property source the script's source
 */
data class RawScript<T> internal constructor(val identifier: Identifier, val source: T)

/**
 * A loaded and initialized Koremods Script Pack.
 * 
 * @property namespace the pack's namespace
 * @property path path to the pack's root file
 * @property scripts list of the pack's loaded scripts
 */
data class KoremodsScriptPack internal constructor(val namespace: String, val path: Path, val scripts: List<KoremodsScript>)

/**
 * A loaded and initialized Koremods Script.
 * 
 * @property identifier the script's unique identifier
 * @property handler holds the script's transformers
 */
data class KoremodsScript internal constructor(val identifier: Identifier, val handler: TransformerHandler)

/**
 * Defines behavior for how Koremods should proceed when loading Script Packs.
 * 
 * @property extension expected script file extension
 */
sealed class LoaderMode(internal val extension: String)

/**
 * Compiles Koremods Scripts in source form with the supplied [libraries].
 * 
 * @property libraries file names of additional libraries to use during compilation
 */
class CompileEvalLoad(internal val libraries: Array<out String>) : LoaderMode(KOREMODS_SCRIPT_EXTENSION)

/**
 * Evaluates compiled Koremods Scripts.
 */
object EvalLoad : LoaderMode("jar")

/**
 * Handles locating, parsing, evaluating and optionally compiling Koremods Scripts
 * and Koremods Script Packs.
 * 
 * @property mode the loader mode to use when loading script packs
 * @property scriptPacks a list of loaded script packs
 */
class KoremodsLoader(private val mode: LoaderMode) {
    var scriptPacks: List<KoremodsScriptPack> = emptyList()
        private set

    /**
     * Load Koremods Script Packs starting with all subpaths in a directory.
     * Add all results to [scriptPacks].
     *
     * @param dir folder to search for Koremods Script Packs
     * @param additionalPaths additional paths to search
     */
    fun loadKoremods(dir: Path, additionalPaths: Iterable<Path>) {
        val paths = Files.walk(dir, 1)
            .filter { !it.isDirectory() && it.name != dir.name }
            .toList()
        loadKoremods(paths + additionalPaths)
    }

    /**
     * Load Koremods Script Packs starting with a set of paths.
     * Add all results to [scriptPacks].
     * 
     * @param paths paths to search for Koremods Script Packs
     */
    fun loadKoremods(paths: Iterable<Path>) {
        val located = scanPaths(paths, mode.extension)

        val compiled = if (mode is CompileEvalLoad) {
            val sources = readScriptSources(located)
            compileScriptPacks(sources, mode.libraries)
        } else {
            readCompiledScripts(located)
        }

        val evaluated = evalScriptPacks(compiled)
        scriptPacks = ImmutableList.copyOf(evaluated)
    }

    /**
     * @return All loaded transformers across loaded Script Packs
     */
    fun getAllTransformers(): List<Transformer<*>> {
        return scriptPacks
            .flatMap(KoremodsScriptPack::scripts)
            .flatMap { it.handler.getTransformers() }
    }
}
