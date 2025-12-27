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
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.Objects;

/**
 * Represents an error that occurred during config binding.
 */
public final class BindingError {

    private final PropertyPath path;
    private final String field;
    private final String message;
    private final Throwable cause;

    /**
     * Creates a new binding error.
     *
     * @param path    the config path where the error occurred
     * @param field   the field name
     * @param message the error message
     * @param cause   the underlying cause, or null
     */
    public BindingError(
            @NotNull PropertyPath path,
            @NotNull String field,
            @NotNull String message,
            @Nullable Throwable cause
    ) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.field = Objects.requireNonNull(field, "field cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.cause = cause;
    }

    /**
     * Gets the config path.
     *
     * @return the path
     */
    public @NotNull PropertyPath getPath() {
        return path;
    }

    /**
     * Gets the field name.
     *
     * @return the field name
     */
    public @NotNull String getField() {
        return field;
    }

    /**
     * Gets the error message.
     *
     * @return the message
     */
    public @NotNull String getMessage() {
        return message;
    }

    /**
     * Gets the underlying cause.
     *
     * @return the cause, or null
     */
    @Nullable
    public Throwable getCause() {
        return cause;
    }

    /**
     * Formats this error for display.
     *
     * @return the formatted error
     */
    @NotNull
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(path.asString()).append("] ");
        sb.append(field).append(": ").append(message);
        if (cause != null) {
            sb.append(" (").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
