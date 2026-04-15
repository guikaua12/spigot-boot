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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Shared base type for controller hook contexts.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @param <R> the hook return type
 * @since 2.0.2
 */
public abstract class AbstractEntityHookContext<T extends Entity, R> implements ControlledEntity<T> {
    private final ControlledEntity<T> entity;
    private final EntityBaseInvoker<R> base;

    private boolean baseInvoked;
    private boolean resultSet;
    private R result;

    protected AbstractEntityHookContext(
            @NotNull ControlledEntity<T> entity,
            @NotNull EntityBaseInvoker<R> base
    ) {
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");
        this.base = new EntityBaseInvoker<R>() {
            @Override
            public R invoke() {
                R baseResult = base.invoke();
                baseInvoked = true;
                result = baseResult;
                return baseResult;
            }
        };
    }

    public final @NotNull EntityBaseInvoker<R> base() {
        return base;
    }

    public final boolean isBaseInvoked() {
        return baseInvoked;
    }

    public final boolean hasResult() {
        return resultSet || baseInvoked;
    }

    public final @Nullable R result() {
        return result;
    }

    public final void setResult(@Nullable R result) {
        this.result = result;
        this.resultSet = true;
    }

    public final @Nullable R resolveResult(@Nullable R suppressedResult) {
        if (resultSet || baseInvoked) {
            return result;
        }
        return suppressedResult;
    }

    @Override
    public final @NotNull T bukkitEntity() {
        return entity.bukkitEntity();
    }

    @Override
    public final @NotNull CustomEntityState state() {
        return entity.state();
    }

    @Override
    public final void remove() {
        entity.remove();
    }

    @Override
    public final boolean isRemoved() {
        return entity.isRemoved();
    }

    @Override
    public final @NotNull CustomEntityBaseType baseType() {
        return entity.baseType();
    }

    @Override
    public final @NotNull MinecraftVersion minecraftVersion() {
        return entity.minecraftVersion();
    }

    @Override
    public final @NotNull EntityController<T> controller() {
        return entity.controller();
    }

    @Override
    public final void setController(@NotNull EntityController<T> controller) {
        entity.setController(controller);
    }

    @Override
    public final void clearController() {
        entity.clearController();
    }

    @Override
    public final boolean isHooked() {
        return entity.isHooked();
    }
}
