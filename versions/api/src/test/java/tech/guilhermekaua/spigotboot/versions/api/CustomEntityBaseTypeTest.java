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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CustomEntityBaseTypeTest {

    @Test
    void shouldResolveEveryAvailableBukkitEntityTypeToLogicalBaseType() {
        for (EntityType entityType : EntityType.values()) {
            CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entityType);
            assertNotNull(baseType, "Expected a logical base type for Bukkit type " + entityType.name());
        }
    }

    @Test
    void shouldResolveLegacyAliasesAndUnavailableFutureTypesLazily() {
        assertSame(CustomEntityBaseType.ITEM, CustomEntityBaseType.fromEntityTypeName("DROPPED_ITEM"));
        assertSame(CustomEntityBaseType.ZOMBIFIED_PIGLIN, CustomEntityBaseType.fromEntityTypeName("PIG_ZOMBIE"));
        assertSame(EntityType.ZOMBIE, CustomEntityBaseType.ZOMBIE.entityTypeOrNull());
        assertSame(Zombie.class, CustomEntityBaseType.ZOMBIE.bukkitTypeOrNull());
        assertNull(CustomEntityBaseType.BREEZE.entityTypeOrNull());
        assertNull(CustomEntityBaseType.BREEZE.bukkitTypeOrNull());
    }
}
