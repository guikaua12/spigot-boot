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
package me.approximations.spigotboot.placeholder.metadata;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.spigotboot.placeholder.annotations.Param;
import me.approximations.spigotboot.placeholder.converter.TypeConverterManager;
import me.approximations.spigotboot.placeholder.metadata.parser.PlaceholderParameterParser;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlaceholderMetadataTest {
    ServerMock server;
    MockPlugin plugin;

    Player player;
    MockedStatic<PlaceholderParameterParser> placeholderParameterParser;
    @Mock
    TypeConverterManager typeConverterManager;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");

        player = server.addPlayer("TestPlayer");
        placeholderParameterParser = mockStatic(PlaceholderParameterParser.class);

        lenient().when(typeConverterManager.convert(eq(String.class), anyString())).thenAnswer(invocation ->
                invocation.getArgument(1) != null ? invocation.getArgument(1) : null
        );
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
        placeholderParameterParser.close();
    }

    @Test
    public void testGetValue_withPlayerAndStringParam() throws Exception {
        class Handler {
            public String handle(Player player, String value) {
                return player.getName() + ":" + value;
            }
        }

        Handler handler = new Handler();
        Method method = Handler.class.getMethod("handle", Player.class, String.class);
        PlaceholderMetadata metadata = new PlaceholderMetadata(handler, method, "test_<value>", "desc", true);

        when(PlaceholderParameterParser.parse("test_<value>", "test_abc")).thenReturn(Map.of("value", "abc"));

        String result = metadata.getValue(player, "test_abc", typeConverterManager);
        assertEquals("TestPlayer:abc", result);
    }

    @Test
    public void testGetValue_withParamAnnotation() throws Exception {
        class Handler {
            public String handle(@Param("foo") String value) {
                return value;
            }
        }

        Handler handler = new Handler();
        Method method = Handler.class.getMethod("handle", String.class);
        PlaceholderMetadata metadata = new PlaceholderMetadata(handler, method, "test_<foo>", "desc", true);

        when(PlaceholderParameterParser.parse("test_<foo>", "test_bar")).thenReturn(Map.of("foo", "bar"));

        String result = metadata.getValue(player, "test_bar", typeConverterManager);
        assertEquals("bar", result);
    }

    @Test
    public void testGetValue_invocationException() throws Exception {
        class Handler {
            public String handle() {
                throw new RuntimeException("fail");
            }
        }

        Handler handler = new Handler();
        Method method = Handler.class.getMethod("handle");
        PlaceholderMetadata metadata = new PlaceholderMetadata(handler, method, "test", "desc", true);

        when(PlaceholderParameterParser.parse("test", "")).thenReturn(Map.of());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> metadata.getValue(player, "", typeConverterManager));
        assertTrue(ex.getMessage().contains("Failed to invoke placeholder method"));
    }

}
