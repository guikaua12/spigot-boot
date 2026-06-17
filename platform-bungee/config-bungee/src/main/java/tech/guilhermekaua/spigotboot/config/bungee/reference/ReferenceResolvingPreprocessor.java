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
package tech.guilhermekaua.spigotboot.config.bungee.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.binding.ConfigNodePreprocessor;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.bungee.node.YamlConfigNode;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A {@link ConfigNodePreprocessor} that resolves config references ({@code ${...}}).
 * <p>
 * This preprocessor wraps a {@link ConfigReferenceResolver} and adapts it to the
 * binder's preprocessor API. It is used during config binding to resolve any
 * reference values before they are deserialized.
 * <p>
 * The preprocessor maintains a "current source key" which identifies the config
 * or folder config item currently being bound. This is used for cycle detection
 * and error reporting.
 */
public class ReferenceResolvingPreprocessor implements ConfigNodePreprocessor {

    private final ConfigReferenceResolver resolver;
    private final ThreadLocal<ReferenceKey> currentSourceKey = new ThreadLocal<>();

    /**
     * Creates a new preprocessor.
     *
     * @param resolver the reference resolver
     */
    public ReferenceResolvingPreprocessor(@NotNull ConfigReferenceResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
    }

    /**
     * Sets the current source key for binding context.
     * <p>
     * This should be called before binding each config or folder config item
     * to provide context for error reporting and cycle detection.
     *
     * @param sourceKey the key of the config being bound
     */
    public void setCurrentSourceKey(@NotNull ReferenceKey sourceKey) {
        currentSourceKey.set(Objects.requireNonNull(sourceKey, "sourceKey cannot be null"));
    }

    /**
     * Clears the current source key.
     */
    public void clearCurrentSourceKey() {
        currentSourceKey.remove();
    }

    /**
     * Gets the current source key.
     *
     * @return the current source key, or null if not set
     */
    public @Nullable ReferenceKey getCurrentSourceKey() {
        return currentSourceKey.get();
    }

    @Override
    public @Nullable ConfigNode preprocess(
            @NotNull ConfigNode node,
            @Nullable Field field,
            @NotNull Type expectedType) {

        if (!node.isScalar()) {
            return null;
        }

        String value = node.get(String.class);
        if (value == null) {
            return null;
        }

        if (!resolver.isReference(value)) {
            return null;
        }

        ReferenceKey sourceKey = currentSourceKey.get();
        if (sourceKey == null) {
            // shouldn't happen in normal usage
            sourceKey = ReferenceKey.singleConfig("unknown");
        }

        ConfigNode resolved = resolver.resolveIfReference(node, sourceKey, field, expectedType);

        // if resolver returns null, it means the reference was not found
        // we must return a "null node" (not java null) so the binder binds to null.
        // returning java null from preprocess() means "use original node" which would
        // leave the "${...}" string in place, that is not what we want for missing references
        if (resolved == null) {
            return createNullNode();
        }

        return resolved;
    }

    private static @NotNull ConfigNode createNullNode() {
        return new YamlConfigNode(null);
    }
}
