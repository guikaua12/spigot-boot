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
package tech.guilhermekaua.spigotboot.data.jdbc.methodHandler;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Query;
import tech.guilhermekaua.spigotboot.data.jdbc.registry.JdbcRepositoryRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.JdbcRepository;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.impl.JdbcRepositoryImpl;
import tech.guilhermekaua.spigotboot.data.jdbc.utils.JdbcTypeUtils;

import java.lang.reflect.Method;

@RequiredArgsConstructor
@RegisterMethodHandler
public class JdbcRepositoryMethodHandler {
    private final JdbcRepositoryRegistry repositoryRegistry;
    private final QueryMethodHandler queryMethodHandler;

    @MethodHandler(targetClass = JdbcRepository.class)
    public Object handle(MethodHandlerContext context) throws Throwable {
        if (context.self() == null || context.thisMethod() == null) {
            throw new IllegalStateException(String.format(
                    "Incomplete proxy context: missing self or method on JdbcRepositoryMethodHandler, self=%s method=%s",
                    context.self(),
                    context.thisMethod()
            ));
        }

        if (context.proceed() != null) {
            return context.proceed().invoke(context.self(), context.args());
        }

        Class<?> entityType = JdbcTypeUtils.resolveEntityType(context.self().getClass());

        // check if the method has @Query annotation
        Method interfaceMethod = findInterfaceMethod(context.self().getClass(), context.thisMethod());
        if (interfaceMethod != null && interfaceMethod.isAnnotationPresent(Query.class)) {
            JdbcRepositoryImpl<?, ?> repositoryImpl = repositoryRegistry.getRepository(entityType);
            if (repositoryImpl == null) {
                throw new IllegalStateException("No JDBC repository found for entity type: " + entityType.getName());
            }
            return queryMethodHandler.execute(interfaceMethod, context.args(), repositoryImpl.getMetadata(), repositoryImpl.getDialect());
        }

        JdbcRepositoryImpl<?, ?> repositoryImpl = repositoryRegistry.getRepository(entityType);

        if (repositoryImpl == null) {
            throw new IllegalStateException("No JDBC repository found for entity type: " + entityType.getName());
        }

        Method method = repositoryImpl.getClass().getMethod(context.thisMethod().getName(), context.thisMethod().getParameterTypes());
        method.setAccessible(true);
        return method.invoke(repositoryImpl, context.args());
    }

    private Method findInterfaceMethod(Class<?> proxyClass, Method proxyMethod) {
        for (Class<?> iface : proxyClass.getInterfaces()) {
            try {
                return iface.getMethod(proxyMethod.getName(), proxyMethod.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // continue searching
            }
        }
        return null;
    }
}
