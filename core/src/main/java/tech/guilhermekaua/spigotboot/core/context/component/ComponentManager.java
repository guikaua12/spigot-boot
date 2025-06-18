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
package tech.guilhermekaua.spigotboot.core.context.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.di.manager.DependencyManager;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public class ComponentManager {
    private final Set<Class<? extends Annotation>> componentsAnnotations = new HashSet<>();
    private final Set<Class<?>> componentsClasses = new HashSet<>();
    private final DependencyManager dependencyManager;
    private final Plugin plugin;

    public void registerComponents() {
        componentsAnnotations.clear();
        componentsClasses.clear();

        ComponentRegistry.RegisterResult registerResult = new ComponentRegistry(dependencyManager, plugin).registerComponents();
        dependencyManager.registerDependency(getClass(), this);

        componentsAnnotations.addAll(registerResult.getRegisteredAnnotations());
        componentsClasses.addAll(registerResult.getRegisteredClasses());
    }

    public void registerComponents(Class<?>... baseClasses) {
        ComponentRegistry.RegisterResult registerResult = new ComponentRegistry(dependencyManager, plugin).registerComponents(baseClasses);
        dependencyManager.registerDependency(getClass(), this);
    }
}
