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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CustomEntityDefinitionTest {

    @Test
    void shouldBuildDefinitionFromLogicalBaseType() {
        CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
                        CustomEntityId.of("test", "logical"),
                        CustomEntityBaseType.ZOMBIE
                )
                .controllerFactory(context -> new EntityController<Zombie>() {
                })
                .build();

        assertEquals(CustomEntityId.of("test", "logical"), definition.id());
        assertSame(CustomEntityBaseType.ZOMBIE, definition.baseType());
        assertSame(Zombie.class, definition.bukkitType());
    }

    @Test
    void shouldBuildDefinitionFromBukkitEntityType() {
        CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
                        CustomEntityId.of("test", "entity-type"),
                        EntityType.ZOMBIE
                )
                .controllerFactory(context -> new EntityController<Zombie>() {
                })
                .build();

        assertEquals(CustomEntityId.of("test", "entity-type"), definition.id());
        assertSame(CustomEntityBaseType.ZOMBIE, definition.baseType());
        assertSame(Zombie.class, definition.bukkitType());
    }
}
