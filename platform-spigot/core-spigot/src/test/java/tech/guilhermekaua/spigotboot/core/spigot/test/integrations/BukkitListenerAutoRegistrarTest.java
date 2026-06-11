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
package tech.guilhermekaua.spigotboot.core.spigot.test.integrations;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.spigot.integrations.BukkitListenerAutoRegistrar;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitListenerAutoRegistrarTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        HandlerList.unregisterAll();
        MockBukkit.unmock();
    }

    @Test
    void proxiedListenerStillReceivesEvents() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");

        // a Listener bean that, like any bean carrying interceptable methods, the container hands back as a
        // javassist proxy. the proxy subclass overrides the @EventHandler method and the override loses the
        // annotation, so registering the raw proxy makes bukkit discover zero handlers.
        Listener proxiedListener = ComponentProxy.createProxy(
                CountingListener.class, null, new Class<?>[0], new Object[0]);
        assertTrue(ProxyUtils.isProxy(proxiedListener),
                "precondition: the listener bean must be a javassist proxy");
        assertInstanceOf(CountingListener.class, proxiedListener);

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getBeansByType(Listener.class)).thenReturn(Collections.singletonList(proxiedListener));

        new BukkitListenerAutoRegistrar().onContextReady(context);

        server.getPluginManager().callEvent(new CountingEvent());

        assertEquals(1, ((CountingListener) proxiedListener).getHits(),
                "@EventHandler methods on a proxied listener must still fire");
    }

    @Test
    void plainListenerStillReceivesEvents() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");

        CountingListener listener = new CountingListener();

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getBeansByType(Listener.class)).thenReturn(Collections.singletonList(listener));

        new BukkitListenerAutoRegistrar().onContextReady(context);

        server.getPluginManager().callEvent(new CountingEvent());

        assertEquals(1, listener.getHits(),
                "@EventHandler methods on a plain listener must still fire");
    }

    // a misconfigured @EventHandler on a proxied listener must not vanish without a trace: bukkit's native path
    // logs a SEVERE diagnostic for an invalid handler signature, and the proxied path must keep that parity.
    @Test
    void invalidEventHandlerSignatureOnProxiedListenerIsLogged() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");

        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(capture);

        Listener proxiedListener = ComponentProxy.createProxy(
                InvalidSignatureListener.class, null, new Class<?>[0], new Object[0]);

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getBeansByType(Listener.class)).thenReturn(Collections.singletonList(proxiedListener));

        new BukkitListenerAutoRegistrar().onContextReady(context);

        assertTrue(
                records.stream().anyMatch(record -> record.getLevel() == Level.SEVERE
                        && record.getMessage() != null
                        && record.getMessage().contains("invalid EventHandler method signature")),
                "an invalid @EventHandler signature on a proxied listener must be logged, matching bukkit's native path");
    }

    // core bundles javassist relocated; a shipped class that references the original javassist.* package throws
    // NoClassDefFoundError on a real server even though tests stay green. proxy detection here must go through
    // ProxyUtils (name-based), never a direct javassist import.
    @Test
    void proxyAwareRegistrarClassesMustNotReferenceUnrelocatedJavassistPackage() throws IOException {
        String[] proxyAwareClasses = {
                "tech/guilhermekaua/spigotboot/core/spigot/integrations/BukkitListenerAutoRegistrar.class",
                "tech/guilhermekaua/spigotboot/core/spigot/integrations/ProxiedListenerEventBinder.class",
        };

        for (String classResource : proxyAwareClasses) {
            assertNoUnrelocatedJavassistReference(classResource);
        }
    }

    private void assertNoUnrelocatedJavassistReference(String classResource) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classResource)) {
            assertNotNull(in, "compiled class not found on the test classpath: " + classResource);

            byte[] bytecode = in.readAllBytes();
            String constantPool = new String(bytecode, StandardCharsets.ISO_8859_1);

            assertFalse(constantPool.contains("javassist/"),
                    classResource + " references the unrelocated javassist package; use ProxyUtils instead.");
        }
    }

    public static class CountingListener implements Listener {
        private int hits = 0;

        @EventHandler
        public void onCountingEvent(CountingEvent event) {
            hits++;
        }

        public int getHits() {
            return hits;
        }
    }

    public static class InvalidSignatureListener implements Listener {
        // annotated as a handler but missing the single Event parameter, so it can never be registered.
        @EventHandler
        public void onBrokenHandler() {
        }
    }

    public static class CountingEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @NotNull
        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}
