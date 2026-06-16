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
package tech.guilhermekaua.spigotboot.config.bungee.test.reload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigChangeListener;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigRef;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigSnapshot;
import tech.guilhermekaua.spigotboot.config.folder.ItemChangeType;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadBinder;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadInvoker;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnConfigReloadBinderTest {

    // ---- fixtures ----
    static class MainConfig { }
    static class Mob { }

    static class SimpleBean {
        Object received = "unset";
        int noArgCount = 0;

        @OnConfigReload(MainConfig.class)
        void onReloadNoArg() { noArgCount++; }

        @OnConfigReload
        void onReloadWithArg(MainConfig cfg) { received = cfg; }
    }

    static class FolderBean {
        FolderConfigItemChange<Mob> change;
        FolderConfigSnapshot<Mob> snapshot;

        @OnConfigReload
        void onChange(FolderConfigItemChange<Mob> change) { this.change = change; }

        @OnConfigReload
        void onSnapshot(FolderConfigSnapshot<Mob> snapshot) { this.snapshot = snapshot; }
    }

    static class BadBean {
        @OnConfigReload
        void twoParams(MainConfig a, MainConfig b) { }

        @OnConfigReload
        String nonVoid() { return ""; }

        @OnConfigReload(MainConfig.class)
        void folderPayloadOnSimpleTarget(FolderConfigItemChange<Mob> change) { }

        @OnConfigReload(Mob.class)
        void simplePayloadOnFolderTarget(Mob mob) { }

        @OnConfigReload(String.class)
        void unregisteredTarget() { }

        @OnConfigReload(MainConfig.class)
        static void staticHook() { }
    }

    private BungeeConfigManager cm;
    private OnConfigReloadBinder binder;

    @BeforeEach
    void setUp() {
        cm = mock(BungeeConfigManager.class);
        when(cm.getRegisteredConfigs()).thenReturn(Set.<Class<?>>of(MainConfig.class));
        when(cm.getRegisteredFolderConfigItemTypes()).thenReturn(Set.<Class<?>>of(Mob.class));
        when(cm.getFolderConfigNames(Mob.class)).thenReturn(Set.of("mobs"));
        binder = new OnConfigReloadBinder(cm, new OnConfigReloadInvoker(Logger.getLogger("test")));
    }

    private static Method method(Class<?> type, String name, Class<?>... params) throws Exception {
        return type.getDeclaredMethod(name, params);
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleInstanceMethodReceivesNewValue() throws Exception {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        SimpleBean bean = new SimpleBean();
        binder.bind(bean, method(SimpleBean.class, "onReloadWithArg", MainConfig.class));

        ArgumentCaptor<Consumer<MainConfig>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(ref).addListener(captor.capture());

        MainConfig fresh = new MainConfig();
        captor.getValue().accept(fresh);
        assertSame(fresh, bean.received);
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleNoArgMethodFiresOnReload() throws Exception {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        SimpleBean bean = new SimpleBean();
        binder.bind(bean, method(SimpleBean.class, "onReloadNoArg"));

        ArgumentCaptor<Consumer<MainConfig>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(ref).addListener(captor.capture());
        captor.getValue().accept(new MainConfig());
        assertEquals(1, bean.noArgCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    void folderChangeMethodReceivesChange() throws Exception {
        FolderConfigRef<Mob> ref = mock(FolderConfigRef.class);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(ref);

        FolderBean bean = new FolderBean();
        binder.bind(bean, method(FolderBean.class, "onChange", FolderConfigItemChange.class));

        ArgumentCaptor<FolderConfigChangeListener<Mob>> captor = ArgumentCaptor.forClass(FolderConfigChangeListener.class);
        verify(ref).addListener(captor.capture());

        FolderConfigItemChange<Mob> change = new FolderConfigItemChange<>(
                "mobs", Mob.class, "zombie", ItemChangeType.MODIFIED, new Mob(), new Mob());
        captor.getValue().onItemChange(change);
        assertSame(change, bean.change);
    }

    @Test
    @SuppressWarnings("unchecked")
    void folderSnapshotMethodReceivesCurrentSnapshot() throws Exception {
        FolderConfigRef<Mob> ref = mock(FolderConfigRef.class);
        FolderConfigSnapshot<Mob> snapshot = mock(FolderConfigSnapshot.class);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(ref);
        when(ref.get()).thenReturn(snapshot);

        FolderBean bean = new FolderBean();
        binder.bind(bean, method(FolderBean.class, "onSnapshot", FolderConfigSnapshot.class));

        ArgumentCaptor<FolderConfigChangeListener<Mob>> captor = ArgumentCaptor.forClass(FolderConfigChangeListener.class);
        verify(ref).addListener(captor.capture());

        captor.getValue().onItemChange(new FolderConfigItemChange<>(
                "mobs", Mob.class, "zombie", ItemChangeType.ADDED, null, new Mob()));
        assertSame(snapshot, bean.snapshot);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyValueZeroArgRegistersOnEverySimpleAndFolderConfig() throws Exception {
        ConfigRef<MainConfig> simpleRef = mock(ConfigRef.class);
        FolderConfigRef<Mob> folderRef = mock(FolderConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(simpleRef);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(folderRef);

        class AnyBean {
            @OnConfigReload
            void onAny() { }
        }

        binder.bind(new AnyBean(), method(AnyBean.class, "onAny"));

        verify(simpleRef).addListener(any());
        verify(folderRef).addListener(any());
    }

    @Test
    void rejectsTwoParameters() {
        BadBean bean = new BadBean();
        assertThrows(ConfigException.class,
                () -> binder.bind(bean, method(BadBean.class, "twoParams", MainConfig.class, MainConfig.class)));
    }

    @Test
    void rejectsNonVoid() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "nonVoid")));
    }

    @Test
    void rejectsFolderPayloadOnSimpleTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "folderPayloadOnSimpleTarget", FolderConfigItemChange.class)));
    }

    @Test
    void rejectsSimplePayloadOnFolderTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "simplePayloadOnFolderTarget", Mob.class)));
    }

    @Test
    void rejectsUnregisteredTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "unregisteredTarget")));
    }

    @Test
    void rejectsStaticMethod() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "staticHook")));
    }
}
