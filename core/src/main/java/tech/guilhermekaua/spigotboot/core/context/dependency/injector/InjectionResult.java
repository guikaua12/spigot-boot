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
package tech.guilhermekaua.spigotboot.core.context.dependency.injector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of a custom injector's resolution attempt.
 * <p>
 * This class provides a tri-state result to distinguish between:
 * <ul>
 *   <li>{@link #notHandled()} - The injector did not handle this injection point</li>
 *   <li>{@link #handled(Object)} - The injector handled the injection and provided a value (which may be null)</li>
 * </ul>
 * This distinction is important because returning null from a custom injector might be intentional,
 * and the injection should not fall back to the default resolution in that case.
 */
public final class InjectionResult {
    private static final InjectionResult NOT_HANDLED = new InjectionResult(false, null);

    private final boolean handled;
    private final @Nullable Object value;

    private InjectionResult(boolean handled, @Nullable Object value) {
        this.handled = handled;
        this.value = value;
    }

    /**
     * Creates a result indicating that the injector did not handle this injection point.
     * <p>
     * When returned, the framework will continue to the next injector or fall back to default resolution.
     *
     * @return a result indicating the injection point was not handled
     */
    public static @NotNull InjectionResult notHandled() {
        return NOT_HANDLED;
    }

    /**
     * Creates a result indicating that the injector handled this injection point.
     * <p>
     * The provided value will be used for injection. Note that null is a valid value here;
     * returning {@code handled(null)} is different from returning {@code notHandled()}.
     *
     * @param value the resolved value to inject, may be null
     * @return a result indicating the injection point was handled with the given value
     */
    public static @NotNull InjectionResult handled(@Nullable Object value) {
        return new InjectionResult(true, value);
    }

    /**
     * Returns whether this injection point was handled by the custom injector.
     *
     * @return true if handled, false if the framework should try other injectors or default resolution
     */
    public boolean isHandled() {
        return handled;
    }

    /**
     * Returns the resolved value if this result was handled.
     * <p>
     * This method should only be called after verifying {@link #isHandled()} returns true.
     *
     * @return the resolved value, may be null even when handled
     * @throws IllegalStateException if called on a not-handled result
     */
    public @Nullable Object getValue() {
        if (!handled) {
            throw new IllegalStateException("Cannot get value from a not-handled result");
        }
        return value;
    }
}
