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
package tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MethodHandlerRegistry {
    private static final List<RegisteredMethodHandler> handlers = new ArrayList<>();

    private MethodHandlerRegistry() {
    }

    public static void registerAll(List<RegisteredMethodHandler> handlers) {
        MethodHandlerRegistry.handlers.addAll(handlers);
    }

    public static List<RegisteredMethodHandler> getHandlersFor(@NotNull MethodHandlerContext context) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(context))
                .collect(Collectors.toList());
    }
}