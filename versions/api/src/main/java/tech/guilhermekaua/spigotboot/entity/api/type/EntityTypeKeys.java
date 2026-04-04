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
package tech.guilhermekaua.spigotboot.entity.api.type;

/**
 * Shared logical entity types exposed by the entity abstraction.
 *
 * @since 2.0.2
 */
public final class EntityTypeKeys {
    public static final EntityTypeKey ARMOR_STAND = EntityTypeKey.minecraft("armor_stand");
    public static final EntityTypeKey CREEPER = EntityTypeKey.minecraft("creeper");
    public static final EntityTypeKey PIG = EntityTypeKey.minecraft("pig");
    public static final EntityTypeKey SKELETON = EntityTypeKey.minecraft("skeleton");
    public static final EntityTypeKey VILLAGER = EntityTypeKey.minecraft("villager");
    public static final EntityTypeKey WOLF = EntityTypeKey.minecraft("wolf");
    public static final EntityTypeKey ZOMBIE = EntityTypeKey.minecraft("zombie");

    private EntityTypeKeys() {
    }
}
