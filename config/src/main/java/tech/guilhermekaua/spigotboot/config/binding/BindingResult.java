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
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.core.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a binding operation.
 *
 * @param <T> the type of the bound object
 */
public final class BindingResult<T> {

    private final T value;
    private final List<BindingError> errors;
    private final List<ValidationError> validationErrors;

    private BindingResult(@Nullable T value, @NotNull List<BindingError> errors, @NotNull List<ValidationError> validationErrors) {
        this.value = value;
        this.errors = errors;
        this.validationErrors = validationErrors;
    }

    /**
     * Creates a successful result.
     *
     * @param value the bound value
     * @param <T>   the type
     * @return a successful result
     */
    public static <T> @NotNull BindingResult<T> success(@NotNull T value) {
        Objects.requireNonNull(value, "value cannot be null");
        return new BindingResult<>(value, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Creates a result with binding errors.
     *
     * @param errors the binding errors
     * @param <T>    the type
     * @return a result with errors
     */
    public static <T> @NotNull BindingResult<T> failure(@NotNull List<BindingError> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        return new BindingResult<>(null, new ArrayList<>(errors), Collections.emptyList());
    }

    /**
     * Creates a result with a value and validation errors.
     *
     * @param value            the bound value
     * @param validationErrors the validation errors
     * @param <T>              the type
     * @return a result with value and validation errors
     */
    public static <T> @NotNull BindingResult<T> withValidationErrors(@NotNull T value, @NotNull List<ValidationError> validationErrors) {
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(validationErrors, "validationErrors cannot be null");
        return new BindingResult<>(value, Collections.emptyList(), new ArrayList<>(validationErrors));
    }

    /**
     * Creates a result with both binding and validation errors.
     *
     * @param value            the bound value (or null if binding failed)
     * @param errors           the binding errors
     * @param validationErrors the validation errors
     * @param <T>              the type
     * @return a combined result
     */
    public static <T> @NotNull BindingResult<T> of(@Nullable T value, @NotNull List<BindingError> errors, @NotNull List<ValidationError> validationErrors) {
        return new BindingResult<>(value, new ArrayList<>(errors), new ArrayList<>(validationErrors));
    }

    /**
     * Checks if binding was successful (no binding errors).
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return errors.isEmpty() && value != null;
    }

    /**
     * Checks if there are any binding errors.
     *
     * @return true if there are binding errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if there are any validation errors.
     *
     * @return true if there are validation errors
     */
    public boolean hasValidationErrors() {
        return !validationErrors.isEmpty();
    }

    /**
     * Gets the bound value.
     *
     * @return the value
     * @throws ConfigException if binding failed
     */
    public @NotNull T get() throws ConfigException {
        if (hasErrors() || hasValidationErrors()) {
            throw new ConfigException("Binding failed: " + formatErrors());
        }

        if (value == null) {
            throw new ConfigException("Value is null.");
        }

        return value;
    }

    /**
     * Gets the value or a fallback.
     *
     * @param fallback the fallback value
     * @return the value or fallback
     */
    public @Nullable T orElse(@Nullable T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Gets the binding errors.
     *
     * @return the binding errors
     */
    public @NotNull List<BindingError> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Gets the validation errors.
     *
     * @return the validation errors
     */
    public @NotNull List<ValidationError> validationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }

    /**
     * Formats all errors for display.
     *
     * @return the formatted errors
     */
    public @NotNull String formatErrors() {
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty()) {
            sb.append("Binding errors:\n");
            for (BindingError error : errors) {
                sb.append("  ").append(error.format()).append("\n");
            }
        }
        if (!validationErrors.isEmpty()) {
            sb.append("Validation errors:\n");
            for (ValidationError error : validationErrors) {
                sb.append("  ").append(error.format()).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "BindingResult[success=" + value.getClass().getSimpleName() + "]";
        }
        return "BindingResult[errors=" + errors.size() + ", validationErrors=" + validationErrors.size() + "]";
    }
}
