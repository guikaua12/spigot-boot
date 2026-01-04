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
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;

/**
 * Loads and saves configuration from/to sources.
 * <p>
 * Implementations handle specific file formats (YAML, JSON, etc.).
 */
public interface ConfigLoader {

    /**
     * Loads configuration from the given source.
     *
     * @param source the source to load from
     * @return the root configuration node
     * @throws ConfigException if loading fails
     */
    @NotNull ConfigNode load(@NotNull ConfigSource source) throws ConfigException;

    /**
     * Saves configuration to the given target.
     *
     * @param node   the root node to save
     * @param target the target to save to
     * @throws ConfigException if saving fails
     */
    void save(@NotNull ConfigNode node, @NotNull ConfigSource target) throws ConfigException;

    /**
     * Creates an empty mutable root node for this loader.
     *
     * @return a new empty node
     */
    @NotNull MutableConfigNode createNode();

    /**
     * Gets the file extensions this loader handles.
     *
     * @return array of extensions (e.g., "yml", "yaml")
     */
    @NotNull String[] extensions();
}
