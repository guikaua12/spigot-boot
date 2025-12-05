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
package tech.guilhermekaua.spigotboot.core.context.component.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.lang.reflect.Method;
import java.util.List;

@RequiredArgsConstructor
public class ComponentProxy implements MethodHandler {
    private final Object realObject;

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> clazz, @Nullable Object realObject, Class<?>[] ctorArgs, Object[] ctorValues) {
        ProxyFactory factory = new ProxyFactory();
        if (clazz.isInterface()) {
            factory.setInterfaces(new Class<?>[]{clazz});
        } else {
            factory.setSuperclass(clazz);
        }

        try {
            return (T) factory.create(ctorArgs, ctorValues, new ComponentProxy(realObject));
        } catch (Throwable e) {
            throw new RuntimeException("Proxy creation failed for " + clazz.getName(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (thisMethod.getName().equals("toString")) {
            return self.getClass().getSimpleName() + "@" + Integer.toHexString(self.hashCode());
        }

        final MethodHandlerContext context = new MethodHandlerContext(self, thisMethod, proceed, args);

        List<RegisteredMethodHandler> handlers = MethodHandlerRegistry.getHandlersFor(context);
        for (RegisteredMethodHandler handler : handlers) {
            try {
                return handler.getRunnable().handle(context);
            } catch (Throwable t) {
                throw new RuntimeException("Error handling method " + thisMethod.getName() + " in " + self.getClass().getName(), t);
            }
        }

        return proceed.invoke(self, args);
    }
}