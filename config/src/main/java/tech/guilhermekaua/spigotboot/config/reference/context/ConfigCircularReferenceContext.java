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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Context object for circular reference errors.
 * <p>
 * Provides information about the cycle chain that was detected.
 */
@ToString
public final class ConfigCircularReferenceContext {

    private final ReferenceKey sourceKey;
    private final @Nullable Field sourceField;
    private final String fullReference;
    private final List<ReferenceKey> resolutionChain;

    /**
     * Creates a new context.
     *
     * @param sourceKey       the key of the config/item containing the reference
     * @param sourceField     the field being bound (may be null for raw resolution)
     * @param fullReference   the full reference string that caused the cycle
     * @param resolutionChain the chain of keys showing the cycle path
     */
    public ConfigCircularReferenceContext(
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @NotNull String fullReference,
            @NotNull List<ReferenceKey> resolutionChain) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey cannot be null");
        this.sourceField = sourceField;
        this.fullReference = Objects.requireNonNull(fullReference, "fullReference cannot be null");
        this.resolutionChain = Collections.unmodifiableList(
                Objects.requireNonNull(resolutionChain, "resolutionChain cannot be null"));
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
     * Gets the full reference string that caused the cycle.
     *
     * @return the full reference
     */
    public @NotNull String getFullReference() {
        return fullReference;
    }

    /**
     * Gets the resolution chain showing the cycle.
     * <p>
     * The chain shows the path of references that led to the cycle.
     * The last element typically points back to an earlier element,
     * forming the cycle.
     *
     * @return the resolution chain
     */
    public @NotNull List<ReferenceKey> getResolutionChain() {
        return resolutionChain;
    }

    /**
     * Formats the resolution chain as a readable string.
     *
     * @return formatted chain string (e.g., "A -> B -> C -> A")
     */
    public @NotNull String formatChain() {
        return resolutionChain.stream()
                .map(ReferenceKey::getDisplayName)
                .collect(Collectors.joining(" -> "));
    }
}
