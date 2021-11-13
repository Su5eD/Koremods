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

package dev.su5ed.koremods.script.host

import java.io.File
import java.util.*

/**
 * STOLEN from kotlin-main-kts [Directories](https://github.com/JetBrains/kotlin/blob/4ad5f01324117335c122cbb062420b3d6145f827/libraries/tools/kotlin-main-kts/src/org/jetbrains/kotlin/mainKts/impl/directories.kt)
 */

class Directories(private val systemProperties: Properties, private val environment: Map<String, String>) {
    // Links to recommendations for storing various kinds of files on different platforms:
    // Windows: http://www.microsoft.com/security/portal/mmpc/shared/variables.aspx
    // unix (freedesktop): http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
    // OS X: https://developer.apple.com/library/mac/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/AccessingFilesandDirectories/AccessingFilesandDirectories.html
    //
    // Note that the temp directory must not be used on Unix, as it is shared between users.
    val cache: File?
        get() = when (os) {
            OSKind.Windows -> getEnv("LOCALAPPDATA")?.toFile() ?: temp
            OSKind.OSX -> userHome?.resolve("Library/Caches")
            OSKind.Unix -> getEnv("XDG_CACHE_HOME")?.toFile() ?: userHome?.resolve(".cache")
            OSKind.Unknown -> userHome?.resolve(".cache")
        }

    private val userHome: File?
        get() = getProperty("user.home")?.toFile()

    private val temp: File?
        get() = getProperty("java.io.tmpdir")?.toFile()

    private enum class OSKind {
        Windows,
        OSX,
        Unix,
        Unknown
    }

    // OS detection based on
    // compiler/daemon/daemon-common/src/org/jetbrains/kotlin/daemon/common/FileSystemUtils.kt
    // which in turn is based on: http://www.code4copy.com/java/post/detecting-os-type-in-java
    private val os: OSKind
        get() = getProperty("os.name")?.lowercase().let { name ->
            when {
                name == null -> OSKind.Unknown
                name.startsWith("windows") -> OSKind.Windows
                name.startsWith("mac os") -> OSKind.OSX
                name.contains("unix") -> OSKind.Unix
                name.startsWith("linux") -> OSKind.Unix
                name.contains("bsd") -> OSKind.Unix
                name.startsWith("irix") -> OSKind.Unix
                name.startsWith("mpe/ix") -> OSKind.Unix
                name.startsWith("aix") -> OSKind.Unix
                name.startsWith("hp-ux") -> OSKind.Unix
                name.startsWith("sunos") -> OSKind.Unix
                name.startsWith("sun os") -> OSKind.Unix
                name.startsWith("solaris") -> OSKind.Unix
                else -> OSKind.Unknown
            }
        }

    private fun getProperty(name: String) = systemProperties.getProperty(name).nullIfBlank()

    private fun getEnv(name: String) = environment[name].nullIfBlank()

    private fun String.toFile() = File(this)

    private fun String?.nullIfBlank() = if (this == null || this.isBlank()) null else this
}
