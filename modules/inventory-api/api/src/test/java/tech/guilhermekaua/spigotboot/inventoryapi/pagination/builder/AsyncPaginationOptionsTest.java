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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncPaginationOptionsTest {

    private static AsyncPaginationOptions<Integer> optionsWithSource() {
        AsyncPaginationOptions<Integer> options = new AsyncPaginationOptions<>();
        options.source(request -> CompletableFuture.completedFuture(
                PageResult.of(Collections.emptyList(), 0)));
        return options;
    }

    @Test
    void buildPageSource_requiresSource() {
        AsyncPaginationOptions<Integer> options = new AsyncPaginationOptions<>();

        assertThrows(NullPointerException.class, options::buildPageSource);
    }

    @Test
    void buildPageSource_withSource_succeeds() {
        PageSource<Integer> source = optionsWithSource().buildPageSource();

        assertNotNull(source);
    }

    @Test
    void requestTimeout_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().requestTimeout(Duration.ZERO));
    }

    @Test
    void cacheTtl_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().cacheTtl(Duration.ofSeconds(-1)));
    }

    @Test
    void cacheMaxPages_rejectsBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().cacheMaxPages(0));
    }

    @Test
    void cacheMaxPages_withoutCacheTtl_failsAtBuild() {
        AsyncPaginationOptions<Integer> options = optionsWithSource();
        options.cacheMaxPages(10);

        assertThrows(IllegalArgumentException.class, options::buildPageSource);
    }
}
