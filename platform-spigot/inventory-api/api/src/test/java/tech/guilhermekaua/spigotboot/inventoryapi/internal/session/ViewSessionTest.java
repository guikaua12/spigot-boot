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
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ViewSessionTest {

    private ServerMock server;
    private PlayerMock player;
    private ProbeView view;
    private ViewConfig config;
    private RegisteredView registered;

    static final class ProbeView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("tester");
        view = new ProbeView();
        config = new ViewConfigBuilder().title("t").rows(1).build();
        registered = new RegisteredView(ProbeView.class, view, config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession newSession() {
        return new ViewSession(player, registered, ViewArguments.empty(), new StateStore(0));
    }

    @Test
    void registeredView_exposesTypeInstanceAndConfig() {
        assertEquals(ProbeView.class, registered.type());
        assertSame(view, registered.instance());
        assertSame(config, registered.config());
    }

    @Test
    void constructor_exposesCollaborators_andStartsOpening() {
        ViewArguments arguments = ViewArguments.of("key", "value");
        StateStore store = new StateStore(0);

        ViewSession session = new ViewSession(player, registered, arguments, store);

        assertSame(player, session.player());
        assertSame(registered, session.registered());
        assertSame(arguments, session.arguments());
        assertSame(store, session.stateStore());
        assertEquals(ViewSession.Status.OPENING, session.status());
        assertFalse(session.isActive());
    }

    @Test
    void status_followsEngineTransitions() {
        ViewSession session = newSession();

        assertEquals(ViewSession.Status.OPENING, session.status());
        session.status(ViewSession.Status.ACTIVE);
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        session.status(ViewSession.Status.TRANSITIONING);
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        // a drained deferral that neither closed nor replaced the session reactivates it
        session.status(ViewSession.Status.ACTIVE);
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        session.status(ViewSession.Status.CLOSED);
        assertEquals(ViewSession.Status.CLOSED, session.status());
    }

    @Test
    void isActive_trueOnlyForActiveStatus() {
        ViewSession session = newSession();

        for (ViewSession.Status status : ViewSession.Status.values()) {
            session.status(status);
            assertEquals(status == ViewSession.Status.ACTIVE, session.isActive(),
                    "isActive() for status " + status);
        }
    }

    @Test
    void components_returnsOneStableComponentTable() {
        ViewSession session = newSession();

        assertSame(session.components(), session.components());
        assertTrue(session.components().all().isEmpty());
    }

    @Test
    void deferredOps_isOneStableMutableList() {
        ViewSession session = newSession();
        Runnable op = () -> {
        };

        session.deferredOps().add(op);

        assertSame(session.deferredOps(), session.deferredOps());
        assertEquals(1, session.deferredOps().size());
        assertSame(op, session.deferredOps().remove(0));
        assertTrue(session.deferredOps().isEmpty());
    }

    @Test
    void inventory_nullUntilContainerCreated_thenRoundTrips() {
        ViewSession session = newSession();
        assertNull(session.inventory());

        Inventory inventory = Bukkit.createInventory(null, 9);
        session.inventory(inventory);

        assertSame(inventory, session.inventory());
    }

    @Test
    void effectiveConfig_defaultsToRegisteredConfig_untilOverridden() {
        ViewSession session = newSession();
        assertSame(config, session.effectiveConfig());

        ViewConfig overridden = config.withOverrides("per-open", 2);
        session.effectiveConfig(overridden);

        assertSame(overridden, session.effectiveConfig());
    }

    @Test
    void layout_nullUntilResolved_thenRoundTrips() {
        ViewSession session = newSession();
        assertNull(session.layout());

        ResolvedLayout resolved = ResolvedLayout.resolve(config);
        session.layout(resolved);

        assertSame(resolved, session.layout());
    }

    @Test
    void updateTask_nullByDefault_settableAndClearable() {
        ViewSession session = newSession();
        assertNull(session.updateTask());

        BukkitTask task = mock(BukkitTask.class);
        session.updateTask(task);
        assertSame(task, session.updateTask());

        session.updateTask(null);
        assertNull(session.updateTask());
    }
}
