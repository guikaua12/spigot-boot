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
package tech.guilhermekaua.spigotboot.config.collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of an edit operation on a config collection.
 * <p>
 * Contains the success status, the resulting value (if any),
 * and any validation errors that occurred.
 *
 * @param <T> the value type
 */
public final class EditResult<T> {

    private final boolean success;
    private final @Nullable T value;
    private final @Nullable String errorMessage;
    private final List<String> validationErrors;

    private EditResult(boolean success, @Nullable T value, @Nullable String errorMessage, List<String> validationErrors) {
        this.success = success;
        this.value = value;
        this.errorMessage = errorMessage;
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
    }

    /**
     * Creates a successful result with a value.
     *
     * @param value the result value
     * @param <T>   the value type
     * @return the success result
     */
    public static <T> EditResult<T> success(@Nullable T value) {
        return new EditResult<>(true, value, null, Collections.emptyList());
    }

    /**
     * Creates a successful result without a value.
     *
     * @param <T> the value type
     * @return the success result
     */
    public static <T> EditResult<T> success() {
        return new EditResult<>(true, null, null, Collections.emptyList());
    }

    /**
     * Creates a failure result with an error message.
     *
     * @param message the error message
     * @param <T>     the value type
     * @return the failure result
     */
    public static <T> EditResult<T> failure(@NotNull String message) {
        Objects.requireNonNull(message, "message cannot be null");
        return new EditResult<>(false, null, message, Collections.emptyList());
    }

    /**
     * Creates a failure result with validation errors.
     *
     * @param message          the main error message
     * @param validationErrors the list of validation errors
     * @param <T>              the value type
     * @return the failure result
     */
    public static <T> EditResult<T> validationFailure(@NotNull String message, @NotNull List<String> validationErrors) {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(validationErrors, "validationErrors cannot be null");
        return new EditResult<>(false, null, message, validationErrors);
    }

    /**
     * Returns true if the operation was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns true if the operation failed.
     *
     * @return true if failed
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Gets the result value.
     *
     * @return the value, may be null even for successful operations
     */
    public @Nullable T getValue() {
        return value;
    }

    /**
     * Gets the result value, throwing if the operation failed.
     *
     * @return the value
     * @throws IllegalStateException if the operation failed
     */
    public @NotNull T getOrThrow() {
        if (!success) {
            throw new IllegalStateException("Operation failed: " + errorMessage);
        }
        if (value == null) {
            throw new IllegalStateException("Operation succeeded but value is null");
        }
        return value;
    }

    /**
     * Gets the error message if the operation failed.
     *
     * @return the error message, or null if successful
     */
    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the list of validation errors.
     *
     * @return the validation errors, empty if none
     */
    public @NotNull List<String> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Returns true if there are validation errors.
     *
     * @return true if there are validation errors
     */
    public boolean hasValidationErrors() {
        return !validationErrors.isEmpty();
    }

    @Override
    public String toString() {
        if (success) {
            return "EditResult{success=true, value=" + value + "}";
        } else {
            return "EditResult{success=false, error='" + errorMessage + "', validationErrors=" + validationErrors + "}";
        }
    }
}
