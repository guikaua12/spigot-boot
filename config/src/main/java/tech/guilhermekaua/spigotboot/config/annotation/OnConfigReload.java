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
 * Marks a method on a DI-managed bean to be invoked after a config is reloaded.
 * <p>
 * For a simple {@code @Config} target, the method may take zero parameters or accept the new config
 * instance. For a folder-config target, it may take zero parameters or accept a
 * {@code FolderConfigItemChange<T>} or {@code FolderConfigSnapshot<T>}.
 * <p>
 * It is not invoked on initial load, only on subsequent reloads. Place it on a bean, never on a
 * {@code @Config}/{@code @FolderConfig} class itself (which is rejected at registration).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnConfigReload {

    /**
     * The config class(es) whose reloads this method reacts to.
     * <p>
     * If non-empty, the method is invoked only for reloads of the listed configs. If empty, the
     * target is inferred from the method's parameter type (the config class, or the item type of a
     * {@code FolderConfigItemChange}/{@code FolderConfigSnapshot} parameter); an empty value on a
     * zero-parameter method listens to every registered config.
     *
     * @return the config classes to listen for, or an empty array to infer the target from the
     * parameter (or to listen to any config when the method takes no parameter)
     */
    Class<?>[] value() default {};
}
