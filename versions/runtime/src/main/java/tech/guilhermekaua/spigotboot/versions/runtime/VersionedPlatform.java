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
package tech.guilhermekaua.spigotboot.versions.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityInitializer;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnBuilder;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.exception.EntityTemplateNotFoundException;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AttachedEntityRegistry;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.registry.EntityTemplateRegistry;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityStrategyBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.EntityStrategyBundle;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Plugin-facing runtime entry point for multi-version native custom entities.
 *
 * @since 2.0.2
 */
public final class VersionedPlatform {
    private final MinecraftVersion minecraftVersion;
    private final VersionAdapter adapter;
    private final EntityTemplateRegistry templateRegistry;
    private final AttachedEntityRegistry attachedEntityRegistry;
    private final VersionCapabilities capabilities;
    private final VersionBindings bindings;
    private final EntityNetworkMetadataContract networkMetadataContract;
    private final EntityNetworkRuntimeBundle networkRuntime;
    private final EntityTransport transport;
    private final EntityPublicationBackend publicationBackend;
    private final EntityStrategyBundle strategies;

    /**
     * Creates a new resolved platform.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param adapter the selected adapter
     */
    public VersionedPlatform(@NotNull MinecraftVersion minecraftVersion, @NotNull VersionAdapter adapter) {
        this(
                new VersionRuntimeProfile(
                        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null"),
                        RuntimeServerFlavor.SPIGOT,
                        false,
                        false,
                        false
                ),
                adapter
        );
    }

    /**
     * Creates a new resolved platform from a concrete runtime profile.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param adapter the selected adapter
     */
    public VersionedPlatform(@NotNull VersionRuntimeProfile runtimeProfile, @NotNull VersionAdapter adapter) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");

