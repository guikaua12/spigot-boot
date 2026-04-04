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
package tech.guilhermekaua.spigotboot.entity.api.goal;

/**
 * Shared logical goal keys exposed by the entity abstraction.
 *
 * @since 2.0.2
 */
public final class EntityGoalKeys {
    public static final EntityGoalKey AVOID_ENTITY = EntityGoalKey.minecraft("avoid_entity");
    public static final EntityGoalKey BREAK_DOOR = EntityGoalKey.minecraft("break_door");
    public static final EntityGoalKey CLIMB_ON_TOP_OF_POWDER_SNOW = EntityGoalKey.minecraft("climb_on_top_of_powder_snow");
    public static final EntityGoalKey FLOAT = EntityGoalKey.minecraft("float");
    public static final EntityGoalKey FOLLOW_OWNER = EntityGoalKey.minecraft("follow_owner");
    public static final EntityGoalKey LOOK_AT_PLAYER = EntityGoalKey.minecraft("look_at_player");
    public static final EntityGoalKey MELEE_ATTACK = EntityGoalKey.minecraft("melee_attack");
    public static final EntityGoalKey MOVE_THROUGH_VILLAGE = EntityGoalKey.minecraft("move_through_village");
    public static final EntityGoalKey OPEN_DOOR = EntityGoalKey.minecraft("open_door");
    public static final EntityGoalKey PANIC = EntityGoalKey.minecraft("panic");
    public static final EntityGoalKey RANDOM_LOOK_AROUND = EntityGoalKey.minecraft("random_look_around");
    public static final EntityGoalKey RANGED_ATTACK = EntityGoalKey.minecraft("ranged_attack");
    public static final EntityGoalKey TEMPT = EntityGoalKey.minecraft("tempt");
    public static final EntityGoalKey USE_ITEM = EntityGoalKey.minecraft("use_item");

    private EntityGoalKeys() {
    }
}
