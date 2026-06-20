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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.UpdatePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flush machinery extracted from {@link ViewEngine}: the dirty-token cascade loop, the
 * shared-state fan-out flush with its re-entrancy guard and per-tick coalescing, and the
 * shared flush-hook wiring. Behavior-preserving extraction — {@link ViewEngine} keeps the
 * public entry points (and their region-ownership asserts) and delegates here.
 */
final class FlushCoordinator {

    // the warnings keep publishing on the ViewEngine logger: this extraction must not change
    // observable behavior, and the cascade-cap warning destination is pinned by UpdateFlushTest
    private static final Logger LOGGER = Logger.getLogger(ViewEngine.class.getName());
    static final int CASCADE_CAP = 8;

    private final SessionRegistry sessions;
    private final UpdatePhase updatePhase;
    private final PlatformScheduler scheduler;

    // per-tick coalescing of shared writes: maps each owner to the accumulated set of dirty
    // token ids; touched from any thread (and any region); drained atomically on the global
    // region when the scheduled task runs, which then fans the repaint out per session
    private final ConcurrentHashMap<View, Set<Integer>> sharedFlushScheduled = new ConcurrentHashMap<>();

    /**
     * Creates the coordinator.
     *
     * @param sessions    the per-player session registry
     * @param updatePhase the update phase running the repaint passes
     * @param scheduler   the platform scheduler
     */
    FlushCoordinator(@NotNull SessionRegistry sessions,
                     @NotNull UpdatePhase updatePhase, @NotNull PlatformScheduler scheduler) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.updatePhase = Objects.requireNonNull(updatePhase, "updatePhase");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Flushes dirty state tokens of a session: each pass drains the dirty set and runs a
     * STATE_CHANGE update over the watchers; passes repeat while handlers re-dirty tokens,
     * capped at {@value #CASCADE_CAP} cascades per flush, after which the remaining dirty
     * tokens are dropped with a WARNING. The {@link ViewEngine} entry point asserts
     * the calling thread owns the viewer's region.
     *
     * @param session the session to flush
     */
    void flushDirty(@NotNull ViewSession session) {
        int cascades = 0;
        while (session.stateStore().hasDirty()) {
            if (++cascades > CASCADE_CAP) {
                LOGGER.log(Level.WARNING, "state feedback loop detected for view {0}; "
                                + "dropping remaining dirty tokens after {1} cascaded flush passes",
                        new Object[]{session.registered().type().getName(), CASCADE_CAP});
                session.stateStore().drainDirty();
                break;
            }
            Set<Integer> dirty = session.stateStore().drainDirty();
            updatePhase.update(session, UpdateTrigger.STATE_CHANGE, dirty);
        }
    }

    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view; full-pass
     * fallback — the wired hooks use the watcher-scoped overload. The {@link ViewEngine}
     * entry point asserts the calling thread owns the viewer's region.
     *
     * @param owner the view singleton whose sessions should flush
     */
    void flushShared(@NotNull View owner) {
        flushShared(owner, null);
    }

    /**
     * Runs a STATE_CHANGE repaint pass on every active session of the given view, restricting
     * the repaint to watchers of the supplied token id set. Passing {@code null} for
     * {@code tokenIds} triggers a full repaint (same as the no-arg overload).
     *
     * <p>The owner's sessions can live in different regions, so each session is repainted on
     * its own region: inline when the current thread already owns the viewer's region,
     * otherwise hopped to that region via {@link PlatformScheduler#runOnEntity}. On legacy
     * Spigot/Paper every session shares the single main thread, so the inline path is always
     * taken.
     *
     * @param owner    the view singleton whose sessions should flush
     * @param tokenIds the dirty token ids to pass to the update phase, or {@code null} for a full pass
     */
    private void flushShared(@NotNull View owner, @Nullable Set<Integer> tokenIds) {
        // snapshot: an onUpdate handler may close a session and mutate the registry
        List<ViewSession> snapshot = new ArrayList<>(sessions.all());
        for (ViewSession session : snapshot) {
            if (session.registered().instance() != owner || !session.isActive()) {
                continue;
            }
            final ViewSession target = session;
            if (scheduler.ownsRegion(target.player())) {
                updatePhase.update(target, UpdateTrigger.STATE_CHANGE, tokenIds);
            } else {
                // no retired callback: a viewer logging out before this runs leaves the
                // session inactive, so the deferred repaint is a harmless no-op
                scheduler.runOnEntity(target.player(),
                        () -> updatePhase.update(target, UpdateTrigger.STATE_CHANGE, tokenIds), null);
            }
        }
    }

    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions. A view's sessions can span regions, so a write from
     * any thread (and any region) coalesces the dirty token id into the per-owner set and, when
     * first scheduled for that owner this tick, schedules a single drain on the global region.
     * The drain only reads the session registry and re-dispatches the repaint per session via
     * {@link #flushShared} — it never touches world state off-region. Already wired tokens are
     * skipped to avoid redundant re-wiring. The flush is watcher-scoped: each hook captures its
     * token id and passes it as a singleton dirty set so only components watching that token are
     * repainted. Called on the main thread via {@code ViewEngine.open}; no separate assertion here.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        final View owner = registered.instance();
        for (StateToken token : owner.tokenTable().tokens()) {
            if (!(token instanceof SharedStateImpl)) {
                continue;
            }
            SharedStateImpl<?> shared = (SharedStateImpl<?>) token;
            if (shared.flushHookWired()) {
                continue;
            }
            final int tokenId = shared.id();
            shared.flushHook(() -> {
                // compute is atomic vs the drain's remove on the same key, so an id can
                // never land in an already-drained set
                boolean[] schedule = {false};
                sharedFlushScheduled.compute(owner, (key, existing) -> {
                    if (existing == null) {
                        Set<Integer> created = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
                        created.add(tokenId);
                        schedule[0] = true;
                        return created;
                    }
                    existing.add(tokenId);
                    return existing;
                });
                if (schedule[0]) {
                    // drain on the global region: it only reads the registry and re-dispatches
                    // per session, so it is the one region-safe place to fan out across regions
                    scheduler.runGlobal(() -> {
                        Set<Integer> ids = sharedFlushScheduled.remove(owner);
                        if (ids != null) {
                            flushShared(owner, ids);
                        }
                    });
                }
            });
        }
    }
}
