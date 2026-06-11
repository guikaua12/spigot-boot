# inventory-api 3.0.0 — Plan 3 of 3: 2.x Deletion, Migration Docs, Attribution, Release

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the remaining 2.x inventory-api public surface, add the two outstanding §12 samples (a `openOnClick`/`initialState` navigation pair and a `SharedState` leaderboard), ship the 2.x→3.0 migration table + devnatan/inventory-framework attribution, and bump the reactor to 3.0.0 — leaving inventory-api a clean v3-only module with every commit green.

**Architecture:** Subtractive: Task 1 strips the 2.x bootstrap from `InventoryApiModule` (so nothing references the soon-deleted types), Task 2 deletes the entire 2.x surface in one atomic sweep, then Tasks 3–4 add the remaining samples on the clean v3-only API, Task 5 writes the migration/attribution docs, and Task 6 bumps the version. The deletion is fully contained in `modules/inventory-api/api/` — no other reactor module references any deleted type (verified; `DiscoveryCategories.INVENTORY` is a core string constant shared by the surviving `@RegisterView`).

**Tech Stack:** Java 8 (main; tests at 17), Maven, Spigot/Paper 1.20.1, spigot-boot DI, JUnit 5, Mockito, MockBukkit-v1.20 3.20.2, Lombok (build with JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` — §4 (deleted public surface), §12 (samples), §13 (migration & attribution, version 3.0.0). Authoring inputs: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-3-notes.md`.

**Build/test commands** (every mvnw invocation MUST set JDK 21 first — the shell default JDK 25 crashes Lombok):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'
.\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=<TestClass>"
# full module check:
.\mvnw.cmd -pl modules/inventory-api/api -am test -B
# tasks touching test-plugin additionally verify:
.\mvnw.cmd -pl test-plugin -am package -B -DskipTests
# the release task runs the whole reactor:
.\mvnw.cmd clean test -B
```

**License header:** every new `.java` file starts with the MIT header block used by every existing file in the module (copy from `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/RegisterView.java` lines 1–22). Markdown/NOTICE files carry no header.

---

## File Structure

Paths relative to `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/` (call it `BASE`), `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/` (`TESTBASE`), `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/` (`TP`), or the repo root.

**Modified:**

| File | Change |
|---|---|
| `BASE/InventoryApiModule.java` | strip the 2.x bootstrap (Task 1) |
| `BASE/internal/discovery/ViewDiscoveryService.java` | delete the stale `@Inventory` comment (Task 1) |
| `TP/listener/JoinListener.java` | wire the two new samples (Tasks 3–4); drop the dead `UserService` field (Task 5) |
| every `pom.xml` in the reactor | version 2.0.2 → 3.0.0 (Task 6); plus two hard-coded `spigot-boot-core` dep versions (`modules/pom.xml`, `config/pom.xml`) |

**Created (test-plugin samples):**

| File | Responsibility |
|---|---|
| `TP/inventory/ShopView.java` | demo: pick an item, navigate to the confirm view via `openOnClick` + `ViewArguments` |
| `TP/inventory/ConfirmView.java` | demo: read the passed `initialState`, confirm/back via `openOnClick` |
| `TP/inventory/LeaderboardView.java` | demo: `SharedState` list rendered reactively via `updateOnStateChange` |

**Created (module e2e tests):**

| File | Responsibility |
|---|---|
| `TESTBASE/internal/engine/NavigationPairFlowTest.java` | drives sample-shaped shop↔confirm views through ViewService (Task 3) |
| `TESTBASE/internal/engine/SharedStateFlowTest.java` | drives a sample-shaped leaderboard view + SharedState.set repaint (Task 4) |

**Created (docs/release):**

| File | Responsibility |
|---|---|
| `modules/inventory-api/README.md` | module docs: overview + 2.x→3.0 migration table + attribution section |
| `NOTICE` (repo root) | attribution entry |
| `BASE/package-info.java` | clean-room + attribution note on the root package |

**Deleted (Task 2 — the complete 2.x surface; the pagination cluster + `InventoryEditor.fillPage` were already deleted in Plan 2, do not re-count):**

*Whole-package main deletions:*
- `BASE/editor/InventoryEditor.java`, `BASE/editor/impl/InventoryEditorImpl.java`
- `BASE/event/CustomInventoryEvent.java`, `BASE/event/impl/CustomInventoryClickEvent.java`, `BASE/event/impl/CustomInventoryCloseEvent.java`
- `BASE/inventory/CustomInventory.java`, `BASE/inventory/configuration/InventoryConfiguration.java`, `BASE/inventory/configuration/InventorySettings.java`, `BASE/inventory/configuration/impl/InventoryConfigurationImpl.java`, `BASE/inventory/impl/CustomInventoryImpl.java`
- `BASE/viewer/Viewer.java`, `BASE/viewer/ViewerContext.java`, `BASE/viewer/configuration/ViewerConfiguration.java`, `BASE/viewer/configuration/impl/ViewerConfigurationImpl.java`, `BASE/viewer/impl/ViewerImpl.java`, `BASE/viewer/property/ViewerPropertyMap.java`
- `BASE/registry/InventoryRegistry.java`, `BASE/registry/ViewerRegistry.java`, `BASE/registry/discovery/InventoryDiscoveryService.java`
- `BASE/schedule/InventoryUpdateRunnable.java`
- `BASE/listener/CustomInventoryListener.java`
- `BASE/item/InventoryItem.java`, `BASE/item/callback/ItemCallback.java`, `BASE/item/callback/update/ItemUpdateCallback.java`, `BASE/item/slot/InventorySlot.java`, `BASE/item/supplier/GenericInventoryItemSupplier.java`, `BASE/item/supplier/InventoryItemSupplier.java`

*Surgical main deletions (keep the v3 sibling):*
- `BASE/annotation/Inventory.java` (keep `RegisterView.java`)
- `BASE/layout/InventoryLayout.java`, `BASE/layout/impl/GridLayout.java`, `BASE/layout/impl/OrderedSlotsLayout.java` (keep `layout/Layout.java` + all of `internal/layout/`)
- `BASE/service/InventoryService.java` (keep `service/ViewService.java`, `service/ViewArguments.java`)

*Matching test deletions:*
- `TESTBASE/editor/impl/InventoryEditorImplTest.java`
- `TESTBASE/inventory/configuration/InventorySettingsTest.java`, `TESTBASE/inventory/configuration/impl/InventoryConfigurationImplTest.java`, `TESTBASE/inventory/impl/CustomInventoryImplTest.java`
- `TESTBASE/item/slot/InventorySlotTest.java`
- `TESTBASE/layout/impl/GridLayoutTest.java`, `TESTBASE/layout/impl/OrderedSlotsLayoutTest.java`
- `TESTBASE/listener/CustomInventoryListenerCloseTest.java`
- `TESTBASE/registry/InventoryRegistryTest.java`, `TESTBASE/registry/ViewerRegistryTest.java`, `TESTBASE/registry/discovery/InventoryDiscoveryServiceTest.java`

**Explicitly NOT deleted (preserved per spec §5.8/§5.10 — verified clean of 2.x imports):** `pagination/source/*` (the preserved engine) + the three new `pagination/{Pagination,PaginationBuilder,PaginationItemRenderer}.java`; `placeholder/*` (PlaceholderApplier SPI); `title/*` (TitleUpdater SPI); `config/*`, `context/*`, `state/*`, `component/*`, `service/{ViewService,ViewArguments}`, `exception/*`, `layout/Layout.java`, `internal/**`, `View.java`, `annotation/RegisterView.java`. Also KEEP tests `TESTBASE/layout/LayoutTest.java`, `TESTBASE/service/ViewArgumentsTest.java`, `TESTBASE/pagination/**`, and all `internal/**` tests.

**Deferred backlog NOT taken in Plan 3** (recorded in plan-3-notes — these are optional hardening, out of the "delete + release" scope and risk destabilizing a green branch at the finish line): #5 (openInventory-cancel ghost session), #7 (ViewRegistry LinkedHashMap), #9 (OPEN_FAILED closeInventory tightening), #10 (onOpen-dirt spurious pass), and the three Plan-2 dev-time observations (unbound-char warning scope, pagination-vs-pagination overlap, SampleAsyncView executor note). A one-line "deferred to 3.1" note lands in the README's known-limitations area (Task 5).

---

## Pinned rules

1. **Deletion is one atomic commit (Task 2).** Package-by-package deletion will not compile between steps (the 2.x types form a reference cycle: `viewer`↔`editor`↔`item`↔`inventory`↔`registry`). `InventoryApiModule` must already be free of 2.x references (Task 1) before the sweep. Use `git rm` for every listed file in a single commit; verify the module + full reactor compile and the remaining suite is green afterward.
2. **No 2.x symbol survives.** After Task 2, a repo-wide grep (excluding `local-only/`, `tmp_*`, `inventory-framework/`, `docs/`) for imports of `inventoryapi.{editor,event,inventory,viewer,registry,schedule,listener,item}`, `inventoryapi.annotation.Inventory`, `inventoryapi.layout.{InventoryLayout,impl.GridLayout,impl.OrderedSlotsLayout}`, and `inventoryapi.service.InventoryService` must return ZERO hits.
3. **Every commit stays green.** Task 1 (module strip) leaves 2.x present-but-unreferenced — module green. Task 2 removes it — reactor green. Tasks 3–4 add samples — module + test-plugin green. Task 5 docs + one code edit — test-plugin green. Task 6 version bump — full reactor `clean test` green.
4. **Samples are real, copyable v3 examples** (spec §1 goal: less boilerplate than 2.x). View fields hold only state/pagination tokens + immutable config; all per-player data lives in per-context state. The module e2e tests use sample-SHAPED views defined in the test (test-plugin classes are not on the module test classpath).
5. **Exact public signatures the samples call** (confirmed against the current source — do not invent):
   - `protected final <T> MutableState<T> initialState(@NotNull String key, @NotNull Class<T> type)` (View)
   - `protected final <T> SharedState<T> sharedState(@Nullable T initialValue)` (View)
   - `ViewArguments.of(@NotNull String key, @NotNull Object value)` / `<T> @NotNull T require(@NotNull String key, @NotNull Class<T> type)`
   - `ItemComponentBuilder.openOnClick(@NotNull Class<? extends View> target)` / `openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments)`
   - `ItemComponentBuilder.item(@NotNull ItemStack)` / `item(@NotNull Function<ViewContext, ItemStack>)` / `onClick(@NotNull Consumer<SlotClickContext>)` / `updateOnStateChange(@NotNull StateToken...)`
   - `SharedState.get()` (NO context arg — unlike `State.get(ViewContext)`), `set(@Nullable T)`, `update(@NotNull UnaryOperator<T>)`
   - `State.get(@NotNull ViewContext)` for `MutableState`/`initialState` reads
   - `ViewContext.close()` (deferred to end of tick during click dispatch)
6. **Version bump (Task 6) is mechanical:** `mvnw.cmd versions:set -DnewVersion=3.0.0 -DgenerateBackupPoms=false` rewrites the root + all parent-inheritance chains, THEN manually fix the TWO hard-coded `<version>2.0.2</version>` `spigot-boot-core` dependency declarations that `versions:set` does NOT touch — `modules/pom.xml` (~line 34) and `config/pom.xml` (~line 27). Reactor is single-version (2.0.2 today); "inventory-api 3.0.0" means the whole reactor goes to 3.0.0.
7. **Style:** 4-space indent, same-line braces, full Javadoc on public/protected API, lowercase normal comments, no fully-qualified inline types, MIT header on new `.java` files, SOLID.
8. **MockBukkit:** `MockBukkit.mock()`/`unmock()` per test; `unmock()` drains pending scheduler tasks — `server.getScheduler().cancelTasks(plugin)` in tearDown when deferred ops were queued; deferred `close()`/`openView()` run on `performTicks(1)`.

---

<!-- TASKS START -->

### Task 1: Strip the 2.x bootstrap from InventoryApiModule

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java`
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryService.java`

This is a behavior-preserving refactor of the v3 boot path: the module stops discovering/ticking 2.x `CustomInventory`s (that whole subsystem is deleted in Task 2) and keeps only the v3 `ViewRegistry` init + the pagination-scheduler `@OnDisable`. After this task the 2.x types still exist but nothing in the module references them, so the module compiles green.

- [ ] **Step 1: Record the green baseline**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dtest=InventoryApiModuleDisableTest"`
Expected: PASS (the `@OnDisable` hook test; it is the only `InventoryApiModule` test and must stay green — Task 1 leaves `onDisable` untouched).

- [ ] **Step 2: Rewrite InventoryApiModule.java**

Replace the entire file body (keep the MIT header lines 1–22) with:

```java
package tech.guilhermekaua.spigotboot.inventoryapi;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;

/**
 * Bootstraps the inventory-api view engine: initializes the {@link ViewRegistry}, which
 * discovers and registers every
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView}-annotated
 * {@link View} subclass under the host plugin's base package and instantiates each through
 * the dependency manager.
 */
public final class InventoryApiModule implements Module {

    @Inject
    private ViewRegistry viewRegistry;

    @Override
    public void onInitialize(Context context) throws Exception {
        viewRegistry.initialize(context);
    }

    /**
     * Shuts the shared pagination timeout scheduler down when the host plugin disables.
     * Open sessions are already closed when this runs: {@code ViewListener.onPluginDisable}
     * reacts to Bukkit's {@code PluginDisableEvent}, which fires before the context destroys
     * its beans and invokes this hook, so no session can still be waiting on a timeout. The
     * scheduler is recreated lazily on the next timeout-bearing request.
     */
    @OnDisable
    public void onDisable() {
        AsyncPageSource.shutdownSharedTimeoutScheduler();
    }
}
```

(Removed: the `InventoryRegistry`/`ViewerRegistry`/`Plugin` fields, the `BukkitScheduler` tick loop, and the imports `org.bukkit.Bukkit`, `org.bukkit.plugin.Plugin`, `org.bukkit.scheduler.BukkitScheduler`, `inventory.CustomInventory`, `inventory.configuration.InventoryConfiguration`, `registry.InventoryRegistry`, `registry.ViewerRegistry`, `schedule.InventoryUpdateRunnable`.)

- [ ] **Step 3: Delete the stale @Inventory comment in ViewDiscoveryService**

In `internal/discovery/ViewDiscoveryService.java`, find the comment (around line 82) that says the INVENTORY index category is shared with 2.x `@Inventory` classes (wording like "the INVENTORY index category is shared with 2.x @Inventory classes"). Delete that sentence/comment only — the `reader.classesInCategory(DiscoveryCategories.INVENTORY, basePackage)` call and all logic stay unchanged (the category constant is a core string, not a 2.x type). If the comment does not exist or has already been removed, skip this step and note it.

- [ ] **Step 4: Verify green**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS; the module compiles with 2.x present-but-unreferenced; `InventoryApiModuleDisableTest` + all v3 suites green).

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryService.java
git commit -m "refactor(inventory-api): drop the 2.x inventory bootstrap from the module" -m "InventoryApiModule now only initializes the v3 ViewRegistry and keeps the pagination-scheduler @OnDisable hook; the 2.x CustomInventory discovery + InventoryUpdateRunnable tick loop (and their InventoryRegistry/ViewerRegistry/Plugin injections) are removed ahead of the 2.x deletion sweep. The stale @Inventory note on ViewDiscoveryService is dropped."
```

---

### Task 2: Delete the remaining 2.x public surface

**Files:** see the "Deleted (Task 2 …)" list in the File Structure section — every path there, main and test. No new files.

One atomic deletion. After Task 1 nothing references these types except other deleted types, so the sweep compiles clean in one commit.

- [ ] **Step 1: git rm the whole-package main deletions**

```bash
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/InventoryEditor.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/impl/InventoryEditorImpl.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/event/CustomInventoryEvent.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/event/impl/CustomInventoryClickEvent.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/event/impl/CustomInventoryCloseEvent.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/CustomInventory.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventoryConfiguration.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/impl/InventoryConfigurationImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImpl.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/Viewer.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/ViewerContext.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/configuration/ViewerConfiguration.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/configuration/impl/ViewerConfigurationImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/impl/ViewerImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/viewer/property/ViewerPropertyMap.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistry.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/ViewerRegistry.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/discovery/InventoryDiscoveryService.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/schedule/InventoryUpdateRunnable.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListener.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/InventoryItem.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/callback/ItemCallback.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/callback/update/ItemUpdateCallback.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlot.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/supplier/GenericInventoryItemSupplier.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/supplier/InventoryItemSupplier.java
```

- [ ] **Step 2: git rm the surgical main deletions (keep the v3 siblings)**

```bash
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/Inventory.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayout.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayout.java
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/service/InventoryService.java
```

- [ ] **Step 3: git rm the matching test deletions**

```bash
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/impl/InventoryEditorImplTest.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettingsTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/impl/InventoryConfigurationImplTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlotTest.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayoutTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListenerCloseTest.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/ViewerRegistryTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/discovery/InventoryDiscoveryServiceTest.java
```

- [ ] **Step 3b: Verify no 2.x test was missed**

Run a grep for any surviving test that imports a deleted type (PowerShell, from repo root):
`Get-ChildItem -Recurse modules/inventory-api/api/src/test -Filter *.java | Select-String -Pattern "inventoryapi\.(editor|event|inventory|viewer|registry|schedule|listener|item)\.","annotation\.Inventory;","layout\.InventoryLayout","layout\.impl\.","service\.InventoryService"`
Expected: NO matches. If any test file matches (e.g. an `InventoryServiceTest` not listed above), `git rm` it too and note it in the report.

- [ ] **Step 4: Verify the deletion is complete and contained**

Run the pinned-rule-2 grep (PowerShell, from repo root — exclude scratch dirs):
`Get-ChildItem -Recurse -Filter *.java modules,test-plugin,platform-spigot,core,utils,commands,config,data | Select-String -Pattern "inventoryapi\.(editor|event|inventory|viewer|registry|schedule|listener|item)\.","annotation\.Inventory;","layout\.(InventoryLayout|impl\.(GridLayout|OrderedSlotsLayout))","service\.InventoryService"`
Expected: NO matches anywhere (the deletion is fully contained in inventory-api; nothing external referenced these types).

- [ ] **Step 5: Verify the reactor compiles and the remaining suite is green**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS; the module is now v3-only; the suite is smaller — the deleted 2.x tests are gone — and green). Then verify the sample plugin still builds:
Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package -B -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git commit -m "refactor(inventory-api)!: delete the 2.x public surface" -m "Removes the entire 2.x inventory API now that the v3 view engine + declarative pagination have replaced it: the editor/event/inventory/viewer/registry/schedule/listener/item packages, the 2.x annotation @Inventory, InventoryLayout (+ GridLayout/OrderedSlotsLayout/InventorySlot), InventoryService, and their tests. The v3 surface (View/@RegisterView/ViewService/Layout/state/component/context/pagination) and the preserved SPIs (PlaceholderApplier, TitleUpdater) and page-source engine remain. BREAKING: pre-3.0 the 2.x types are gone with no deprecation layer."
```

---

### Task 3: ShopView ↔ ConfirmView navigation-pair sample

**Files:**
- Create: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/ShopView.java`
- Create: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/ConfirmView.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/NavigationPairFlowTest.java`

Demonstrates spec §12's cross-view navigation: a shop where clicking an item opens a confirm view with the choice passed via `ViewArguments`/`initialState`, and a back button that returns to the shop. The e2e test uses sample-SHAPED views defined in the test (test-plugin classes aren't on the module test classpath).

- [ ] **Step 1: Write the failing e2e test**

Create `NavigationPairFlowTest.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class NavigationPairFlowTest {

    private ServerMock server;
    private MockPlugin plugin;
    private PlayerMock player;
    private ViewService service;
    private ViewListener listener;
    private SessionRegistry sessions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        views.register(new ShopFlowView());
        views.register(new ConfirmFlowView());
        ViewEngine engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    @Test
    void buyButton_opensConfirmWithTheSelectedItem_thenBackReturnsToShop() {
        service.open(player, ShopFlowView.class);
        Inventory shop = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        assertEquals(Material.DIAMOND, shop.getItem(0).getType());

        // click the diamond "buy" button → deferred openView(ConfirmFlowView, {item:DIAMOND})
        listener.onClick(click(shop, 0));
        server.getScheduler().performTicks(1);

        Inventory confirm = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // the confirm view rendered the passed selection (its title-slot shows the chosen material)
        assertNotNull(confirm.getItem(4));
        assertEquals(Material.DIAMOND, confirm.getItem(4).getType());

        // click the "back" button (slot 0) → deferred openView(ShopFlowView)
        listener.onClick(click(confirm, 0));
        server.getScheduler().performTicks(1);

        Inventory backToShop = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // back on the shop: its diamond offer is rendered again, and the confirm container is gone
        assertEquals(Material.DIAMOND, backToShop.getItem(0).getType());
        assertTrue(backToShop != confirm, "navigating back replaced the confirm container with a fresh shop");
    }

    private InventoryClickEvent click(Inventory top, int slot) {
        InventoryView view = Mockito.mock(InventoryView.class);
        Inventory bottom = player.getInventory();
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getPlayer()).thenReturn(player);
        when(view.convertSlot(Mockito.anyInt())).thenAnswer(i -> i.getArgument(0));
        when(view.getInventory(Mockito.anyInt())).thenAnswer(i -> {
            int raw = i.getArgument(0);
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    // sample-shaped views (mirror ShopView/ConfirmView in test-plugin) ----------------

    public static final class ShopFlowView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Shop").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.DIAMOND))
                    .openOnClick(ConfirmFlowView.class,
                            ViewArguments.of("item", Material.DIAMOND.name()));
        }
    }

    public static final class ConfirmFlowView extends View {
        private final MutableState<String> item = initialState("item", String.class);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Confirm").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.BARRIER))
                    .openOnClick(ShopFlowView.class);
            render.slot(4).item(ctx -> new ItemStack(Material.valueOf(item.get(ctx))));
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NavigationPairFlowTest"`
Expected: PASS (1 test). This is a behavior gate over the existing v3 engine (the test defines its own `ShopFlowView`/`ConfirmFlowView`), like Plan 2's sample-flow tests — it should pass on first run. If it FAILS, the failure reveals an engine bug — debug the engine, do not weaken the test; a compile error means a missing import to add.

- [ ] **Step 3: Create the ShopView test-plugin sample**

Create `test-plugin/.../inventory/ShopView.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

/**
 * Navigation sample (1 of 2): a tiny shop. Clicking an offer opens {@link ConfirmView},
 * passing the chosen material through {@link ViewArguments} — the v3 replacement for
 * stashing the selection in a {@code ViewerPropertyMap}. Navigation is declarative: the
 * click handler calls {@code ctx.openView(target, args)}, which closes this view with
 * {@code REPLACED} and opens the confirm view at end of tick.
 */
@RegisterView
public final class ShopView extends View {

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&2Shop").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(2, new ItemStack(Material.DIAMOND))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.DIAMOND.name()));
        render.slot(4, new ItemStack(Material.EMERALD))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.EMERALD.name()));
        render.slot(6, new ItemStack(Material.GOLD_INGOT))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.GOLD_INGOT.name()));
    }
}
```

- [ ] **Step 4: Create the ConfirmView test-plugin sample**

Create `test-plugin/.../inventory/ConfirmView.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

/**
 * Navigation sample (2 of 2): the confirm screen for {@link ShopView}. The chosen material
 * arrives as an {@code initialState} token bound from the {@code ViewArguments} the shop
 * passed to {@code openView} — type-checked at the open site. The centre slot renders the
 * selection; "confirm" closes the view, "back" reopens the shop.
 */
@RegisterView
public final class ConfirmView extends View {

    private final MutableState<String> item = initialState("item", String.class);

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&eConfirm purchase").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(0, new ItemStack(Material.BARRIER))
                .openOnClick(ShopView.class);
        render.slot(4).item(ctx -> new ItemStack(Material.valueOf(item.get(ctx))));
        render.slot(8, new ItemStack(Material.LIME_WOOL))
                .onClick(ctx -> {
                    ctx.player().sendMessage("[ApxPlugin] - purchased " + item.get(ctx));
                    ctx.close();
                });
    }
}
```

- [ ] **Step 5: Wire the shop into JoinListener**

In `test-plugin/.../listener/JoinListener.java`, add the import `import tech.guilhermekaua.spigotboot.testPlugin.inventory.ShopView;` and append a new block inside `onBlockPlace`, directly after the existing `NETHERITE_BLOCK` block's closing brace and before the method's closing brace (the `NETHERITE_BLOCK` block itself is unchanged — it is already the last block and needs no `return`):

```java
        if (blockType == Material.IRON_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening shop navigation sample");
            viewService.open(player, ShopView.class);
            return;
        }
```

(`ConfirmView` is reached via `openView` from the shop, so it needs no block trigger; it is still auto-registered by `@RegisterView`.)

- [ ] **Step 6: Verify green**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NavigationPairFlowTest"`
Expected: PASS (1 test). Then: `.\mvnw.cmd -pl test-plugin -am package -B -DskipTests`
Expected: BUILD SUCCESS (ShopView/ConfirmView compile against the v3 API).

- [ ] **Step 7: Commit**

```bash
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/NavigationPairFlowTest.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/ShopView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/ConfirmView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
git commit -m "feat(test-plugin): add the shop↔confirm navigation sample" -m "Demonstrates v3 cross-view navigation: ShopView passes the chosen material to ConfirmView through ViewArguments + initialState via openView, and ConfirmView navigates back. NavigationPairFlowTest drives the full open→buy→confirm→back flow through ViewService + ViewListener. JoinListener opens the shop on IRON_BLOCK; ConfirmView is reached via openView and auto-registered."
```

---

### Task 4: LeaderboardView SharedState sample

**Files:**
- Create: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/LeaderboardView.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/SharedStateFlowTest.java`

Demonstrates spec §12's `SharedState` leaderboard: one value shared by every viewer of the view, written from anywhere, with watching components repainting reactively on `set`.

- [ ] **Step 1: Write the failing e2e test**

Create `SharedStateFlowTest.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedStateFlowTest {

    private ServerMock server;
    private MockPlugin plugin;
    private ViewService service;
    private SessionRegistry sessions;
    private LeaderboardFlowView leaderboard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        leaderboard = new LeaderboardFlowView();
        views.register(leaderboard);
        ViewEngine engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
        service = new ViewService(engine, sessions);
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    @Test
    void sharedStateSet_repaintsTheWatchingComponentForEveryOpenSession() {
        PlayerMock a = server.addPlayer("a");
        PlayerMock b = server.addPlayer("b");
        leaderboard.topName.set("nobody");

        service.open(a, LeaderboardFlowView.class);
        service.open(b, LeaderboardFlowView.class);

        Inventory invA = sessions.find(a.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        Inventory invB = sessions.find(b.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // amount encodes the name length so we can observe repaints deterministically
        assertEquals("nobody".length(), invA.getItem(0).getAmount());
        assertEquals("nobody".length(), invB.getItem(0).getAmount());

        // a single shared write must repaint the watching component of BOTH open sessions
        leaderboard.topName.set("champion");

        assertEquals("champion".length(), invA.getItem(0).getAmount());
        assertEquals("champion".length(), invB.getItem(0).getAmount());
    }

    public static final class LeaderboardFlowView extends View {
        final SharedState<String> topName = sharedState("nobody");

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Leaderboard").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> new ItemStack(Material.PAPER, Math.max(1, topName.get().length())))
                    .updateOnStateChange(topName);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes (behavior gate)**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=SharedStateFlowTest"`
Expected: PASS (1 test) — `SharedState.set` on the main thread flushes watchers of every open session inline (Plan 1 behavior). If it FAILS, the failure reveals a SharedState-flush bug — debug the engine, do not weaken the test. A compile error means a missing import.

- [ ] **Step 3: Create the LeaderboardView test-plugin sample**

Create `test-plugin/.../inventory/LeaderboardView.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.Arrays;
import java.util.List;

/**
 * SharedState sample: a leaderboard whose top entries are one value shared by every viewer
 * of the view (not per-player). {@code sharedState} holds an immutable list; any thread may
 * {@code set}/{@code update} it, and the framework marshals the watcher repaint to the main
 * thread — so calling {@code topThree.set(...)} from a scoreboard task repaints every open
 * leaderboard at once. Components watch the token via {@code updateOnStateChange}; no
 * scheduled update is needed.
 */
@RegisterView
public final class LeaderboardView extends View {

    private final SharedState<List<String>> topThree =
            sharedState(Arrays.asList("—", "—", "—"));

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&6Leaderboard").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        for (int i = 0; i < 3; i++) {
            final int rank = i;
            render.slot(3 + i)
                    .item(ctx -> named(new ItemStack(Material.PAPER, rank + 1),
                            "#" + (rank + 1) + " " + entryAt(rank)))
                    .updateOnStateChange(topThree);
        }
    }

    private String entryAt(int rank) {
        List<String> entries = topThree.get();
        return entries != null && rank < entries.size() ? entries.get(rank) : "—";
    }

    private static ItemStack named(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
```

- [ ] **Step 4: Wire the leaderboard into JoinListener**

In `JoinListener.java`, add `import tech.guilhermekaua.spigotboot.testPlugin.inventory.LeaderboardView;` and append after the `IRON_BLOCK` block:

```java
        if (blockType == Material.LAPIS_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening leaderboard sample");
            viewService.open(player, LeaderboardView.class);
        }
```

- [ ] **Step 5: Verify green**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=SharedStateFlowTest"`
Expected: PASS (1 test). Then: `.\mvnw.cmd -pl test-plugin -am package -B -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/SharedStateFlowTest.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/LeaderboardView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
git commit -m "feat(test-plugin): add the SharedState leaderboard sample" -m "Demonstrates view-global SharedState: one shared top-three list rendered reactively via updateOnStateChange, repainting every open session when set from anywhere. SharedStateFlowTest pins that a single set repaints the watching component of two concurrent sessions. JoinListener opens it on LAPIS_BLOCK."
```

---

### Task 5: Migration docs, attribution, and the JoinListener cleanup

**Files:**
- Create: `modules/inventory-api/README.md`
- Create: `NOTICE` (repo root)
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/package-info.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java` (drop the dead `UserService` field)

Ships the 2.x→3.0 migration table + the devnatan/inventory-framework attribution (spec §13) and removes the last dead 2.x-era scaffolding.

- [ ] **Step 1: Create the module README with the migration table + attribution**

Create `modules/inventory-api/README.md`:

````markdown
# spigot-boot-inventory-api

A view-based inventory framework for spigot-boot: discovered `View` singletons opened through
an injected `ViewService`, with typed per-phase contexts, reactive state, and declarative
pagination.

## Quick start

```java
@RegisterView
public final class HelloView extends View {
    @Override protected void onInit(ViewConfigBuilder config) {
        config.title("&aHello").rows(3);
    }
    @Override protected void onFirstRender(RenderContext render) {
        render.slot(13, new ItemStack(Material.DIAMOND))
              .onClick(ctx -> ctx.player().sendMessage("clicked!"));
    }
}
```

```java
@Component
public final class Opener {
    private final ViewService views;
    Opener(ViewService views) { this.views = views; }
    void open(Player player) { views.open(player, HelloView.class); }
}
```

## Migration from 2.x

The 3.0.0 API is a clean break — the 2.x template-method surface (`CustomInventory`,
`Viewer`, `InventoryEditor`, `ViewerPropertyMap`, `@Inventory`) is removed with no
deprecation layer.

| 2.x | 3.0 |
|---|---|
| `@Inventory` | `@RegisterView` |
| `CustomInventoryImpl.configure(...)` | `View.onInit(ViewConfigBuilder)` |
| `firstOpen(viewer, editor)` | `View.onOpen(OpenContext)` / `onFirstRender(RenderContext)` |
| `configureInventory(...)` + `update(...)` | `onFirstRender(...)` + reactive state (`mutableState`/`sharedState`/`lazyState`) |
| `InventoryService.open(player, class)` (returned `null` for unknown) | `ViewService.open(player, class)` (throws `UnknownViewException`) |
| `InventoryItem.of(item).callback(...)` | `RenderContext.slot(...).item(...).onClick(...)` |
| `ViewerPropertyMap` (stringly-typed per-viewer state) | state tokens: `mutableState`, `initialState(key, type)`, `lazyState`, `sharedState` |
| `NormalPaginationBuilder` / `ScrollPaginationBuilder` / `PatternPaginationBuilder` + `init`/`apply` | `View.paginate(...)` / `paginateAsync(...)` / `paginateSource(...)` returning a `PaginationBuilder` |
| `InventoryLayout` (with reserved `<`/`>` back/next slots) | `Layout` (navigation is ordinary components bound with `displayIf(pagination::canBack)`) |
| `Pagination.setSource(list)` | a lazy source (`paginate(ctx -> list)`) + `pagination.refresh(ctx)` |
| `Pagination.getPageOfIndex(i)` | removed (no public replacement) |
| `InventoryConfiguration.tickAsync()` / async scheduled updates | removed — async pagination + reactive settle repaint cover the live-data use case |

## Attribution

The public API design is **inspired by [devnatan/inventory-framework](https://github.com/devnatan/inventory-framework)** (MIT) — its
view/state/declarative-pagination vocabulary and ergonomics. The implementation is
clean-room: no code or documentation text was copied, and the page-source pagination engine
is original spigot-boot code carried over from the 2.x module.

## Known limitations (3.1 candidates)

- A view opened while another plugin cancels `InventoryOpenEvent` can leave an ACTIVE ghost
  session until the player quits or replaces it (inherited 2.x behavior).
- Two pagination tokens targeting the same slot are not validated (paint/click would mismatch).
- The unbound-layout-char boot warning only treats `layoutSlot(char)` declarations as
  "binding" a char; covering the same slots with absolute `slot(int)` components still warns.
````

- [ ] **Step 2: Create the repo-root NOTICE**

Create `NOTICE` (repo root):

```
spigot-boot
Copyright © 2025 Guilherme Kauã da Silva

This product includes software developed as part of spigot-boot, licensed under the MIT License.

The spigot-boot-inventory-api module's public API design is inspired by
devnatan/inventory-framework (https://github.com/devnatan/inventory-framework), licensed
under the MIT License. The implementation is clean-room; no source code or documentation
text was copied.
```

- [ ] **Step 3: Create the root package-info.java**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/package-info.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
/**
 * The spigot-boot inventory framework (3.0.0): view-based inventories with typed per-phase
 * contexts, reactive state, and declarative pagination.
 *
 * <p>The public API design is inspired by
 * <a href="https://github.com/devnatan/inventory-framework">devnatan/inventory-framework</a>
 * (MIT) — concepts and ergonomics are adopted; the implementation is clean-room and no code
 * or documentation text was copied. The page-source pagination engine is original
 * spigot-boot code.
 */
package tech.guilhermekaua.spigotboot.inventoryapi;
```

- [ ] **Step 4: Drop the dead UserService injection from JoinListener**

In `test-plugin/.../listener/JoinListener.java`, remove the field `private final UserService userService;` and the import `import tech.guilhermekaua.spigotboot.testPlugin.services.UserService;` (it is never used — the `@RequiredArgsConstructor` then injects only `ViewService`). Leave everything else.

- [ ] **Step 5: Verify the sample plugin still builds**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package -B -DskipTests`
Expected: BUILD SUCCESS (JoinListener compiles with only the `ViewService` injection; DI still resolves it). Then the module suite (the package-info is a new compilation unit):
Run: `.\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/README.md NOTICE modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/package-info.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
git commit -m "docs(inventory-api): add the 2.x→3.0 migration guide and devnatan attribution" -m "Module README ships the migration table + a clean-room attribution to devnatan/inventory-framework (MIT); a repo-root NOTICE and a root package-info.java carry the same attribution. Drops the unused UserService injection left in the test-plugin JoinListener."
```

---

### Task 6: Bump the reactor to 3.0.0 and verify the whole build

**Files:** every `pom.xml` in the reactor (`<version>`/`<parent><version>` 2.0.2 → 3.0.0) + the one hard-coded dependency version in `modules/pom.xml`.

The reactor is single-version (2.0.2 today); spec §13 takes the whole reactor to 3.0.0.

- [ ] **Step 1: Run versions:set**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd versions:set -DnewVersion=3.0.0 -DgenerateBackupPoms=false -B`
Expected: BUILD SUCCESS; it rewrites the root `<version>` and every module's `<parent><version>` from 2.0.2 to 3.0.0.

- [ ] **Step 2: Fix the one hard-coded dependency version**

`versions:set` updates project/parent versions but NOT hard-coded `<dependency>` versions. There are TWO such literals to fix: in `modules/pom.xml` (~line 34) AND in `config/pom.xml` (~line 27), each a `spigot-boot-core` dependency with a literal `<version>2.0.2</version>` — change both to `<version>3.0.0</version>`. Then grep for any remaining `2.0.2` (PowerShell, repo root):
`Get-ChildItem -Recurse -Filter pom.xml modules,test-plugin,platform-spigot,core,utils,commands,config,data,. -Depth 4 | Select-String -Pattern "2\.0\.2"`
Expected: NO matches (every `2.0.2` is now `3.0.0`). Fix any stragglers `versions:set` missed.

- [ ] **Step 3: Verify the whole reactor compiles, tests, and packages at 3.0.0**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd clean test -B`
Expected: BUILD SUCCESS — every module green at 3.0.0. Paste the Reactor Summary into the task report. If any module fails to resolve `spigot-boot-*:3.0.0`, a `<version>` was missed in step 2 — fix and rerun.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(release): bump the reactor to 3.0.0" -m "inventory-api ships its v3 view-based API; the reactor is single-version, so the whole build moves 2.0.2 → 3.0.0. Updates every pom <version>/<parent><version> plus the hard-coded spigot-boot-core dependency version in modules/pom.xml."
```

- [ ] **Step 5: Finish the branch**

The three-plan v3 migration is complete. Use superpowers:finishing-a-development-branch to merge `feat/inventory-api` into `dev` (or open a PR to `dev` per spec §13 "PR targets `dev`"). Verify the full reactor is green first (step 3 already did).
