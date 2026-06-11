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
package tech.guilhermekaua.spigotboot.config.spigot.test.proxy;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.proxy.ConfigProxy;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigProxyTest {

    @Test
    void toString_WithDefaultObjectMethod_IsIntercepted() {
        TestConfig proxy = createProxy();

        assertEquals(
                "ConfigProxy[TestConfig]@" + Integer.toHexString(System.identityHashCode(proxy)),
                proxy.toString()
        );
    }

    @Test
    void hashCode_WithDefaultObjectMethod_IsIntercepted() {
        TestConfig proxy = createProxy();

        assertEquals(System.identityHashCode(proxy), proxy.hashCode());
    }

    @Test
    void equals_WithSameInstance_ReturnsTrue() {
        TestConfig proxy = createProxy();

        assertEquals(proxy, proxy, "proxy equality must be reflexive");
    }

    @Test
    void equals_WithNull_ReturnsFalse() {
        TestConfig proxy = createProxy();

        assertNotEquals(null, proxy);
    }

    @Test
    void equals_WithNonProxyObject_ReturnsFalse() {
        TestConfig proxy = createProxy();

        assertNotEquals(new Object(), proxy);
    }

    @Test
    void equals_WithDifferentProxyInstance_ReturnsFalse() {
        TestConfig first = createProxy();
        TestConfig second = createProxy();

        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    @Test
    void toString_WithOverride_DelegatesToCurrentConfig() {
        OverriddenObjectMethodsConfig proxy = createProxy(
                OverriddenObjectMethodsConfig.class,
                new OverriddenObjectMethodsConfig()
        );

        assertEquals(OverriddenObjectMethodsConfig.TO_STRING_RESULT, proxy.toString());
    }

    @Test
    void hashCode_WithOverride_DelegatesToCurrentConfig() {
        OverriddenObjectMethodsConfig proxy = createProxy(
                OverriddenObjectMethodsConfig.class,
                new OverriddenObjectMethodsConfig()
        );

        assertEquals(OverriddenObjectMethodsConfig.HASH_CODE_RESULT, proxy.hashCode());
    }

    @Test
    void equals_WithOverride_DelegatesToCurrentConfig() {
        OverriddenObjectMethodsConfig proxy = createProxy(
                OverriddenObjectMethodsConfig.class,
                new OverriddenObjectMethodsConfig()
        );

        assertTrue(proxy.equals(OverriddenObjectMethodsConfig.EQUALS_MATCH_VALUE));
    }

    private @NotNull TestConfig createProxy() {
        return createProxy(TestConfig.class, new TestConfig());
    }

    private <T> @NotNull T createProxy(Class<T> configClass, T instance) {
        return ConfigProxy.createProxy(configClass, new StubConfigRef<>(configClass, instance));
    }

    static class TestConfig {
        public TestConfig() {
        }
    }

    public static class OverriddenObjectMethodsConfig {
        static final String TO_STRING_RESULT = "overridden-to-string";
        static final int HASH_CODE_RESULT = 1337;
        static final String EQUALS_MATCH_VALUE = "magic-equals";

        public OverriddenObjectMethodsConfig() {
        }

        @Override
        public String toString() {
            return TO_STRING_RESULT;
        }

        @Override
        public int hashCode() {
            return HASH_CODE_RESULT;
        }

        @SuppressWarnings("EqualsDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            return EQUALS_MATCH_VALUE.equals(obj);
        }
    }

    static final class StubConfigRef<T> implements ConfigRef<T> {
        private final Class<T> configClass;
        private final T instance;

        StubConfigRef(Class<T> configClass, T instance) {
            this.configClass = configClass;
            this.instance = instance;
        }

        @Override
        public @NotNull T get() {
            return instance;
        }

        @Override
        public void addListener(@NotNull Consumer<T> listener) {
        }

        @Override
        public void removeListener(@NotNull Consumer<T> listener) {
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public void reload() {
        }

        @Override
        public @NotNull Class<T> getConfigClass() {
            return configClass;
        }
    }
}
