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
package me.approximations.spigotboot.data.ormLite.methodHandler;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations.RegisterMethodHandler;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import me.approximations.spigotboot.data.ormLite.registry.OrmLiteRepositoryRegistry;
import me.approximations.spigotboot.data.ormLite.repository.OrmLiteRepository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@RequiredArgsConstructor
@RegisterMethodHandler
public class OrmLiteRepositoryMethodHandler {
    private final OrmLiteRepositoryRegistry repositoryRegistry;

    @MethodHandler(targetClass = OrmLiteRepository.class)
    public Object handle(MethodHandlerContext context) throws Throwable {
        if (context.self() == null || context.thisMethod() == null) {
            return null;
        }

        try {
            return context.proceed().invoke(context.self(), context.args());
        } catch (IllegalAccessException | IllegalArgumentException | NullPointerException e) {
            Class<?> self = context.self().getClass().getInterfaces()[0];
            Type[] types = ((ParameterizedType) self.getGenericInterfaces()[0]).getActualTypeArguments();
            Class<?> entityType = (Class<?>) types[0];

            OrmLiteRepository<?, ?> repositoryImpl = repositoryRegistry.getDao(entityType);

            if (repositoryImpl == null) {
                throw new IllegalStateException("No repository found for entity type: " + entityType.getName());
            }

            Method method = repositoryImpl.getClass().getMethod(context.thisMethod().getName(), context.thisMethod().getParameterTypes());
            method.setAccessible(true);
            return method.invoke(repositoryImpl, context.args());
        }
    }
}
