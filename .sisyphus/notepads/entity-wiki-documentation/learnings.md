## 2026-04-14T12:51:11Z Task: session-bootstrap
- The deliverable is documentation only: create exactly 10 files under `wiki/wiki/` and do not touch `_Sidebar.md`, code, scripts, tests, or extra wiki pages.
- Use the existing local wiki house style from `wiki/wiki/Commands_CommandsExplained.md` and `wiki/wiki/Commands_Home.md`: code example first, explanatory prose, strong direct guidance, and inline contextual links.
- Every non-Home page must remain user-facing and explain why packet and tracker control matters beyond plain spawn and despawn.
- The Home page is special: opening paragraph plus one hierarchical bulleted list only, with exact top-level category labels and internal wiki links without `.md` suffixes.
- Ground every page in real repository sources; the best practical examples live in `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java` and `EntityDemoCommand.java`.
- Exact scenario ids to preserve: `orbit`, `deathfx-cow`, `metadata-dirty-zombie`, `viewer-cycle-zombie`, `attach-existing-zombie`.
- Exact representative server ids to preserve in version/testing coverage: `spigot-1.8.8`, `spigot-1.13.2`, `spigot-1.16.5`, `spigot-1.17.1`, `paper-1.19.2`, `paper-1.21.11`.

## 2026-04-14T12:57:00Z Task: wiki-style-research
- Highest-signal local style references are `wiki/wiki/Commands_CommandsExplained.md`, `wiki/wiki/DependencyInjectionExplained.md`, `wiki/wiki/DataJDBC_DataJdbcExplained.md`, `wiki/wiki/DataJDBC_EntityMappingExplained.md`, and `wiki/wiki/CoreSharpEdgesAndTips.md`.
- Practical local page shape is: H1 -> immediate code block -> short payoff paragraph -> `## Why?` -> `## How?` -> targeted detail sections. Keep the page explanatory, not encyclopedic.
- Home-page tone should borrow from `wiki/wiki/Commands_Home.md`: each linked bullet sells the destination page instead of merely naming it.
- Local wiki links are inconsistent today (`Home.md` uses no `.md`, `Commands_Home.md` sometimes includes `.md`), so entity pages must deliberately normalize to the prompt-required `[Page Title](PageName)` form without `.md`.
- Tables are acceptable when used as compact cheat sheets for scenarios, core types, or support matrices; do not turn entire pages into tables.
- Warnings should stay plain Markdown using `*Note:*`, `**Important:**`, and `**Warning:**`; never use GitHub admonition blocks.

## 2026-04-14T12:57:00Z Task: guava-github-wiki-research
- GitHub wiki page filenames determine the page slug/title used in wiki navigation and URLs; stable prompt-defined filenames are the canonical target.
- GitHub heading anchors are auto-generated from heading text, lowercase with hyphenated spaces; duplicate headings get numeric suffixes. Keep headings stable once linked.
- Guava-style wiki organization favors a curated Home page plus strong page-local headings, with examples appearing early and deep links used inline at the exact sentence where the reader needs them.

## 2026-04-14T13:00:00Z Task: page-source-anchors
- Highest-value reusable snippet anchors across multiple pages:
  - `versions/runtime/.../SpigotEntityBootstrap.java:108-127` for bootstrap entry.
  - `test-plugin/.../EntityDemoService.java:120-135` for minimal spawn + initialize + controller.
  - `test-plugin/.../EntityDemoService.java:245-258` for metadata initial snapshot + dirty delta.
  - `test-plugin/.../EntityDemoService.java:268-279` for network controller wiring.
  - `test-plugin/.../EntityDemoService.java:446-452` for attach existing entity and swap controllers.
  - `scripts/run-entity-matrix.ps1:177-210` for matrix runner contract.
- Best grounding for `EntitiesExplained.md` specifically:
  - `versions/runtime/.../SpigotEntityBootstrap.java:103-170`.
  - `versions/runtime/.../VersionedEntityPlatform.java:176-209,397-460`.
  - `test-plugin/.../EntityDemoService.java:118-135,172-186`.
