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
package tech.guilhermekaua.spigotboot.config.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a configuration source (file, resource, or other stream-based source).
 */
public interface ConfigSource {

    /**
     * Opens an input stream to read the configuration.
     *
     * @return the input stream
     * @throws IOException if the stream cannot be opened
     */
    @NotNull
    InputStream openRead() throws IOException;

    /**
     * Opens an output stream to write the configuration.
     *
     * @return the output stream
     * @throws IOException if the stream cannot be opened
     */
    @NotNull
    OutputStream openWrite() throws IOException;

    /**
     * Checks if this source exists.
     *
     * @return true if the source exists and can be read
     */
    boolean exists();

    /**
     * Gets the name of this source (for display/logging purposes).
     *
     * @return the source name
     */
    @NotNull
    String name();

    /**
     * Gets the file path for file-based sources.
     *
     * @return the path, or null if not file-based
     */
    @Nullable
    Path path();

    /**
     * Creates a file-based configuration source.
     *
     * @param path the file path
     * @return a new ConfigSource
     */
    @NotNull
    static ConfigSource file(@NotNull Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        return new FileConfigSource(path);
    }

    /**
     * Creates a file-based configuration source.
     *
     * @param file the file
     * @return a new ConfigSource
     */
    @NotNull
    static ConfigSource file(@NotNull File file) {
        Objects.requireNonNull(file, "file cannot be null");
        return new FileConfigSource(file.toPath());
    }

    /**
     * Creates a classpath resource configuration source.
     *
     * @param loader the class loader to use
     * @param path   the resource path
     * @return a new ConfigSource
     */
    @NotNull
    static ConfigSource resource(@NotNull ClassLoader loader, @NotNull String path) {
        Objects.requireNonNull(loader, "loader cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        return new ResourceConfigSource(loader, path);
    }

    /**
     * File-based configuration source implementation.
     */
    class FileConfigSource implements ConfigSource {
        private final Path filePath;

        FileConfigSource(@NotNull Path filePath) {
            this.filePath = filePath;
        }

        @Override
        @NotNull
        public InputStream openRead() throws IOException {
            return Files.newInputStream(filePath);
        }

        @Override
        @NotNull
        public OutputStream openWrite() throws IOException {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            return Files.newOutputStream(filePath);
        }

        @Override
        public boolean exists() {
            return Files.exists(filePath);
        }

        @Override
        @NotNull
        public String name() {
            return filePath.getFileName().toString();
        }

        @Override
        @NotNull
        public Path path() {
            return filePath;
        }
    }

    /**
     * Classpath resource configuration source implementation.
     */
    class ResourceConfigSource implements ConfigSource {
        private final ClassLoader loader;
        private final String resourcePath;

        ResourceConfigSource(@NotNull ClassLoader loader, @NotNull String resourcePath) {
            this.loader = loader;
            this.resourcePath = resourcePath;
        }

        @Override
        @NotNull
        public InputStream openRead() throws IOException {
            InputStream stream = loader.getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return stream;
        }

        @Override
        @NotNull
        public OutputStream openWrite() throws IOException {
            throw new IOException("Cannot write to classpath resource: " + resourcePath);
        }

        @Override
        public boolean exists() {
            return loader.getResource(resourcePath) != null;
        }

        @Override
        @NotNull
        public String name() {
            int lastSlash = resourcePath.lastIndexOf('/');
            return lastSlash >= 0 ? resourcePath.substring(lastSlash + 1) : resourcePath;
        }

        @Override
        @Nullable
        public Path path() {
            return null;
        }
    }
}
