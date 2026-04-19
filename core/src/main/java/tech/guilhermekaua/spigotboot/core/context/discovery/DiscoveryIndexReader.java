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
package tech.guilhermekaua.spigotboot.core.context.discovery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Aggregates every {@link DiscoveryIndex} found on the classpath into a single lookup table keyed
 * by {@link DiscoveryCategories category}. Runtime scan sites query this first and fall back to
 * classpath scanning only when the reader has no indexes available.
 *
 * <p>Indexes are discovered by enumerating marker files under
 * {@value #INDEX_PATH}{@code /<GeneratedIndexClassFQCN>}. This mirrors the mechanism used by
 * {@code ModuleDiscovery}: each jar contributes one or more uniquely-named marker files, so the
 * Maven Shade Plugin merges them without requiring an explicit resource transformer.
 */
public final class DiscoveryIndexReader {
    private static final Logger LOGGER = Logger.getLogger(DiscoveryIndexReader.class.getName());
    private static final String INDEX_PATH = "META-INF/spigot-boot/discovery";
    private static final String INDEX_PATH_PREFIX = INDEX_PATH + "/";

    private final boolean anyIndexLoaded;
    private final Map<String, List<Class<?>>> byCategory;

    public DiscoveryIndexReader(@NotNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");

        Set<String> indexFqcns = findIndexFqcns(classLoader);

        Map<String, LinkedHashSet<Class<?>>> acc = new HashMap<>();
        int loaded = 0;

        for (String fqcn : indexFqcns) {
            try {
                Class<?> clazz = Class.forName(fqcn, true, classLoader);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (!(instance instanceof DiscoveryIndex)) {
                    LOGGER.log(Level.WARNING, "Discovery marker '{0}' points to a class that does not implement DiscoveryIndex",
                            fqcn);
                    continue;
                }
                Map<String, Class<?>[]> entries = ((DiscoveryIndex) instance).entries();
                if (entries == null) {
                    continue;
                }
                loaded++;
                for (Map.Entry<String, Class<?>[]> entry : entries.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    LinkedHashSet<Class<?>> bucket = acc.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>());
                    for (Class<?> c : entry.getValue()) {
                        if (c != null) {
                            bucket.add(c);
                        }
                    }
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.log(Level.WARNING, "Failed to load DiscoveryIndex '" + fqcn + "'", e);
            }
        }

        Map<String, List<Class<?>>> frozen = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<Class<?>>> entry : acc.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.byCategory = Collections.unmodifiableMap(frozen);
        this.anyIndexLoaded = loaded > 0;
    }

    /**
     * Convenience factory that uses the current thread's context class loader (falling back to
     * this class's loader if the context loader is {@code null}).
     */
    public static DiscoveryIndexReader create() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = DiscoveryIndexReader.class.getClassLoader();
        }
        return new DiscoveryIndexReader(cl);
    }

    /**
     * @return {@code true} if at least one {@link DiscoveryIndex} was loaded successfully. When
     * {@code false}, callers should fall back to classpath scanning.
     */
    public boolean hasAnyIndex() {
        return anyIndexLoaded;
    }

    /**
     * @return {@code true} when any loaded index contains entries for {@code category}.
     */
    public boolean hasEntriesFor(@NotNull String category) {
        List<Class<?>> list = byCategory.get(category);
        return list != null && !list.isEmpty();
    }

    /**
     * Returns every class registered under {@code category} by any loaded index.
     */
    public @NotNull List<Class<?>> classesInCategory(@NotNull String category) {
        List<Class<?>> list = byCategory.get(category);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Returns classes registered under {@code category} whose fully-qualified name starts with
     * {@code packageFilter}. A null or empty filter means "no filter". The filter matches
     * {@code packageFilter} exactly or {@code packageFilter + "."} as a prefix, so
     * {@code "com.foo"} does not match {@code "com.foobar.Baz"}.
     */
    public @NotNull List<Class<?>> classesInCategory(@NotNull String category, @Nullable String packageFilter) {
        List<Class<?>> all = classesInCategory(category);
        if (all.isEmpty() || packageFilter == null || packageFilter.isEmpty()) {
            return all;
        }

        List<Class<?>> filtered = new ArrayList<>(all.size());
        for (Class<?> clazz : all) {
            String name = clazz.getName();
            if (name.equals(packageFilter) || name.startsWith(packageFilter + ".")) {
                filtered.add(clazz);
            }
        }
        return filtered;
    }

    /**
     * @return every category key present across all loaded indexes (unordered).
     */
    public @NotNull Set<String> knownCategories() {
        return Collections.unmodifiableSet(byCategory.keySet());
    }

    private static Set<String> findIndexFqcns(ClassLoader classLoader) {
        Set<String> fqcns = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(INDEX_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if ("jar".equals(protocol)) {
                    readFromJar(resource, fqcns);
                } else if ("file".equals(protocol)) {
                    readFromDirectory(resource, fqcns);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan for discovery index markers", e);
        }
        return fqcns;
    }

    private static void readFromJar(URL resource, Set<String> fqcns) {
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            jarConnection.setUseCaches(false);

            try (JarFile jarFile = jarConnection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (entry.isDirectory() || !name.startsWith(INDEX_PATH_PREFIX)) {
                        continue;
                    }
                    String fqcn = name.substring(INDEX_PATH_PREFIX.length());
                    if (!fqcn.isEmpty() && !fqcn.contains("/")) {
                        fqcns.add(fqcn);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read discovery index markers from jar: " + resource, e);
        }
    }

    private static void readFromDirectory(URL resource, Set<String> fqcns) {
        try {
            Path directory = Paths.get(resource.toURI());
            if (!Files.isDirectory(directory)) {
                return;
            }
            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(n -> !n.isEmpty())
                        .forEach(fqcns::add);
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read discovery index markers from directory: " + resource, e);
        }
    }
}
