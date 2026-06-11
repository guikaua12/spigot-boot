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

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaginationImplTest {

    private static final List<String> ELEMENTS = Arrays.asList("a", "b", "c", "d", "e");
    private static final AtomicReference<List<String>> LAZY_ELEMENTS =
            new AtomicReference<>(Collections.emptyList());

    /** declares one eager pagination token; the renderer is never evaluated by these tests. */
    static final class PagedView extends View {
        final PaginationImpl<String> pagination = (PaginationImpl<String>) new PaginationBuilderImpl<>(
                this, tokenTable(), PaginationSourceSpec.eager(ELEMENTS))
                .itemRenderer((context, item, index, value) -> {
                })
                .build();
    }

    /** declares one eager-lazy pagination token reading {@link #LAZY_ELEMENTS} per context. */
    static final class LazyPagedView extends View {
        final PaginationImpl<String> pagination = (PaginationImpl<String>) new PaginationBuilderImpl<>(
                this, tokenTable(), PaginationSourceSpec.lazy(context -> LAZY_ELEMENTS.get()))
                .itemRenderer((context, item, index, value) -> {
                })
                .build();
    }

    static final class ForeignView extends View {
    }

    private ServerMock server;
    private Plugin plugin;
    private ViewEngine engine;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), mock(TitleUpdater.class));
        player = server.addPlayer("paginator");
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel anything a test queued first
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    // three 'O' slots (0, 1, 2): five eager elements paginate into two pages of limit 3
    private static ViewConfig pagedConfig() {
        return new ViewConfigBuilder().title("Paged").layout("OOO      ", "         ").build();
    }

    // the sessionFor fixture pattern from ContextPhaseValidityTest: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.status(ViewSession.Status.OPENING);
        return session;
    }

    // hand-places an UNINITIALIZED binding into the token's own state store slot (what OpenPhase does)
    private PaginationBinding placeBinding(PaginationImpl<?> token, ViewSession session) {
        PaginationBinding binding = new PaginationBinding(token.spec(), token.tokenId(), session, engine);
        session.stateStore().set(token.id(), binding);
        return binding;
    }

    // places the binding and runs PaginationInitPhase's per-binding step by hand
    private PaginationBinding initializeBinding(PaginationImpl<?> token, ViewSession session) {
        PaginationBinding binding = placeBinding(token, session);
        ResolvedLayout layout = ResolvedLayout.resolve(session.effectiveConfig());
        session.layout(layout);
        binding.initialize(layout, session.effectiveConfig());
        session.status(ViewSession.Status.ACTIVE);
        return binding;
    }

    // --- guard: foreign / closed / missing binding ---

    @Test
    void foreignViewsContext_throwsStaleContextException_withExactMessage() {
        PagedView view = new PagedView();
        ForeignView foreign = new ForeignView();
        ViewSession foreignSession = sessionFor(foreign, pagedConfig());
        PlainViewContextImpl foreignContext = new PlainViewContextImpl(foreignSession, engine);

        StaleContextException thrown = assertThrows(StaleContextException.class,
                () -> view.pagination.currentPage(foreignContext));

        assertEquals("state token of " + PagedView.class.getName()
                + " used with a context of " + ForeignView.class.getName(), thrown.getMessage());
    }

    @Test
    void closedContext_throwsStaleContextException_withExactMessage() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        session.status(ViewSession.Status.CLOSED);

        StaleContextException thrown = assertThrows(StaleContextException.class,
                () -> view.pagination.totalPages(context));

        assertEquals("context of " + PagedView.class.getName() + " is closed", thrown.getMessage());
    }

    @Test
    void missingBinding_throwsIllegalStateException() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> view.pagination.currentPage(context));

        assertEquals("pagination binding missing for " + PagedView.class.getName(), thrown.getMessage());
    }

    // --- threading: mutators assert the main thread ---

    @Test
    void mutators_offMainThread_throwIllegalStateException() throws Exception {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        placeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        // the test thread created the mock server, so it is the primary thread
        assertDoesNotThrow(() -> view.pagination.switchTo(context, 2));

        assertThrowsOffMain(() -> view.pagination.advance(context));
        assertThrowsOffMain(() -> view.pagination.back(context));
        assertThrowsOffMain(() -> view.pagination.switchTo(context, 3));
        assertThrowsOffMain(() -> view.pagination.refresh(context));
    }

    // background-thread pattern mirrored from StateImplTest.MainThreadGuard
    private static void assertThrowsOffMain(Runnable call) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            executor.submit(() -> {
                try {
                    call.run();
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }).get(5, TimeUnit.SECONDS);
            assertTrue(thrown.get() instanceof IllegalStateException,
                    "off-main mutator must throw IllegalStateException, got " + thrown.get());
            assertTrue(thrown.get().getMessage().contains("must be called on the main server thread"),
                    "exception message must contain 'must be called on the main server thread', got: " + thrown.get().getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- pre-init delegation table (pinned rule 6, left column) ---

    @Test
    void preInit_readsAndMutationsFollowTheDelegationTable() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PaginationBinding binding = placeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertFalse(binding.isInitialized());
        assertFalse(binding.hasPendingNavigation());
        assertEquals(1, view.pagination.currentPage(context));     // pendingTarget starts at 1
        assertEquals(1, view.pagination.totalPages(context));
        assertEquals(0, view.pagination.totalElements(context));
        assertFalse(view.pagination.canAdvance(context));
        assertFalse(view.pagination.canBack(context));             // pendingTarget() > 1 is false
        assertFalse(view.pagination.isLoading(context));
        assertNull(view.pagination.lastError(context));

        view.pagination.advance(context);                          // records pendingTarget + 1
        assertEquals(2, view.pagination.currentPage(context));
        assertTrue(view.pagination.canBack(context));
        assertTrue(binding.hasPendingNavigation());

        view.pagination.back(context);                             // records pendingTarget - 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.back(context);                             // recordSwitchTo stores max(1, target)
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.switchTo(context, 7);
        assertEquals(7, view.pagination.currentPage(context));

        view.pagination.switchTo(context, -3);                     // clamped up to 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.refresh(context);                          // pre-init refresh is a no-op
        assertFalse(binding.isInitialized());
        assertEquals(1, view.pagination.currentPage(context));
    }

    // --- post-init delegation (pinned rule 6, right column) ---

    @Test
    void postInit_delegatesToTheBoundPaginator() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PaginationBinding binding = initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertTrue(binding.isInitialized());
        // three 'O' slots, five elements: pages are [a b c] and [d e]
        assertEquals(1, view.pagination.currentPage(context));
        assertEquals(2, view.pagination.totalPages(context));
        assertEquals(5, view.pagination.totalElements(context));
        assertTrue(view.pagination.canAdvance(context));
        assertFalse(view.pagination.canBack(context));
        assertFalse(view.pagination.isLoading(context));           // always false for eager sources
        assertNull(view.pagination.lastError(context));

        view.pagination.advance(context);
        assertEquals(2, view.pagination.currentPage(context));
        assertFalse(view.pagination.canAdvance(context));
        assertTrue(view.pagination.canBack(context));

        view.pagination.switchTo(context, 99);                     // upper-clamped exactly as 2.x changePage
        assertEquals(2, view.pagination.currentPage(context));

        view.pagination.switchTo(context, -4);                     // lower-clamped to 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.back(context);                             // already at the first page
        assertEquals(1, view.pagination.currentPage(context));
    }

    @Test
    void refresh_postInit_eagerStaticSource_routesToPaginatorRefresh() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        view.pagination.advance(context);
        view.pagination.refresh(context);                          // re-requests the current page (forced)

        assertEquals(2, view.pagination.currentPage(context));
        assertEquals(5, view.pagination.totalElements(context));
        assertNull(view.pagination.lastError(context));
    }

    @Test
    void refresh_postInit_eagerLazySource_reinvokesTheSourceFunction() {
        LAZY_ELEMENTS.set(Arrays.asList("a", "b"));
        LazyPagedView view = new LazyPagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertEquals(2, view.pagination.totalElements(context));
        assertEquals(1, view.pagination.totalPages(context));

        LAZY_ELEMENTS.set(Arrays.asList("a", "b", "c", "d"));
        view.pagination.refresh(context);                          // refreshLazy replaces the source

        assertEquals(4, view.pagination.totalElements(context));
        assertEquals(2, view.pagination.totalPages(context));
    }
}
