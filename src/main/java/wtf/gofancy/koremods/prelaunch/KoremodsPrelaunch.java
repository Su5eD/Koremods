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

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wtf.gofancy.koremods.api.KoremodsLaunchPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class KoremodsPrelaunch {
    static final Logger LOGGER = LogManager.getLogger("Koremods.Prelaunch");
    private static final String LAUNCH_TARGET = "wtf.gofancy.koremods.launch.KoremodsLaunch";
    public static final List<String> KOTLIN_DEP_PACKAGES = Arrays.asList(
            "org.jetbrains.",
            "kotlin.",
            "org.intellij.lang.",
            "kotlinx.coroutines.",
            "javaslang.",
            "gnu.trove.",
            "codes.som.anthony.koffee.",
            "io.github.config4k.",
            "wtf.gofancy.koremods."
    );

    private final Path gameDir;
    private final Path modsDir;
    private final File cacheDir;
    private final Path depsPath;
    public final URL mainJarUrl;
    public final JarFile mainJar;
    private final Attributes attributes;

    private ClassLoader dependencyClassLoader;
    private KoremodsLaunchPlugin launchPlugin;

    public KoremodsPrelaunch(Path gameDir, String mcVersion, URL mainJarUrl) throws Exception {
        this.gameDir = gameDir;
        this.modsDir = gameDir.resolve("mods");
        Path koremodsDir = this.modsDir.resolve(mcVersion).resolve(KoremodsBlackboard.NAMESPACE);
        this.cacheDir = koremodsDir.resolve("cache").toFile();
        this.cacheDir.mkdirs();
        this.depsPath = koremodsDir.resolve("dependencies");

        this.mainJarUrl = mainJarUrl;
        File file = new File(this.mainJarUrl.toURI());
        this.mainJar = new JarFile(file);
        this.attributes = this.mainJar.getManifest().getMainAttributes();
    }

    public void launch(String launchPluginClass, URL[] discoveryUrls, ClassLoader classLoader) throws Exception {
        Path configDir = this.gameDir.resolve("config");

        dependencyClassLoader = classLoader;
        Class<?> launchClass = dependencyClassLoader.loadClass(LAUNCH_TARGET);
        Method launchMethod = launchClass.getDeclaredMethod("launch", KoremodsPrelaunch.class, File.class, Path.class, Path.class, URL[].class, KoremodsLaunchPlugin.class);
        Object instance = launchClass.getConstructor().newInstance();

        launchPlugin = launchPluginClass != null
                ? (KoremodsLaunchPlugin) dependencyClassLoader.loadClass(launchPluginClass).getConstructor().newInstance()
                : null;

        LOGGER.info("Launching Koremods instance");
        launchMethod.invoke(instance, this, this.cacheDir, configDir, this.modsDir, discoveryUrls, launchPlugin);
    }

    public URL extractDependency(String name) {
        String depName = this.attributes.getValue("Additional-Dependencies-" + name);
        if (depName == null) throw new IllegalArgumentException("Required dependency " + name + " not found");
        
        try {
            Path destPath = this.depsPath.resolve(depName);
            if (Files.notExists(destPath)) {
                Files.createDirectories(this.depsPath);

                ZipEntry entry = this.mainJar.getEntry(depName);
                InputStream source = this.mainJar.getInputStream(entry);
                OutputStream dest = Files.newOutputStream(destPath);
                IOUtils.copy(source, dest);
            }
            return destPath.toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException("Can't extract required dependency " + name, e);
        }
    }

    public ClassLoader getDependencyClassLoader() {
        return this.dependencyClassLoader;
    }

    public KoremodsLaunchPlugin getLaunchPlugin() {
        return this.launchPlugin;
    }
}