- Best exact wording pulls from code/comments:
  - `EntityTemplate`: "Immutable reusable blueprint for spawned controlled entities."
  - `SpawnBuilder`: "Fluent one-off spawn customizer."
  - `ControlledEntity`: "Represents a live hooked entity whose native lifecycle is delegated through a controller."
  - `EntityNetworkController`: "Separate controller surface responsible for tracker and network synchronization behavior."
  - `EntityNetworkMetadataContract`: "Dedicated runtime contract for watcher and metadata synchronization payload assembly."
  - `CustomEntityDefinition`: "Legacy definition wrapper kept for migration from the old custom-entity API."

## 2026-04-14T13:10:00Z Task: entities-explained-page
- `EntitiesExplained.md` should frame entities as a managed runtime, not a spawn helper: lead with `SpigotEntityBootstrap.boot()` plus a one-off `platform.spawn(...)`, then immediately explain packet and tracker control, metadata sync, viewer lifecycle ownership, and attach support.
- The two layers readers need early are `versions/api` for author-facing types and `VersionedEntityPlatform` from `versions/runtime` for the runtime entry and version selection boundary.
- For this page, keep template mechanics shallow: distinguish reusable templates from one-off spawns here, then hand off the deep blueprint story through inline links to `EntityTemplatesExplained`, `SpawningAndControllersExplained`, `PacketAndTrackerControlExplained`, and `AttachingAndWrappingExplained`.

## 2026-04-14T00:00:00Z Task: packet-and-tracker-control-page
- `PacketAndTrackerControlExplained.md` works best when the concrete story stays on `viewer-cycle-zombie`: explain `onViewerAdded`, `onViewerRemoved`, and `onUnbind` through observer coherence, then place `onBind` and `onTick` beside them as the broader `EntityNetworkController` lifecycle surface.
- Keep sync wording user-facing: describe absolute sync, relative sync, rotation sync, and velocity sync as publication strategies chosen by thresholds, not as raw packet trivia plugin authors must memorize.

## 2026-04-14T13:40:00Z Task: metadata-sync-page
- `MetadataSyncExplained.md` should stay anchored on `metadata-dirty-zombie`: lead with one-line `networkMetadataContract().initialSnapshot(...)` and `dirtyDelta(...)` calls, then explain initial full state versus later dirty watcher deltas through watcher items, living attributes, equipment, active effects, and head rotation.
- The strongest user-facing framing is viewer coherence: new viewers need a complete baseline, while replacement and attach flows need the same guarantee before incremental deltas make sense across version adapters.

## 2026-04-14T00:00:00Z Task: attaching-and-wrapping-page
- `AttachingAndWrappingExplained.md` should stay centered on `platform().get(zombie)` as the ownership handoff for existing Bukkit entities, with `wrap` as the simple entry and `attach-existing-zombie` as the proof-oriented scenario.
- The strongest attach proof story is the exact trio from `EntityDemoService`: `entityIdStable` for same entity identity, `duplicateSpawnCount` for no duplicate controlled spawn, and `trackerRebound` for coherent rebound to the still-hooked runtime binding.

## 2026-04-14T00:00:00Z Task: version-support-page
- `VersionSupportExplained.md` should describe selection as runtime-profile-aware and validated before use, not as simple version-string matching.
- Keep the support evidence table limited to the six representative ids from `servers.json`, then mention any extra entries in one short sentence outside the table.

## 2026-04-14T00:00:00Z Task: entity-testing-and-matrix-page
- `EntityTestingAndMatrixVerificationExplained.md` should stay command-first: open with a short `pwsh` call to `scripts/run-entity-matrix.ps1`, then immediately position `/entitydemo ...` as the fastest local proof surface before shifting into retained matrix evidence.
- Best grounded reading guidance comes from `EntityScenarioDescriptor`, `EntityScenarioArtifacts`, and `run-entity-matrix.ps1`: explain the five stable scenario ids, map each one to an in-game observation, and teach failures as `assertions.json` first, `trace.json` second, with evidence rooted at `target/entity-matrix/<server>/<scenario>/`.

