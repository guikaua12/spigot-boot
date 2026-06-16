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
package tech.guilhermekaua.spigotboot.config.bungee.reload;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Invokes {@code @OnConfigReload} callback methods reflectively, isolating any failure so it never
 * escapes a config reload listener.
 */
public class OnConfigReloadInvoker {

    private final Logger logger;

    /**
     * Creates the invoker.
     *
     * @param logger the plugin logger, not null
     */
    public OnConfigReloadInvoker(@NotNull Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Invokes the given callback. Exceptions thrown by the callback are logged, not rethrown.
     *
     * @param bean   the bean instance to invoke on, not null
     * @param method the callback method, not null
     * @param args   the arguments to pass, not null (may be empty)
     */
    public void invoke(@NotNull Object bean, @NotNull Method method, @NotNull Object[] args) {
        Objects.requireNonNull(bean, "bean cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(args, "args cannot be null");
        try {
            method.setAccessible(true);
            method.invoke(bean, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.log(Level.SEVERE, describe(bean, method), cause);
        } catch (Exception e) {
            logger.log(Level.SEVERE, describe(bean, method), e);
        }
    }

    private static String describe(Object bean, Method method) {
        return "Error invoking @OnConfigReload method " + method.getName()
                + " on bean " + bean.getClass().getName();
    }
}
