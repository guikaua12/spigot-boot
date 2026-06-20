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
package tech.guilhermekaua.spigotboot.core.context.component.registry;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.discovery.AbsentApiType;
import tech.guilhermekaua.spigotboot.core.context.discovery.ComponentExtendingAbsentApi;
import tech.guilhermekaua.spigotboot.core.context.discovery.ComponentReferencingAbsentApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentRegistryTest {

    /**
     * Reproduces issue #95 at its mechanical root: a component that links fine on its own
     * ({@link ComponentReferencingAbsentApi}, the {@code PlaceholderRegistry} analog) but declares a
     * constructor parameter whose type ({@link ComponentExtendingAbsentApi}, the {@code PAPIExpansion}
     * analog) pulls in an absent optional dependency ({@link AbsentApiType}, the
     * {@code PlaceholderExpansion} analog). The cycle-detection pre-scan calls
     * {@code Class#getDeclaredConstructors()}, which forces the JVM to link the constructor parameter
     * types, throwing {@link NoClassDefFoundError}. This documents the unguarded behavior the scan
     * must tolerate; raw registration still surfaces the error.
     */
    @Test
    void registeringComponentWhoseConstructorParamNeedsAbsentOptionalDependency_throwsNoClassDefFoundError() throws Exception {
        ClassLoader loader = blockingLoaderHidingAbsentApi();
        Class<?> referencing = Class.forName(ComponentReferencingAbsentApi.class.getName(), false, loader);

        DependencyManager dependencyManager = new DependencyManager();

        assertThrows(NoClassDefFoundError.class,
                () -> dependencyManager.registerDependency(referencing, null, false, null, null),
                "resolving the constructor parameter types of a component that references an absent optional"
                        + " dependency must surface as a NoClassDefFoundError");
    }

    /**
     * The scan must not let a single component referencing an absent optional dependency abort the
     * whole boot: it skips that component (mirroring the discovery index's soft-dependency resilience)
     * instead of propagating the {@link NoClassDefFoundError}. The skipped component must not be left
     * registered as a bean.
     */
    @Test
    void registerScannedComponent_skipsComponentReferencingAbsentOptionalDependency() throws Exception {
        ClassLoader loader = blockingLoaderHidingAbsentApi();
        Class<?> referencing = Class.forName(ComponentReferencingAbsentApi.class.getName(), false, loader);

        DependencyManager dependencyManager = new DependencyManager();
        ComponentRegistry componentRegistry = new ComponentRegistry();

        boolean registered = assertDoesNotThrow(
                () -> componentRegistry.registerScannedComponent(referencing, dependencyManager),
                "a component referencing an absent optional dependency must be skipped, not abort the scan");

        assertFalse(registered, "the unlinkable component must report as skipped");
        assertFalse(dependencyManager.getBeanDefinitionRegistry().getRegisteredTypes().contains(referencing),
                "the skipped component must not be registered as a bean");
    }

    /**
     * Guards against over-eager skipping: a component that links fine must still be registered. Uses a
     * blocking loader for symmetry with the skip case, but loads only well-formed classes.
     */
    @Test
    void registerScannedComponent_registersComponentThatLinksFine() {
        DependencyManager dependencyManager = new DependencyManager();
        ComponentRegistry componentRegistry = new ComponentRegistry();

        boolean registered = componentRegistry.registerScannedComponent(AbsentApiType.class, dependencyManager);

        assertTrue(registered, "a component that links fine must be registered");
        assertTrue(dependencyManager.getBeanDefinitionRegistry().getRegisteredTypes().contains(AbsentApiType.class),
                "a component that links fine must be present in the bean registry");
    }

    private static ClassLoader blockingLoaderHidingAbsentApi() {
        Set<String> childLoaded = new HashSet<>();
        childLoaded.add(ComponentReferencingAbsentApi.class.getName());
        childLoaded.add(ComponentExtendingAbsentApi.class.getName());

        Set<String> blocked = new HashSet<>();
        blocked.add(AbsentApiType.class.getName());

        return new BlockingClassLoader(ComponentRegistryTest.class.getClassLoader(), childLoaded, blocked);
    }

    /**
     * Loads the {@code childLoaded} names itself (child-first) so their supertypes and member types
     * must resolve through this loader, while refusing the {@code blocked} names. Defining a child
     * whose hierarchy or members reference a blocked name therefore fails with
     * {@link NoClassDefFoundError}, reproducing the real "optional dependency absent at runtime"
     * scenario. Everything else delegates to the parent loader.
     */
    private static final class BlockingClassLoader extends ClassLoader {
        private final Set<String> childLoaded;
        private final Set<String> blocked;

        BlockingClassLoader(ClassLoader parent, Set<String> childLoaded, Set<String> blocked) {
            super(parent);
            this.childLoaded = childLoaded;
            this.blocked = blocked;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (blocked.contains(name)) {
                throw new ClassNotFoundException(name + " is blocked to simulate an absent optional dependency");
            }
            if (childLoaded.contains(name)) {
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
