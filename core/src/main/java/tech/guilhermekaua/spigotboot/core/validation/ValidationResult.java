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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Result of validating an object.
 */
public final class ValidationResult {

    private static final ValidationResult VALID = new ValidationResult(Collections.emptyList());

    private final List<ValidationError> errors;

    private ValidationResult(@NotNull List<ValidationError> errors) {
        this.errors = errors;
    }

    /**
     * Creates a valid result with no errors.
     *
     * @return a valid result
     */
    public static @NotNull ValidationResult valid() {
        return VALID;
    }

    /**
     * Creates a result with the given errors.
     *
     * @param errors the errors
     * @return a result with errors
     */
    public static @NotNull ValidationResult of(@NotNull List<ValidationError> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            return VALID;
        }
        return new ValidationResult(new ArrayList<>(errors));
    }

    /**
     * Creates a result with a single error.
     *
     * @param error the error
     * @return a result with the error
     */
    public static @NotNull ValidationResult of(@NotNull ValidationError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new ValidationResult(Collections.singletonList(error));
    }

    /**
     * Checks if the validation passed (no errors).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Checks if there are any errors.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Gets all validation errors.
     *
     * @return the list of errors
     */
    public @NotNull List<ValidationError> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Gets only errors marked as fail-fast.
     *
     * @return the list of fail-fast errors
     */
    public @NotNull List<ValidationError> getFailFastErrors() {
        return errors.stream()
                .filter(ValidationError::isFailFast)
                .collect(Collectors.toList());
    }

    /**
     * Formats all errors for display.
     *
     * @return the formatted errors
     */
    public @NotNull String formatErrors() {
        return formatErrors("");
    }

    /**
     * Formats all errors with a prefix.
     *
     * @param prefix the prefix for each line
     * @return the formatted errors
     */
    public @NotNull String formatErrors(@NotNull String prefix) {
        if (errors.isEmpty()) {
            return prefix + "No errors";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(prefix).append(errors.get(i).format());
        }
        return sb.toString();
    }

    /**
     * Merges this result with another.
     *
     * @param other the other result
     * @return a merged result
     */
    public @NotNull ValidationResult merge(@NotNull ValidationResult other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (this.errors.isEmpty()) {
            return other;
        }
        if (other.errors.isEmpty()) {
            return this;
        }
        List<ValidationError> merged = new ArrayList<>(this.errors);
        merged.addAll(other.errors);
        return new ValidationResult(merged);
    }

    @Override
    public String toString() {
        if (isValid()) {
            return "ValidationResult[valid]";
        }
        return "ValidationResult[" + errors.size() + " errors]";
    }
}
