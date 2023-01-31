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

@file:Suppress("unused")

package wtf.gofancy.koremods.script

import org.apache.logging.log4j.Logger
import wtf.gofancy.koremods.Identifier
import wtf.gofancy.koremods.dsl.TransformerBuilder
import wtf.gofancy.koremods.dsl.TransformerHandler
import kotlin.script.experimental.annotations.KotlinScript

/**
 * The file extension used by Koremods script source files
 */
const val KOREMODS_SCRIPT_EXTENSION = "core.kts"

/**
 * Main Koremods Kotlin Script definition
 *
 * @param identifier unique script identifier
 * @param logger dedicated logger usable inside the script
 *
 * @see KotlinScript
 */
@KotlinScript(
    fileExtension = KOREMODS_SCRIPT_EXTENSION,
    compilationConfiguration = KoremodsScriptCompilationConfiguration::class,
    evaluationConfiguration = KoremodsScriptEvaluationConfiguration::class,
)
abstract class KoremodsKtsScript(identifier: Identifier, val logger: Logger) {
    /**
     * Stores the instance's configured transformers.
     */
    val transformerHandler = TransformerHandler(identifier)

    /**
     * Configure the script's transformers.
     * 
     * @param configuration The configuration function
     */
    fun transformers(configuration: TransformerBuilder.() -> Unit) {
        transformerHandler.transformers(configuration)
    }
}
