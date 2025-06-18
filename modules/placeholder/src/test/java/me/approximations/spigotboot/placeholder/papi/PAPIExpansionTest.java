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
package me.approximations.spigotboot.placeholder.papi;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.spigotboot.placeholder.converter.TypeConverterManager;
import me.approximations.spigotboot.placeholder.metadata.PlaceholderMetadata;
import me.approximations.spigotboot.placeholder.registry.PlaceholderRegistry;
import me.approximations.spigotboot.utils.ReflectionUtils;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PAPIExpansionTest {
    ServerMock server;
    MockPlugin plugin;

    @Mock
    PlaceholderRegistry placeholderRegistry;

    @Mock
    TypeConverterManager typeConverterManager;

    PAPIExpansion papiExpansion;

    @SuppressWarnings("deprecation")
    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");

        ReflectionUtils.setFieldValue(plugin.getDescription(), "version", "1.0.0");
        ReflectionUtils.setFieldValue(plugin.getDescription(), "authors", List.of("Author1", "Author2"));

        papiExpansion = new PAPIExpansion(plugin, placeholderRegistry, typeConverterManager);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testOnPlaceholderRequest_validPlaceholder() {
        Player player = server.addPlayer("TestPlayer");
        String params = "user_name";
        PlaceholderMetadata metadata = mock(PlaceholderMetadata.class);
        when(placeholderRegistry.findPlaceholderMetadata(params)).thenReturn(metadata);
        when(metadata.isPlaceholderApi()).thenReturn(true);
        when(metadata.getValue(player, params, typeConverterManager)).thenReturn("TestPlayer");

        String result = papiExpansion.onPlaceholderRequest(player, params);
        assertEquals("TestPlayer", result);
    }

    @Test
    public void testOnPlaceholderRequest_invalidPlaceholder() {
        Player player = server.addPlayer("TestPlayer");
        String params = "invalid_placeholder";
        when(placeholderRegistry.findPlaceholderMetadata(params)).thenReturn(null);

        String result = papiExpansion.onPlaceholderRequest(player, params);
        assertNull(result);
    }

    @Test
    public void testOnPlaceholderRequest_notPlaceholderApi() {
        Player player = server.addPlayer("TestPlayer");
        String params = "user_name";
        PlaceholderMetadata metadata = mock(PlaceholderMetadata.class);
        when(placeholderRegistry.findPlaceholderMetadata(params)).thenReturn(metadata);
        when(metadata.isPlaceholderApi()).thenReturn(false);

        String result = papiExpansion.onPlaceholderRequest(player, params);
        assertNull(result);
    }

    @Test
    public void testGetIdentifier() {
        assertEquals("TestPlugin", papiExpansion.getIdentifier());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", papiExpansion.getVersion());
    }

    @Test
    public void testGetAuthor() {
        assertEquals("Author1, Author2", papiExpansion.getAuthor());
    }
}
