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
package tech.guilhermekaua.spigotboot.config.injector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Objects;

/**
 * Resolves the value for a {@code @ConfigValue}-annotated injection point.
 * <p>
 * Holds all resolution logic so {@link ConfigValueInjector} stays a thin adapter. Detects the
 * addressing mode, builds the lookup path, reads the value (or the default) through
 * {@link DefaultConfigManager}, and fails fast with a {@link ConfigException} naming the injection
 * site on any misconfiguration.
 */
public class ConfigValueResolver {

    private final DefaultConfigManager configManager;

    /**
     * Creates a resolver.
     *
     * @param configManager the config manager, not null
     */
    public ConfigValueResolver(@NotNull DefaultConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    /**
     * Resolves the value to inject for the given {@code @ConfigValue} injection point.
     *
     * @param injectionPoint the injection point, not null
     * @return the resolved value (may be null only if a registered serializer yields null)
     * @throws ConfigException on any misconfiguration or missing required value
     */
    public @Nullable Object resolve(@NotNull InjectionPoint injectionPoint) {
        Objects.requireNonNull(injectionPoint, "injectionPoint cannot be null");

        ConfigValue annotation = injectionPoint.getAnnotatedElement().getAnnotation(ConfigValue.class);
        if (annotation == null) {
            throw new ConfigException("ConfigValueResolver invoked for an element without @ConfigValue");
        }

        String site = describeSite(injectionPoint);

        Class<?> targetType = injectionPoint.getRawType();
        if (targetType == null || targetType.isArray()) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": unsupported target type (type variables, wildcards and arrays are not supported)");
        }

        if (!configManager.isInitialized()) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": the config system is not initialized yet. Beans using @ConfigValue must be " +
                    "constructed at or after ConfigModule (@Order(-500)).");
        }

        String combinedPath = buildPath(annotation, site);

        Object value;
        try {
            value = configManager.deserializeAt(combinedPath, targetType);
        } catch (ConfigException e) {
            throw new ConfigException("@ConfigValue on " + site + ": " + e.getMessage(), e);
        }

        if (value != null) {
            return value;
        }

        if (annotation.defaultValue().equals(ConfigValue.DEFAULT_NONE)) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": no value found for config path '" + combinedPath + "' and no defaultValue was provided");
        }

        try {
            return configManager.coerceDefault(annotation.defaultValue(), targetType);
        } catch (ConfigException e) {
            throw new ConfigException("@ConfigValue on " + site + ": cannot coerce defaultValue '" +
                    annotation.defaultValue() + "' to " + targetType.getName() + ": " + e.getMessage(), e);
        }
    }

    // builds the "configName:path" lookup string; config() takes precedence over value()
    private @NotNull String buildPath(@NotNull ConfigValue annotation, @NotNull String site) {
        if (annotation.config() != Void.class) {
            if (annotation.path().isEmpty()) {
                throw new ConfigException("@ConfigValue on " + site + ": config() is set but path() is empty");
            }
            String configName;
            try {
                configName = configManager.getConfigName(annotation.config());
            } catch (ConfigException e) {
                throw new ConfigException("@ConfigValue on " + site + ": config class " +
                        annotation.config().getName() + " is not a registered @Config", e);
            }
            return configName + ":" + annotation.path();
        }
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        throw new ConfigException("@ConfigValue on " + site + ": must specify either value() or config()");
    }

    // renders the injection site as ClassName#field or ClassName#method(param) for error messages
    private @NotNull String describeSite(@NotNull InjectionPoint injectionPoint) {
        AnnotatedElement element = injectionPoint.getAnnotatedElement();
        if (element instanceof Field) {
            Field field = (Field) element;
            return field.getDeclaringClass().getName() + "#" + field.getName();
        }
        if (element instanceof Parameter) {
            Parameter parameter = (Parameter) element;
            return parameter.getDeclaringExecutable().getDeclaringClass().getName() + "#" +
                    parameter.getDeclaringExecutable().getName() + "(" + parameter.getName() + ")";
        }
        return element.toString();
    }
}
