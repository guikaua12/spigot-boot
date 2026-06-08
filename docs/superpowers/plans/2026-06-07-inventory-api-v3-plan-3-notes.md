# inventory-api 3.0.0 — Plan 3 Authoring Notes

Input for whoever authors Plan 3 (final 2.x deletion, migration table, attribution,
3.0.0 release). Plan 2 (pagination switchover) is complete on `feat/inventory-api`
(Tasks 1–15, module suite and full reactor green). The spec stays authoritative for
Plan 3's content; this file carries the Plan-2 outcomes and the deferred backlog that
the spec and the Plan 2 doc do not.

## Authoritative sources

- Spec: `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` —
  Plan 3 implements §13 (migration & attribution, version 3.0.0) and deletes the
  remaining 2.x public surface listed in §4 ("Deleted from the public surface").
  §5.4's `update()` comment was amended in Plan 2 Task 15 (reviewer backlog #2).
- Plan 2 doc: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-2-pagination.md`
  — Shared Type Contracts for the entire pagination surface; Plan 3 must not break
  them. Follow the same authoring process (contracts pinned centrally, task bodies
  drafted against them, subagent execution with two-stage review per task).
- Plan 2 notes: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-2-notes.md` —
  the original Plan-1 outcomes and reviewer backlog; resolution status is recorded
  below.

## Plan-2 outcomes Plan 3 builds on

- The new public pagination surface shipped: `pagination/Pagination` (the reactive
  token; implements `StateToken`, so `updateOnStateChange(pagination)` and settle
  repaints work), `pagination/PaginationBuilder`, `pagination/PaginationItemRenderer`,
  and the four `View.paginate*` factories (spec §5.2/§5.7).
- The 2.x public pagination cluster is ALREADY DELETED (Plan 2 Task 5, a conscious
  deviation from the locked decisions forced by the `pagination.Pagination` FQN
  collision): the old `Pagination` interface, `pagination/impl/{
  AbstractPageSourcePagination, NormalPagination, ScrollPagination, PatternPagination}`,
  `pagination/builder/{NormalPaginationBuilder, ScrollPaginationBuilder,
  PatternPaginationBuilder, AsyncPaginationOptions}`, their test classes, and
  `InventoryEditor.fillPage`/`InventoryEditorImpl.fillPage`. Plan 3 deletes the REST of
  2.x — do not double-count the pagination cluster.
- test-plugin samples are ALREADY on the v3 API (Plan 2 Task 14):
  `inventory/{SampleScrollView, SampleNormalView, SamplePatternView, SampleAsyncView}`
  plus a `JoinListener` that opens them through the injected `ViewService`. Plan 3's
  sample work is JoinListener-adjacent cleanup only — verifying nothing in test-plugin
  still references a 2.x type once the final deletions land. Sample work REMAINING for
  Plan 3 (spec §12, last paragraph): the navigation pair (`ShopView` ↔ confirm view via
  `openOnClick`/`initialState`) and the `SharedState` leaderboard sample — the four PAGED
  samples are done; these two were never part of the pagination switchover.
- Internal pagination architecture (all `@ApiStatus.Internal`): the relocated 2.x
  engine lives in `internal/pagination/engine/` — `Paginator` (the renamed ex-2.x
  `Pagination` interface), `AbstractPageSourcePagination` and `Normal/Scroll/Pattern`
  subclasses with geometry math and the navigation/settle/rollback algorithms verbatim
  — talking to the world only through the `internal/pagination/PaginationHost` seam.
  `PaginationBinding` (one per session+token, stored in the token's `StateStore` slot)
  implements the host and owns slot mapping, element components, pre-init pending
  navigation and the per-context source; `PaginationSpec`/`PaginationSourceSpec` carry
  the frozen declaration; `PaginationInitPhase` builds engines between open and first
  render (§7 step 7); settles flow `host.requestRender()` →
  `ViewEngine.paginationSettle(session, tokenId)` → scoped PAGINATION_SETTLE update
  pass → `flushDirty`. `FlushCoordinator` (extracted from `ViewEngine`, backlog #1)
  owns the flush cascade and shared-flush coalescing.
- `PageRequest` was REWRITTEN slim (`page`/`pageSize`/`offset` plus
  `@Nullable UUID playerId()` and `@Nullable Plugin plugin()`; `Viewer` gone) and
  `BukkitSettleDispatcher` was REWRITTEN (the `tickAsync` consultation is deleted; the
  plugin is read from the request; null plugin or primary thread → inline). Spec §5.8
  documents both.
- `AsyncPageSource` gained `@ApiStatus.Internal shutdownSharedTimeoutScheduler()`,
  invoked from `InventoryApiModule`'s `@OnDisable` hook (fixes the preexisting
  classloader leak on reload).
- Reviewer backlog resolved in Plan 2: #1 (`FlushCoordinator` extraction), #2
  (`update()` coalescing — implementation truth kept, spec §5.4 amended), #6
  (`ContextStateAccess.storeFor` consolidation across the state impls).

## Deferred backlog carried into Plan 3 (one-liners from the Plan-2 notes)

- #5: `FirstRenderPhase` ignores the `player.openInventory` result: another plugin
  cancelling `InventoryOpenEvent` yields an ACTIVE ghost session until quit/replace
  (2.x had the same flaw). Candidate fix in Plan 3.
- #7: `ViewRegistry` uses `LinkedHashMap` (boot-time writes only; 2.x used
  `ConcurrentHashMap`) — optional hardening.
