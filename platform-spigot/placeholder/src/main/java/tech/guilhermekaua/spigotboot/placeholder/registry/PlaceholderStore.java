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
package tech.guilhermekaua.spigotboot.placeholder.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.placeholder.metadata.PlaceholderMetadata;
import tech.guilhermekaua.spigotboot.placeholder.metadata.parser.PlaceholderParameterParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the registered placeholder metadata and resolves it by parameter string.
 * <p>
 * This store owns the placeholder lookup table so that the component producing placeholders
 * ({@link PlaceholderRegistry}) and the component consuming them
 * ({@link tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion}) can both depend on a shared,
 * neutral collaborator instead of on each other. Decoupling the lookup from both sides breaks the
 * registry/expansion dependency cycle while keeping a single source of truth for placeholder data.
 */
@Component
public class PlaceholderStore {
    private final Map<String, PlaceholderMetadata> placeholders = new HashMap<>();

    /**
     * Registers placeholder metadata, keyed by its placeholder pattern.
     * <p>
     * Re-registering the same pattern overwrites the previous metadata.
     *
     * @param metadata the placeholder metadata to store, not null
     */
    public void register(@NotNull PlaceholderMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null.");
        placeholders.put(metadata.getPlaceholder(), metadata);
    }

    /**
     * Finds the placeholder metadata matching the given parameter string.
     * <p>
     * An exact key match takes precedence: if a placeholder is registered under exactly {@code params}
     * it is returned directly. Otherwise the first placeholder whose parameter syntax accepts the supplied
     * value is returned (see {@link PlaceholderParameterParser#isValidPlaceholderPattern(String, String)}).
     * Resolving exact matches first keeps lookup deterministic regardless of map iteration order.
     *
     * @param params the placeholder parameter string requested, not null
     * @return the matching metadata, or {@code null} if none matches
     */
    public @Nullable PlaceholderMetadata findPlaceholderMetadata(@NotNull String params) {
        Objects.requireNonNull(params, "params cannot be null.");

        final PlaceholderMetadata exactMatch = placeholders.get(params);
        if (exactMatch != null) {
            return exactMatch;
        }

        return placeholders.values().stream()
                .filter(metadata -> PlaceholderParameterParser.isValidPlaceholderPattern(metadata.getPlaceholder(), params))
                .findFirst()
                .orElse(null);
    }

    /**
     * Removes all registered placeholders.
     */
    public void clear() {
        placeholders.clear();
    }
}
