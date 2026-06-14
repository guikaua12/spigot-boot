/*
 * The MIT License
 * Copyright © 2026 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.config.spigot.test.binding;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.lenient;

/**
 * reproduces the binding of {@link Material} values from config, both as a scalar field
 * and as a {@code List<Material>}, using the built-in {@link BukkitSerializers}.
 */
@ExtendWith(MockitoExtension.class)
class MaterialSerializerBindingTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(MaterialSerializerBindingTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);

        TypeSerializerRegistryCustomizer bukkit = BukkitSerializers::registerAll;
        configManager = new SpigotConfigManager(plugin, null, Collections.singletonList(bukkit));
    }

    @Test
    void bindsListOfMaterialFromNames() throws IOException {
        writeYaml("mat.yml", "blacklisted_items:\n  - BEDROCK\n  - BAMBOO\n");
        configManager.register(MaterialListConfig.class);
        configManager.initializeAll();

        MaterialListConfig config = configManager.get(MaterialListConfig.class);

        assertEquals(Arrays.asList(Material.BEDROCK, Material.BAMBOO), config.getBlacklistedItems());
    }

    @Test
    void bindsScalarMaterialFromName() throws IOException {
        writeYaml("scalarmat.yml", "favorite: BEDROCK\n");
        configManager.register(ScalarMaterialConfig.class);
        configManager.initializeAll();

        ScalarMaterialConfig config = configManager.get(ScalarMaterialConfig.class);

        assertEquals(Material.BEDROCK, config.getFavorite());
        assertFalse(config.getFavorite().isLegacy(), "expected a modern material, not a LEGACY_ one");
    }

    private void writeYaml(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }

    @Config(value = "mat.yml", name = "mat", generateDefaults = false)
    public static class MaterialListConfig {
        private List<Material> blacklistedItems = new ArrayList<>();

        public MaterialListConfig() {
        }

        public List<Material> getBlacklistedItems() {
            return blacklistedItems;
        }
    }

    @Config(value = "scalarmat.yml", name = "scalarmat", generateDefaults = false)
    public static class ScalarMaterialConfig {
        private Material favorite;

        public ScalarMaterialConfig() {
        }

        public Material getFavorite() {
            return favorite;
        }
    }
}
