/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2022 Garden of Fancy
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
import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.dsl.TransformerHandler
import wtf.gofancy.koremods.script.KOREMODS_SCRIPT_EXTENSION
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.toList

data class Identifier internal constructor(val namespace: String, val name: String) : Serializable {
    override fun toString(): String = "$namespace:$name"
}

data class RawScriptPack<T> internal constructor(val namespace: String, val path: Path, val scripts: List<RawScript<T>>)
data class RawScript<T> internal constructor(val identifier: Identifier, val source: T)

data class KoremodsScriptPack internal constructor(val namespace: String, val path: Path, val scripts: List<KoremodsScript>)
data class KoremodsScript internal constructor(val identifier: Identifier, val handler: TransformerHandler)

sealed class LoaderMode(internal val extension: String)
class CompileEvalLoad(internal val libraries: Array<out String>) : LoaderMode(KOREMODS_SCRIPT_EXTENSION)
object EvalLoad : LoaderMode("jar")

class KoremodsLoader(private val mode: LoaderMode) {
    var scriptPacks: List<KoremodsScriptPack> = emptyList()
        private set

    fun loadKoremods(dir: Path, additionalPaths: Iterable<Path>) {
        val paths = Files.walk(dir, 1)
            .filter { !it.isDirectory() && it.name != dir.name }
            .toList()
        loadKoremods(paths + additionalPaths)
    }

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

    fun getAllTransformers(): List<Transformer<*>> {
        return scriptPacks
            .flatMap(KoremodsScriptPack::scripts)
            .flatMap { it.handler.getTransformers() }
    }
}
