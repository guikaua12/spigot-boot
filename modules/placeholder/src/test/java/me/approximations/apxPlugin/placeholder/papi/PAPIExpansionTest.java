package me.approximations.apxPlugin.placeholder.papi;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.apxPlugin.placeholder.converter.TypeConverterManager;
import me.approximations.apxPlugin.placeholder.metadata.PlaceholderMetadata;
import me.approximations.apxPlugin.placeholder.registry.PlaceholderRegistry;
import me.approximations.apxPlugin.utils.ReflectionUtils;
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
