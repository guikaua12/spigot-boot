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
package tech.guilhermekaua.spigotboot.config.binding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * Preprocessor for config nodes before binding.
 * <p>
 * This interface allows transformation of config nodes before they are
 * deserialized by the binder. The primary use case is resolving config
 * references ({@code ${...}}) to their target values.
 * <p>
 * If a preprocessor returns a non-null node, that node will be used for
 * deserialization instead of the original. If it returns null, the original
 * node is used.
 */
public interface ConfigNodePreprocessor {

    /**
     * Preprocesses a config node before binding.
     * <p>
     * This method is called for each node before deserialization. If the
     * preprocessor handles the node (e.g., resolves a reference), it should
     * return the resolved node. Otherwise, return null to use the original.
     *
     * @param node         the original config node
     * @param field        the field being bound (may be null for constructor params or root binding)
     * @param expectedType the expected type of the value
     * @return the preprocessed node, or null to use the original
     */
    @Nullable ConfigNode preprocess(@NotNull ConfigNode node, @Nullable Field field, @NotNull Type expectedType);
}
