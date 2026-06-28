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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.utils.testproxy.SpigotBootProxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyUtilsTest {

    public static class Sample {
        public String greet() {
            return "hello";
        }
    }

    public static class FakeProxy extends Sample implements SpigotBootProxy {}

    @Test
    void isProxyDetectsSpigotBootProxies() {
        Object proxy = new FakeProxy();
        assertTrue(ProxyUtils.isProxy(proxy), "a SpigotBootProxy instance must be detected as a proxy");
        assertFalse(ProxyUtils.isProxy(new Sample()), "a plain instance must not be detected as a proxy");
    }

    @Test
    void unwrapProxyTypeReturnsRealSuperclass() {
        assertSame(Sample.class, ProxyUtils.unwrapProxyType(FakeProxy.class), "proxy type must unwrap to its real superclass");
        assertSame(Sample.class, ProxyUtils.unwrapProxyType(Sample.class), "a non-proxy type must be returned unchanged");
    }

    @Test
    void getRealClassReturnsRealType() {
        Object proxy = new FakeProxy();
        assertSame(Sample.class, ProxyUtils.getRealClass(proxy), "proxy instance must resolve to its real class");
        Sample plain = new Sample();
        assertSame(Sample.class, ProxyUtils.getRealClass(plain), "plain instance must resolve to its own class");
    }
}
