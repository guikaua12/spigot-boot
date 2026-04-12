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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Legacy spawned-entity view kept for migration from the old custom-entity API.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 * @deprecated use {@link SpawnedEntity}
 */
@Deprecated
public interface CustomEntityContext<T extends Entity> extends SpawnedEntity<T> {

    /**
     * Returns the logical custom entity id.
     *
     * @return the logical custom entity id
     */
    default @NotNull CustomEntityId definitionId() {
        CustomEntityId templateId = templateId();
        if (templateId == null) {
            throw new IllegalStateException("This entity was created without a registered definition id.");
        }
        return templateId;
    }

    /**
     * Returns the Bukkit type exposed to plugin code.
     *
     * @return the Bukkit entity type
     */
    default @NotNull Class<T> bukkitType() {
        return template().bukkitType();
    }

    /**
     * Returns the immutable spawn request used for this entity instance.
     *
     * @return the spawn request
     */
    default @NotNull CustomEntitySpawnRequest spawnRequest() {
        return CustomEntitySpawnRequest.fromOptions(spawnOptions());
    }

    static <T extends Entity> @NotNull CustomEntityContext<T> adapt(@NotNull SpawnedEntity<T> entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        if (entity instanceof CustomEntityContext) {
            return (CustomEntityContext<T>) entity;
        }
        return new CustomEntityContext<T>() {
            @Override
            public @NotNull EntityTemplate<T> template() {
                return entity.template();
            }

            @Override
            public @NotNull SpawnOptions spawnOptions() {
                return entity.spawnOptions();
            }

            @Override
            public @NotNull T bukkitEntity() {
                return entity.bukkitEntity();
            }

            @Override
            public @NotNull CustomEntityState state() {
                return entity.state();
            }

            @Override
            public void remove() {
                entity.remove();
            }

            @Override
            public boolean isRemoved() {
                return entity.isRemoved();
            }

            @Override
            public @NotNull CustomEntityBaseType baseType() {
                return entity.baseType();
            }

            @Override
            public @NotNull MinecraftVersion minecraftVersion() {
                return entity.minecraftVersion();
            }

            @Override
            public @NotNull EntityController<T> controller() {
                return entity.controller();
            }

            @Override
            public void setController(@NotNull EntityController<T> controller) {
                entity.setController(controller);
            }

            @Override
            public void clearController() {
                entity.clearController();
            }

            @Override
            public boolean isHooked() {
                return entity.isHooked();
            }
        };
    }
}
