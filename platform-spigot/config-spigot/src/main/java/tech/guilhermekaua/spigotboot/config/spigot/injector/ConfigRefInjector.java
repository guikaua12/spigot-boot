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
package tech.guilhermekaua.spigotboot.config.spigot.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public class ConfigRefInjector implements CustomInjector {

    private final SpigotConfigManager configManager;

    public ConfigRefInjector(@NotNull SpigotConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    @Override
    public boolean supports(@NotNull InjectionPoint injectionPoint) {
        return injectionPoint.getRawType() == ConfigRef.class;
    }

    @Override
    public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
        Type type = injectionPoint.getType();
        Class<?> configClass = extractConfigType(type);
        if (configClass == null) {
            return InjectionResult.notHandled();
        }

        try {
            ConfigRef<?> ref = configManager.getRef(configClass);
            return InjectionResult.handled(ref);
        } catch (Exception e) {
            return InjectionResult.notHandled();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Class<?> extractConfigType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType paramType = (ParameterizedType) type;
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length == 0) {
            return null;
        }

        Type configType = typeArgs[0];
        if (configType instanceof Class) {
            return (Class<?>) configType;
        }

        return null;
    }
}
