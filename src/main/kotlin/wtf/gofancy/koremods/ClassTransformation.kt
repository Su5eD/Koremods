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

import org.apache.logging.log4j.Logger
import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.dsl.TransformerPropertiesExtension
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard

private val LOGGER: Logger = KoremodsBlackboard.createLogger("Transformer")

fun <T : Transformer<U>, U> applyTransform(name: Any, transformers: List<T>, node: U): List<TransformerPropertiesExtension> {
    return transformers
        .filter { transformer ->
            LOGGER.debug("Transforming $name with transformer script ${transformer.scriptIdentifier}")
            try {
                transformer.visit(node)
                return@filter true
            } catch (t: Throwable) {
                LOGGER.error("Error transforming node", t)
            }
            return@filter false
        }
        .map(Transformer<*>::props)
        .toList()
}
