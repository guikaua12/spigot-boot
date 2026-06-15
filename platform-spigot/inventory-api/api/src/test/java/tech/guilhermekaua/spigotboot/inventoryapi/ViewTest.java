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
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSourceSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ViewTest {

    private static final class BlankView extends View {
    }

    @Test
    void stateFactories_registerDistinctTokensWithSequentialIds() {
        BlankView view = new BlankView();
        assertEquals(0, view.tokenTable().size());
        assertFalse(view.tokenTable().isFrozen());

        MutableState<String> first = view.mutableState("a");
        assertEquals(1, view.tokenTable().size());
        State<Integer> second = view.lazyState(ctx -> 1);
        assertEquals(2, view.tokenTable().size());
        MutableState<Integer> third = view.initialState("count", Integer.class);
        assertEquals(3, view.tokenTable().size());
        SharedState<String> fourth = view.sharedState("shared");
        assertEquals(4, view.tokenTable().size());
        MutableState<List<String>> fifth = view.mutableState(ctx -> new ArrayList<>());
        assertEquals(5, view.tokenTable().size());

        List<StateToken> tokens = view.tokenTable().tokens();
        assertSame(first, tokens.get(0));
        assertSame(second, tokens.get(1));
        assertSame(third, tokens.get(2));
        assertSame(fourth, tokens.get(3));
        assertSame(fifth, tokens.get(4));
    }

    @Test
    void stateFactories_afterFreeze_throwIllegalStateException() {
        BlankView view = new BlankView();
        view.tokenTable().freeze();
        assertTrue(view.tokenTable().isFrozen());

        assertThrows(IllegalStateException.class, () -> view.mutableState("late"));
        assertThrows(IllegalStateException.class, () -> view.mutableState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.lazyState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.initialState("key", String.class));
        assertThrows(IllegalStateException.class, () -> view.sharedState("late"));
    }

    @Test
    void tokenTable_registerAfterFreeze_throwsIllegalStateException() {
        TokenTable table = new BlankView().tokenTable();
        table.freeze();

        assertThrows(IllegalStateException.class, () -> table.register(new StateToken() {
        }));
    }

    @Test
    void tokenTable_tokensListIsUnmodifiable() {
        BlankView view = new BlankView();
        view.mutableState("a");

        assertThrows(UnsupportedOperationException.class, () -> view.tokenTable().tokens().clear());
    }

    private static <T> PaginationItemRenderer<T> noopRenderer() {
        return (context, item, index, value) -> {
        };
    }

    @Test
    void paginateFactories_buildersRegisterExactlyOneTokenEach() {
        BlankView view = new BlankView();

        PaginationBuilder<String> eager = view.paginate(Arrays.asList("a", "b"));
        PaginationBuilder<String> lazy = view.paginate(context -> Arrays.asList("a"));
        PaginationBuilder<String> async = view.paginateAsync(request ->
                CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)));
        PaginationBuilder<String> custom = view.paginateSource(context ->
                new EagerPageSource<>(Arrays.asList("x")));

        // factory calls only return builders; build() is what registers
        assertEquals(0, view.tokenTable().size());

        Pagination<String> eagerToken = eager.itemRenderer(noopRenderer()).build();
        assertEquals(1, view.tokenTable().size());
        Pagination<String> lazyToken = lazy.itemRenderer(noopRenderer()).build();
        assertEquals(2, view.tokenTable().size());
        Pagination<String> asyncToken = async.itemRenderer(noopRenderer()).build();
        assertEquals(3, view.tokenTable().size());
        Pagination<String> customToken = custom.itemRenderer(noopRenderer()).build();
        assertEquals(4, view.tokenTable().size());

        List<StateToken> tokens = view.tokenTable().tokens();
        assertSame(eagerToken, tokens.get(0));
        assertSame(lazyToken, tokens.get(1));
        assertSame(asyncToken, tokens.get(2));
        assertSame(customToken, tokens.get(3));
        for (int i = 0; i < tokens.size(); i++) {
            assertTrue(tokens.get(i) instanceof Pagination,
                    "token " + i + " must implement Pagination");
            assertTrue(tokens.get(i) instanceof IdentifiableToken,
                    "token " + i + " must implement IdentifiableToken");
            assertEquals(i, ((IdentifiableToken) tokens.get(i)).tokenId());
        }
    }

    @Test
    void paginateFactories_mapToTheMatchingSourceKind() {
        BlankView view = new BlankView();

        assertEquals(PaginationSourceSpec.Kind.EAGER_STATIC,
                kindOf(view.paginate(Arrays.asList("a"))));
        assertEquals(PaginationSourceSpec.Kind.EAGER_LAZY,
                kindOf(view.paginate(context -> Arrays.asList("a"))));
        assertEquals(PaginationSourceSpec.Kind.ASYNC,
                kindOf(view.paginateAsync(request ->
                        CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)))));
        assertEquals(PaginationSourceSpec.Kind.CUSTOM,
                kindOf(view.paginateSource(context -> new EagerPageSource<>(Arrays.asList("x")))));
    }

    private static <T> PaginationSourceSpec.Kind kindOf(PaginationBuilder<T> builder) {
        PaginationImpl<T> token = (PaginationImpl<T>) builder.itemRenderer(noopRenderer()).build();
        return token.spec().source().kind();
    }

    @Test
    void paginateList_takesDefensiveCopy_andSharesOneEagerSourceAcrossContexts() {
        BlankView view = new BlankView();
        List<String> original = new ArrayList<>(Arrays.asList("a", "b"));

        PaginationBuilder<String> builder = view.paginate(original);
        original.add("mutated-after-the-factory-call");

        PaginationImpl<String> token = (PaginationImpl<String>) builder.itemRenderer(noopRenderer()).build();
        ViewContext context = mock(ViewContext.class);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        PageSource<String> first = token.spec().source().createSource(context, token.spec(), scheduler);
        PageSource<String> second = token.spec().source().createSource(context, token.spec(), scheduler);

        assertEquals(Arrays.asList("a", "b"), first.elements());
        // EAGER_STATIC serves the one shared immutable source to every context
        assertSame(first, second);
    }

    @Test
    void paginateBuilders_buildAfterFreeze_throwIllegalStateException() {
        BlankView view = new BlankView();
        PaginationBuilder<String> builder = view.paginate(Arrays.asList("a")).itemRenderer(noopRenderer());

        view.tokenTable().freeze();

        assertThrows(IllegalStateException.class, builder::build);
        assertEquals(0, view.tokenTable().size());
    }

    @Test
    void twoPaginateCalls_buildDistinctTokensWithSequentialIds() {
        BlankView view = new BlankView();

        Pagination<String> first = view.paginate(Arrays.asList("a")).itemRenderer(noopRenderer()).build();
        Pagination<Integer> second = view.paginate(Arrays.asList(1, 2)).itemRenderer(noopRenderer()).build();

        assertNotSame(first, second);
        assertEquals(0, ((IdentifiableToken) first).tokenId());
        assertEquals(1, ((IdentifiableToken) second).tokenId());
        assertEquals(2, view.tokenTable().size());
    }
}
