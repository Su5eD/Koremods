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

package dev.su5ed.koremods.prelaunch;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class KoremodsPrelaunch {
    static final Logger LOGGER = LogManager.getLogger("Koremods.Prelaunch");
    private static final String LAUNCH_TARGET = "dev.su5ed.koremods.launch.KoremodsLaunch";
    private static final List<String> KOTLIN_DEP_PACKAGES = Arrays.asList(
            "org.jetbrains.",
            "kotlin.",
            "org.intellij.lang.",
            "kotlinx.coroutines.",
            "javaslang.",
            "gnu.trove.",
            "codes.som.anthony.koffee.",
            "io.github.config4k.",
            "dev.su5ed.koremods."
    );
    
    public static DependencyClassLoader dependencyClassLoader;
    
    private final Path gameDir;
    private final URL[] discoveryUrls;
    
    private final Path modsDir;
    private final File cacheDir;
    private final Path depsPath;
    public final URL modJarUrl;
    private final JarFile modJar;
    private final Attributes modJarAttributes;

    public KoremodsPrelaunch(Path gameDir, URL[] discoveryUrls, String mcVersion) throws Exception {
        this.gameDir = gameDir;
        this.discoveryUrls = discoveryUrls;
        
        this.modsDir = gameDir.resolve("mods");
        Path koremodsDir = this.modsDir.resolve(mcVersion).resolve("koremods");
        this.cacheDir = koremodsDir.resolve("cache").toFile();
        this.cacheDir.mkdirs();
        this.depsPath = koremodsDir.resolve("dependencies");
        
        this.modJarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        File modFile = new File(this.modJarUrl.toURI());
        this.modJar = new JarFile(modFile);
        this.modJarAttributes = this.modJar.getManifest().getMainAttributes();
    }
    
    public void launch(String splashFactoryClass) throws Exception {
        Path configDir = this.gameDir.resolve("config");
        URL kotlinDep = extractDependency("Kotlin");
        
        dependencyClassLoader = new DependencyClassLoader(new URL[]{ this.modJarUrl, kotlinDep }, (URLClassLoader) getClass().getClassLoader(), KOTLIN_DEP_PACKAGES);
        Class<?> launchClass = dependencyClassLoader.loadClass(LAUNCH_TARGET);
        Method launchMethod = launchClass.getDeclaredMethod("launch", KoremodsPrelaunch.class, File.class, Path.class, Path.class, URL[].class, SplashScreenFactory.class);
        Object instance = launchClass.newInstance();
        
        SplashScreenFactory splashFactory = splashFactoryClass != null
                ? (SplashScreenFactory) dependencyClassLoader.loadClass(splashFactoryClass).newInstance()
                : null;
        
        LOGGER.info("Launching Koremods instance");
        launchMethod.invoke(instance, this, this.cacheDir, configDir, this.modsDir, this.discoveryUrls, splashFactory);
    }
    
    public URL extractDependency(String name) {
        String depName = this.modJarAttributes.getValue("Additional-Dependencies-" + name);
        if (depName == null) throw new IllegalArgumentException("Required dependency " + name + " not found");

        ZipEntry entry = this.modJar.getEntry(depName);
        try {
            Path destPath = this.depsPath.resolve(entry.getName());
            if (Files.notExists(destPath)) {
                Files.createDirectories(this.depsPath);

                InputStream source = this.modJar.getInputStream(entry);
                OutputStream dest = Files.newOutputStream(destPath);
                IOUtils.copy(source, dest);
            }
            return destPath.toUri().toURL();
        } catch (IOException e) {
            throw new RuntimeException("Can't extract required dependency " + name, e);
        }
    }
}
