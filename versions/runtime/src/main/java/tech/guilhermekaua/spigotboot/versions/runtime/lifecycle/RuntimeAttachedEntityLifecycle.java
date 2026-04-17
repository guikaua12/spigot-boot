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
package tech.guilhermekaua.spigotboot.versions.runtime.lifecycle;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

/**
 * Runtime bridge used when attaching controllers to pre-existing vanilla entities.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public class RuntimeAttachedEntityLifecycle<T extends Entity> extends AbstractRuntimeControlledEntity<T> {

    public RuntimeAttachedEntityLifecycle(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController
    ) {
        this(baseType, minecraftVersion, initialController, EntityTransportResolver.noop());
    }

    /**
     * Creates a new attached runtime lifecycle with an explicit internal transport backend.
     *
     * @param baseType the logical base type
     * @param minecraftVersion the resolved Minecraft version
     * @param initialController the initial logical controller
     * @param transport the internal semantic transport backend
     */
    public RuntimeAttachedEntityLifecycle(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController,
            @NotNull EntityTransport transport
    ) {
        this(baseType, minecraftVersion, initialController, transport, EntityPublicationBackendResolver.noop());
    }

    /**
     * Creates a new attached runtime lifecycle with explicit internal transport and publication backends.
     *
     * @param baseType the logical base type
     * @param minecraftVersion the resolved Minecraft version
     * @param initialController the initial logical controller
     * @param transport the internal semantic transport backend
     * @param publicationBackend the internal publication backend
     */
    public RuntimeAttachedEntityLifecycle(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController,
            @NotNull EntityTransport transport,
            @NotNull EntityPublicationBackend publicationBackend
    ) {
        this(
                baseType,
                minecraftVersion,
                initialController,
                transport,
                publicationBackend,
                RuntimeGoalMutationExecutor.noop(emptyManagedGoals()),
                false
        );
    }

    /**
     * Creates a new attached runtime lifecycle with explicit managed-goal snapshot and executor seams.
     *
     * @param baseType the logical base type
     * @param minecraftVersion the resolved Minecraft version
     * @param initialController the initial logical controller
     * @param transport the internal semantic transport backend
     * @param publicationBackend the internal publication backend
     * @param goalMutationExecutor the shared runtime goal executor seam
     */
    public RuntimeAttachedEntityLifecycle(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController,
            @NotNull EntityTransport transport,
            @NotNull EntityPublicationBackend publicationBackend,
            @NotNull RuntimeGoalMutationExecutor<T> goalMutationExecutor
    ) {
        this(baseType, minecraftVersion, initialController, transport, publicationBackend, goalMutationExecutor, true);
    }

    private RuntimeAttachedEntityLifecycle(
            @NotNull CustomEntityBaseType baseType,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityController<T> initialController,
            @NotNull EntityTransport transport,
            @NotNull EntityPublicationBackend publicationBackend,
            @NotNull RuntimeGoalMutationExecutor<T> goalMutationExecutor,
            boolean ignored
    ) {
        super(baseType, minecraftVersion, initialController, transport, publicationBackend, goalMutationExecutor);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull GoalProfile<T> emptyManagedGoals() {
        return GoalProfile.<T>builder((Class<T>) Entity.class.asSubclass(Entity.class)).build();
    }
}
