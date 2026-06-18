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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaginationSourceSpecTest {

    private final ViewContext context = mock(ViewContext.class);
    private final PlatformScheduler scheduler = mock(PlatformScheduler.class);

    // pins the package-private PaginationSpec constructor order Task 8's builder must use:
    // (geometry, target, layoutChar, explicitLayout, patterns, renderer, fallbackItem,
    //  loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages)
    private PaginationSpec<Integer> specOf(PaginationSourceSpec<Integer> source) {
        // the renderer is a no-op lambda: createSource never invokes it
        return new PaginationSpec<Integer>(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                (ctx, item, index, value) -> { }, null, null, source, null, null, null, 128,
                null, new int[0], new int[0]);
    }

    @Test
    void eager_returnsTheSameSharedSourceForEveryCreateCall() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(Arrays.asList(1, 2, 3));
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        PageSource<Integer> first = sourceSpec.createSource(context, spec, scheduler);
        PageSource<Integer> second = sourceSpec.createSource(mock(ViewContext.class), spec, scheduler);

        assertTrue(first instanceof EagerPageSource);
        assertSame(first, second);
    }

    @Test
    void eager_originalListMutationDoesNotLeakIntoTheSource() {
        List<Integer> backing = new ArrayList<>(Arrays.asList(1, 2, 3));
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(backing);

        backing.add(4);

        PageSource<Integer> source = sourceSpec.createSource(context, specOf(sourceSpec), scheduler);
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void eager_reportsItsKind() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(Collections.singletonList(1));

        assertEquals(PaginationSourceSpec.Kind.EAGER_STATIC, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
        assertNull(sourceSpec.lazyFunction());
    }

    @Test
    void lazy_buildsAFreshEagerSourcePerCallWrappingTheFunctionResult() {
        AtomicReference<ViewContext> seen = new AtomicReference<>();
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(ctx -> {
            seen.set(ctx);
            return Arrays.asList(7, 8);
        });
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        PageSource<Integer> first = sourceSpec.createSource(context, spec, scheduler);
        PageSource<Integer> second = sourceSpec.createSource(context, spec, scheduler);

        assertTrue(first instanceof EagerPageSource);
        assertNotSame(first, second);
        assertEquals(Arrays.asList(7, 8), first.elements());
        assertSame(context, seen.get());
    }

    @Test
    void lazy_nullFunctionResultFailsWithThePinnedMessage() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(ctx -> null);
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> sourceSpec.createSource(context, spec, scheduler));

        assertEquals("lazy pagination source function returned null", error.getMessage());
    }

    @Test
    void lazy_exposesTheFunctionForTheRefreshPath() {
        Function<ViewContext, List<Integer>> fn = ctx -> Collections.singletonList(1);
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(fn);

        assertEquals(PaginationSourceSpec.Kind.EAGER_LAZY, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
        assertSame(fn, sourceSpec.lazyFunction());
    }

    @Test
    void async_buildsAFreshAsyncPageSourcePerCall() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.async(
                request -> CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2), 2)));
        // requestTimeout/cacheTtl/cacheMaxPages/errorCallback are set to prove the option
        // pass-through compiles and constructs; the behavioral pass-through (timeouts firing,
        // cache hits, error-callback invocation) is covered by the engine tests of later tasks
        PaginationSpec<Integer> spec = new PaginationSpec<Integer>(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                (ctx, item, index, value) -> { }, null, null, sourceSpec,
                (request, error) -> { }, Duration.ofSeconds(5), Duration.ofSeconds(30), 64,
                null, new int[0], new int[0]);

        PageSource<Integer> first = sourceSpec.createSource(context, spec, scheduler);
        PageSource<Integer> second = sourceSpec.createSource(context, spec, scheduler);

        assertTrue(first instanceof AsyncPageSource);
        assertNotSame(first, second);
        assertEquals(PaginationSourceSpec.Kind.ASYNC, sourceSpec.kind());
        assertTrue(sourceSpec.isAsync());
    }

    @Test
    void custom_returnsTheFactoryResultAsIs() {
        PageSource<Integer> made = new EagerPageSource<>(Collections.singletonList(5));
        AtomicReference<ViewContext> seen = new AtomicReference<>();
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.custom(ctx -> {
            seen.set(ctx);
            return made;
        });

        PageSource<Integer> created = sourceSpec.createSource(context, specOf(sourceSpec), scheduler);

        assertSame(made, created);
        assertSame(context, seen.get());
        assertEquals(PaginationSourceSpec.Kind.CUSTOM, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
    }

    @Test
    void custom_nullFactoryResultFailsWithThePinnedMessage() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.custom(ctx -> null);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> sourceSpec.createSource(context, specOf(sourceSpec), scheduler));

        assertEquals("paginateSource factory returned null", error.getMessage());
    }
}
