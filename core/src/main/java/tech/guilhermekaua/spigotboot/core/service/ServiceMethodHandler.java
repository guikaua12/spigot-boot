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
package tech.guilhermekaua.spigotboot.core.service;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.service.configuration.ServiceProperties;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@RegisterMethodHandler
public class ServiceMethodHandler {
    private final ServiceProperties serviceProperties;

    @MethodHandler(
            classAnnotatedWith = Service.class,
            methodAnnotatedWith = Service.class
    )
    public Object handle(MethodHandlerContext context) {
        if (context.self() == null || context.thisMethod() == null) {
            return null;
        }

        if (context.thisMethod().getReturnType() != CompletableFuture.class) {
            throw new IllegalArgumentException("Service methods must return CompletableFuture");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<?> result = (CompletableFuture<?>) context.proceed().invoke(context.self(), context.args());

                if (result == null) {
                    return null;
                }

                return result.join();
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke service method: " + context.thisMethod().getName(), e);
            }
        }, serviceProperties.getExecutorService());
    }
}