## 2026-04-14T13:20:00Z Task: entity-templates-explained-page
- Ground the page in the API comments: `EntityTemplate` is an "Immutable reusable blueprint for spawned controlled entities," `SpawnControllerFactory` creates the controller used for a single spawn, and `SpawnNetworkControllerFactory` does the same for the network controller.
- Registration guidance comes straight from `VersionedEntityPlatform`: only templates with a non-null `CustomEntityId` can be registered, while direct `platform.spawn(template, ...)` works without prior registration.
- Keep plugin-author wording practical: explain `CustomEntityId` as the stable name of a registered blueprint, `CustomEntityBaseType` as the logical vanilla family, and the Bukkit type as the class plugin code actually touches.
- `CustomEntityDefinition` should appear only as deprecated migration context. Recommend `EntityTemplate` first for all new documentation and examples.

## 2026-04-14T13:55:00Z Task: entity-sharp-edges-and-tips-page
- `EntitySharpEdgesAndTips.md` should stay advisory and compact: code-first opener, then direct caveats tied to source-backed evidence instead of retelling the full entity API.
- Best grounding points are: `CustomEntityDefinition` deprecation, nullable `EntityTemplate.id()` for one-off templates, `VersionedEntityPlatform.register(...)` rejecting id-less templates, `platform.get(entity)` as attach support for existing entities, `metadata-dirty-zombie` for post-spawn sync, and matrix ids/scenarios from `servers.json` and `scenarios.json`.

## 2026-04-14T13:30:00Z Task: spawning-and-controllers-page
- Best grounding split for this page is `SpawnBuilder.initialize(...)` for Bukkit-visible starting state and `SpawnBuilder.controller(...)` for live behavior and lifecycle work after spawn.
- `EntityDemoService` gives the two anchor scenarios the page needs: `orbit` proves controller value through ongoing `onTick` motion, and `deathfx-cow` proves lifecycle value through `onDie` and `onRemove` callbacks.
- Keep network publication explicitly separate from behavior on this page, and link inline to `PacketAndTrackerControlExplained` at the sentence where visibility or packet ownership would otherwise start to distract from controller responsibilities.

## 2026-04-14T14:00:00Z Task: entity-home-page
- `wiki/wiki/Home.md` must replace the generic framework landing page entirely and stay entity-specific from the H1 onward.
- The required shape is exact: one H1, one value paragraph, then one hierarchical bullet tree only. No `##` headings, summary blocks, or trailing navigation sections.
- Match the selling tone from `wiki/wiki/Commands_Home.md`, but normalize every internal wiki link to the prompt-required no-suffix form such as `(EntitiesExplained)`.
- Best page routing for the entity Home TOC is overview first, then templates and spawning, then packet or tracker control, metadata sync, attach flows, version support, verification, and sharp edges.

## 2026-04-14T14:20:00Z Task: entity-wiki-crosslink-normalization
- The 10 target entity wiki pages already had normalized no-suffix links and no heading-anchor links, so the main integration drift was uneven onward linking and repeated terminology, not broken anchor targets.
- Four target content pages needed inline cross-links added to satisfy the two-link rule without adding forbidden navigation sections: `AttachingAndWrappingExplained.md`, `VersionSupportExplained.md`, `EntityTestingAndMatrixVerificationExplained.md`, and `EntitySharpEdgesAndTips.md`.
- The terminology pass was most useful in pages that previously mixed shorter variants like `dirty delta`, `dirty deltas`, `wrapping`, and generic `runtime`; the normalized phrases that fit cleanly in prose were `custom entity runtime`, `network controller`, `viewer lifecycle`, `dirty metadata delta`, `attach`, `wrap`, and `matrix verification`.
- The target-file verifier passes when scoped to the 10 requested files.
- The repo-wide verifier from the prompt cannot pass without touching out-of-scope wiki pages, because raw `.md` wiki links still exist in non-target files including `wiki/wiki/Commands_CooldownsExplained.md` and `wiki/wiki/Commands_Home.md`.
