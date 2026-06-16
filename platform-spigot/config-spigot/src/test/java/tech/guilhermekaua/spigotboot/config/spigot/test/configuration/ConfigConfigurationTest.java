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
package tech.guilhermekaua.spigotboot.config.spigot.test.configuration;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.configuration.ConfigConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigConfigurationTest {

    @Test
    void bukkitTypeSerializers_registersBukkitSerializers() {
        TypeSerializerRegistryCustomizer customizer = new ConfigConfiguration().bukkitTypeSerializers();
        assertEquals(-100, customizer.getOrder());

        TypeSerializerRegistry registry = mock(TypeSerializerRegistry.class);
        customizer.customize(registry);

        // BukkitSerializers registers Material/Sound/World/Location; verify at least Material was registered.
        verify(registry).register(org.mockito.ArgumentMatchers.eq(Material.class),
                org.mockito.ArgumentMatchers.<TypeSerializer<Material>>any());
    }
}
