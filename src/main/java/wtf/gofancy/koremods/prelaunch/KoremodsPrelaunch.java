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

package wtf.gofancy.koremods.prelaunch;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class KoremodsPrelaunch {
    private static final Logger LOGGER = KoremodsBlackboard.createLogger("Prelaunch");
    public static final List<String> KOTLIN_DEP_PACKAGES = Arrays.asList(
            "codes.som.anthony.koffee.",
            "gnu.trove.",
            "io.github.config4k.",
            "javaslang.",
            "kotlin.",
            "kotlinx.coroutines.",
            "org.intellij.lang.",
            "org.jetbrains.",
            "wtf.gofancy.koremods."
    );

    public final Path gameDir;
    public final Path configDir;
    public final Path modsDir;
    public final File cacheDir;
    private final Path depsDir;

    public final URL mainJarUrl;
    public final JarFile mainJar;
    public final File mainJarFile;
    private final Attributes attributes;

    public KoremodsPrelaunch(Path gameDir, URL mainJarUrl) throws Exception {
        this.gameDir = gameDir;
        this.configDir = this.gameDir.resolve("config");
        this.modsDir = gameDir.resolve("mods");
        Path koremodsDir = this.modsDir.resolve(KoremodsBlackboard.NAMESPACE);
        this.cacheDir = koremodsDir.resolve("cache").toFile();
        this.cacheDir.mkdirs();
        this.depsDir = koremodsDir.resolve("dependencies");

        this.mainJarUrl = mainJarUrl;
        this.mainJarFile = new File(this.mainJarUrl.toURI());
        this.mainJar = new JarFile(this.mainJarFile);
        this.attributes = this.mainJar.getManifest().getMainAttributes();
    }

    public URL extractDependency(String name) {
        String depName = this.attributes.getValue("Additional-Dependencies-" + name);
        if (depName == null) throw new IllegalArgumentException("Required dependency " + name + " not found");

        try {
            Path destPath = this.depsDir.resolve(depName);
            if (Files.notExists(destPath)) {
                LOGGER.info("Extracting dependency '{}' to {}", name, destPath);
                Files.createDirectories(this.depsDir);

                ZipEntry entry = this.mainJar.getEntry(depName);
                InputStream source = this.mainJar.getInputStream(entry);
                Files.copy(source, destPath);
            }
            return destPath.toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract required dependency " + name, e);
        }
    }
}
