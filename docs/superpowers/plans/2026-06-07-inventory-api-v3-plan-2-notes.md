# inventory-api 3.0.0 — Plan 2 Authoring Notes

Input for whoever authors Plan 2 (pagination switchover). Plan 1 (core view engine) is
complete on `feat/inventory-api` (Tasks 1–18, 343 module tests green, full reactor green,
final review approved). The spec is authoritative for Plan 2's content; this file carries
the Plan-1 outcomes and reviewer backlog that the spec and Plan 1 doc do not.

## Authoritative sources

- Spec: `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` —
  Plan 2 implements §5.7 (declarative pagination), §5.8 (preserved engine, slim
  `PageRequest`, rewritten `BukkitSettleDispatcher`), the `PaginationHost` seam (§10),
  `PaginationInitPhase` in the §7 open order (between layout resolution and
  `onFirstRender`), and the pagination rows of the behavior-preservation map.
- Plan 1 doc: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-1-core-engine.md` —
  Shared Type Contracts + pinned rules; Plan 2 should follow the same authoring process
  (contracts pinned centrally, task bodies drafted against them, seam handshakes explicit,
  subagent execution with two-stage review per task).
- Plan 3 scope (after Plan 2): delete the 2.x public surface, rewrite test-plugin samples,
  migration table, attribution (README + NOTICE: "API design inspired by
  devnatan/inventory-framework (MIT)"), version 3.0.0 (spec §13).

## Plan-1 outcomes Plan 2 must build on (not in the spec)

- `internal/HandlerInvoker.java` — cached reflective dispatch for all View handlers;
  use it, never inline reflection.
- `UpdateTrigger.PAGINATION_SETTLE` is declared and unused — Plan 2 wires it (settle
  marks the pagination token dirty → watcher repaint + `onUpdate(PAGINATION_SETTLE)`).
- `ComponentInstance.renderForPaint` + `RENDER_FAILURE` identity sentinel: EVERY paint
  site must skip painting when `item == ComponentInstance.RENDER_FAILURE`.
- `flushShared` is watcher-scoped (token-id dirty sets) with `ConcurrentHashMap.compute`
  based off-main coalescing — reference pattern for settle-driven token marking.
- `ViewEngine.open`/`close` self-defer during click dispatch (engine level, not only the
  context paths); `defer` wraps ops with a closed-session guard and drains FIFO.
- `ViewEngine` constructor: `(Plugin, ViewRegistry, SessionRegistry, SlotPainter,
  TitleUpdater)` — phases are engine-owned package classes; their constructors are free
  to change. `SlotPainter` is also a `ViewEngine` field (title path uses `applyText`).
- `View` does NOT yet have the `paginate*` factories from spec §5.2 — Plan 2 adds them
  (tokens register through the existing `TokenTable`; `IdentifiableToken` seam gives ids;
  the new `Pagination<T>` token implements `StateToken` so `updateOnStateChange` works).
- FQN collision: old `pagination.Pagination` (2.x interface) occupies the package+name the
  new public token wants. Plan 2 must move the old engine (`AbstractPageSourcePagination`,
  `NormalPagination`, `ScrollPagination`, `PatternPagination`, old `Pagination` interface,
  old builders) to `internal.pagination.engine` behind the `PaginationHost` seam in the
  SAME phase that introduces the new public `pagination` surface, keeping old tests
  relocated/adapted, then retarget 2.x callers (editor/CustomInventoryImpl) or leave the
  2.x public API delegating until Plan 3 deletes it. `BukkitSettleDispatcher` is REWRITTEN
  (tickAsync consultation deleted; plugin from slim `PageRequest`) per spec §5.8 — the
  spec's behavior map row says "modified", not verbatim.
- Build rule (memory): JDK 21 (`$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'`)
  for every mvnw invocation; test pattern
  `.\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false"`.
- HEAD note: `5dc93fb` extracted the duplicated main-thread assertion into a `ThreadUtils`
  helper after the final review fixes — read it before pinning contracts that mention
  `assertMainThread`.

## Reviewer backlog accepted into Plan 2 (from per-task + final reviews)

1. Extract a `FlushCoordinator` from `ViewEngine` (flush loop, shared-flush coalescing
   state, hook wiring) — the natural place to also unify service-path deferral; do this
   before pagination piles settle plumbing onto `ViewEngine`.
2. Resolve the `update()` coalescing question explicitly: spec §5.4 wording implies
   same-tick coalescing; the implementation runs a synchronous pass per call and the
   public Javadoc now documents that truth. Settle-driven updates multiply same-tick
   `update()` calls — decide coalesce-vs-synchronous in Plan 2's design notes and amend
   the spec or the code.
3. `flushShared` runs no trailing per-session `flushDirty`: `MutableState` dirt written
   during a shared repaint waits for the next entry point. Acceptable today; revisit when
   settles also write state.
4. `defer` double-scheduling: each `defer` call schedules a drain; the first drains all,
   later ones no-op on an empty list. Harmless now — do not let settle plumbing make
   drains non-idempotent.
5. `FirstRenderPhase` ignores the `player.openInventory` result: another plugin cancelling
   `InventoryOpenEvent` yields an ACTIVE ghost session until quit/replace (2.x had the
   same flaw). Candidate fix in Plan 2 or 3.
6. `storeFor` + thread-assert duplicated across the three context-keyed state impls
   (`ContextStateAccess` is the seam for consolidation; check what `5dc93fb` already did).
7. `ViewRegistry` uses `LinkedHashMap` (boot-time writes only; 2.x used
   `ConcurrentHashMap`) — optional hardening.
8. Per-view DI registration failure can leave partial dependency-manager state (inherited
   2.x pattern) — note only.
9. OPEN_FAILED with no previous session calls `player.closeInventory()` and may close an
   unrelated vanilla screen — acceptable on the exceptional path; could be tightened by
   checking what is actually on screen.
10. State written in `onOpen` stays dirty until the first entry point (one spurious
    STATE_CHANGE pass on first click) — cosmetic.

## Known preserved quirks (do NOT "fix" silently — spec'd)

- Rapid advance→advance→fail rolls navigation back to the last requested page, not the
  last rendered one (2.x `navigationSnapshot`/`restoreNavigation`).
- Scroll geometry: request offset slides by one element per page (`currentPage - 1`);
  page count `max(1, total - limit + 1)` — regression tests must assert the exact math.