- #9: OPEN_FAILED with no previous session calls `player.closeInventory()` and may
  close an unrelated vanilla screen — acceptable on the exceptional path; could be
  tightened by checking what is actually on screen.
- #10: State written in `onOpen` stays dirty until the first entry point (one spurious
  STATE_CHANGE pass on first click) — cosmetic.

Items #3, #4 and #8 of the Plan-2 notes were note-only observations and remain
informational.

## Plan-3 scope reminders (spec §13)

- Delete the remaining 2.x public surface (spec §4 list): packages `editor/`, `event/`,
  `inventory/`, `item/`, `viewer/`, `registry/`, `schedule/`, `listener/`;
  `CustomInventory(Impl)`, `InventoryService`, `InventoryItem`, `InventoryEditor`,
  `Viewer`, `ViewerPropertyMap`, `@Inventory`, `InventoryLayout` (with `GridLayout`,
  the 2.x `OrderedSlotsLayout` and `InventorySlot`; the v3 replacement `Layout` already
  exists), `InventorySettings`, `InventoryConfiguration` — plus their tests. The
  pagination cluster and `InventoryEditor.fillPage` are already gone (Plan 2).
- Migration table (2.x → 3.0) ships with the module docs:
  `@Inventory`→`@RegisterView`; `CustomInventoryImpl.configure`→`onInit`;
  `configureViewer`/`firstOpen`→`onOpen`/`onFirstRender`;
  `configureInventory`+`update`→`onFirstRender`+reactive state;
  `InventoryService.open`→`ViewService.open` (throws);
  `InventoryItem.of(...).callback`→`ItemComponentBuilder.onClick`;
  `ViewerPropertyMap`→state tokens;
  `*PaginationBuilder`+`init`/`apply`→`paginate*(...)` factories;
  `InventoryLayout`→`Layout` (back/next slots removed); and the documented removals
  `Pagination.getPageOfIndex` (no public replacement; survives internally on
  `Paginator` for geometry regression value) and `Pagination.setSource` (use a lazy
  source + `refresh(ctx)`, which re-invokes the source function).
- Attribution: README section and NOTICE entry — "API design inspired by
  devnatan/inventory-framework (MIT)" — plus a note in the root `package-info.java`.
  Implementation is clean-room; the engine is original 2.x code.
- Version bump to 3.0.0 across the reactor; PR targets `dev`.
- test-plugin/JoinListener final state: already on the v3 API after Plan 2; Plan 3 only
  re-verifies `mvnw.cmd -pl test-plugin -am package` after each deletion sweep.
- Build rule (memory): JDK 21 for every mvnw invocation
  (`$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'`); the shell-default JDK 25
  crashes Lombok.

## Additional deferred items (surfaced during Plan 2 execution)

- **JoinListener `UserService` dead injection** (test-plugin): the rewired `JoinListener` keeps an injected `UserService` field that is never used (an empty `@Service`); the v3 sample rewrite (Plan-2 Task 14) preserved it because it is pre-existing test-plugin scratch-pad scaffolding (next to commented-out bungee/people experiments). Plan 3 should decide whether to drop it as part of the final test-plugin/migration cleanup.
- **Unbound-layout-char warning scope** (`FirstRenderPhase.warnUnboundLayoutChars`): the once-per-view-class warning only treats a layout char as "bound" when declared via `layoutSlot(char)`; a char whose slots are covered by absolute `slot(int)`/`slot(row,col)` components (or by `EXPLICIT_LAYOUT`/`PATTERN` pagination targets) still warns. Dev-time WARNING only; the §12 samples bind nav chars via `layoutSlot` so they are unaffected. Plan 3 (or a 3.1 polish) could widen the "bound" set to the component table's actual slot coverage.
- **Pagination-vs-pagination slot overlap** is unvalidated: `FirstRenderPhase.validatePaginationOverlap` only checks pagination-vs-static-component overlap (spec §5.3 scope). Two pagination tokens targeting the same slot would produce a paint/click mismatch. Latent; note for a future validation hardening.
- **SampleAsyncView executor ergonomics**: the §12-faithful async sample runs its simulated query via `CompletableFuture.supplyAsync` with a blocking sleep on the common ForkJoinPool — a copyable trap for plugin authors. Consider a production-executor note in the migration docs / sample comments.
