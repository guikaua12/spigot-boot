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
package tech.guilhermekaua.spigotboot.data.ormLite.config.registry.discovery;

import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.reflection.DiscoveryService;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.data.ormLite.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class PersistenceConfigDiscoveryService implements DiscoveryService<Class<? extends PersistenceConfig>> {
    private final Plugin plugin;

    public Optional<Class<? extends PersistenceConfig>> discover() {
        final Set<Class<? extends PersistenceConfig>> configs = ReflectionUtils.getSubClassesOf(ProxyUtils.getRealClass(plugin), PersistenceConfig.class);

        if (configs.isEmpty()) return Optional.empty();

        if (configs.size() > 1) {
            throw new IllegalStateException("Multiple PersistenceConfig implementations currently not supported, found: (" + configs.stream().map(Class::getName).reduce((a, b) -> a + ", " + b).get() + ")");
        }

        return Optional.of(configs.iterator().next());
    }
}
