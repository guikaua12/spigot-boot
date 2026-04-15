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
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.PassThroughEntityController;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime bridge shared between spawned custom entities and the shared controller pipeline.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public class RuntimeNativeEntityLifecycle<T extends Entity>
        extends AbstractRuntimeControlledEntity<T>
        implements SpawnedEntity<T> {
    private final EntityTemplate<T> template;
    private final SpawnOptions spawnOptions;
    private final RuntimeSpawnContext<T> spawnContext;
    private final AtomicBoolean spawned;

    public RuntimeNativeEntityLifecycle(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        this(template, spawnOptions, minecraftVersion, EntityTransportResolver.noop());
    }

    /**
     * Creates a new spawned runtime lifecycle with an explicit internal transport backend.
     *
     * @param template the entity template
     * @param spawnOptions the immutable spawn options
     * @param minecraftVersion the resolved Minecraft version
     * @param transport the internal semantic transport backend
     */
    public RuntimeNativeEntityLifecycle(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityTransport transport
    ) {
        this(template, spawnOptions, minecraftVersion, transport, EntityPublicationBackendResolver.noop());
    }

    /**
     * Creates a new spawned runtime lifecycle with explicit internal transport and publication backends.
     *
     * @param template the entity template
     * @param spawnOptions the immutable spawn options
     * @param minecraftVersion the resolved Minecraft version
     * @param transport the internal semantic transport backend
     * @param publicationBackend the internal publication backend
     */
    public RuntimeNativeEntityLifecycle(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityTransport transport,
            @NotNull EntityPublicationBackend publicationBackend
    ) {
        super(
                template.baseType(),
                minecraftVersion,
                PassThroughEntityController.instance(),
                transport,
                publicationBackend
        );
        this.template = Objects.requireNonNull(template, "template cannot be null");
        this.spawnOptions = Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        this.spawnContext = new RuntimeSpawnContext<T>(template, spawnOptions, minecraftVersion);
        this.spawned = new AtomicBoolean(false);
        setController(template.controllerFactory().create(spawnContext));
        setNetworkController(template.networkControllerFactory().create(spawnContext));
    }

    @Override
    public void onSpawn() {
        if (!spawned.compareAndSet(false, true)) {
            return;
        }
        template.initializer().initialize(this);
        if (!isRemoved()) {
            controller().onSpawn(this);
        }
    }

    @Override
    public @NotNull EntityTemplate<T> template() {
        return template;
    }

    @Override
    public @NotNull SpawnOptions spawnOptions() {
        return spawnOptions;
    }
}
