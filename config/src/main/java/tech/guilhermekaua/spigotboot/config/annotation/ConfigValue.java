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
package tech.guilhermekaua.spigotboot.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a single configuration value into a field or constructor parameter, similar to Spring's
 * {@code @Value}. Honored only on dependency-injection-managed beans (e.g. {@code @Component} /
 * {@code @Bean}); it has no effect on fields of {@code @Config} or {@code @FolderConfig} classes
 * (those are bound by the config binder, not the DI container).
 * <p>
 * Two addressing modes are supported:
 * <ul>
 *   <li><b>String mode</b> — {@link #value()} holds {@code "configName:path.to.value"} (or just
 *       {@code "path.to.value"} when exactly one config is registered).</li>
 *   <li><b>Type-safe mode</b> — {@link #config()} names a {@code @Config} class and {@link #path()}
 *       the path within it. When {@link #config()} is set it takes precedence over {@link #value()}.</li>
 * </ul>
 * <p>
 * The value is resolved <b>once at injection time</b> and does <b>not</b> refresh on config reload.
 * For values that must track reloads, inject {@code ConfigRef<T>} or the {@code @Config} class itself.
 * <p>
 * If the path resolves to no value and no {@link #defaultValue()} was supplied, injection fails fast
 * with a {@code ConfigException} naming the path and the injection site.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigValue {

    /**
     * Sentinel meaning "no {@link #defaultValue()} was supplied". Lets an empty string be a valid
     * explicit default while still distinguishing the unset case. Not intended to be referenced by
     * user code.
     */
    String DEFAULT_NONE = "\n\t\t\t  @ConfigValue#DEFAULT_NONE  \t\t\t\n";

    /**
     * Config path in {@code "configName:path"} or bare {@code "path"} form. Ignored when
     * {@link #config()} is set.
     *
     * @return the config path, or empty if {@link #config()} is used instead
     */
    String value() default "";

    /**
     * Default value applied (and coerced to the target type) when the path resolves to nothing.
     * Defaults to {@link #DEFAULT_NONE}, which means "no default" — absence then fails fast.
     *
     * @return the default value
     */
    String defaultValue() default DEFAULT_NONE;

    /**
     * Alternative to {@link #value()}: reference the {@code @Config} class directly. Takes precedence
     * over {@link #value()} when set to anything other than {@code Void.class}.
     *
     * @return the config class
     */
    Class<?> config() default Void.class;

    /**
     * Path within the {@link #config()} class. Required when {@link #config()} is set.
     *
     * @return the path
     */
    String path() default "";
}
