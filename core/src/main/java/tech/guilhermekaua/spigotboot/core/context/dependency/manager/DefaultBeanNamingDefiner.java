/*
 * The MIT License
 * Copyright 陡 2025 Guilherme Kau菧 da Silva
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
package tech.guilhermekaua.spigotboot.core.context.dependency.manager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.beans.Introspector;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultBeanNamingDefiner implements BeanNamingDefiner {
    private final AtomicInteger generatedQualifierCounter = new AtomicInteger(0);

    @Override
    public @NotNull String defineQualifier(@NotNull Class<?> dependencyClass,
                                           @Nullable Object instance,
                                           @Nullable DependencyResolveResolver<?> resolver,
                                           @Nullable String qualifier) {
        Objects.requireNonNull(dependencyClass, "dependencyClass cannot be null.");

        String normalizedQualifier = normalizeQualifier(qualifier);
        if (normalizedQualifier != null) {
            return normalizedQualifier;
        }

        Class<?> namingClass = dependencyClass;
        if (instance != null) {
            namingClass = ProxyUtils.getRealClass(instance);
        }

        String baseName = Introspector.decapitalize(getSimpleNameOrFallback(namingClass));
        if (baseName.isEmpty()) {
            baseName = "bean";
        }

        if (!dependencyClass.isInterface()) {
            return baseName;
        }

        if (resolver == null) {
            return baseName;
        }

        return baseName + "#" + generatedQualifierCounter.incrementAndGet();
    }

    private @Nullable String normalizeQualifier(@Nullable String qualifier) {
        if (qualifier == null) {
            return null;
        }

        String normalized = qualifier.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private @NotNull String getSimpleNameOrFallback(@NotNull Class<?> type) {
        String simpleName = type.getSimpleName();
        if (simpleName != null && !simpleName.isEmpty()) {
            return simpleName;
        }

        String name = type.getName();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            return name.substring(lastDotIndex + 1);
        }

        return name;
    }
}

