/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
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

package dev.su5ed.koremods

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.extract
import io.github.config4k.toConfig
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists
import kotlin.io.path.writeText

/**
 * Individual config for each discovered Koremod
 */
data class KoremodModConfig(val modid: String, val scripts: List<String>)

/**
 * Configuration of Koremods itself
 */
data class KoremodConfig(val enableSplashScreen: Boolean = true)

inline fun <reified T> parseConfig(reader: Reader): T {
    return ConfigFactory.parseReader(reader).extract()
}

fun parseMainConfig(path: Path): KoremodConfig {
    if (path.notExists()) {
        val koremodConfig = KoremodConfig()
        val obj = koremodConfig.toConfig("koremods").getObject("koremods")
        val options = ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setJson(false)
        val render = obj.render(options)
        
        path.parent.createDirectories()
        path.writeText(render)
        return koremodConfig
    }
    
    return parseConfig(path.bufferedReader())
}
