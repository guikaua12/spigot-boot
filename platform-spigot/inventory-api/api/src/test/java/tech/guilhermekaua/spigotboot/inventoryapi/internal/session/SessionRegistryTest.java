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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {

    private ServerMock server;
    private SessionRegistry registry;
    private RegisteredView registered;

    static final class ProbeView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        registry = new SessionRegistry();
        registered = new RegisteredView(ProbeView.class, new ProbeView(),
                new ViewConfigBuilder().title("t").rows(1).build());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionFor(PlayerMock player) {
        return new ViewSession(player, registered, ViewArguments.empty(), new StateStore(0));
    }

    @Test
    void registerThenFind_returnsSessionByPlayerId() {
        PlayerMock player = server.addPlayer("first");
        ViewSession session = sessionFor(player);

        registry.register(session);

        assertSame(session, registry.find(player.getUniqueId()).orElseThrow());
    }

    @Test
    void find_unknownPlayer_returnsEmpty() {
        assertFalse(registry.find(UUID.randomUUID()).isPresent());
    }

    @Test
    void unregister_removesTheMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession session = sessionFor(player);
        registry.register(session);

        registry.unregister(session);

        assertFalse(registry.find(player.getUniqueId()).isPresent());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void register_samePlayerAgain_replacesTheMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession previous = sessionFor(player);
        ViewSession replacement = sessionFor(player);
        registry.register(previous);

        registry.register(replacement);

        assertSame(replacement, registry.find(player.getUniqueId()).orElseThrow());
        assertEquals(1, registry.all().size());
    }

    @Test
    void unregister_staleReplacedSession_keepsTheCurrentMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession previous = sessionFor(player);
        ViewSession replacement = sessionFor(player);
        registry.register(previous);
        registry.register(replacement);

        registry.unregister(previous);

        assertSame(replacement, registry.find(player.getUniqueId()).orElseThrow());
    }

    @Test
    void all_containsEveryRegisteredSession_andIsUnmodifiable() {
        ViewSession first = sessionFor(server.addPlayer("first"));
        ViewSession second = sessionFor(server.addPlayer("second"));
        registry.register(first);
        registry.register(second);

        Collection<ViewSession> all = registry.all();

        assertEquals(2, all.size());
        assertTrue(all.contains(first));
        assertTrue(all.contains(second));
        assertThrows(UnsupportedOperationException.class, all::clear);
    }
}
