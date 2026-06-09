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
package tech.guilhermekaua.spigotboot.inventoryapi.internal;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflective dispatcher for the protected {@link View} lifecycle handlers. Every handler
 * {@link Method} is resolved and made accessible once in the static initializer, so the
 * engine phases and the registry share one cached handle per handler instead of resolving
 * it on every dispatch.
 */
@ApiStatus.Internal
public final class HandlerInvoker {

    /** Cached handle of {@code View.onInit(ViewConfigBuilder)}. */
    public static final Method ON_INIT;

    /** Cached handle of {@code View.onOpen(OpenContext)}. */
    public static final Method ON_OPEN;

    /** Cached handle of {@code View.onFirstRender(RenderContext)}. */
    public static final Method ON_FIRST_RENDER;

    /** Cached handle of {@code View.onUpdate(UpdateContext)}. */
    public static final Method ON_UPDATE;

    /** Cached handle of {@code View.onClick(SlotClickContext)}. */
    public static final Method ON_CLICK;

    /** Cached handle of {@code View.onClose(CloseContext)}. */
    public static final Method ON_CLOSE;

    static {
        ON_INIT = resolve("onInit", ViewConfigBuilder.class);
        ON_OPEN = resolve("onOpen", OpenContext.class);
        ON_FIRST_RENDER = resolve("onFirstRender", RenderContext.class);
        ON_UPDATE = resolve("onUpdate", UpdateContext.class);
        ON_CLICK = resolve("onClick", SlotClickContext.class);
        ON_CLOSE = resolve("onClose", CloseContext.class);
    }

    private HandlerInvoker() {
    }

    /**
     * Invokes a cached handler on a view, unwrapping {@link InvocationTargetException} so
     * the handler's own failure surfaces: unchecked throwables are rethrown as-is and
     * checked ones are wrapped in {@link IllegalStateException}.
     *
     * @param handler the cached handler method, one of the constants of this class
     * @param view    the view to dispatch on
     * @param arg     the single handler argument (builder or per-phase context)
     * @throws IllegalStateException when the handler throws a checked exception or the
     *                               reflective dispatch itself fails
     */
    public static void invoke(@NotNull Method handler, @NotNull View view, @NotNull Object arg) {
        try {
            handler.invoke(view, arg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(handler.getName() + " failed for view "
                    + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch " + handler.getName()
                    + " for view " + view.getClass().getName(), ex);
        }
    }

    // handlers are protected on the public View type; resolve once and open access here
    private static Method resolve(String name, Class<?> parameterType) {
        try {
            Method method = View.class.getDeclaredMethod(name, parameterType);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
