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
package tech.guilhermekaua.spigotboot.utils;

import javassist.util.proxy.ProxyFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyUtilsTest {

    public static class Sample {
        public String greet() {
            return "hello";
        }
    }

    /**
     * regression for the shaded-javassist NoClassDefFoundError: ProxyUtils ships inside downstream
     * plugins, but javassist is only present at runtime under the relocated
     * {@code tech.guilhermekaua.spigotboot.shaded.javassist} package. if the compiled class carries a
     * hard reference to the original {@code javassist/...} package it throws
     * {@code NoClassDefFoundError: javassist/util/proxy/ProxyObject} on a real server while still
     * passing tests (where the original javassist is on the classpath). proxy detection must therefore
     * not bake the original package name into the bytecode.
     */
    @Test
    void proxyUtilsClassMustNotReferenceUnrelocatedJavassistPackage() throws Exception {
        byte[] bytes;
        try (InputStream in = ProxyUtils.class.getResourceAsStream("ProxyUtils.class")) {
            assertNotNull(in, "could not load ProxyUtils.class to inspect");
            bytes = in.readAllBytes();
        }

        // type references in the constant pool use the internal slashed form (e.g. javassist/util/proxy/ProxyObject);
        // a string literal such as "javassist.util.proxy.ProxyObject" uses dots and is fine.
        String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(
                constantPool.contains("javassist/"),
                "ProxyUtils must not reference the un-relocated javassist package; detect proxies by interface name instead"
        );
    }

    @Test
    void isProxyDetectsJavassistProxies() throws Exception {
        Object proxy = newJavassistProxy(Sample.class);
        assertTrue(ProxyUtils.isProxy(proxy), "a javassist proxy instance must be detected as a proxy");
        assertFalse(ProxyUtils.isProxy(new Sample()), "a plain instance must not be detected as a proxy");
    }

    @Test
    void unwrapProxyTypeReturnsRealSuperclass() throws Exception {
        Class<?> proxyClass = newJavassistProxy(Sample.class).getClass();
        assertSame(Sample.class, ProxyUtils.unwrapProxyType(proxyClass), "proxy type must unwrap to its real superclass");
        assertSame(Sample.class, ProxyUtils.unwrapProxyType(Sample.class), "a non-proxy type must be returned unchanged");
    }

    @Test
    void getRealClassReturnsRealType() throws Exception {
        Object proxy = newJavassistProxy(Sample.class);
        assertSame(Sample.class, ProxyUtils.getRealClass(proxy), "proxy instance must resolve to its real class");
        Sample plain = new Sample();
        assertSame(Sample.class, ProxyUtils.getRealClass(plain), "plain instance must resolve to its own class");
    }

    private static Object newJavassistProxy(Class<?> superclass) throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(superclass);
        Class<?> proxyClass = factory.createClass();
        return proxyClass.getDeclaredConstructor().newInstance();
    }
}
