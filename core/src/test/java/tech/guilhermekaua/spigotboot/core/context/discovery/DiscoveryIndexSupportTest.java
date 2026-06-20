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
package tech.guilhermekaua.spigotboot.core.context.discovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryIndexSupportTest {

    @Test
    void resolve_dropsClassWhoseSupertypeIsAbsent_andKeepsTheRest() {
        // simulate PlaceholderAPI being absent: ComponentExtendingAbsentApi (the PAPIExpansion
        // analog) cannot link because its supertype AbsentApiType is blocked.
        ClassLoader loader = new BlockingClassLoader(getClass().getClassLoader(),
                ComponentExtendingAbsentApi.class.getName(), AbsentApiType.class.getName());

        Class<?>[] resolved = DiscoveryIndexSupport.resolve(loader,
                PresentComponent.class.getName(),
                ComponentExtendingAbsentApi.class.getName());

        assertArrayEquals(new Class<?>[]{PresentComponent.class}, resolved,
                "a class whose optional supertype is absent must be skipped, not poison the whole index");
    }

    @Test
    void resolve_dropsAbsentClass_andKeepsTheRest() {
        Class<?>[] resolved = DiscoveryIndexSupport.resolve(getClass().getClassLoader(),
                PresentComponent.class.getName(),
                "tech.guilhermekaua.spigotboot.core.context.discovery.DoesNotExist");

        assertArrayEquals(new Class<?>[]{PresentComponent.class}, resolved,
                "a genuinely absent class name must be skipped, not throw");
    }

    @Test
    void resolve_returnsEveryClassWhenAllResolve() {
        Class<?>[] resolved = DiscoveryIndexSupport.resolve(getClass().getClassLoader(),
                PresentComponent.class.getName(),
                AbsentApiType.class.getName());

        assertArrayEquals(new Class<?>[]{PresentComponent.class, AbsentApiType.class}, resolved,
                "all resolvable classes must be returned in order");
    }

    @Test
    void resolve_returnsEmptyForNoNames() {
        assertEquals(0, DiscoveryIndexSupport.resolve(getClass().getClassLoader()).length);
    }

    /**
     * Loads {@code childName} itself (child-first) so its supertype must be resolved through this
     * loader, while refusing to load {@code blockedName}. Defining the child therefore fails with
     * {@link NoClassDefFoundError}, reproducing the real "optional dependency absent at runtime"
     * scenario. Everything else delegates to the parent loader.
     */
    private static final class BlockingClassLoader extends ClassLoader {
        private final String childName;
        private final String blockedName;

        BlockingClassLoader(ClassLoader parent, String childName, String blockedName) {
            super(parent);
            this.childName = childName;
            this.blockedName = blockedName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(blockedName)) {
                throw new ClassNotFoundException(name + " is blocked to simulate an absent optional dependency");
            }
            if (name.equals(childName)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] bytes = readClassBytes(name);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private byte[] readClassBytes(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(path)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
