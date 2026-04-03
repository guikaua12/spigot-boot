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
package tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context;

import java.lang.reflect.Method;
import java.util.Objects;

public class MethodHandlerContext {
    private final Object self;
    private final Method thisMethod;
    private final Method proceed;
    private final Object[] args;
    private final InvocationStep nextInvocation;

    public MethodHandlerContext(Object self, Method thisMethod, Method proceed, Object[] args) {
        this(self, thisMethod, proceed, args, createDefaultInvocation(self, thisMethod, proceed, args));
    }

    public MethodHandlerContext(Object self, Method thisMethod, Method proceed, Object[] args, InvocationStep nextInvocation) {
        this.self = self;
        this.thisMethod = thisMethod;
        this.proceed = proceed;
        this.args = args == null ? new Object[0] : args;
        this.nextInvocation = Objects.requireNonNull(nextInvocation, "nextInvocation cannot be null");
    }

    public Object self() {
        return self;
    }

    /**
     * Returns metadata about the intercepted method.
     *
     * @deprecated This accessor does not continue the handler chain. Handlers
     * should call {@link #invokeNext()} to continue execution.
     */
    @Deprecated
    public Method thisMethod() {
        return thisMethod;
    }

    /**
     * Returns metadata about the underlying invocation target method.
     *
     * @deprecated This accessor does not continue the handler chain. Handlers
     * should call {@link #invokeNext()} to continue execution.
     */
    @Deprecated
    public Method proceed() {
        return proceed;
    }

    public Object[] args() {
        return args;
    }

    /**
     * Continue to the next matching handler in the chain. This is the
     * continuation method handlers should call. If none remain, this invokes
     * the underlying target method.
     *
     * @return the return object of the next handler
     */
    public Object invokeNext() throws Throwable {
        return nextInvocation.invoke();
    }

    private static InvocationStep createDefaultInvocation(Object self, Method thisMethod, Method proceed, Object[] args) {
        return () -> {
            Method targetMethod = proceed != null ? proceed : thisMethod;
            if (targetMethod == null) {
                throw new IllegalStateException("No target method available for invocation");
            }

            targetMethod.setAccessible(true);
            return targetMethod.invoke(self, args == null ? new Object[0] : args);
        };
    }

    @FunctionalInterface
    public interface InvocationStep {
        Object invoke() throws Throwable;
    }
}
