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
package tech.guilhermekaua.spigotboot.config.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;

/**
 * <p>
 * This interface work like a handler for errors that occur during config reference resolution
 * and allows customization of how reference resolution errors are handled.
 * <p>
 * Users can provide their own implementation to customize error handling
 * behavior (e.g. throwing exceptions for all errors, logging to a different
 * system, etc...).
 *
 * @see DefaultConfigReferenceErrorHandler
 */
public interface ConfigReferenceErrorHandler {

    /**
     * Called when a reference cannot be resolved because the target
     * config, folder config, item, or path was not found.
     *
     * @param context context information about the error
     * @return a fallback value to use (typically null), or throw an exception
     */
    @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context);

    /**
     * Called when a circular reference is detected during resolution.
     * <p>
     * This method should typically throw an exception as circular
     * references cannot be resolved. Returning normally is allowed
     * but may lead to incomplete resolution.
     *
     * @param context context information about the cycle
     */
    void onCircularReference(@NotNull ConfigCircularReferenceContext context);

    /**
     * Called when the resolved value's type doesn't match the expected
     * type of the target field.
     * <p>
     * Note: The binder may still attempt type coercion after this method
     * returns. This is called when the raw types are incompatible.
     *
     * @param context context information about the type mismatch
     * @return a fallback value to use (typically null), or throw an exception
     */
    @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context);
}
