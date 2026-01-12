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
package tech.guilhermekaua.spigotboot.config.reference.context;

import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Context object for config reference not found errors.
 * <p>
 * Provides information about where the reference was used and what
 * alternatives are available.
 */
@ToString
public final class ConfigReferenceNotFoundContext {

    private final ReferenceKey sourceKey;
    private final @Nullable Field sourceField;
    private final String fullReference;
    private final Set<String> availableAlternatives;

    /**
     * Creates a new context.
     *
     * @param sourceKey             the key of the config/item containing the reference
     * @param sourceField           the field being bound (may be null for raw resolution)
     * @param fullReference         the full reference string (e.g., "${items:custom_item}")
     * @param availableAlternatives suggested alternatives (config/collection/item names)
     */
    public ConfigReferenceNotFoundContext(
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @NotNull String fullReference,
            @NotNull Set<String> availableAlternatives) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey cannot be null");
        this.sourceField = sourceField;
        this.fullReference = Objects.requireNonNull(fullReference, "fullReference cannot be null");
        this.availableAlternatives = Collections.unmodifiableSet(
                Objects.requireNonNull(availableAlternatives, "availableAlternatives cannot be null"));
    }

    /**
     * Gets the key of the config or collection item containing the reference.
     *
     * @return the source key
     */
    public @NotNull ReferenceKey getSourceKey() {
        return sourceKey;
    }

    /**
     * Gets the field being bound, if available.
     *
     * @return the field, or null if not binding to a specific field
     */
    public @Nullable Field getSourceField() {
        return sourceField;
    }

    /**
     * Gets the full reference string.
     *
     * @return the full reference (e.g., "${items:custom_item}")
     */
    public @NotNull String getFullReference() {
        return fullReference;
    }

    /**
     * Gets suggested alternatives.
     * <p>
     * May contain available config names, collection names, or item IDs
     * depending on what part of the reference was not found.
     *
     * @return set of suggested alternatives
     */
    public @NotNull Set<String> getAvailableAlternatives() {
        return availableAlternatives;
    }
}
