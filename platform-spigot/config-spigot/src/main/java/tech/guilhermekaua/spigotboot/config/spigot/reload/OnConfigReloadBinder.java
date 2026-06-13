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
package tech.guilhermekaua.spigotboot.config.spigot.reload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigRef;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigSnapshot;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a single {@code @OnConfigReload} method and registers the reload listener(s) it implies
 * on the {@link SpigotConfigManager}.
 */
public class OnConfigReloadBinder {

    private static final Object[] NO_ARGS = new Object[0];

    private enum PayloadType { NONE, SIMPLE_INSTANCE, FOLDER_CHANGE, FOLDER_SNAPSHOT }

    private enum ConfigKind { SIMPLE, FOLDER }

    private final SpigotConfigManager configManager;
    private final OnConfigReloadInvoker invoker;

    /**
     * Creates the binder.
     *
     * @param configManager the config manager, not null
     * @param invoker       the reflective invoker, not null
     */
    public OnConfigReloadBinder(@NotNull SpigotConfigManager configManager, @NotNull OnConfigReloadInvoker invoker) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker cannot be null");
    }

    /**
     * Validates the method and registers reload listeners for every target config it declares.
     *
     * @param bean   the owning bean instance, not null
     * @param method a method annotated with {@link OnConfigReload}, not null
     * @throws ConfigException if the method signature or targets are invalid
     */
    public void bind(@NotNull Object bean, @NotNull Method method) {
        validateSignature(bean, method);

        PayloadType payload = resolvePayloadType(method);
        Class<?> paramItemType = (payload == PayloadType.FOLDER_CHANGE || payload == PayloadType.FOLDER_SNAPSHOT)
                ? extractItemType(method) : null;
        Class<?> simpleParamType = (payload == PayloadType.SIMPLE_INSTANCE)
                ? method.getParameterTypes()[0] : null;

        Set<Class<?>> targets = resolveTargets(bean, method, payload, simpleParamType, paramItemType);

        for (Class<?> target : targets) {
            ConfigKind kind = configKind(target);
            if (kind == null) {
                throw fail(bean, method, "targets unregistered config " + target.getName());
            }
            validatePayloadForKind(bean, method, payload, kind, target, simpleParamType, paramItemType);
            if (kind == ConfigKind.SIMPLE) {
                bindSimple(bean, method, payload, target);
            } else {
                bindFolder(bean, method, payload, target);
            }
        }
    }

    private void validateSignature(Object bean, Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw fail(bean, method, "must be a non-static instance method");
        }
        if (!void.class.equals(method.getReturnType())) {
            throw fail(bean, method, "must return void");
        }
        if (method.getParameterCount() > 1) {
            throw fail(bean, method, "must declare zero or one parameter");
        }
    }

    private PayloadType resolvePayloadType(Method method) {
        if (method.getParameterCount() == 0) {
            return PayloadType.NONE;
        }
        Class<?> p = method.getParameterTypes()[0];
        if (FolderConfigItemChange.class.equals(p)) {
            return PayloadType.FOLDER_CHANGE;
        }
        if (FolderConfigSnapshot.class.equals(p)) {
            return PayloadType.FOLDER_SNAPSHOT;
        }
        return PayloadType.SIMPLE_INSTANCE;
    }

    private Set<Class<?>> resolveTargets(Object bean, Method method, PayloadType payload,
                                         @Nullable Class<?> simpleParamType, @Nullable Class<?> paramItemType) {
        Class<?>[] declared = method.getAnnotation(OnConfigReload.class).value();
        if (declared.length > 0) {
            return new LinkedHashSet<>(java.util.Arrays.asList(declared));
        }
        switch (payload) {
            case SIMPLE_INSTANCE:
                return Set.of(simpleParamType);
            case FOLDER_CHANGE:
            case FOLDER_SNAPSHOT:
                if (paramItemType == null) {
                    throw fail(bean, method, "cannot infer the folder item type from a raw parameter; specify value()");
                }
                return Set.of(paramItemType);
            case NONE:
            default:
                Set<Class<?>> all = new LinkedHashSet<>(configManager.getRegisteredConfigs());
                all.addAll(configManager.getRegisteredFolderConfigItemTypes());
                return all;
        }
    }

    private @Nullable ConfigKind configKind(Class<?> target) {
        if (configManager.getRegisteredConfigs().contains(target)) {
            return ConfigKind.SIMPLE;
        }
        if (configManager.getRegisteredFolderConfigItemTypes().contains(target)) {
            return ConfigKind.FOLDER;
        }
        return null;
    }

    private void validatePayloadForKind(Object bean, Method method, PayloadType payload, ConfigKind kind,
                                        Class<?> target, @Nullable Class<?> simpleParamType, @Nullable Class<?> paramItemType) {
        if (kind == ConfigKind.SIMPLE) {
            if (payload == PayloadType.FOLDER_CHANGE || payload == PayloadType.FOLDER_SNAPSHOT) {
                throw fail(bean, method, "simple config " + target.getName()
                        + " cannot be received as FolderConfigItemChange/FolderConfigSnapshot");
            }
            if (payload == PayloadType.SIMPLE_INSTANCE && !simpleParamType.isAssignableFrom(target)) {
                throw fail(bean, method, "parameter type " + simpleParamType.getName()
                        + " is not assignable from config " + target.getName());
            }
        } else {
            if (payload == PayloadType.SIMPLE_INSTANCE) {
                throw fail(bean, method, "folder config " + target.getName()
                        + " must be received via no parameter, FolderConfigItemChange<" + target.getSimpleName()
                        + "> or FolderConfigSnapshot<" + target.getSimpleName() + ">");
            }
            if (paramItemType != null && !paramItemType.equals(target)) {
                throw fail(bean, method, "parameter item type " + paramItemType.getName()
                        + " does not match folder config " + target.getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void bindSimple(Object bean, Method method, PayloadType payload, Class<?> target) {
        ConfigRef<Object> ref = (ConfigRef<Object>) configManager.getRef((Class<Object>) target);
        ref.addListener(newValue -> {
            Object[] args = (payload == PayloadType.NONE) ? NO_ARGS : new Object[]{newValue};
            invoker.invoke(bean, method, args);
        });
    }

    @SuppressWarnings("unchecked")
    private void bindFolder(Object bean, Method method, PayloadType payload, Class<?> target) {
        for (String name : configManager.getFolderConfigNames(target)) {
            FolderConfigRef<Object> ref =
                    (FolderConfigRef<Object>) configManager.getFolderConfigRef((Class<Object>) target, name);
            ref.addListener(change -> {
                Object[] args;
                switch (payload) {
                    case FOLDER_CHANGE:
                        args = new Object[]{change};
                        break;
                    case FOLDER_SNAPSHOT:
                        args = new Object[]{ref.get()};
                        break;
                    case NONE:
                        args = NO_ARGS;
                        break;
                    default:
                        return;
                }
                invoker.invoke(bean, method, args);
            });
        }
    }

    private static @Nullable Class<?> extractItemType(Method method) {
        Type t = method.getGenericParameterTypes()[0];
        if (t instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) t).getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class) {
                return (Class<?>) args[0];
            }
        }
        return null;
    }

    private static ConfigException fail(Object bean, Method method, String reason) {
        return new ConfigException("@OnConfigReload method " + method.getName()
                + " on " + bean.getClass().getName() + " " + reason);
    }
}
