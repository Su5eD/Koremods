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
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import wtf.gofancy.koremods.prelaunch.KoremodsBlackboard
import wtf.gofancy.koremods.script.KOREMODS_SCRIPT_EXTENSION
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.*

private val LOGGER: Logger = KoremodsBlackboard.createLogger("Discovery")
private val SCRIPT_SCAN: Marker = MarkerManager.getMarker("SCRIPT_SCAN")
private val SCRIPT_NAME_PATTERN = "^[a-zA-Z0-9]*\$".toRegex()

fun scanPaths(paths: Iterable<Path>, scriptExtension: String): List<RawScriptPack<Path>> {
    LOGGER.debug("Scanning paths for Koremods script packs")

    return paths.mapNotNull { path -> scanPath(path, scriptExtension) }
}

fun scanPath(path: Path, scriptExtension: String = KOREMODS_SCRIPT_EXTENSION): RawScriptPack<Path>? {
    var pack: RawScriptPack<Path>? = null

    if (path.isDirectory()) {
        LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.relativeTo(path.parent.parent.parent)}")

        val conf = path.resolve(KoremodsBlackboard.CONFIG_FILE_LOCATION)
        if (conf.exists()) {
            pack = readScriptPack(path, conf, path, scriptExtension)
        }
    } else if (path.extension == "jar" || path.extension == "zip") {
        LOGGER.debug(SCRIPT_SCAN, "Scanning ${path.name}")

        val zipFs = FileSystems.newFileSystem(path, null)
        val conf = zipFs.getPath(KoremodsBlackboard.CONFIG_FILE_LOCATION)
        if (conf.exists()) {
            pack = readScriptPack(path, conf, zipFs.getPath(""), scriptExtension)
        }
    }

    return pack?.apply {
        LOGGER.debug("Found ${scripts.size} scripts in namespace $namespace")
    }
}

private fun readScriptPack(parent: Path, configPath: Path, rootPath: Path, extension: String): RawScriptPack<Path>? {
    val config: KoremodsPackConfig = configPath.bufferedReader().use(::parseConfig)
    LOGGER.info("Loading scripts for pack ${config.namespace}")

    if (config.scripts.isEmpty()) {
        LOGGER.error("Script pack ${config.namespace} defines a koremod configuration without any scripts")
        return null
    }

    val scripts = locateScripts(config.namespace, config.scripts, rootPath, extension)

    return if (scripts.isNotEmpty()) RawScriptPack(config.namespace, parent, scripts) else null
}

private fun locateScripts(namespace: String, scripts: List<String>, rootPath: Path, scriptExt: String): List<RawScript<Path>> {
    return scripts
        .map { script ->
            val nameWithExt = script.substringAfterLast('/')
            val index = nameWithExt.indexOf(".$KOREMODS_SCRIPT_EXTENSION")
            if (index == -1) {
                val extension = script.substringAfterLast('.')
                LOGGER.error("Script $nameWithExt has an invalid extension '$extension', expected '.core.kts'")
                throw IllegalArgumentException("Invalid script extension '$extension'")
            }

            val name = nameWithExt.substring(0, index)
            if (!name.matches(SCRIPT_NAME_PATTERN)) {
                LOGGER.error("Script name '$name' does not match the pattern /$SCRIPT_NAME_PATTERN/")
                throw IllegalArgumentException("Invalid script name '$name'")
            }

            val adjustedPath = script.replace(KOREMODS_SCRIPT_EXTENSION, scriptExt)
            val identifier = Identifier(namespace, name)
            LOGGER.debug("Locating script $identifier")
            val scriptPath = rootPath.resolve(adjustedPath)
            if (scriptPath.notExists()) {
                throw IllegalArgumentException("Script $identifier file $adjustedPath not found")
            }

            return@map RawScript(identifier, scriptPath)
        }
}