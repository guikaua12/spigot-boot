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
package tech.guilhermekaua.spigotboot.entity.api.goal;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Basic immutable {@link GoalDefinition} implementation.
 *
 * @since 2.0.2
 */
public final class SimpleGoalDefinition implements GoalDefinition {
    private final EntityGoalKey key;
    private final int priority;
    private final Map<String, Object> parameters;

    private SimpleGoalDefinition(EntityGoalKey key, int priority, Map<String, Object> parameters) {
        this.key = key;
        this.priority = priority;
        this.parameters = parameters;
    }

    /**
     * Creates a goal definition.
     *
     * @param key the logical goal key
     * @param priority the goal priority
     * @param parameters the goal parameters
     * @return the created goal definition
     */
    public static @NotNull SimpleGoalDefinition of(
            @NotNull EntityGoalKey key,
            int priority,
            @NotNull Map<String, Object> parameters
    ) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");
        return new SimpleGoalDefinition(
                key,
                priority,
                Collections.unmodifiableMap(new LinkedHashMap<String, Object>(parameters))
        );
    }

    @Override
    public @NotNull EntityGoalKey key() {
        return key;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public @NotNull Map<String, Object> parameters() {
        return parameters;
    }
}
