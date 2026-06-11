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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Strategy answering "where do page items come from" for a paginator. {@link EagerPageSource}
 * serves an in-memory list synchronously; {@link AsyncPageSource} loads pages on demand through
 * an {@link AsyncPageSupplier}.
 */
public interface PageSource<T> {

    /**
     * Requests the page described by {@code request}.
     *
     * <p>Contract for implementations:
     * <ul>
     *   <li>{@code onSettle} is invoked <strong>at most once</strong> per request, with exactly
     *       one of result/error non-null — and possibly never (a superseded request).</li>
     *   <li>It MAY be invoked synchronously inside this method and MAY be invoked on any
     *       thread.</li>
     *   <li>A request superseded by a newer one MUST NOT be settled.</li>
     *   <li>{@link #isLoading()} must return {@code false} once the latest request settled.</li>
     * </ul>
     *
     * @param request  the page to load, never null
     * @param onSettle receives the result or the failure, never null
     */
    void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle);

    /**
     * @return the total number of elements in the backing store; {@code 0} until known for
     * async sources
     */
    int totalElements();

    /**
     * @return {@code true} once {@link #totalElements()} reflects the real store size — always
     * for eager sources, after the first successful settle for async sources
     */
    boolean totalsKnown();

    /**
     * @return {@code true} while the latest request has not settled
     */
    boolean isLoading();

    /**
     * @return the failure of the most recently settled request, or {@code null}; cleared when a
     * new request is dispatched
     */
    Throwable lastError();

    /**
     * @return the elements currently held locally — the full backing list for eager sources, the
     * items of the most recently applied page for async sources
     */
    List<T> elements();

    /**
     * Clears any cached pages. No-op by default.
     */
    default void invalidate() {
    }
}
