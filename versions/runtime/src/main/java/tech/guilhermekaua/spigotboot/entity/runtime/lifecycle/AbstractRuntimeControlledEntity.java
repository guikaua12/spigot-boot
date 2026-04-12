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
package tech.guilhermekaua.spigotboot.entity.runtime.lifecycle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.*;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.controller.ControllerMethodResolver;
import tech.guilhermekaua.spigotboot.entity.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.entity.runtime.controller.PassThroughEntityController;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared controller-aware runtime bridge used by both spawned and attached entities.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public abstract class AbstractRuntimeControlledEntity<T extends Entity>
        implements ControlledEntity<T>, NativeEntityLifecycle<T> {
    private static final ControllerMethodResolver CONTROLLER_METHOD_RESOLVER = new ControllerMethodResolver();
    private static final Runnable NOOP_REMOVAL_CALLBACK = new Runnable() {
        @Override
        public void run() {
        }
    };

    private final CustomEntityBaseType baseType;
    private final MinecraftVersion minecraftVersion;
    private final SimpleCustomEntityState state;
    private final EntityNetworkState networkState;
    private final AtomicBoolean removed;
    private final AtomicBoolean removeDispatchInProgress;
    private final AtomicBoolean repairPending;
    private final AtomicBoolean repairTaskScheduled;
    private final AtomicBoolean removalCallbackInvoked;

    private volatile NativeHookBinder<T> hookBinder;
    private volatile T bukkitEntity;
    private volatile EntityController<T> controller;
    private volatile EntityNetworkController<T> networkController;
    private volatile Runnable nextTickRepair;
    private volatile Runnable removalCallback;

    protected AbstractRuntimeControlledEntity(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController
    ) {
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.state = new SimpleCustomEntityState();
        this.networkState = new EntityNetworkState();
        this.removed = new AtomicBoolean(false);
        this.removeDispatchInProgress = new AtomicBoolean(false);
        this.repairPending = new AtomicBoolean(false);
        this.repairTaskScheduled = new AtomicBoolean(false);
        this.removalCallbackInvoked = new AtomicBoolean(false);
        this.controller = Objects.requireNonNull(initialController, "initialController cannot be null");
        this.networkController = EntityNetworkController.passThrough();
        this.removalCallback = NOOP_REMOVAL_CALLBACK;
    }

    @Override
    public void bind(@NotNull T bukkitEntity) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        if (this.bukkitEntity != null) {
            throw new IllegalStateException("The Bukkit entity has already been bound.");
        }
        this.bukkitEntity = bukkitEntity;
        networkController.onBind(this, networkState);
    }

    @Override
    public void onSpawn() {
    }

    @Override
    public @Nullable Object onNativeHook(
            @NotNull String hookName,
            @NotNull LifecycleAwareNativeEntity nativeEntity,
            @Nullable Object[] arguments
    ) {
        Objects.requireNonNull(hookName, "hookName cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        runPendingRepair();
        NativeHookBinder<T> binder = hookBinder();
        return binder.dispatch(this, nativeEntity, hookName, arguments);
    }

    @Override
    public @NotNull ControlledEntity<T> handle() {
        return this;
    }

    @Override
    public @NotNull T bukkitEntity() {
        return ensureBound();
    }

    @Override
    public @NotNull CustomEntityState state() {
        return state;
    }

    @Override
    public @NotNull EntityNetworkState networkState() {
        return networkState;
    }

    @Override
    public void remove() {
        if (removed.get()) {
            return;
        }
        ensureBound().remove();
    }

    @Override
    public boolean isRemoved() {
        if (removed.get()) {
            return true;
        }
        T entity = bukkitEntity;
        return entity != null && !entity.isValid();
    }

    @Override
    public @NotNull CustomEntityBaseType baseType() {
        return baseType;
    }

    @Override
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public @NotNull EntityController<T> controller() {
        return controller;
    }

    @Override
    public @NotNull EntityNetworkController<T> networkController() {
        return networkController;
    }

    @Override
    public void setController(@NotNull EntityController<T> controller) {
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
    }

    @Override
    public void setNetworkController(@NotNull EntityNetworkController<T> controller) {
        Objects.requireNonNull(controller, "controller cannot be null");
        EntityNetworkController<T> previous = networkController;
        previous.onUnbind(this, networkState);
        networkController = controller;
        if (bukkitEntity != null) {
            controller.onBind(this, networkState);
        }
    }

    @Override
    public void clearController() {
        this.controller = PassThroughEntityController.instance();
    }

    @Override
    public void clearNetworkController() {
        setNetworkController(EntityNetworkController.<T>passThrough());
    }

    @Override
    public boolean isHooked() {
        return true;
    }

    public final void scheduleNextTickRepair(@NotNull Runnable repair) {
        this.nextTickRepair = Objects.requireNonNull(repair, "repair cannot be null");
        this.repairPending.set(true);
        scheduleRepairTaskIfPossible();
    }

    public final void bindRemovalCallback(@NotNull Runnable removalCallback) {
        Objects.requireNonNull(removalCallback, "removalCallback cannot be null");
        if (removed.get()) {
            removalCallback.run();
            return;
        }
        this.removalCallback = removalCallback;
    }

    public final void dispatchTick(@NotNull ContextualBaseInvoker<EntityTickContext<T>, Void> base) {
        if (removed.get()) {
            return;
        }
        EntityTickContext<T>[] holder = new EntityTickContext[1];
        holder[0] = new EntityTickContext<>(this, () -> base.invoke(holder[0]));
        dispatchHook(LogicalEntityHook.TICK, holder[0], null);
        refreshNetworkState();
        networkController.onTick(this, networkState);
    }

    public final void dispatchMove(
            double x,
            double y,
            double z,
            @NotNull ContextualBaseInvoker<EntityMoveContext<T>, Void> base
    ) {
        EntityMoveContext<T>[] holder = new EntityMoveContext[1];
        holder[0] = new EntityMoveContext<>(this, () -> base.invoke(holder[0]), x, y, z);
        dispatchHook(LogicalEntityHook.MOVE, holder[0], null);
    }

    public final void dispatchPush(
            double x,
            double y,
            double z,
            @NotNull ContextualBaseInvoker<EntityPushContext<T>, Void> base
    ) {
        EntityPushContext<T>[] holder = new EntityPushContext[1];
        holder[0] = new EntityPushContext<>(this, () -> base.invoke(holder[0]), x, y, z);
        dispatchHook(LogicalEntityHook.PUSH, holder[0], null);
    }

    public final boolean dispatchDamage(
            float amount,
            @NotNull ContextualBaseInvoker<EntityDamageContext<T>, Boolean> base
    ) {
        EntityDamageContext<T>[] holder = new EntityDamageContext[1];
        holder[0] = new EntityDamageContext<>(this, () -> base.invoke(holder[0]), amount);
        return Boolean.TRUE.equals(
                dispatchHook(LogicalEntityHook.DAMAGE, holder[0], Boolean.FALSE)
        );
    }

    public final @NotNull EntityInteractionResult dispatchInteract(
            @NotNull Player player,
            @NotNull EntityInteractionHand hand,
            @NotNull ContextualBaseInvoker<EntityInteractContext<T>, EntityInteractionResult> base
    ) {
        EntityInteractContext<T>[] holder = new EntityInteractContext[1];
        holder[0] = new EntityInteractContext<>(this, () -> base.invoke(holder[0]), player, hand);
        EntityInteractionResult result = dispatchHook(LogicalEntityHook.INTERACT, holder[0], EntityInteractionResult.PASS);
        return result != null ? result : EntityInteractionResult.PASS;
    }

    public final void dispatchDie(@NotNull ContextualBaseInvoker<EntityDieContext<T>, Void> base) {
        EntityDieContext<T>[] holder = new EntityDieContext[1];
        holder[0] = new EntityDieContext<>(this, () -> base.invoke(holder[0]));
        dispatchHook(LogicalEntityHook.DIE, holder[0], null);
    }

    public final void dispatchRemove(@NotNull ContextualBaseInvoker<EntityRemoveContext<T>, Void> base) {
        if (removeDispatchInProgress.get()) {
            EntityRemoveContext<T> nestedContext = new EntityRemoveContext<T>(this, new EntityBaseInvoker<Void>() {
                @Override
                public Void invoke() {
                    return null;
                }
            });
            base.invoke(nestedContext);
            return;
        }
        if (!removed.compareAndSet(false, true)) {
            return;
        }
        removeDispatchInProgress.set(true);
        try {
            EntityRemoveContext<T>[] holder = new EntityRemoveContext[1];
            holder[0] = new EntityRemoveContext<>(this, () -> base.invoke(holder[0]));
            dispatchHook(LogicalEntityHook.REMOVE, holder[0], null);
        } finally {
            runRemovalCallback();
            removeDispatchInProgress.set(false);
        }
    }

    public final void dispatchCollide(
            @NotNull Entity entity,
            @NotNull ContextualBaseInvoker<EntityCollideContext<T>, Void> base
    ) {
        EntityCollideContext<T>[] holder = new EntityCollideContext[1];
        holder[0] = new EntityCollideContext<>(this, () -> base.invoke(holder[0]), entity);
        dispatchHook(LogicalEntityHook.COLLIDE, holder[0], null);
    }

    public final void dispatchPositionPassenger(
            @Nullable Entity passenger,
            @NotNull ContextualBaseInvoker<EntityPositionPassengerContext<T>, Void> base
    ) {
        EntityPositionPassengerContext<T>[] holder = new EntityPositionPassengerContext[1];
        holder[0] = new EntityPositionPassengerContext<>(this, () -> base.invoke(holder[0]), passenger);
        dispatchHook(LogicalEntityHook.POSITION_PASSENGER, holder[0], null);
    }

    public final void dispatchInventoryChange(
            @NotNull EntityEquipmentSlot slot,
            @Nullable ItemStack previousItem,
            @Nullable ItemStack newItem,
            @NotNull ContextualBaseInvoker<EntityInventoryChangeContext<T>, Void> base
    ) {
        EntityInventoryChangeContext<T>[] holder = new EntityInventoryChangeContext[1];
        holder[0] = new EntityInventoryChangeContext<>(this, () -> base.invoke(holder[0]), slot, previousItem, newItem);
        dispatchHook(LogicalEntityHook.INVENTORY_CHANGE, holder[0], null);
    }

    private <R, C extends tech.guilhermekaua.spigotboot.entity.api.AbstractEntityHookContext<T, R>> @Nullable R dispatchHook(
            @NotNull LogicalEntityHook hook,
            @NotNull C context,
            @Nullable R suppressedResult
    ) {
        ControllerMethodResolver.HookResolution resolution =
                CONTROLLER_METHOD_RESOLVER.resolve(controller, hook);
        if (!resolution.intercepted()) {
            context.base().invoke();
            return context.resolveResult(suppressedResult);
        }

        try {
            resolution.contextMethod().invoke(controller, context);
            return context.resolveResult(suppressedResult);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Could not access controller hook '" + hook.methodName() + "'.",
                    exception
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getTargetException();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(
                    "Controller hook '" + hook.methodName() + "' failed.",
                    cause
            );
        }
    }

    public final void bindHookBinder(@NotNull NativeHookBinder<T> hookBinder) {
        Objects.requireNonNull(hookBinder, "hookBinder cannot be null");
        if (this.hookBinder != null) {
            throw new IllegalStateException("The native hook binder has already been bound.");
        }
        this.hookBinder = hookBinder;
    }

    protected final @NotNull NativeHookBinder<T> hookBinder() {
        NativeHookBinder<T> binder = hookBinder;
        if (binder == null) {
            throw new IllegalStateException("The native hook binder has not been bound yet.");
        }
        return binder;
    }

    protected final boolean markRemoved() {
        return removed.compareAndSet(false, true);
    }

    protected final @NotNull T ensureBound() {
        T entity = bukkitEntity;
        if (entity == null) {
            throw new IllegalStateException("The Bukkit entity has not been bound yet.");
        }
        return entity;
    }

    private void runPendingRepair() {
        if (!repairPending.compareAndSet(true, false)) {
            return;
        }
        repairTaskScheduled.set(false);
        Runnable repair = nextTickRepair;
        nextTickRepair = null;
        if (repair != null) {
            repair.run();
        }
    }

    private void scheduleRepairTaskIfPossible() {
        if (!repairTaskScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            if (Bukkit.getServer() == null || Bukkit.getScheduler() == null) {
                repairTaskScheduled.set(false);
                return;
            }

            Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
            if (plugin == null || !plugin.isEnabled()) {
                repairTaskScheduled.set(false);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    runPendingRepair();
                }
            });
        } catch (IllegalStateException ignored) {
            repairTaskScheduled.set(false);
        } catch (LinkageError ignored) {
            repairTaskScheduled.set(false);
        }
    }

    private void runRemovalCallback() {
        if (!removalCallbackInvoked.compareAndSet(false, true)) {
            return;
        }
        Runnable callback = removalCallback;
        removalCallback = NOOP_REMOVAL_CALLBACK;
        networkController.onUnbind(this, networkState);
        callback.run();
    }

    private void refreshNetworkState() {
        T entity = bukkitEntity;
        if (entity == null) {
            return;
        }
        Location location = entity.getLocation();
        if (location != null) {
            networkState.setLivePosition(location.getX(), location.getY(), location.getZ());
            networkState.setLiveRotation(location.getYaw(), location.getPitch());
        }
        if (entity.getVelocity() != null) {
            networkState.setLiveVelocity(entity.getVelocity().getX(), entity.getVelocity().getY(), entity.getVelocity().getZ());
        }
        networkState.incrementTicksSinceAbsoluteSync();
    }
}
