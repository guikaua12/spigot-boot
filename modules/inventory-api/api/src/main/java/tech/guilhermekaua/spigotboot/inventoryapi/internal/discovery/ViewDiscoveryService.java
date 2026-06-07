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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds every concrete {@link View} subclass annotated with {@link RegisterView} under the
 * user's base package. Reads the compile-time {@code DiscoveryIndex} when present and falls
 * back to runtime classpath scanning otherwise — mirrors {@code InventoryDiscoveryService}.
 *
 * <p>Boot guard: a concrete class carrying {@code @RegisterView} that does not extend
 * {@code View} is logged SEVERE and excluded instead of being dropped silently.
 */
@ApiStatus.Internal
@Component
public final class ViewDiscoveryService {

    private static final Logger LOGGER = Logger.getLogger(ViewDiscoveryService.class.getName());

    /**
     * Discovers the registrable view classes under the given base package.
     *
     * @param basePackage the package scanned recursively
     * @return every concrete {@code @RegisterView}-annotated {@link View} subclass found; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public @NotNull Set<Class<? extends View>> discoverFromPackage(@NotNull String basePackage) {
        LinkedHashSet<Class<?>> candidates = new LinkedHashSet<>(
                ReflectionUtils.getClassesAnnotatedWith(basePackage, RegisterView.class));

        DiscoveryIndexReader reader = DiscoveryIndexReader.create();
        if (reader.hasAnyIndex()) {
            candidates.addAll(reader.classesInCategory(DiscoveryCategories.INVENTORY, basePackage));
        }

        Set<Class<? extends View>> views = new LinkedHashSet<>();
        for (Class<?> candidate : candidates) {
            if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
                continue;
            }
            if (!isViewOrWarn(candidate)) {
                continue;
            }
            views.add((Class<? extends View>) candidate);
        }
        return views;
    }

    // boot guard (§5.1): the INVENTORY index category is shared with 2.x @Inventory classes,
    // so only candidates that explicitly carry @RegisterView are a user mistake worth a SEVERE
    private static boolean isViewOrWarn(Class<?> candidate) {
        if (View.class.isAssignableFrom(candidate)) {
            return true;
        }
        if (candidate.isAnnotationPresent(RegisterView.class)) {
            LOGGER.severe("class " + candidate.getName()
                    + " is annotated @RegisterView but does not extend View; it will not be registered");
        }
        return false;
    }
}
