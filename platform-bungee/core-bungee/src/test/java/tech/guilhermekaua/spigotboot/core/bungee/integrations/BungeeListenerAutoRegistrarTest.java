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
package tech.guilhermekaua.spigotboot.core.bungee.integrations;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeListenerAutoRegistrarTest {

    public static class TestListener implements Listener {
    }

    private Context contextWith(Plugin plugin, Listener listener) {
        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getBeansByType(Listener.class)).thenReturn(Collections.singletonList(listener));
        return context;
    }

    @Test
    void registersListenerBeansAndUnregistersOnShutdown() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        TestListener listener = new TestListener();
        Context context = contextWith(plugin, listener);

        new BungeeListenerAutoRegistrar().onContextReady(context);

        verify(pluginManager).registerListener(plugin, listener);

        ArgumentCaptor<Runnable> shutdownHook = ArgumentCaptor.forClass(Runnable.class);
        verify(context).registerShutdownHook(shutdownHook.capture());
        shutdownHook.getValue().run();

        verify(pluginManager).unregisterListener(listener);
    }

    @Test
    void warnsWhenListenerIsAProxy() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getLogger()).thenReturn(logger);

        // the container hands interceptable beans back as javassist proxies; the proxy override drops the
        // @EventHandler annotation, and BungeeCord exposes no per-method registration to rebind it (known limitation).
        Listener proxiedListener = ComponentProxy.createProxy(
                TestListener.class, null, new Class<?>[0], new Object[0]);
        assertTrue(ProxyUtils.isProxy(proxiedListener), "precondition: the listener bean must be a javassist proxy");

        Context context = contextWith(plugin, proxiedListener);

        new BungeeListenerAutoRegistrar().onContextReady(context);

        verify(logger).warning(contains("Proxied listener"));
        verify(pluginManager).registerListener(plugin, proxiedListener);
    }

    // core bundles javassist relocated; a shipped class that references the original javassist.* package throws
    // NoClassDefFoundError on a real server even though tests stay green. proxy detection here must go through
    // ProxyUtils (name-based), never a direct javassist import.
    @Test
    void proxyAwareRegistrarClassMustNotReferenceUnrelocatedJavassistPackage() throws IOException {
        assertNoUnrelocatedJavassistReference(
                "tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrar.class");
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
}
