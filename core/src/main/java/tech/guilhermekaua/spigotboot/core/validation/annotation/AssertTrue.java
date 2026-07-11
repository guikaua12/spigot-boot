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
package tech.guilhermekaua.spigotboot.core.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a no-arg method returns {@code true}.
 * <p>
 * Place on a zero-parameter method returning {@code boolean} or {@link Boolean}.
 * The method is invoked during validation; a {@code true} return passes, a
 * {@code false} return fails. A {@code null} {@link Boolean} return is treated
 * as valid (pair with field constraints if absence must fail).
 * <p>
 * Typical use is cross-field rules:
 * <pre>{@code
 * @AssertTrue(message = "max must be >= min", path = "max")
 * private boolean isMaxGteMin() {
 *     return max >= min;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssertTrue {

    /**
     * The error message.
     *
     * @return the message
     */
    String message() default "Expression must be true";

    /**
     * Relative property path for the validation error (e.g. {@code "max"} or
     * {@code "database.host"}). Empty uses the method name as the path segment.
     *
     * @return the relative path, or empty for the method name
     */
    String path() default "";

    /**
     * Whether validation failure should fail fast (prevent plugin enable).
     *
     * @return true to fail fast
     */
    boolean failFast() default true;

    /**
     * An optional suggestion describing how to fix the error, shown alongside the
     * message. When empty, the built-in default suggestion is used (or none, for
     * constraints without a default). Used verbatim; no placeholder substitution.
     *
     * @return the suggestion, or empty for the built-in default
     */
    String suggestion() default "";
}
