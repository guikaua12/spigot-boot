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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClassPathScanner {
    private static final Logger LOGGER = Logger.getLogger(ClassPathScanner.class.getName());
    private static final String CLASS_EXTENSION = ".class";

    private final ClassLoader classLoader;
    private final Set<Class<?>> scannedClasses;
    private final Set<String> scannedPackages;

    /**
     * Creates a new ClassPathScanner with the specified class loader.
     *
     * @param classLoader the class loader to use for loading classes
     * @param packages    the packages to scan (can be empty for lazy scanning)
     */
    public ClassPathScanner(@NotNull ClassLoader classLoader, @NotNull String... packages) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
        this.scannedClasses = new HashSet<>();
        this.scannedPackages = new HashSet<>();

        if (packages.length > 0) {
            addPackages(packages);
        }
    }

    /**
     * Creates a scanner using the context class loader.
     *
     * @param packages the packages to scan
     * @return a new ClassPathScanner instance
     */
    public static ClassPathScanner create(@NotNull String... packages) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClassPathScanner.class.getClassLoader();
        }
        return new ClassPathScanner(classLoader, packages);
    }

    /**
     * Adds additional packages to scan. Classes from these packages will be
     * added to the existing scanned classes.
     *
     * @param packages the packages to add
     */
    public void addPackages(@NotNull String... packages) {
        for (String pkg : packages) {
            if (pkg == null || pkg.trim().isEmpty()) {
                continue;
            }

            String normalizedPackage = pkg.trim();
            if (scannedPackages.contains(normalizedPackage)) {
                continue;
            }

            scannedPackages.add(normalizedPackage);
            scanPackage(normalizedPackage);
        }
    }

    /**
     * Gets all classes annotated with the specified annotation.
     * This includes classes with meta-annotations (annotations on annotations).
     *
     * @param annotation           the annotation class to search for
     * @param checkMetaAnnotations whether to check for meta-annotations
     * @return a set of classes annotated with the annotation
     */
    public Set<Class<?>> getTypesAnnotatedWith(@NotNull Class<? extends Annotation> annotation, boolean checkMetaAnnotations) {
        Objects.requireNonNull(annotation, "annotation cannot be null");

        return scannedClasses.stream()
                .filter(clazz -> hasAnnotation(clazz, annotation, checkMetaAnnotations))
                .collect(Collectors.toSet());
    }

    /**
     * Gets all classes annotated with the specified annotation.
     * This includes classes with meta-annotations (annotations on annotations).
     *
     * @param annotation the annotation class to search for
     * @return a set of classes annotated with the annotation
     */
    public Set<Class<?>> getTypesAnnotatedWith(@NotNull Class<? extends Annotation> annotation) {
        return getTypesAnnotatedWith(annotation, true);
    }

    /**
     * Gets all subtypes (subclasses and implementations) of the specified type.
     *
     * @param superType the super type to search for
     * @param <T>       the type parameter
     * @return a set of classes that extend or implement the super type
     */
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> getSubTypesOf(@NotNull Class<T> superType) {
        Objects.requireNonNull(superType, "superType cannot be null");

        return scannedClasses.stream()
                .filter(clazz -> clazz != superType)
                .filter(superType::isAssignableFrom)
                .map(clazz -> (Class<? extends T>) clazz)
                .collect(Collectors.toSet());
    }

    /**
     * Gets all scanned classes.
     *
     * @return an unmodifiable set of all scanned classes
     */
    public Set<Class<?>> getAllClasses() {
        return Collections.unmodifiableSet(scannedClasses);
    }

    /**
     * Gets the packages that have been scanned.
     *
     * @return an unmodifiable set of scanned package names
     */
    public Set<String> getScannedPackages() {
        return Collections.unmodifiableSet(scannedPackages);
    }

    /**
     * Clears all cached scanning results.
     */
    public void clear() {
        scannedClasses.clear();
        scannedPackages.clear();
    }

    private void scanPackage(@NotNull String packageName) {
        String packagePath = packageName.replace('.', '/');

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                scanResource(resource, packagePath, packageName);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan package: " + packageName, e);
        }
    }

    private void scanResource(@NotNull URL resource, @NotNull String packagePath, @NotNull String packageName) {
        String protocol = resource.getProtocol();

        if ("jar".equals(protocol)) {
            scanJar(resource, packagePath);
        } else if ("file".equals(protocol)) {
            scanDirectory(resource, packageName);
        }
    }

    private void scanJar(@NotNull URL resource, @NotNull String packagePath) {
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            // prevent file locking issues on Windows
            jarConnection.setUseCaches(false);

            try (JarFile jarFile = jarConnection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (isValidClassEntry(entryName, packagePath)) {
                        String className = entryNameToClassName(entryName);
                        loadAndAddClass(className);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan JAR: " + resource, e);
        }
    }

    private void scanDirectory(@NotNull URL resource, @NotNull String packageName) {
        try {
            Path basePath = Paths.get(resource.toURI());
            List<Path> classFiles = ResourceScanUtils.scanDirectoryRecursive(basePath,
                    path -> ResourceScanUtils.hasExtension(path.toString(), "class"));

            for (Path path : classFiles) {
                String className = pathToClassName(basePath, path, packageName);
                loadAndAddClass(className);
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan directory: " + resource, e);
        }
    }

    private boolean isValidClassEntry(@NotNull String entryName, @NotNull String packagePath) {
        if (!entryName.endsWith(CLASS_EXTENSION)) {
            return false;
        }

        if (!entryName.startsWith(packagePath + "/")) {
            return false;
        }

        String simpleName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if ("module-info.class".equals(simpleName)) {
            return false;
        }

        if (entryName.startsWith("META-INF/")) {
            return false;
        }

        return true;
    }

    private String entryNameToClassName(@NotNull String entryName) {
        return entryName
                .substring(0, entryName.length() - CLASS_EXTENSION.length())
                .replace('/', '.');
    }

    private String pathToClassName(@NotNull Path basePath, @NotNull Path classPath, @NotNull String packageName) {
        Path relativePath = basePath.relativize(classPath);
        String relativePathStr = relativePath.toString()
                .replace(File.separatorChar, '.');

        String className = relativePathStr.substring(0, relativePathStr.length() - CLASS_EXTENSION.length());

        return packageName + "." + className;
    }

    private void loadAndAddClass(@Nullable String className) {
        if (className == null || className.isEmpty()) {
            return;
        }

        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            scannedClasses.add(clazz);
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
    }

    private boolean hasAnnotation(@NotNull Class<?> clazz, @NotNull Class<? extends Annotation> annotation, boolean checkMetaAnnotations) {
        if (clazz.isAnnotationPresent(annotation)) {
            return true;
        }

        if (!checkMetaAnnotations) return false;

        for (Annotation anno : clazz.getAnnotations()) {
            if (anno.annotationType().isAnnotationPresent(annotation)) {
                return true;
            }
        }

        return false;
    }
}
