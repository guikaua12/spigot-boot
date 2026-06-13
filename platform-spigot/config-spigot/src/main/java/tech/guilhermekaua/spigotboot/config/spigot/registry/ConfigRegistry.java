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

import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfigs;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.proxy.ConfigProxy;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.scanner.ClassPathScanner;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

@Component
public class ConfigRegistry {

    /**
     * Scans for and registers all @Config and @FolderConfig annotated classes.
     * <p>
     * After all classes are registered, calls {@link SpigotConfigManager#initializeAll()}
     * to bind configs in topological order (respecting {@code ${...}} references).
     *
     * @param context the context
     */
    public void registerConfigs(Context context) {
        String basePackage = context.getPlugin().getMainClass().getPackage().getName();
        ClassLoader classLoader = context.getPlugin().getClassLoader();

        ConfigManager configManager = context.getBean(ConfigManager.class);
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager is null");
        }

        Logger logger = context.getPlugin().getLogger();

        ClassPathScanner scanner = new ClassPathScanner(classLoader, basePackage);
        LinkedHashSet<Class<?>> configAnnotatedClasses = new LinkedHashSet<>(
                scanner.getTypesAnnotatedWith(Config.class));
        LinkedHashSet<Class<?>> folderAnnotatedClasses = new LinkedHashSet<>(
                scanner.getTypesAnnotatedWith(FolderConfig.class));
        LinkedHashSet<Class<?>> foldersContainerAnnotatedClasses = new LinkedHashSet<>(
                scanner.getTypesAnnotatedWith(FolderConfigs.class));

        DiscoveryIndexReader reader = new DiscoveryIndexReader(classLoader);
        if (reader.hasAnyIndex()) {
            List<Class<?>> indexed = reader.classesInCategory(DiscoveryCategories.CONFIG, basePackage);
            configAnnotatedClasses.addAll(filterByAnnotation(indexed, Config.class));
            folderAnnotatedClasses.addAll(filterByAnnotation(indexed, FolderConfig.class));
            foldersContainerAnnotatedClasses.addAll(filterByAnnotation(indexed, FolderConfigs.class));
        }

        for (Class<?> configClass : configAnnotatedClasses) {
            processConfigClass(configClass, context, configManager);
        }

        if (configManager instanceof SpigotConfigManager) {
            SpigotConfigManager spigotConfigManager = (SpigotConfigManager) configManager;

            for (Class<?> itemClass : folderAnnotatedClasses) {
                processFolderConfigClass(itemClass, spigotConfigManager, logger);
            }

            for (Class<?> itemClass : foldersContainerAnnotatedClasses) {
                processFolderConfigClass(itemClass, spigotConfigManager, logger);
            }

            spigotConfigManager.initializeAll();
        }
    }

    private static List<Class<?>> filterByAnnotation(Collection<Class<?>> classes,
                                                    Class<? extends Annotation> annotation) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : classes) {
            if (c.isAnnotationPresent(annotation)) {
                out.add(c);
            }
        }
        return out;
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
     * Processes a class annotated with @FolderConfig.
     *
     * @param itemClass           the folder config item class
     * @param spigotConfigManager the config manager
     * @param logger              the logger
     */
    @SuppressWarnings("unchecked")
    public <T> void processFolderConfigClass(
            Class<T> itemClass,
            SpigotConfigManager spigotConfigManager,
            Logger logger) {
        try {
            rejectConfigValueFields(itemClass, "@FolderConfig item");

            List<FolderConfig> annotations = getFolderConfigAnnotations(itemClass);

            if (annotations.isEmpty()) {
                return;
            }

            for (FolderConfig annotation : annotations) {
                spigotConfigManager.registerFolderConfig(itemClass, annotation);
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to register folder config for " + itemClass.getName() + ": " + e.getMessage());
        }
    }

    private List<FolderConfig> getFolderConfigAnnotations(Class<?> itemClass) {
        List<FolderConfig> result = new ArrayList<>();

        FolderConfig single = itemClass.getAnnotation(FolderConfig.class);
        if (single != null) {
            result.add(single);
        }

        FolderConfigs container = itemClass.getAnnotation(FolderConfigs.class);
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
        rejectConfigValueFields(configClass, "@Config");
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

    // declared fields only (consistent with the non-private-field check); inherited fields are out of scope by design
    private void rejectConfigValueFields(Class<?> targetClass, String kind) {
        for (Field field : targetClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigValue.class)) {
                throw new ConfigException(
                        kind + " class " + targetClass.getName() + " has a @ConfigValue field '" +
                                field.getName() + "'. @ConfigValue is only honored on DI-managed beans " +
                                "(@Component/@Bean), not on " + kind + " classes."
                );
            }
        }
    }

}
