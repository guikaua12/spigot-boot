/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.runtime.controller;

import tech.guilhermekaua.spigotboot.entity.api.EntityController;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves controller overrides once per controller class.
 *
 * @since 2.0.2
 */
public final class ControllerMethodResolver {
    private final ConcurrentMap<Class<?>, Map<LogicalEntityHook, HookResolution>> cache =
            new ConcurrentHashMap<Class<?>, Map<LogicalEntityHook, HookResolution>>();

    public HookResolution resolve(EntityController<?> controller, LogicalEntityHook hook) {
        Map<LogicalEntityHook, HookResolution> hooks = cache.computeIfAbsent(
                controller.getClass(),
                this::resolveHooks
        );
        return hooks.get(hook);
    }

    private Map<LogicalEntityHook, HookResolution> resolveHooks(Class<?> controllerType) {
        Map<LogicalEntityHook, HookResolution> hooks = new EnumMap<LogicalEntityHook, HookResolution>(LogicalEntityHook.class);
        for (LogicalEntityHook hook : LogicalEntityHook.values()) {
            try {
                Method contextMethod = hook.resolveContextMethod(controllerType);
                Method convenienceMethod = hook.resolveConvenienceMethod(controllerType);
                boolean contextOverride = contextMethod.getDeclaringClass() != EntityController.class;
                boolean convenienceOverride = convenienceMethod.getDeclaringClass() != EntityController.class;
                contextMethod.setAccessible(true);
                hooks.put(hook, new HookResolution(contextMethod, contextOverride || convenienceOverride));
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException(
                        "Could not resolve controller hook '" + hook.methodName() + "' on "
                                + controllerType.getName() + ".",
                        exception
                );
            }
        }
        return hooks;
    }

    /**
     * Resolved dispatch data for a single hook.
     *
     * @since 2.0.2
     */
    public static final class HookResolution {
        private final Method contextMethod;
        private final boolean intercepted;

        private HookResolution(Method contextMethod, boolean intercepted) {
            this.contextMethod = contextMethod;
            this.intercepted = intercepted;
        }

        public Method contextMethod() {
            return contextMethod;
        }

        public boolean intercepted() {
            return intercepted;
        }
    }
}
