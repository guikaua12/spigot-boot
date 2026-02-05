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
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Context object for type mismatch errors during reference resolution.
 * <p>
 * Provides information about the expected type, actual type, and the
 * resolved value.
 */
@ToString
public final class ConfigTypeMismatchContext {

    private final ReferenceKey sourceKey;
    private final @Nullable Field sourceField;
    private final String fullReference;
    private final Type expectedType;
    private final Class<?> actualType;
    private final @Nullable Object actualValue;

    /**
     * Creates a new context.
     *
     * @param sourceKey     the key of the config/item containing the reference
     * @param sourceField   the field being bound (may be null for raw resolution)
     * @param fullReference the full reference string
     * @param expectedType  the expected type for the field
     * @param actualType    the actual type of the resolved value
     * @param actualValue   the actual resolved value (may be null)
     */
    public ConfigTypeMismatchContext(
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @NotNull String fullReference,
            @NotNull Type expectedType,
            @NotNull Class<?> actualType,
            @Nullable Object actualValue) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey cannot be null");
        this.sourceField = sourceField;
        this.fullReference = Objects.requireNonNull(fullReference, "fullReference cannot be null");
        this.expectedType = Objects.requireNonNull(expectedType, "expectedType cannot be null");
        this.actualType = Objects.requireNonNull(actualType, "actualType cannot be null");
        this.actualValue = actualValue;
    }

    /**
     * Gets the key of the config or folder config item containing the reference.
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
     * @return the full reference
     */
    public @NotNull String getFullReference() {
        return fullReference;
    }

    /**
     * Gets the expected type for the target field.
     *
     * @return the expected type
     */
    public @NotNull Type getExpectedType() {
        return expectedType;
    }

    /**
     * Gets the actual type of the resolved value.
     *
     * @return the actual type
     */
    public @NotNull Class<?> getActualType() {
        return actualType;
    }

    /**
     * Gets the actual resolved value.
     *
     * @return the actual value, may be null
     */
    public @Nullable Object getActualValue() {
        return actualValue;
    }
}
