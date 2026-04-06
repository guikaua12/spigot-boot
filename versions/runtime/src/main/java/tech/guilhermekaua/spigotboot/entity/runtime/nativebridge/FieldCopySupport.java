/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.runtime.nativebridge;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Copies instance state from one native entity instance into another compatible replacement.
 *
 * @since 2.0.2
 */
public final class FieldCopySupport {

    private FieldCopySupport() {
    }

    public static void copyInstanceFields(
            @NotNull Object source,
            @NotNull Object target,
            @NotNull String... ignoredFieldNames
    ) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(ignoredFieldNames, "ignoredFieldNames cannot be null");
        if (!source.getClass().isAssignableFrom(target.getClass())) {
            throw new IllegalArgumentException(
                    "Target type '" + target.getClass().getName() + "' must extend or equal source type '"
                            + source.getClass().getName() + "'."
            );
        }

        Set<String> ignoredNames = new HashSet<String>(Arrays.asList(ignoredFieldNames));
        Class<?> current = source.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || ignoredNames.contains(field.getName())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    field.set(target, field.get(source));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(
                            "Could not copy field '" + field.getName() + "' from "
                                    + source.getClass().getName() + " to " + target.getClass().getName() + ".",
                            exception
                    );
                }
            }
            current = current.getSuperclass();
        }
    }
}
