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
package tech.guilhermekaua.spigotboot.data.ormLite.config.registry;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.di.annotations.Component;
import tech.guilhermekaua.spigotboot.core.di.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.reflection.DiscoveryService;
import tech.guilhermekaua.spigotboot.data.ormLite.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.data.ormLite.config.registry.discovery.PersistenceConfigDiscoveryService;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class PersistenceConfigRegistry {
    private final AtomicReference<PersistenceConfig> persistenceConfigAtomicReference = new AtomicReference<>();
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public PersistenceConfig initialize() {
        final DiscoveryService<Class<? extends PersistenceConfig>> persistenceConfigDiscoveryService = new PersistenceConfigDiscoveryService(plugin);
        Class<? extends PersistenceConfig> persistenceConfigClass = persistenceConfigDiscoveryService.discover().orElseThrow(
                () -> new IllegalStateException("data-orm-lite is on classpath but no persistence configuration is found. Ensure that a valid PersistenceConfig is provided.")
        );

        dependencyManager.registerDependency(persistenceConfigClass);
        PersistenceConfig persistenceConfig = dependencyManager.resolveDependency(persistenceConfigClass);

        persistenceConfigAtomicReference.set(persistenceConfig);

        return persistenceConfig;
    }

    public PersistenceConfig getPersistenceConfig() {
        return persistenceConfigAtomicReference.get();
    }
}
