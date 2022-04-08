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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;

/**
 * Because some dependencies cannot be relocated when shadowed, we use a custom classloader to load them in an
 * isolated environment and prevent conflicts with other consumers on the classpath that might be using the same library
 */
public class DependencyClassLoader extends URLClassLoader {
    private static final Set<String> EXCLUSIONS = Set.of(
            "wtf.gofancy.koremods.prelaunch."
    );
    
    /**
     * Classes that are preferably loaded by this classloader. If not found, we'll attempt loading them using the parent CL instead of throwing an exception.
     */
    private final List<String> priorityClasses;

    public DependencyClassLoader(URL[] urls, ClassLoader parent, List<String> priorityClasses) {
        super(urls, parent);

        this.priorityClasses = priorityClasses;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> existing = findLoadedClass(name);
        if (existing != null) return existing;
        
        if (EXCLUSIONS.stream().noneMatch(name::startsWith) && this.priorityClasses.stream().anyMatch(name::startsWith)) {
            return findClass(name);
        }
        
        return loadClassFallback(name, resolve);
    }
    
    protected Class<?> loadClassFallback(String name, boolean resolve) throws ClassNotFoundException {
        return super.loadClass(name, resolve);
    }
}
