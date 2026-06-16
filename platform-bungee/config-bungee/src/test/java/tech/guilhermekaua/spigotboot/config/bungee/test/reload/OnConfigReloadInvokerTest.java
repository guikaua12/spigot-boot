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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadInvoker;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class OnConfigReloadInvokerTest {

    static class Bean {
        Object received = "unset";
        boolean noArgCalled = false;

        private void withArg(String value) {
            this.received = value;
        }

        void noArg() {
            this.noArgCalled = true;
        }

        void boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    private final OnConfigReloadInvoker invoker = new OnConfigReloadInvoker(Logger.getLogger("test"));

    @Test
    void invokesPrivateMethodWithArg() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("withArg", String.class);
        invoker.invoke(bean, m, new Object[]{"hello"});
        assertEquals("hello", bean.received);
    }

    @Test
    void invokesNoArgMethod() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("noArg");
        invoker.invoke(bean, m, new Object[0]);
        assertTrue(bean.noArgCalled);
    }

    @Test
    void swallowsAndLogsCallbackException() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("boom");
        // must not propagate — a throwing callback cannot break the reload listener loop
        assertDoesNotThrow(() -> invoker.invoke(bean, m, new Object[0]));
    }
}
