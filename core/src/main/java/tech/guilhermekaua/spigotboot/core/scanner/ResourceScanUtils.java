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
package tech.guilhermekaua.spigotboot.core.scanner;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ResourceScanUtils {

    private ResourceScanUtils() {
    }

    /**
     * Normalizes a resource path by converting backslashes to forward slashes,
     * removing leading slash, and ensuring trailing slash.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    @NotNull
    public static String normalizePath(@NotNull String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    /**
     * Checks if a filename has any of the given extensions.
     *
     * @param fileName   the filename to check
     * @param extensions the extensions to check for (without the dot)
     * @return true if the filename ends with any of the extensions
     */
    public static boolean hasExtension(@NotNull String fileName, @NotNull String... extensions) {
        for (String ext : extensions) {
            if (fileName.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scans a JAR file for entries matching a path prefix and filter.
     * Only non-directory entries directly under the path prefix are returned.
     *
     * @param jar        the JAR file to scan
     * @param pathPrefix the path prefix to match (should end with '/')
     * @param filter     additional filter for entries
     * @return list of matching entry names
     */
    @NotNull
    public static List<String> scanJar(@NotNull JarFile jar,
                                       @NotNull String pathPrefix,
                                       @NotNull Predicate<JarEntry> filter) {
        List<String> results = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entry.isDirectory()) {
                continue;
            }

            if (!entryName.startsWith(pathPrefix)) {
                continue;
            }

            String relativeName = entryName.substring(pathPrefix.length());
            if (relativeName.contains("/")) {
                continue;
            }

            if (filter.test(entry)) {
                results.add(entryName);
            }
        }

        return results;
    }

    /**
     * Scans a JAR file for all entries matching a path prefix and filter,
     * including entries in subdirectories.
     *
     * @param jar        the JAR file to scan
     * @param pathPrefix the path prefix to match (should end with '/')
     * @param filter     additional filter for entries
     * @return list of matching entry names
     */
    @NotNull
    public static List<String> scanJarRecursive(@NotNull JarFile jar,
                                                @NotNull String pathPrefix,
                                                @NotNull Predicate<JarEntry> filter) {
        List<String> results = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entry.isDirectory()) {
                continue;
            }

            if (!entryName.startsWith(pathPrefix)) {
                continue;
            }

            if (filter.test(entry)) {
                results.add(entryName);
            }
        }

        return results;
    }

    /**
     * Scans a directory for files matching a filter (non-recursive).
     *
     * @param directory the directory to scan
     * @param filter    filter for files
     * @return list of matching file paths
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    public static List<Path> scanDirectory(@NotNull Path directory,
                                           @NotNull Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(filter)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Scans a directory recursively for files matching a filter.
     *
     * @param directory the directory to scan
     * @param filter    filter for files
     * @return list of matching file paths
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    public static List<Path> scanDirectoryRecursive(@NotNull Path directory,
                                                    @NotNull Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(filter)
                    .collect(Collectors.toList());
        }
    }
}
