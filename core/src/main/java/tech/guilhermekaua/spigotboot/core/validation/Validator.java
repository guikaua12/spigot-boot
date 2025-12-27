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
import tech.guilhermekaua.spigotboot.core.exceptions.ValidationException;

/**
 * Validates objects based on their annotations.
 */
public interface Validator {

    /**
     * Validates an object and returns all errors.
     *
     * @param object the object to validate
     * @return the validation result
     */
    @NotNull ValidationResult validate(@NotNull Object object);

    /**
     * Validates an object at a specific path context.
     *
     * @param object   the object to validate
     * @param basePath the base path for error reporting
     * @return the validation result
     */
    @NotNull ValidationResult validate(@NotNull Object object, @NotNull PropertyPath basePath);

    /**
     * Validates and throws if invalid (for fail-fast mode).
     *
     * @param object the object to validate
     * @throws ValidationException if validation fails
     */
    void validateOrThrow(@NotNull Object object) throws ValidationException;

    /**
     * Creates a default validator.
     *
     * @return a new validator
     */
    static @NotNull Validator create() {
        return new DefaultValidator();
    }
}
