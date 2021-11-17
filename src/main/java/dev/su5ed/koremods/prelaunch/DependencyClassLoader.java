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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Because some dependencies cannot be relocated when shadowed, we use jar-in-jar and extract them at runtime.
 * To prevent conflicts with other mods that might be using them, they're loaded by a separate classloader.
 */
public class DependencyClassLoader extends URLClassLoader {
    private static final List<String> EXCLUSIONS = Arrays.asList(
            "dev.su5ed.koremods.api.",
            "dev.su5ed.koremods.prelaunch."
    );
    
    /**
     * Classes that are preferably loaded by this classloader. If not found, we'll attempt loading them using the parent CL instead of throwing an exception.
     */
    private final List<String> priorityClasses;
    private final URLClassLoader delegateParent;
    /**
     * Completely isolates loading from the parent CL. This helps prevent conflicts in loading packages which were previously loaded as sealed by the parent.
     */
    private final DelegateClassLoader delegateClassLoader;
    private final Map<String, Class<?>> cachedClasses = new HashMap<>();
    private final boolean strict;

    public DependencyClassLoader(URL[] urls, URLClassLoader parent, List<String> priorityClasses) {
        super(urls, null);
        
        this.delegateParent = parent;
        this.priorityClasses = priorityClasses;
        this.delegateClassLoader = new DelegateClassLoader(this.delegateParent);
        this.strict = urls.length > 0;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (EXCLUSIONS.stream().noneMatch(name::startsWith)) {
            if (this.cachedClasses.containsKey(name)) return this.cachedClasses.get(name);
            
            if (this.strict && this.priorityClasses.stream().anyMatch(name::startsWith)) {
                Class<?> cls = findClass(name);
                this.cachedClasses.put(name, cls);
                return cls;
            }
        }
        
        return this.delegateClassLoader.loadClass(name, resolve);
    }

    @Override
    public URL[] getURLs() {
        URL[] urls = super.getURLs();
        URL[] delegateUrls = this.delegateParent.getURLs();

        URL[] merged = new URL[urls.length + delegateUrls.length];
        int i = 0;
        for (URL url : urls) merged[i++] = url;
        for (URL url : delegateUrls) merged[i++] = url;

        return merged;
    }

    /**
     * Custom class to widen access of {@link #loadClass(String, boolean)}
     */
    private static class DelegateClassLoader extends ClassLoader {
        
        protected DelegateClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }
}
