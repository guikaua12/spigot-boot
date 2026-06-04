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
package tech.guilhermekaua.spigotboot.inventoryapi.registry.discovery;

import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds every concrete {@link CustomInventory} subclass annotated with {@link Inventory} under
 * the user's base package. Reads the compile-time {@code DiscoveryIndex} when present and falls
 * back to runtime classpath scanning otherwise — mirrors {@code JdbcRepositoryDiscoveryService}.
 */
@Component
public final class InventoryDiscoveryService {

    @SuppressWarnings("unchecked")
    public Set<Class<? extends CustomInventory>> discoverFromPackage(String basePackage) {
        LinkedHashSet<Class<?>> candidates = new LinkedHashSet<>(
                ReflectionUtils.getClassesAnnotatedWith(basePackage, Inventory.class));

        DiscoveryIndexReader reader = DiscoveryIndexReader.create();
        if (reader.hasAnyIndex()) {
            candidates.addAll(reader.classesInCategory(DiscoveryCategories.INVENTORY, basePackage));
        }

        return candidates.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .filter(CustomInventory.class::isAssignableFrom)
                .map(c -> (Class<? extends CustomInventory>) c)
                .collect(Collectors.toSet());
    }
}