        this.minecraftVersion = runtimeProfile.minecraftVersion();
        this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
        this.templateRegistry = new EntityTemplateRegistry();
        this.attachedEntityRegistry = new AttachedEntityRegistry();
        this.capabilities = resolveCapabilities(adapter);
        this.bindings = resolveBindings(adapter);
        this.networkMetadataContract = resolveNetworkMetadataContract(adapter);
        this.networkRuntime = EntityNetworkRuntimeBundleSelector.select(runtimeProfile, capabilities, bindings);
        this.transport = EntityTransportResolver.resolve(runtimeProfile, networkRuntime, adapter, networkMetadataContract);
        this.publicationBackend = EntityPublicationBackendResolver.resolve(networkRuntime);
        this.strategies = EntityStrategyBundleSelector.select(minecraftVersion, capabilities, bindings);
    }

    /**
     * Returns the resolved Minecraft version.
     *
     * @return the active Minecraft version
     */
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the active version adapter.
     *
     * @return the active adapter
     */
    public @NotNull VersionAdapter adapter() {
        return adapter;
    }

    /**
     * Returns the runtime-facing capability summary for the active adapter.
     *
     * @return the adapter capability summary
     */
    public @NotNull VersionCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Returns the runtime-facing binding bundle for the active adapter.
     *
     * @return the adapter binding bundle
     */
    public @NotNull VersionBindings bindings() {
        return bindings;
    }

    /**
     * Returns the runtime-selected network subsystem bundle for the active adapter metadata.
     *
     * @return the selected network subsystem bundle
     */
    public @NotNull EntityNetworkRuntimeBundle networkRuntime() {
        return networkRuntime;
    }

    /**
     * Returns the dedicated watcher and network metadata synchronization contract for the active adapter.
     *
     * <p>This contract is resolved separately from the transport family selection metadata and from the version-local
     * `EntityFactory...EntityMetadata` records used to resolve logical base types.
     *
     * @return the resolved network metadata contract
     */
    public @NotNull EntityNetworkMetadataContract networkMetadataContract() {
        return networkMetadataContract;
    }

    final @NotNull EntityTransport transport() {
        return transport;
    }

    /**
     * Returns the runtime-selected strategy bundle for the active adapter metadata.
     *
     * @return the selected strategy bundle
     */
    public @NotNull EntityStrategyBundle strategies() {
        return strategies;
    }

    /**
     * Returns whether the active adapter supports the supplied logical base type.
     *
     * @param baseType the base type to inspect
     * @return {@code true} when the active adapter supports the type
     */
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return adapter.supports(baseType);
    }

    /**
     * Registers a reusable template for later lookup by id.
     *
     * @param template the template to register
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public <T extends Entity> void register(@NotNull EntityTemplate<T> template) {
        Objects.requireNonNull(template, "template cannot be null");
        CustomEntityId templateId = template.id();
        if (templateId == null) {
            throw new IllegalArgumentException("Only templates with an id can be registered.");
        }
        requireSupportedBaseType(template.baseType());
        templateRegistry.register(template);
    }

    /**
     * Registers each supplied template.
     *
     * @param templates the templates to register
     */
    public void registerAll(@NotNull Iterable<? extends EntityTemplate<?>> templates) {
        Objects.requireNonNull(templates, "templates cannot be null");
        for (EntityTemplate<?> template : templates) {
            register(template);
        }
    }

    /**
     * Returns the registered template for the supplied id, or {@code null} when it does not exist.
     *
     * @param id the template id
     * @return the registered template, or {@code null}
     */
    public @Nullable EntityTemplate<?> template(@NotNull CustomEntityId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return templateRegistry.find(id);
    }

    /**
     * Returns every registered template.
     *
     * @return the registered templates
     */
    public @NotNull Collection<EntityTemplate<?>> templates() {
        return templateRegistry.templates();
    }

    /**
     * Attaches the shared controller runtime to an existing supported Bukkit entity.
     *
     * @param entity the entity to attach
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the controlled entity handle
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull ControlledEntity<T> get(@NotNull T entity) {
        Objects.requireNonNull(entity, "entity cannot be null");

        ControlledEntity<?> attachedEntity = attachedEntityRegistry.findByBukkit(entity);
        if (attachedEntity != null) {
            if (isActiveAttachedEntity(attachedEntity)) {
                return (ControlledEntity<T>) attachedEntity;
            }
            attachedEntityRegistry.unregister(entity, resolveNativeHandle(entity));
        }

        Object previousNativeHandle = resolveNativeHandle(entity);
        if (previousNativeHandle != null) {
            ControlledEntity<?> nativeCached = attachedEntityRegistry.findByNative(previousNativeHandle);
            if (nativeCached != null && isActiveAttachedEntity(nativeCached)) {
                attachRegistryCleanup(nativeCached);
                attachedEntityRegistry.register(entity, previousNativeHandle, nativeCached);
                return (ControlledEntity<T>) nativeCached;
            }
            attachedEntityRegistry.unregister(entity, previousNativeHandle);
        }

        RuntimeAttachedEntityLifecycle<T> lifecycle =
                new RuntimeAttachedEntityLifecycle<T>(
                        resolveBaseType(entity),
                        minecraftVersion,
                        nullController(),
                        transport,
                        publicationBackend
                );
        ControlledEntity<T> attached = strategies.replacement().attach(adapter, entity, lifecycle);
        attachRegistryCleanup(attached);
        Object currentNativeHandle = resolveNativeHandle(entity);
        if (currentNativeHandle != null) {
            if (previousNativeHandle != null && previousNativeHandle != currentNativeHandle) {
                attachedEntityRegistry.unregister(entity, previousNativeHandle);
            }
            attachedEntityRegistry.register(entity, currentNativeHandle, attached);
        }
        return attached;
    }

    private boolean isActiveAttachedEntity(@NotNull ControlledEntity<?> controlledEntity) {
        return controlledEntity.isHooked() && !controlledEntity.isRemoved();
    }

    private void attachRegistryCleanup(@NotNull ControlledEntity<?> entity) {
        if (!(entity instanceof AbstractRuntimeControlledEntity)) {
            return;
        }
        final ControlledEntity<?> controlledEntity = entity;
        ((AbstractRuntimeControlledEntity<?>) entity).bindRemovalCallback(() -> {
            Entity bukkitEntity = controlledEntity.bukkitEntity();
            attachedEntityRegistry.unregister(bukkitEntity, resolveNativeHandle(bukkitEntity));
        });
    }

    /**
     * Spawns a registered template by id.
     *
     * @param id the registered template id
     * @param spawnOptions the spawn options
     * @return the spawned entity
     */
    public @NotNull SpawnedEntity<?> spawn(@NotNull CustomEntityId id, @NotNull SpawnOptions spawnOptions) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");

        EntityTemplate<?> template = templateRegistry.find(id);
        if (template == null) {
            throw new EntityTemplateNotFoundException(
                    "No custom entity template has been registered for '" + id + "'."
            );
        }
        return spawnUnchecked(template, spawnOptions);
    }

    /**
     * Spawns a registered template by id at the supplied location.
     *
     * @param id the registered template id
     * @param location the spawn location
     * @return the spawned entity
     */
    public @NotNull SpawnedEntity<?> spawn(@NotNull CustomEntityId id, @NotNull Location location) {
        return spawn(id, SpawnOptions.at(location));
    }

    /**
     * Spawns the supplied template without requiring prior registration.
     *
     * @param template the template to spawn
     * @param spawnOptions the spawn options
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned entity
     */
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        requireSupportedBaseType(template.baseType());
        return spawnUnchecked(template, spawnOptions);
    }

    /**
     * Spawns the supplied template at the supplied location.
     *
     * @param template the template to spawn
     * @param location the spawn location
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned entity
     */
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull Location location
    ) {
        return spawn(template, SpawnOptions.at(location));
    }

    /**
     * Spawns the supplied template with inline spawn customization.
     *
     * @param template the template to spawn
     * @param location the spawn location
     * @param customizer the spawn customizer
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned entity
     */
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull Location location,
            @NotNull Consumer<SpawnBuilder<T>> customizer
    ) {
        Objects.requireNonNull(customizer, "customizer cannot be null");
        SpawnBuilder<T> spawnBuilder = SpawnBuilder.fromTemplate(template, location);
        customizer.accept(spawnBuilder);
        return spawn(spawnBuilder.template(), spawnBuilder.spawnOptions());
    }

    /**
     * Spawns a one-off controlled entity for the supplied logical base type.
     *
     * @param baseType the logical vanilla base type
     * @param location the spawn location
     * @return the spawned entity
     */
    public @NotNull SpawnedEntity<?> spawn(@NotNull CustomEntityBaseType baseType, @NotNull Location location) {
        return spawn(baseType, Entity.class, location, spawnBuilder -> {
        });
    }

    /**
     * Spawns a one-off controlled entity for the supplied logical base type with inline customization.
     *
     * @param baseType the logical vanilla base type
     * @param location the spawn location
     * @param customizer the spawn customizer
     * @return the spawned entity
     */
    public @NotNull SpawnedEntity<?> spawn(
            @NotNull CustomEntityBaseType baseType,
            @NotNull Location location,
            @NotNull Consumer<SpawnBuilder<Entity>> customizer
    ) {
        return spawn(baseType, Entity.class, location, customizer);
    }

    /**
     * Spawns a typed one-off controlled entity for the supplied logical base type.
     *
     * @param baseType the logical vanilla base type
     * @param bukkitType the Bukkit type exposed to plugin code
     * @param location the spawn location
     * @param customizer the spawn customizer
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned entity
     */
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType,
            @NotNull Location location,
            @NotNull Consumer<SpawnBuilder<T>> customizer
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        Objects.requireNonNull(bukkitType, "bukkitType cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(customizer, "customizer cannot be null");

        SpawnBuilder<T> spawnBuilder = SpawnBuilder.oneOff(null, baseType, bukkitType, location);
        customizer.accept(spawnBuilder);
        return spawn(spawnBuilder.template(), spawnBuilder.spawnOptions());
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> @NotNull SpawnedEntity<T> spawnUnchecked(
            @NotNull EntityTemplate<?> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        EntityTemplate<T> typedTemplate = (EntityTemplate<T>) template;
        RuntimeNativeEntityLifecycle<T> lifecycle = new RuntimeNativeEntityLifecycle<T>(
                typedTemplate,
                spawnOptions,
                minecraftVersion,
                transport,
                publicationBackend
        );
        SpawnedEntity<T> entity = strategies.freshSpawn().spawn(adapter, typedTemplate, spawnOptions, lifecycle);
        registerIfHooked(entity);
        return entity;
    }

    private static @NotNull VersionCapabilities resolveCapabilities(@NotNull VersionAdapter adapter) {
        if (adapter instanceof VersionMetadataProvider) {
            return ((VersionMetadataProvider) adapter).entityCapabilities();
        }
        return VersionCapabilities.unspecified();
    }

    private static @NotNull VersionBindings resolveBindings(@NotNull VersionAdapter adapter) {
        if (adapter instanceof VersionMetadataProvider) {
            return ((VersionMetadataProvider) adapter).entityBindings();
        }
        return VersionBindings.unspecified();
    }

    private static @NotNull EntityNetworkMetadataContract resolveNetworkMetadataContract(
            @NotNull VersionAdapter adapter
    ) {
        if (adapter instanceof VersionNetworkMetadataProvider) {
            return ((VersionNetworkMetadataProvider) adapter).entityNetworkMetadataContract();
        }
        return EntityNetworkMetadataContract.unspecified();
    }

    private void requireSupportedBaseType(@NotNull CustomEntityBaseType baseType) {
        if (!supports(baseType)) {
            throw new IllegalArgumentException(
                    "The active adapter does not support base type '" + baseType + "'."
            );
        }
    }

    private void registerIfHooked(@NotNull ControlledEntity<?> entity) {
        Object nativeHandle = resolveNativeHandle(entity.bukkitEntity());
        if (nativeHandle != null) {
            attachedEntityRegistry.register(entity.bukkitEntity(), nativeHandle, entity);
            attachRegistryCleanup(entity);
        }
    }

    private static @Nullable Object resolveNativeHandle(@NotNull Entity entity) {
        try {
            Method getHandleMethod = entity.getClass().getMethod("getHandle");
            getHandleMethod.setAccessible(true);
            return getHandleMethod.invoke(entity);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull EntityController<T> nullController() {
        return (EntityController<T>) tech.guilhermekaua.spigotboot.versions.runtime.controller.PassThroughEntityController.instance();
    }

    private static @NotNull CustomEntityBaseType resolveBaseType(@NotNull Entity entity) {
        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entity.getType());
        if (baseType == null) {
            throw new UnsupportedOperationException(
                    "The active adapter does not expose a logical base type for Bukkit type '" + entity.getType().name() + "'."
            );
        }
        return baseType;
    }
}
