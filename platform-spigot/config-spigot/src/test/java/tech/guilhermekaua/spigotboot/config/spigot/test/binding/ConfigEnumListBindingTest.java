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

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

/**
 * reproduces binding of a {@code List} whose element type is an enum that has a
 * registered {@link TypeSerializer}.
 */
@ExtendWith(MockitoExtension.class)
class ConfigEnumListBindingTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(ConfigEnumListBindingTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);

        TypeSerializerRegistryCustomizer colorSerializer =
                registry -> registry.register(Color.class, new ColorSerializer());

        configManager = new SpigotConfigManager(plugin, null, Collections.singletonList(colorSerializer));
    }

    @Test
    void bindsScalarEnumWithRegisteredSerializer() throws IOException {
        writeYaml("scalar.yml", "favorite: GREEN\n");
        configManager.register(ScalarEnumConfig.class);
        configManager.initializeAll();

        ScalarEnumConfig config = configManager.get(ScalarEnumConfig.class);

        assertEquals(Color.GREEN, config.getFavorite());
    }

    @Test
    void bindsListOfEnumWithRegisteredSerializer() throws IOException {
        writeYaml("list.yml", "palette:\n  - RED\n  - BLUE\n");
        configManager.register(ListEnumConfig.class);
        configManager.initializeAll();

        ListEnumConfig config = configManager.get(ListEnumConfig.class);

        assertEquals(Arrays.asList(Color.RED, Color.BLUE), config.getPalette());
    }

    private void writeYaml(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }

    public enum Color {
        RED, GREEN, BLUE
    }

    /**
     * simple serializer that maps a scalar string to a {@link Color} constant by name.
     */
    public static class ColorSerializer implements TypeSerializer<Color> {
        @Override
        public Color deserialize(ConfigNode node, Class<Color> type) {
            String value = node.get(String.class);
            return value == null ? null : Color.valueOf(value);
        }

        @Override
        public void serialize(Color value, MutableConfigNode node) {
            node.set(value.name());
        }
    }

    @Config(value = "scalar.yml", name = "scalar", generateDefaults = false)
    public static class ScalarEnumConfig {
        private Color favorite = Color.RED;

        public ScalarEnumConfig() {
        }

        public Color getFavorite() {
            return favorite;
        }
    }

    @Config(value = "list.yml", name = "list", generateDefaults = false)
    public static class ListEnumConfig {
        private List<Color> palette = new ArrayList<>();

        public ListEnumConfig() {
        }

        public List<Color> getPalette() {
            return palette;
        }
    }
}
