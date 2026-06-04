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
package tech.guilhermekaua.spigotboot.inventoryapi.viewer.property;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-viewer key/value scratchpad. Pagination instances stash their per-viewer state here under
 * a well-known key so the same viewer object can drive next/prev navigation across renders.
 */
public final class ViewerPropertyMap {

    private final Map<String, Object> map = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) this.map.get(key);
    }

    public <T> T get(String key, Supplier<T> supplier) {
        if (this.map.containsKey(key))
            return this.get(key);

        this.set(key, supplier.get());

        return this.get(key);
    }

    public ViewerPropertyMap set(String key, Object value) {
        this.map.put(key, value);
        return this;
    }

}
