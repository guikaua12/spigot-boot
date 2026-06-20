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
package tech.guilhermekaua.spigotboot.placeholder.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.utils.ClassUtils;
import tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #95: the PlaceholderAPI-dependent components must self-skip during the component
 * scan when PlaceholderAPI is absent (soft-dependency contract), instead of being registered and later
 * crashing the boot with {@code NoClassDefFoundError}. The {@code @ConditionalOnClass} guard is what the
 * scan evaluates ({@code ConditionEvaluator.shouldSkip}) before registering each component.
 */
class PlaceholderConditionalOnClassTest {

    private static final String PLACEHOLDER_EXPANSION = "me.clip.placeholderapi.expansion.PlaceholderExpansion";

    @BeforeEach
    @AfterEach
    void resetClassPresenceCache() {
        // isPresent caches per classloader; clear so the blocking loader is never served a stale "present".
        ClassUtils.clearCache();
    }

    @Test
    void placeholderComponentsAreSkippedWhenPlaceholderApiAbsent() {
        ConditionContext papiAbsent = new SimpleConditionContext(
                new BeanDefinitionRegistry(),
                null,
                new PapiHidingClassLoader(getClass().getClassLoader())
        );

        assertTrue(ConditionEvaluator.shouldSkip(PlaceholderRegistry.class, papiAbsent),
                "PlaceholderRegistry must self-skip when PlaceholderAPI is absent");
        assertTrue(ConditionEvaluator.shouldSkip(PAPIExpansion.class, papiAbsent),
                "PAPIExpansion must self-skip when PlaceholderAPI is absent");
    }

    @Test
    void placeholderRegistryIsRegisteredWhenPlaceholderApiPresent() {
        ConditionContext papiPresent = new SimpleConditionContext(
                new BeanDefinitionRegistry(),
                null,
                getClass().getClassLoader()
        );

        assertFalse(ConditionEvaluator.shouldSkip(PlaceholderRegistry.class, papiPresent),
                "PlaceholderRegistry must not be skipped when PlaceholderAPI is on the classpath");
    }

    /**
     * Refuses to load PlaceholderAPI's {@code PlaceholderExpansion} so {@code ClassUtils.isPresent}
     * reports it absent, simulating a server without PlaceholderAPI installed. Everything else delegates
     * to the parent loader.
     */
    private static final class PapiHidingClassLoader extends ClassLoader {
        PapiHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(PLACEHOLDER_EXPANSION)) {
                throw new ClassNotFoundException(name + " is blocked to simulate PlaceholderAPI being absent");
            }
            return super.loadClass(name, resolve);
        }
    }
}
