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
package tech.guilhermekaua.spigotboot.config.spigot.test.reload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnConfigReloadProcessorTest {

    static class MainConfig { }

    static class Base {
        // public so the getMethods()-based scan (inherited public + own declared) finds it
        @OnConfigReload(MainConfig.class)
        public void inherited() { }
    }

    static class Bean extends Base {
        @OnConfigReload(MainConfig.class)
        void own() { }
    }

    static class Plain { }

    private SpigotConfigManager cm;
    private OnConfigReloadProcessor processor;

    @BeforeEach
    void setUp() {
        cm = mock(SpigotConfigManager.class);
        when(cm.getRegisteredConfigs()).thenReturn(Set.<Class<?>>of(MainConfig.class));
        when(cm.getRegisteredFolderConfigItemTypes()).thenReturn(Set.<Class<?>>of());
        processor = new OnConfigReloadProcessor(cm, Logger.getLogger("test"));
    }

    @Test
    void runsAfterProxyProcessor() {
        assertTrue(processor.getOrder() > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registersListenersForOwnAndInheritedMethodsAndReturnsSameInstance() {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        Bean bean = new Bean();
        BeanDefinition def = mock(BeanDefinition.class);
        DependencyManager dm = mock(DependencyManager.class);

        Object result = processor.postProcess(def, bean, dm);

        assertSame(bean, result);
        // one binding for own() + one for inherited() = 2 listeners on the same ref
        verify(ref, times(2)).addListener(any());
    }

    @Test
    void ignoresBeansWithoutAnnotatedMethods() {
        Plain plain = new Plain();
        Object result = processor.postProcess(mock(BeanDefinition.class), plain, mock(DependencyManager.class));
        assertSame(plain, result);
        verify(cm, never()).getRef(any());
    }
}
