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
package tech.guilhermekaua.spigotboot.config.reference;

import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;

/**
 * Identifies the kind of target a config reference points to.
 */
public enum ReferenceTargetKind {

    /**
     * References a single {@link Config} annotated configuration.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code ${configName}} - entire config root</li>
     *   <li>{@code ${configName:path.to.value}} - specific path within config</li>
     * </ul>
     */
    SINGLE_CONFIG,

    /**
     * References an item within a {@link FolderConfig} folder-based configuration.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code ${folderConfigName.itemId}} - entire item root</li>
     *   <li>{@code ${folderConfigName.itemId:path.to.value}} - specific path within item</li>
     * </ul>
     */
    FOLDER_CONFIG_ITEM
}
