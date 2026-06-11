/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class providing helper methods for class existence checking with caching.
 * <p>
 * This class provides a safe way to check whether a class exists on a given classloader
 * without throwing exceptions. Results are cached per classloader for performance.
 */
public final class ClassUtils {
    private static final Map<ClassLoader, Map<String, Boolean>> cache =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Checks whether a class with the given name is present on the specified classloader.
     * <p>
     * This method uses {@link ClassLoader#loadClass(String)} which does NOT initialize the class,
     * avoiding unwanted side effects from static initializers. Results are cached per classloader
     * to avoid redundant classloader lookups.
     *
     * @param className   the fully qualified name of the class to check, not null
     * @param classLoader the classloader to check for the class, not null
     * @return {@code true} if the class exists on the classloader, {@code false} otherwise
     * @throws NullPointerException if className or classLoader is null
     */
    public static boolean isPresent(@NotNull String className, @NotNull ClassLoader classLoader) {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(classLoader, "classLoader cannot be null");

        Map<String, Boolean> perLoaderCache;
        synchronized (cache) {
            perLoaderCache = cache.computeIfAbsent(classLoader, k -> new ConcurrentHashMap<>());
        }

        return perLoaderCache.computeIfAbsent(className, name -> {
            try {
                classLoader.loadClass(name);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    public static void clearCache() {
        cache.clear();
    }
}
