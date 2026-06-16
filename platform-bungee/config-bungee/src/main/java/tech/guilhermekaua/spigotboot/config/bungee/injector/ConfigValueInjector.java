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
package tech.guilhermekaua.spigotboot.config.bungee.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.util.Objects;

/**
 * Custom injector that wires {@link ConfigValue} onto fields and constructor parameters of
 * DI-managed beans.
 * <p>
 * {@link #supports(InjectionPoint)} matches strictly on the presence of {@link ConfigValue} so it
 * never hijacks unrelated injection points. {@link #resolve(InjectionPoint)} always returns a
 * {@linkplain InjectionResult#handled(Object) handled} result (or throws) — it never returns
 * {@linkplain InjectionResult#notHandled() not-handled}, because that would let the framework fall
 * back to by-type bean resolution and silently mis-inject or null the field.
 */
public class ConfigValueInjector implements CustomInjector {

    private final ConfigValueResolver resolver;

    /**
     * Creates the injector.
     *
     * @param configManager the config manager, not null
     */
    public ConfigValueInjector(@NotNull BungeeConfigManager configManager) {
        Objects.requireNonNull(configManager, "configManager cannot be null");
        this.resolver = new ConfigValueResolver(configManager);
    }

    @Override
    public boolean supports(@NotNull InjectionPoint injectionPoint) {
        Objects.requireNonNull(injectionPoint, "injectionPoint cannot be null");
        return injectionPoint.getAnnotatedElement().isAnnotationPresent(ConfigValue.class);
    }

    @Override
    public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
        Objects.requireNonNull(injectionPoint, "injectionPoint cannot be null");
        return InjectionResult.handled(resolver.resolve(injectionPoint));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
