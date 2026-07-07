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
package tech.guilhermekaua.spigotboot.core.proxy;

import java.lang.reflect.Method;

/**
 * Intercepts method calls on a generated proxy instance.
 */
@FunctionalInterface
public interface MethodInterceptor {
    /**
     * @param self       the proxy instance receiving the call
     * @param thisMethod the method being invoked
     * @param proceed    the generated super-call method (never {@code null}); for abstract and
     *                   interface methods it does not invoke anything and returns the return
     *                   type's default value ({@code null}, {@code 0} or {@code false})
     * @param args       invocation arguments (owned by the caller; do not retain)
     * @return the value to return to the caller
     * @throws Throwable propagated to the caller unchanged
     */
    Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable;
}
