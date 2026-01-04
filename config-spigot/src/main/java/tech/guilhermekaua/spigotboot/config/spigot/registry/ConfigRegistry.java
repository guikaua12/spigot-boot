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
package tech.guilhermekaua.spigotboot.config.spigot.registry;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigCollection;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigCollections;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.proxy.ConfigProxy;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Component
public class ConfigRegistry {

    /**
     * Scans for and registers all @Config and @ConfigCollection annotated classes.
     *
     * @param context the context
     */
    public void registerConfigs(Context context) {
        String basePackage = ProxyUtils.getRealClass(context.getPlugin()).getPackage().getName();
        Reflections reflections = new Reflections(basePackage, new SubTypesScanner(), new TypeAnnotationsScanner());

        ConfigManager configManager = context.getBean(ConfigManager.class);
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager is null");
        }

        Logger logger = context.getPlugin().getLogger();

        for (Class<?> configClass : reflections.getTypesAnnotatedWith(Config.class)) {
            processConfigClass(configClass, context, configManager);
        }

        if (configManager instanceof SpigotConfigManager) {
            SpigotConfigManager spigotConfigManager = (SpigotConfigManager) configManager;

            for (Class<?> itemClass : reflections.getTypesAnnotatedWith(ConfigCollection.class)) {
                processCollectionClass(itemClass, spigotConfigManager, logger);
            }

            for (Class<?> itemClass : reflections.getTypesAnnotatedWith(ConfigCollections.class)) {
                processCollectionClass(itemClass, spigotConfigManager, logger);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void processConfigClass(Class<?> configClass, Context context, ConfigManager configManager) {
        try {
            validateConfigClass(configClass);

            configManager.register(configClass);

            Object configProxy = createConfigProxy(configClass, configManager);

            context.getDependencyManager().registerDependency(
                    (Class<Object>) configClass,
                    configProxy,
                    BeanUtils.getQualifier(configClass),
                    BeanUtils.getIsPrimary(configClass)
            );
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register config: " + configClass.getName(), e);
        }
    }

    /**
     * Processes a class annotated with @ConfigCollection.
     *
     * @param itemClass           the collection item class
     * @param spigotConfigManager the config manager
     * @param logger              the logger
     */
    @SuppressWarnings("unchecked")
    public <T> void processCollectionClass(
            Class<T> itemClass,
            SpigotConfigManager spigotConfigManager,
            Logger logger) {
        try {
            List<ConfigCollection> annotations = getConfigCollectionAnnotations(itemClass);

            if (annotations.isEmpty()) {
                return;
            }

            for (ConfigCollection annotation : annotations) {
                spigotConfigManager.registerCollection(itemClass, annotation);
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to register collection for " + itemClass.getName() + ": " + e.getMessage());
        }
    }

    private List<ConfigCollection> getConfigCollectionAnnotations(Class<?> itemClass) {
        List<ConfigCollection> result = new ArrayList<>();

        ConfigCollection single = itemClass.getAnnotation(ConfigCollection.class);
        if (single != null) {
            result.add(single);
        }

        ConfigCollections container = itemClass.getAnnotation(ConfigCollections.class);
        if (container != null) {
            result.addAll(Arrays.asList(container.value()));
        }

        return result;
    }

    private <T> T createConfigProxy(Class<T> configClass, ConfigManager configManager) {
        ConfigRef<T> configRef = configManager.getRef(configClass);
        return ConfigProxy.createProxy(configClass, configRef);
    }

    private void validateConfigClass(Class<?> configClass) {
        for (Field field : configClass.getDeclaredFields()) {
            if (!Modifier.isPrivate(field.getModifiers())) {
                throw new ConfigException(
                        "Config class " + configClass.getName() +
                                " has non-private field '" + field.getName() + "'. " +
                                "All fields must be private to ensure reload safety."
                );
            }
        }
    }

}
