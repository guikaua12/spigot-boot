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
package tech.guilhermekaua.spigotboot.core.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a validation error for a field.
 */
public final class ValidationError {

    private final PropertyPath path;
    private final String fieldName;
    private final Object invalidValue;
    private final String message;
    private final boolean failFast;
    private final String suggestedFix;

    /**
     * Creates a new validation error.
     *
     * @param path         the property path to the invalid value
     * @param fieldName    the field name
     * @param invalidValue the invalid value
     * @param message      the error message
     * @param failFast     whether this error should prevent plugin loading
     * @param suggestedFix an optional suggested fix
     */
    public ValidationError(
            @NotNull PropertyPath path,
            @NotNull String fieldName,
            @Nullable Object invalidValue,
            @NotNull String message,
            boolean failFast,
            @Nullable String suggestedFix
    ) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.invalidValue = invalidValue;
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.failFast = failFast;
        this.suggestedFix = suggestedFix;
    }

    /**
     * Gets the property path to the invalid value.
     *
     * @return the property path
     */
    public @NotNull PropertyPath getPath() {
        return path;
    }

    /**
     * Gets the field name.
     *
     * @return the field name
     */
    public @NotNull String getFieldName() {
        return fieldName;
    }

    /**
     * Gets the invalid value.
     *
     * @return the invalid value
     */
    public @Nullable Object getInvalidValue() {
        return invalidValue;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    public @NotNull String getMessage() {
        return message;
    }

    /**
     * Checks if this error should fail fast.
     *
     * @return true if fail fast
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Gets the suggested fix.
     *
     * @return the suggested fix, or null
     */
    public @Nullable String getSuggestedFix() {
        return suggestedFix;
    }

    /**
     * Formats this error for display.
     *
     * @return the formatted error message
     */
    public @NotNull String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(path.asString()).append("] ");
        sb.append(message);
        sb.append(" (was: ").append(invalidValue).append(")");
        if (suggestedFix != null) {
            sb.append(" - Suggestion: ").append(suggestedFix);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
