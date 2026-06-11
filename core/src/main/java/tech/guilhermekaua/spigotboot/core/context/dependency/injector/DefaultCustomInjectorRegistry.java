/*
 * The MIT License
 * Copyright © 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.core.context.dependency.injector;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link CustomInjectorRegistry}.
 * <p>
 * This implementation is thread-safe and uses a {@link CopyOnWriteArrayList} for
 * storage with a volatile sorted view for efficient reads.
 */
public final class DefaultCustomInjectorRegistry implements CustomInjectorRegistry {
    private final List<CustomInjector> injectors = new CopyOnWriteArrayList<>();
    private volatile List<CustomInjector> sortedView = Collections.emptyList();

    @Override
    public void register(@NotNull CustomInjector injector) {
        Objects.requireNonNull(injector, "injector cannot be null");

        injectors.add(injector);
        updateSortedView();
    }

    @Override
    public boolean unregister(@NotNull CustomInjector injector) {
        Objects.requireNonNull(injector, "injector cannot be null");

        boolean removed = injectors.remove(injector);
        if (removed) {
            updateSortedView();
        }
        return removed;
    }

    @Override
    public @NotNull List<CustomInjector> getInjectors() {
        return sortedView;
    }

    @Override
    public boolean customInjectorSupported(@NotNull InjectionPoint injectionPoint) {
        for (CustomInjector injector : getInjectors()) {
            if (injector.supports(injectionPoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        injectors.clear();
        sortedView = Collections.emptyList();
    }

    @Override
    public int size() {
        return injectors.size();
    }

    @Override
    public boolean isEmpty() {
        return injectors.isEmpty();
    }

    /**
     * Updates the sorted view after modifications.
     */
    private void updateSortedView() {
        List<CustomInjector> sorted = new ArrayList<>(injectors);
        sorted.sort(Comparator.comparingInt(CustomInjector::getOrder));
        sortedView = Collections.unmodifiableList(sorted);
    }
}
