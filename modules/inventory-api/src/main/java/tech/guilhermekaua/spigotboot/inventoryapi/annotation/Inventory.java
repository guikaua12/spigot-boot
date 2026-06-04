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
package tech.guilhermekaua.spigotboot.inventoryapi.annotation;

import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.SpigotBootDiscoveryCategory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory} subclass
 * for auto-discovery and registration by {@code InventoryApiModule}.
 *
 * <p>Annotated classes are instantiated via the dependency manager at boot time and registered
 * into the {@code InventoryRegistry}, where they can be looked up by class through
 * {@code InventoryService#open(Player, Class)}.
 *
 * <p>Note: this annotation lives in {@code tech.guilhermekaua.spigotboot.inventoryapi.annotation}
 * and shares its simple name with {@code org.bukkit.inventory.Inventory}. When both are needed
 * in the same compilation unit, qualify the Bukkit type with its fully-qualified name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpigotBootDiscoveryCategory(value = DiscoveryCategories.INVENTORY, kind = SpigotBootDiscoveryCategory.Kind.ANNOTATION)
public @interface Inventory {
}
