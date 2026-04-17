# Custom Entity Wiki Documentation

## TL;DR
> **Summary**: Produce exactly ten new GitHub wiki markdown pages in `wiki/wiki/` for the Spigot Boot custom entity runtime, grounded in the real entity API, runtime, demo plugin, and matrix verification sources already present in the repo.
> **Deliverables**:
> - `wiki/wiki/Home.md`
> - `wiki/wiki/EntitiesExplained.md`
> - `wiki/wiki/EntityTemplatesExplained.md`
> - `wiki/wiki/SpawningAndControllersExplained.md`
> - `wiki/wiki/PacketAndTrackerControlExplained.md`
> - `wiki/wiki/MetadataSyncExplained.md`
> - `wiki/wiki/AttachingAndWrappingExplained.md`
> - `wiki/wiki/VersionSupportExplained.md`
> - `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md`
> - `wiki/wiki/EntitySharpEdgesAndTips.md`
> **Effort**: Large
> **Parallel**: YES - 3 waves
> **Critical Path**: Tasks 1-10 → 11 → 12 → 13 → F1-F4

## Context
### Original Request
Create a GitHub wiki for the Spigot Boot custom entity packet and tracker feature using the rules in `entity-wiki-prompt.md`, with complete ready-to-publish markdown pages, Guava-style narrative structure, real repo-grounded examples, and strong emphasis on packet/tracker control beyond plain spawn/despawn.

### Interview Summary
- The user supplied a fully specified wiki prompt, including filenames, style guide, required sections, required examples, and the repository files that must ground the documentation.
- Repository exploration confirmed the target publication surface is `wiki/wiki/`, the entity wiki pages are net-new, and the prompt-listed scenarios plus representative server ids exist exactly in code and matrix config.
- No blocking preference questions remain. This plan freezes the output to the exact ten prompt-listed pages and deliberately excludes `_Sidebar.md` plus any additional wiki navigation changes.

### Metis Review (gaps addressed)
- Exact deliverables are frozen to the ten prompt-listed files, with prompt filenames taking precedence over inferred wiki naming habits.
- Scope is constrained to `wiki/wiki/<10 exact files>` only; `_Sidebar.md`, non-entity wiki pages, source code, and scripts must remain untouched.
- Every page is assigned ownership boundaries to prevent overlap between overview, templates, controllers, packet/tracker control, metadata, attach/wrap, version support, testing, and sharp edges.
- Grounding must be mechanical: each page must mention the exact repo classes, methods, scenarios, and/or server ids it is based on; no unsupported claims or invented APIs are allowed.
- Verification includes diff isolation, structure compliance, forbidden-pattern checks, and exact scenario/server-id checks so no manual judgment is required.

## Work Objectives
### Core Objective
Author a complete entity-runtime wiki section that teaches plugin authors what the feature is for, how to use it, why packet and tracker control matters, how metadata and attach flows work, and how to verify the behavior across supported server versions.

### Deliverables
- Ten standalone GitHub-flavored markdown pages in `wiki/wiki/` with the exact filenames listed in the prompt.
- Each page must be fully user-facing, example-driven, cross-linked inline, and grounded in real repository classes, scripts, scenarios, and matrix ids.
- The wiki content must remain documentation-only: no source code, config, script, or non-target wiki files are changed.

### Deliverable Manifest
| File | Exact H1 | Purpose | Primary Grounding | Must NOT Overlap With |
|---|---|---|---|---|
| `wiki/wiki/Home.md` | `Spigot Boot Custom Entities Wiki` | Curated entity wiki TOC with persuasive one-line descriptions and the prompt-defined progression | `entity-wiki-prompt.md:244-262`, `wiki/wiki/Commands_Home.md:1-40` | Deep API/tutorial content owned by the other nine pages |
| `wiki/wiki/EntitiesExplained.md` | `Entities` | Main conceptual entry point: what the runtime is, why it exists, when to use it, and when it is overkill | `entity-wiki-prompt.md:264-275`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:103-170`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:176-209,324-460` | Detailed builder APIs, deep metadata internals, attach specifics, and matrix procedure details |
| `wiki/wiki/EntityTemplatesExplained.md` | `Entity Templates` | Reusable blueprint model, ids, base types, registered vs one-off templates, and deprecated migration wrapper context | `entity-wiki-prompt.md:276-287`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityTemplate.java:32-44,65-129,240-332`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/CustomEntityDefinition.java:31-47,141-191` | Spawn lifecycle walkthroughs, viewer lifecycle, attach flows |
| `wiki/wiki/SpawningAndControllersExplained.md` | `Spawning and Controllers` | One-off spawn flow, initializer vs controller responsibilities, orbit and death-effect examples | `entity-wiki-prompt.md:288-300`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/SpawnBuilder.java:35-49,80-128,151-168`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:118-163,189-223,499-515,645-769` | Packet/tracker heuristics, metadata contract deep dive, matrix automation details |
| `wiki/wiki/PacketAndTrackerControlExplained.md` | `Packet and Tracker Control` | Why network control is separate, lifecycle hooks, sync heuristics, viewer ownership, and the viewer-cycle scenario | `entity-wiki-prompt.md:301-313`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkController.java:29-97`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/ControlledEntity.java:81-125`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:266-289,387-414,806-830` | General spawn/controller basics already owned by `SpawningAndControllersExplained.md` |
| `wiki/wiki/MetadataSyncExplained.md` | `Metadata Synchronization` | Initial snapshots vs dirty deltas, practical metadata categories, and the metadata-dirty-zombie walkthrough | `entity-wiki-prompt.md:314-325`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/network/metadata/EntityNetworkMetadataContract.java:29-134`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:176-185`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:225-263,348-385,833-907` | General packet/tracker heuristics and attach lifecycle specifics |
| `wiki/wiki/AttachingAndWrappingExplained.md` | `Attaching and Wrapping` | Taking ownership of an existing Bukkit entity, wrap demo, attach-existing scenario, stability/rebound semantics | `entity-wiki-prompt.md:326-337`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:259-307`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/ControlledEntity.java:34-133`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:172-186,417-463`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java:79-148` | Template registration or version selection internals |
| `wiki/wiki/VersionSupportExplained.md` | `Version Support` | Runtime profile selection, adapter discovery, support gates, and what cross-version support does and does not promise | `entity-wiki-prompt.md:338-350`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:46-170,221-230`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:121-127,201-209`, `scripts/entity-matrix/servers.json:1-123` | Step-by-step matrix execution instructions owned by the testing page |
| `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md` | `Entity Testing and Matrix Verification` | Local demo commands, matrix runner usage, scenario interpretation, and artifact reading | `entity-wiki-prompt.md:351-363`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java:53-148`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:71-103`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioArtifacts.java:47-98`, `scripts/run-entity-matrix.ps1:139-271`, `scripts/entity-matrix/scenarios.json:1-58`, `scripts/entity-matrix/servers.json:1-123` | The broader conceptual “why entities” page and the sharp-edges recommendation page |
| `wiki/wiki/EntitySharpEdgesAndTips.md` | `Entity Sharp Edges and Tips` | Practical caveats, migration advice, lifecycle warnings, and strong recommendations | `entity-wiki-prompt.md:364-375`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/CustomEntityDefinition.java:31-47`, `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityTemplate.java:65-117`, `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:206-223,267-307`, `scripts/entity-matrix/servers.json:1-123` | Core explanation/tutorial content already owned by other pages |

### Definition of Done
- All ten exact files exist under `wiki/wiki/` and no additional wiki files were added or modified.
- Every page follows the prompt-defined markdown structure and formatting rules from `entity-wiki-prompt.md:11-60,379-408`.
- Every non-Home page contains an H1, a code-first opening example, `## Why?`, `## How?`, and `## Details`, with no conclusion section.
- `Home.md` contains the opening value proposition paragraph plus a single hierarchical bulleted list grouped into the prompt-defined categories.
- Every page uses only repository-grounded APIs, scenario ids, and server ids, with no invented behavior.
- Internal wiki links use page names without `.md` suffixes.
- The docs explain packet/tracker control as a user-facing capability, not as opaque internals.
- The following verification commands all pass:
  - `pwsh -NoProfile -Command "$files=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($f in $files){ if(-not (Test-Path $f)){ throw \"Missing $f\" } }; 'OK'"`
  - `pwsh -NoProfile -Command "$changed = git diff --name-only -- 'wiki/wiki'; $expected=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); Compare-Object $expected $changed | ForEach-Object { throw ($_ | Out-String) }; 'OK'"`
  - `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern 'TODO|See Also|Next Steps|^## Summary$|^# Summary$|> \[!' -Quiet){ throw 'Forbidden wiki patterns found' }; 'OK'"`
  - `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern '\.md\)' -Quiet){ throw 'Wiki links must omit .md extension' }; 'OK'"`

### Must Have
- Exact filenames from `entity-wiki-prompt.md:244-375`; no renaming, no extra pages, no aliases.
- Home page structure and category ordering exactly as described in `entity-wiki-prompt.md:246-262`.
- Existing wiki narrative style mirrored from `wiki/wiki/Commands_CommandsExplained.md:1-30` and `wiki/wiki/Commands_Home.md:1-40`.
- Inline examples grounded in `EntityTemplate.builder(...)`, `platform().spawn(...)`, `platform().get(zombie)`, `spawnBuilder.controller(...)`, `spawnBuilder.networkController(...)`, demo commands, and the matrix scripts.
- Exact scenario ids: `orbit`, `deathfx-cow`, `metadata-dirty-zombie`, `viewer-cycle-zombie`, `attach-existing-zombie`.
- Exact representative server ids in narrative coverage: `spigot-1.8.8`, `spigot-1.13.2`, `spigot-1.16.5`, `spigot-1.17.1`, `paper-1.19.2`, `paper-1.21.11`.
- Inline contextual cross-links only; no standalone “See Also” sections.
- Strong, user-facing explanation that packet and tracker control matters beyond plain spawn and despawn.

### Must NOT Have
- No edits to `_Sidebar.md`, existing non-target wiki pages, code, scripts, configs, or tests.
- No placeholder text, TODO markers, maintainer notes, or private planning commentary.
- No unsupported compatibility claims such as “all versions” or “every version works the same”.
- No raw API-dump pages that only enumerate methods without explaining why the feature exists.
- No GitHub admonition blocks, no emoji, no `.md` suffix in internal links, and no conclusion/summary/next-step sections.
- No usage of `CustomEntityDefinition` as the recommended primary API.

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: tests-after - no dedicated docs test framework; use agent-executed PowerShell, git diff, and file-content verification after each page and again in the integration wave.
- QA policy: Every task includes a happy-path verification plus a failure/edge-case check against forbidden patterns, unsupported claims, or scope violations.
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: Tasks 1-5 - core explanatory pages (`writing`)

Wave 2: Tasks 6-10 - advanced/support pages plus curated home page (`writing`)

Wave 3: Tasks 11-13 - cross-page normalization, style compliance, and repo diff isolation (`writing` + `unspecified-low`)

### Dependency Matrix (full, all tasks)
| Task | Depends On | Notes |
|---|---|---|
| 1. EntitiesExplained | none | foundational overview page |
| 2. EntityTemplatesExplained | none | builder/template page is independent |
| 3. SpawningAndControllersExplained | none | uses demo service directly |
| 4. PacketAndTrackerControlExplained | none | uses network controller + viewer-cycle sources directly |
| 5. MetadataSyncExplained | none | uses metadata contract + scenario sources directly |
| 6. AttachingAndWrappingExplained | none | uses attach/wrap sources directly |
| 7. VersionSupportExplained | none | uses bootstrap + server matrix sources directly |
| 8. EntityTestingAndMatrixVerificationExplained | none | uses command, scenario, and script sources directly |
| 9. EntitySharpEdgesAndTips | none | advisory page grounded by other source files, but can draft independently |
| 10. Home | none | TOC is fixed by prompt-defined page list and purposes |
| 11. Cross-page link and anchor normalization | 1-10 | normalize inline links, anchor text, and repeated terminology after all pages exist |
| 12. Style-guide and structure compliance pass | 1-11 | enforce code-first, headings, voice, tables, and formatting consistency across all ten pages |
| 13. Diff-isolation and prompt-compliance pass | 1-12 | final pre-review implementation gate; ensure only the ten target files changed |

### Agent Dispatch Summary
| Wave | Task Count | Categories |
|---|---:|---|
| Wave 1 | 5 | `writing` |
| Wave 2 | 5 | `writing` |
| Wave 3 | 3 | `writing`, `unspecified-low` |
| Final Verification | 4 | `oracle`, `unspecified-high`, `unspecified-high`, `deep` |

## TODOs

- [x] 1. Create `EntitiesExplained.md`

  **What to do**: Create `wiki/wiki/EntitiesExplained.md` with the exact H1 `# Entities`. Open with a short `java` snippet that shows `SpigotEntityBootstrap.boot()` and a minimal `platform.spawn(...)` one-off spawn within the first 15 lines. Then write `## Why?`, `## How?`, and `## Details` sections that: explain the runtime as more than spawn/despawn, introduce `versions/api` plus `VersionedEntityPlatform`, distinguish reusable templates from one-off spawns, and state when the feature is overkill versus exactly right. Add inline cross-links to `EntityTemplatesExplained`, `SpawningAndControllersExplained`, `PacketAndTrackerControlExplained`, and `AttachingAndWrappingExplained` at the moment those concepts are introduced.
  **Must NOT do**: Do not deep-dive packet sync thresholds, metadata record shapes, or matrix script mechanics on this page. Do not recommend `CustomEntityDefinition` as a first-choice API.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: user-facing explanation page with strong narrative control and precise source grounding
  - Skills: `[]` - no extra skill required; repo reads plus markdown authoring are sufficient
  - Omitted: [`playwright`] - no browser interaction is needed for markdown authoring

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:264-275` - exact page purpose and required coverage
  - Style: `entity-wiki-prompt.md:13-60,379-408` - naming, structure, tone, cross-linking, formatting, and forbidden patterns
  - Pattern: `wiki/wiki/Commands_CommandsExplained.md:1-30` - example-first narrative shape and tone
  - Pattern: `wiki/wiki/Commands_Home.md:1-8` - concise feature-selling opening paragraph style
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:103-170` - `boot()` and platform resolution framing
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:176-209,324-460` - metadata contract, support gating, registered spawn, explicit template spawn, and one-off spawn APIs
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityTemplate.java:65-129,240-332` - template semantics to mention briefly without deep-diving
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:118-163,172-186,225-289,417-463` - orbit, wrap, metadata, viewer-cycle, and attach scenarios to name as concrete examples

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Entities'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; foreach($pattern in '## Why\?','## How\?','## Details','SpigotEntityBootstrap\.boot\(','VersionedEntityPlatform','packet and tracker','metadata','attach','EntityTemplatesExplained','SpawningAndControllersExplained','PacketAndTrackerControlExplained','AttachingAndWrappingExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; if(Select-String -Path $p -Pattern 'CustomEntityDefinition.*first|^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Forbidden content present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Core overview page is present and code-first
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; Test-Path $p"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; $lines=Get-Content $p; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if($lines[0] -ne '# Entities' -or $firstFence -gt 15){ throw 'Structure mismatch' }; 'OK'"`.
      3. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; foreach($pattern in 'SpigotEntityBootstrap\.boot\(','platform\.spawn\(','EntityTemplatesExplained','PacketAndTrackerControlExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
    Expected: file exists, starts with the exact H1, has a `java` code block near the top, and includes the required conceptual links.
    Evidence: .sisyphus/evidence/task-1-entities-overview.txt

  Scenario: Page does not drift into forbidden structure or legacy-first guidance
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; if(Select-String -Path $p -Pattern 'See Also|Next Steps|^## Summary$|TODO|> \[!|CustomEntityDefinition.*recommended' -Quiet){ throw 'Forbidden pattern present' }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitiesExplained.md'; if(-not (Select-String -Path $p -Pattern 'overkill|right tool' -Quiet)){ throw 'Missing scope guidance' }; 'OK'"`.
    Expected: no conclusion/placeholder/admonition patterns, and the page explicitly states when the runtime is or is not the right tool.
    Evidence: .sisyphus/evidence/task-1-entities-overview-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add entities overview page` | Files: [`wiki/wiki/EntitiesExplained.md`]

- [x] 2. Create `EntityTemplatesExplained.md`

  **What to do**: Create `wiki/wiki/EntityTemplatesExplained.md` with the exact H1 `# Entity Templates`. Open with a short `java` snippet using `EntityTemplate.builder(...)`, `id(...)`, `initialize(...)`, and `controller(...)`. Explain `CustomEntityId`, logical base type, exposed Bukkit type, registered templates versus one-off templates, and why controller/network-controller factories are per-spawn. Mention `CustomEntityDefinition` only as legacy migration context and recommend `EntityTemplate` first. Include practical guidance for startup registration versus inline construction at call sites, and cross-link to `EntitiesExplained` plus `SpawningAndControllersExplained`.
  **Must NOT do**: Do not turn the page into a raw method list. Do not explain attach flows or matrix automation here.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: explanation-heavy API page that must stay narrative rather than reference-dump
  - Skills: `[]` - core repo reading and markdown authoring are sufficient
  - Omitted: [`playwright`] - no UI/browser validation needed

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:276-287`
  - Style: `entity-wiki-prompt.md:13-60,379-408`
  - Pattern: `wiki/wiki/Commands_CommandsExplained.md:1-30`
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityTemplate.java:32-44,65-129,188-197,240-332` - template fields, builder entrypoints, defaults, and per-spawn factory semantics
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/SpawnBuilder.java:35-58,115-168` - one-off spawn builder inherits template semantics
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:212-257,324-460` - registration, lookup, spawn-by-id, and explicit-template spawning
  - Legacy: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/CustomEntityDefinition.java:31-47,141-191` - deprecated wrapper to mention only as migration context

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Entity Templates'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; foreach($pattern in 'EntityTemplate\.builder\(','id\(','initialize\(','controller\(','CustomEntityId','registered','one-off','per-spawn','CustomEntityDefinition','EntitiesExplained','SpawningAndControllersExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; if(Select-String -Path $p -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!|attach-existing-zombie|run-entity-matrix' -Quiet){ throw 'Out-of-scope or forbidden content present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Template page explains reusable blueprints and migration context
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; Test-Path $p"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; foreach($pattern in 'EntityTemplate\.builder\(','CustomEntityId','registered','one-off','per-spawn','CustomEntityDefinition'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      3. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; foreach($pattern in 'EntitiesExplained','SpawningAndControllersExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing cross-link $pattern\" } }; 'OK'"`.
    Expected: file exists, template semantics are explained, and migration context plus cross-links are present.
    Evidence: .sisyphus/evidence/task-2-entity-templates.txt

  Scenario: Page does not drift into attach/matrix detail or forbidden wiki structure
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTemplatesExplained.md'; if(Select-String -Path $p -Pattern 'attach-existing-zombie|run-entity-matrix|See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Forbidden drift detected' }; 'OK'"`.
    Expected: no attach/matrix tutorial content and no forbidden summary/admonition placeholders.
    Evidence: .sisyphus/evidence/task-2-entity-templates-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add entity templates page` | Files: [`wiki/wiki/EntityTemplatesExplained.md`]

- [x] 3. Create `SpawningAndControllersExplained.md`

  **What to do**: Create `wiki/wiki/SpawningAndControllersExplained.md` with the exact H1 `# Spawning and Controllers`. Open with a short `java` snippet showing `platform.spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, location, spawnBuilder -> { ... })`, `spawnBuilder.initialize(...)`, and `spawnBuilder.controller(...)`. In `## Why?`, explain why controlled behavior is better than a static spawn. In `## How?`, walk through initializer responsibilities versus controller responsibilities using the orbit and death-effect scenarios from `EntityDemoService`. In `## Details`, recommend small focused controllers, explicitly state that network publication is a separate concern, and cross-link to `PacketAndTrackerControlExplained` for network behavior plus `EntityTemplatesExplained` for reusable blueprints.
  **Must NOT do**: Do not explain sync thresholds, viewer add/remove hooks, or dirty metadata internals on this page.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: narrative walkthrough with example selection and strong conceptual boundaries
  - Skills: `[]` - no additional skill injection required
  - Omitted: [`playwright`] - no browser work is required

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:288-300`
  - Style: `entity-wiki-prompt.md:16-40,50-60,379-408`
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/SpawnBuilder.java:35-58,79-128,151-168` - builder semantics for initialize/controller/networkController
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:397-460` - one-off typed spawn entrypoint
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:118-163` - orbit scenario setup
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:189-223,499-515,731-769` - death effect scenario and controller lifecycle callbacks
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:645-729` - orbit controller behavior proving non-static movement

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Spawning and Controllers'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; foreach($pattern in 'platform\.spawn\(CustomEntityBaseType\.ZOMBIE, Zombie\.class','spawnBuilder\.initialize\(','spawnBuilder\.controller\(','orbit','deathfx-cow','PacketAndTrackerControlExplained','EntityTemplatesExplained','network publication'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; if(Select-String -Path $p -Pattern 'viewerAddCount|viewerRemoveCount|RELATIVE_POSITION_THRESHOLD|dirtyDelta|run-entity-matrix|TODO|See Also|Next Steps|> \[!' -Quiet){ throw 'Out-of-scope details present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Spawn/controller page teaches one-off setup and behavior lifecycle
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; foreach($pattern in '# Spawning and Controllers','platform\.spawn\(','spawnBuilder\.initialize\(','spawnBuilder\.controller\(','orbit','deathfx-cow'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; foreach($pattern in 'PacketAndTrackerControlExplained','EntityTemplatesExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing cross-link $pattern\" } }; 'OK'"`.
    Expected: page exists, demonstrates initialization plus control, references orbit and death-effect examples, and links onward correctly.
    Evidence: .sisyphus/evidence/task-3-spawning-controllers.txt

  Scenario: Page keeps network and metadata internals out of scope
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/SpawningAndControllersExplained.md'; if(Select-String -Path $p -Pattern 'RELATIVE_POSITION_THRESHOLD|viewerAddCount|dirtyDelta|run-entity-matrix|^## Summary$|See Also|TODO|> \[!' -Quiet){ throw 'Scope leak detected' }; 'OK'"`.
    Expected: the page stays on spawn/controller concerns and avoids tracker/metadata/matrix deep dives.
    Evidence: .sisyphus/evidence/task-3-spawning-controllers-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add spawning and controllers page` | Files: [`wiki/wiki/SpawningAndControllersExplained.md`]

- [x] 4. Create `PacketAndTrackerControlExplained.md`

  **What to do**: Create `wiki/wiki/PacketAndTrackerControlExplained.md` with the exact H1 `# Packet and Tracker Control`. Open with a short `java` snippet showing `spawnBuilder.networkController(...)`. In `## Why?`, make the core argument that this feature is about viewer lifecycle, packet publication, and coherence for observers, not just spawn/despawn. In `## How?`, explain `EntityNetworkController` lifecycle hooks (`onBind`, `onUnbind`, `onTick`, `onViewerAdded`, `onViewerRemoved`) and use `viewer-cycle-zombie` as the anchor scenario. In `## Details`, explain absolute sync, relative sync, rotation sync, velocity sync, and why thresholds exist, while keeping the language user-facing. Explicitly explain that this split keeps network publication logic out of the main entity controller.
  **Must NOT do**: Do not bury the value proposition under internal runtime class names. Do not omit the viewer-lifecycle explanation.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: conceptually dense explanation page that must translate runtime mechanics into user language
  - Skills: `[]` - repo reading and writing are sufficient
  - Omitted: [`playwright`] - browser automation is irrelevant here

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:301-313`
  - Style: `entity-wiki-prompt.md:16-24,27-55,401-404`
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkController.java:29-97` - hooks and sync heuristics to explain
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/ControlledEntity.java:81-125` - live network controller swapping and state surface
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:266-289,387-414,806-830` - viewer-cycle scenario plus network-controller implementation
  - Scenario Contract: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:85-102` - `viewer-cycle-zombie` assertion keys

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Packet and Tracker Control'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; foreach($pattern in 'spawnBuilder\.networkController\(','EntityNetworkController','onBind','onUnbind','onTick','onViewerAdded','onViewerRemoved','viewer-cycle-zombie','absolute sync','relative sync','rotation sync','velocity sync','viewer lifecycle'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; if(Select-String -Path $p -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Forbidden structure present' }; if(-not (Select-String -Path $p -Pattern 'beyond plain spawn and despawn|more than spawn and despawn' -Quiet)){ throw 'Missing core value proposition' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Packet/tracker page explains viewer ownership and sync heuristics
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; foreach($pattern in '# Packet and Tracker Control','spawnBuilder\.networkController\(','viewer-cycle-zombie','onViewerAdded','onViewerRemoved','absolute sync','relative sync','rotation sync','velocity sync'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; if(-not (Select-String -Path $p -Pattern 'beyond plain spawn and despawn|more than spawn and despawn' -Quiet)){ throw 'Missing value proposition' }; 'OK'"`.
    Expected: the page clearly teaches the network-controller split, viewer lifecycle, and sync modes in user-facing language.
    Evidence: .sisyphus/evidence/task-4-packet-tracker-control.txt

  Scenario: Page does not collapse into internal-only jargon or forbidden formatting
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; if(Select-String -Path $p -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Forbidden structure present' }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/PacketAndTrackerControlExplained.md'; if(-not (Select-String -Path $p -Pattern 'viewer lifecycle' -Quiet)){ throw 'Missing user-facing framing' }; 'OK'"`.
    Expected: no forbidden formatting and the explanation remains centered on what viewers see and when.
    Evidence: .sisyphus/evidence/task-4-packet-tracker-control-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add packet and tracker control page` | Files: [`wiki/wiki/PacketAndTrackerControlExplained.md`]

- [x] 5. Create `MetadataSyncExplained.md`

  **What to do**: Create `wiki/wiki/MetadataSyncExplained.md` with the exact H1 `# Metadata Synchronization`. Open with a short `java` snippet based on `platform().networkMetadataContract().initialSnapshot(...)` and `dirtyDelta(...)`. In `## Why?`, explain why viewers need a full initial state and later deltas. In `## How?`, walk through the `metadata-dirty-zombie` scenario using practical categories: watcher items, living attributes, equipment, active effects, and head rotation. In `## Details`, explain initial full state versus later deltas, why replacement/attach flows care, and why this matters for cross-version consistency. Cross-link inline to `PacketAndTrackerControlExplained`, `AttachingAndWrappingExplained`, and `EntityTestingAndMatrixVerificationExplained`.
  **Must NOT do**: Do not dump raw internal record types or turn the page into a field-by-field JavaDoc mirror.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: explanatory runtime-behavior page that must stay concrete without becoming internal API sludge
  - Skills: `[]` - standard repo reading and markdown authoring are enough
  - Omitted: [`playwright`] - no browser work is needed

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:314-325`
  - Style: `entity-wiki-prompt.md:16-24,34-55,379-408`
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/network/metadata/EntityNetworkMetadataContract.java:29-134` - `initialSnapshot(...)` and `dirtyDelta(...)`
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:176-185` - `networkMetadataContract()` exposure
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:225-263,348-385` - initial snapshot and later dirty delta flow
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:833-907` - practical watcher items, attributes, equipment, active effects, and head rotation source material
  - Scenario Contract: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:75-84` - `metadata-dirty-zombie` assertion keys

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Metadata Synchronization'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; foreach($pattern in 'networkMetadataContract\(\)\.initialSnapshot','dirtyDelta\(','metadata-dirty-zombie','watcher','attributes','equipment','active effects','head rotation','PacketAndTrackerControlExplained','AttachingAndWrappingExplained','EntityTestingAndMatrixVerificationExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; if(Select-String -Path $p -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!|record class|sealed interface' -Quiet){ throw 'Forbidden structure or internal dump present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Metadata page explains snapshots, deltas, and practical metadata categories
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; foreach($pattern in '# Metadata Synchronization','initialSnapshot','dirtyDelta','metadata-dirty-zombie','watcher','equipment','active effects','head rotation'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; foreach($pattern in 'PacketAndTrackerControlExplained','AttachingAndWrappingExplained','EntityTestingAndMatrixVerificationExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing cross-link $pattern\" } }; 'OK'"`.
    Expected: the page exists, teaches initial-vs-delta behavior, names practical metadata categories, and routes readers to the right adjacent pages.
    Evidence: .sisyphus/evidence/task-5-metadata-sync.txt

  Scenario: Page stays explanatory instead of devolving into internal type dumps
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/MetadataSyncExplained.md'; if(Select-String -Path $p -Pattern 'See Also|Next Steps|^## Summary$|TODO|> \[!|record class|sealed interface' -Quiet){ throw 'Forbidden/internal dump pattern present' }; 'OK'"`.
    Expected: no summary/admonition placeholders and no raw-internals-only presentation.
    Evidence: .sisyphus/evidence/task-5-metadata-sync-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add metadata sync page` | Files: [`wiki/wiki/MetadataSyncExplained.md`]

- [x] 6. Create `AttachingAndWrappingExplained.md`

  **What to do**: Create `wiki/wiki/AttachingAndWrappingExplained.md` with the exact H1 `# Attaching and Wrapping`. Open with a short `java` snippet showing `ControlledEntity<Zombie> controlledZombie = platform().get(zombie);`. In `## Why?`, explain why attach support matters when you do not control the original spawn moment. In `## How?`, cover the `wrap` command plus `wrapBukkitZombie(...)`, then the more advanced `attach-existing-zombie` scenario. In `## Details`, explain `entityIdStable`, `duplicateSpawnCount`, and `trackerRebound` as the user-facing proof signals of correct behavior, and clearly state that attach support differentiates this runtime from APIs that only handle fresh spawns. Cross-link to `PacketAndTrackerControlExplained` when tracker rebound comes up.
  **Must NOT do**: Do not blur attach flows with template registration or matrix provisioning details.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: lifecycle explanation page grounded in attach/wrap demo flows and runtime semantics
  - Skills: `[]` - no additional skills required
  - Omitted: [`playwright`] - browser validation is unnecessary

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:326-337`
  - Style: `entity-wiki-prompt.md:16-24,34-55,379-408`
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:259-307` - attach flow through `get(entity)`
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/ControlledEntity.java:34-133` - controlled entity surface, controller swapping, network controller swapping, hook state
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java:79-148` - `wrap` and `attach-existing-zombie` command entry points
  - Demo: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:172-186,417-463` - wrap flow and attach/rebound proof signals
  - Scenario Contract: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:94-102` - attach assertion keys

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Attaching and Wrapping'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; foreach($pattern in 'platform\(\)\.get\(zombie\)|platform\.get\(zombie\)','wrap','wrapBukkitZombie','attach-existing-zombie','entityIdStable','duplicateSpawnCount','trackerRebound','PacketAndTrackerControlExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; if(Select-String -Path $p -Pattern 'register\(|run-entity-matrix|^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Out-of-scope or forbidden content present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Attach/wrap page demonstrates existing-entity ownership and proof signals
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; foreach($pattern in '# Attaching and Wrapping','platform\.get\(zombie\)','wrap','attach-existing-zombie','entityIdStable','duplicateSpawnCount','trackerRebound'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; if(-not (Select-String -Path $p -Pattern 'PacketAndTrackerControlExplained' -Quiet)){ throw 'Missing tracker cross-link' }; 'OK'"`.
    Expected: the page explains both wrap and attach flows, and names the exact correctness signals from the demo scenario.
    Evidence: .sisyphus/evidence/task-6-attaching-wrapping.txt

  Scenario: Page does not drift into template registration or matrix tutorial content
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/AttachingAndWrappingExplained.md'; if(Select-String -Path $p -Pattern 'register\(|run-entity-matrix|See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Scope drift detected' }; 'OK'"`.
    Expected: attach/wrap content stays lifecycle-focused and avoids unrelated registration/provisioning instructions.
    Evidence: .sisyphus/evidence/task-6-attaching-wrapping-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add attaching and wrapping page` | Files: [`wiki/wiki/AttachingAndWrappingExplained.md`]

- [x] 7. Create `VersionSupportExplained.md`

  **What to do**: Create `wiki/wiki/VersionSupportExplained.md` with the exact H1 `# Version Support`. Open with a short `java` snippet showing `VersionedEntityPlatform platform = SpigotEntityBootstrap.boot();`. In `## Why?`, explain that plugin authors need a runtime selector instead of hard-coding one server family. In `## How?`, explain runtime profile resolution, adapter discovery, and `platform.supports(baseType)` in plain language. In `## Details`, include a six-row representative support table using exactly these ids: `spigot-1.8.8`, `spigot-1.13.2`, `spigot-1.16.5`, `spigot-1.17.1`, `paper-1.19.2`, `paper-1.21.11`, and explain what cross-version support means and does not promise. If you mention that `servers.json` currently has additional entries, do it in one sentence outside the main evidence table.
  **Must NOT do**: Do not turn this page into matrix-runner instructions; that belongs to `EntityTestingAndMatrixVerificationExplained.md`. Do not claim universal or identical behavior across all versions.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: translation of version-selection internals into user-facing operational guidance
  - Skills: `[]` - no additional skill load required
  - Omitted: [`playwright`] - no browser-based verification needed

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:338-350`
  - Style: `entity-wiki-prompt.md:16-24,42-48,379-408`
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:46-170,221-230` - boot flow, runtime profile resolution, adapter discovery, and selection framing
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:121-127,201-209` - capability bundle resolution and `supports(baseType)`
  - Matrix Config: `scripts/entity-matrix/servers.json:1-123` - exact representative server ids and their current presence in config

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/VersionSupportExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Version Support'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/VersionSupportExplained.md'; foreach($pattern in 'SpigotEntityBootstrap\.boot\(','runtime profile','adapter','supports\(baseType\)','spigot-1\.8\.8','spigot-1\.13\.2','spigot-1\.16\.5','spigot-1\.17\.1','paper-1\.19\.2','paper-1\.21\.11'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/VersionSupportExplained.md'; if(Select-String -Path $p -Pattern 'all versions|every version works the same|run-entity-matrix|^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Unsupported claim or out-of-scope content present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Version support page explains runtime selection and six representative ids
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/VersionSupportExplained.md'; foreach($pattern in '# Version Support','SpigotEntityBootstrap\.boot\(','runtime profile','adapter','supports\(baseType\)','spigot-1\.8\.8','spigot-1\.13\.2','spigot-1\.16\.5','spigot-1\.17\.1','paper-1\.19\.2','paper-1\.21\.11'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
    Expected: page exists, explains selection in plain language, and includes the exact six representative ids.
    Evidence: .sisyphus/evidence/task-7-version-support.txt

  Scenario: Page avoids over-promising or duplicating testing instructions
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/VersionSupportExplained.md'; if(Select-String -Path $p -Pattern 'all versions|every version works the same|run-entity-matrix|See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Unsupported or out-of-scope content present' }; 'OK'"`.
    Expected: no universal compatibility claims and no matrix procedure tutorial content.
    Evidence: .sisyphus/evidence/task-7-version-support-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add version support page` | Files: [`wiki/wiki/VersionSupportExplained.md`]

- [x] 8. Create `EntityTestingAndMatrixVerificationExplained.md`

  **What to do**: Create `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md` with the exact H1 `# Entity Testing and Matrix Verification`. Open with a short `powershell` example invoking `scripts/run-entity-matrix.ps1` and include at least one `/entitydemo ...` command snippet in the opening flow. In `## Why?`, explain why the sample plugin and matrix are the fastest proof surface. In `## How?`, cover the local command playground and the five stable scenarios `orbit`, `deathfx-cow`, `metadata-dirty-zombie`, `viewer-cycle-zombie`, and `attach-existing-zombie`, including what a reader should observe in game for each. In `## Details`, explain `trace.json` and `assertions.json`, the `target/entity-matrix/<server>/<scenario>/` output path, failure reading, and why validating multiple server ids matters.
  **Must NOT do**: Do not imply that committed `target/entity-matrix` artifacts already exist in the repo. Do not rename scenarios or add unsupported assertion keys.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: operational/testing guide that must stay precise about commands, files, and scenario semantics
  - Skills: `[]` - normal repo reading is sufficient
  - Omitted: [`playwright`] - no browser or UI automation required for the doc-writing step

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:351-363`
  - Style: `entity-wiki-prompt.md:34-48,57-60,379-408`
  - Commands: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java:53-148` - exact command surface and user entry points
  - Demo Service: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:86-109,138-163,189-289,417-463` - scenario behaviors and matrix autorun entry
  - Scenario Contract: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:71-103` - canonical scenario ids and assertion keys
  - Artifact Writer: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioArtifacts.java:47-98` - output path and trace/assertions writing
  - Matrix Script: `scripts/run-entity-matrix.ps1:139-271` - provisioning/reuse, startup, outputs, validation, and retained server home
  - Matrix Scenario Config: `scripts/entity-matrix/scenarios.json:1-58` - exact stable scenario ids and expected assertion keys
  - Matrix Server Config: `scripts/entity-matrix/servers.json:1-123` - server ids readers can try

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; $lines=Get-Content $p; if($lines[0] -ne '# Entity Testing and Matrix Verification'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```powershell$|^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing opening example block' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; foreach($pattern in 'run-entity-matrix\.ps1','/entitydemo orbit','deathfx-cow','metadata-dirty-zombie','viewer-cycle-zombie','attach-existing-zombie','trace\.json','assertions\.json','target/entity-matrix/<server>/<scenario>/'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; if(Select-String -Path $p -Pattern 'target/entity-matrix/.*already committed|^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Unsupported or forbidden content present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Testing page shows exact commands, scenarios, and artifact paths
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; foreach($pattern in 'run-entity-matrix\.ps1','/entitydemo orbit','deathfx-cow','metadata-dirty-zombie','viewer-cycle-zombie','attach-existing-zombie','trace\.json','assertions\.json','target/entity-matrix/<server>/<scenario>/'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; foreach($pattern in 'spigot-1\.8\.8','paper-1\.19\.2','paper-1\.21\.11'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing representative server $pattern\" } }; 'OK'"`.
    Expected: page teaches how to run and interpret both local demo commands and matrix verification.
    Evidence: .sisyphus/evidence/task-8-testing-matrix.txt

  Scenario: Page does not claim pre-generated runtime artifacts are committed
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; if(Select-String -Path $p -Pattern 'already committed|checked in artifacts|bundled trace\.json|See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Unsupported or forbidden wording present' }; 'OK'"`.
    Expected: the page tells readers how to generate evidence without pretending the repo ships live matrix outputs.
    Evidence: .sisyphus/evidence/task-8-testing-matrix-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add testing and matrix verification page` | Files: [`wiki/wiki/EntityTestingAndMatrixVerificationExplained.md`]

- [x] 9. Create `EntitySharpEdgesAndTips.md`

  **What to do**: Create `wiki/wiki/EntitySharpEdgesAndTips.md` with the exact H1 `# Entity Sharp Edges and Tips`. Open with a short `java` snippet that uses `platform.supports(...)` and `platform.get(entity)` to frame safe usage. Then write `## Why?`, `## How?`, and `## Details` sections that cover: `CustomEntityDefinition` is legacy and `EntityTemplate` is preferred; registered templates need an id while one-off templates do not; `platform.get(entity)` is for supported existing entities and does not eliminate lifecycle thinking; metadata sync matters after spawn; matrix verification matters more than one local server; and readers should start with the test-plugin scenarios before building a heavier abstraction layer. Link inline to `EntityTemplatesExplained`, `AttachingAndWrappingExplained`, `VersionSupportExplained`, and `EntityTestingAndMatrixVerificationExplained`.
  **Must NOT do**: Do not re-teach the full API. Do not introduce new caveats not grounded in the prompt or source files.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: advisory page requiring strong judgment about emphasis while remaining evidence-based
  - Skills: `[]` - normal repo reading and markdown writing are enough
  - Omitted: [`playwright`] - no browser or UI activity needed

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:364-375`
  - Style: `entity-wiki-prompt.md:16-24,26-55,379-408`
  - Legacy API: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/CustomEntityDefinition.java:31-47,141-191` - deprecation and migration context
  - Template API: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityTemplate.java:65-117,126-197,240-332` - id semantics and template defaults
  - Attach API: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:206-223,259-307` - support gate and attach semantics
  - Testing Evidence: `scripts/entity-matrix/servers.json:1-123`, `scripts/entity-matrix/scenarios.json:1-58` - cross-version evidence source
  - Demo Entry: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java:53-148` - start-with-the-demo recommendation grounding

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; $lines=Get-Content $p; if($lines[0] -ne '# Entity Sharp Edges and Tips'){ throw 'Bad H1' }; $firstFence=($lines|Select-String '^```java$'|Select-Object -First 1).LineNumber; if(-not $firstFence -or $firstFence -gt 15){ throw 'Missing code-first example' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; foreach($pattern in 'CustomEntityDefinition','EntityTemplate','registered templates need an id|registered template needs an id','one-off','platform\.get\(entity\)|platform\.get\(','metadata','matrix','test-plugin','EntityTemplatesExplained','AttachingAndWrappingExplained','VersionSupportExplained','EntityTestingAndMatrixVerificationExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; if(Select-String -Path $p -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!|brand new caveat' -Quiet){ throw 'Forbidden structure or unsupported caveat text present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Sharp-edges page contains all required practical warnings and links
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; foreach($pattern in 'CustomEntityDefinition','EntityTemplate','platform\.get\(','metadata','matrix','test-plugin'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; foreach($pattern in 'EntityTemplatesExplained','AttachingAndWrappingExplained','VersionSupportExplained','EntityTestingAndMatrixVerificationExplained'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing cross-link $pattern\" } }; 'OK'"`.
    Expected: the page gives grounded caveats and routes readers toward the supporting deep-dive pages.
    Evidence: .sisyphus/evidence/task-9-sharp-edges.txt

  Scenario: Page does not become a generic opinion list or forbidden wiki structure
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/EntitySharpEdgesAndTips.md'; if(Select-String -Path $p -Pattern 'See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Forbidden structure present' }; 'OK'"`.
    Expected: the page remains grounded and stays within the prompt-defined format.
    Evidence: .sisyphus/evidence/task-9-sharp-edges-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add entity sharp edges page` | Files: [`wiki/wiki/EntitySharpEdgesAndTips.md`]

- [x] 10. Create `Home.md`

  **What to do**: Create `wiki/wiki/Home.md` with the exact H1 `# Spigot Boot Custom Entities Wiki`. Write an opening paragraph that sells the wiki as the fastest way to understand why the custom entity runtime exists, what control it gives over spawning/tracking, and how to prove behavior on real server versions. Then add one hierarchical bulleted list only, using these exact category labels in this exact order as top-level bullets: `**Getting Started**`, `**Core Runtime**`, `**Behavior Control**`, `**Synchronization**`, `**Lifecycle Variants**`, `**Version Support**`, `**Verification**`, `**Advanced Topics**`. Under those top-level bullets, add nested bullets that link to the nine content pages with one-line descriptions that sell the page rather than merely naming it. Use internal wiki links without `.md` suffixes.
  **Must NOT do**: Do not reuse the repo’s current generic getting-started Home content. Do not add narrative sections after the bulleted TOC.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: concise navigation page requiring clear page positioning and cross-link precision
  - Skills: `[]` - no special skill required
  - Omitted: [`playwright`] - no browser automation needed

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [11, 12, 13] | Blocked By: [none]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:57-60,244-262`
  - Style Pattern: `wiki/wiki/Commands_Home.md:1-40` - section-home tone and one-line sales descriptions
  - Contrast Only: `wiki/wiki/Home.md:1-157` - existing generic home page to replace for this entity wiki scope; do not mimic its broad getting-started content

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; if((Get-Content $p -First 1) -ne '# Spigot Boot Custom Entities Wiki'){ throw 'Bad H1' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; foreach($pattern in '^- \*\*Getting Started\*\*','^- \*\*Core Runtime\*\*','^- \*\*Behavior Control\*\*','^- \*\*Synchronization\*\*','^- \*\*Lifecycle Variants\*\*','^- \*\*Version Support\*\*','^- \*\*Verification\*\*','^- \*\*Advanced Topics\*\*','\(EntitiesExplained\)','\(EntityTemplatesExplained\)','\(SpawningAndControllersExplained\)','\(PacketAndTrackerControlExplained\)','\(MetadataSyncExplained\)','\(AttachingAndWrappingExplained\)','\(VersionSupportExplained\)','\(EntityTestingAndMatrixVerificationExplained\)','\(EntitySharpEdgesAndTips\)'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing pattern: $pattern\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; if(Select-String -Path $p -Pattern '^## |^## Why\?$|^## How\?$|^## Details$|^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Home contains forbidden structure' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Home page is a curated TOC with exact category bullets and links
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; foreach($pattern in '# Spigot Boot Custom Entities Wiki','^- \*\*Getting Started\*\*','^- \*\*Core Runtime\*\*','^- \*\*Behavior Control\*\*','^- \*\*Synchronization\*\*','^- \*\*Lifecycle Variants\*\*','^- \*\*Version Support\*\*','^- \*\*Verification\*\*','^- \*\*Advanced Topics\*\*'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing $pattern\" } }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; foreach($pattern in '\(EntitiesExplained\)','\(EntityTemplatesExplained\)','\(SpawningAndControllersExplained\)','\(PacketAndTrackerControlExplained\)','\(MetadataSyncExplained\)','\(AttachingAndWrappingExplained\)','\(VersionSupportExplained\)','\(EntityTestingAndMatrixVerificationExplained\)','\(EntitySharpEdgesAndTips\)'){ if(-not (Select-String -Path $p -Pattern $pattern -Quiet)){ throw \"Missing link $pattern\" } }; 'OK'"`.
    Expected: Home functions as the curated entity TOC and links to every entity wiki page.
    Evidence: .sisyphus/evidence/task-10-home.txt

  Scenario: Home page does not drift back into generic getting-started tutorial content
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p='wiki/wiki/Home.md'; if(Select-String -Path $p -Pattern 'SpigotBoot\.initialize|BootPlugin|SpigotBootBuilder|^## |See Also|Next Steps|^## Summary$|TODO|> \[!' -Quiet){ throw 'Old-home drift or forbidden structure present' }; 'OK'"`.
    Expected: Home stays entity-wiki-specific and remains a TOC rather than a framework bootstrap tutorial.
    Evidence: .sisyphus/evidence/task-10-home-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): add entity wiki home page` | Files: [`wiki/wiki/Home.md`]

- [x] 11. Cross-page link and anchor normalization

  **What to do**: After Tasks 1-10 are complete, normalize cross-links and anchor text across all ten pages. Replace any raw `.md` wiki links with page-name links, ensure each non-Home page links to at least two adjacent entity pages, and ensure section anchors use GitHub-style anchor fragments only where they point to headings that actually exist. Where repeated terminology drifts, standardize on these phrases: `custom entity runtime`, `network controller`, `viewer lifecycle`, `dirty metadata delta`, `attach`, `wrap`, `matrix verification`.
  **Must NOT do**: Do not add a `See Also` section, appendix, or navigation page outside the ten target files.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: integration pass across multiple markdown files requiring consistency and safe link edits
  - Skills: `[]` - no extra skill load needed
  - Omitted: [`playwright`] - browser validation is not required for link normalization

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [12, 13] | Blocked By: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:45-49,386-387,404`
  - Style Pattern: `wiki/wiki/Commands_Home.md:1-40` - clean wiki links without `.md`
  - Deliverable List: `entity-wiki-prompt.md:244-375` - exact page names available for cross-link targets

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern '\.md\)' -Quiet){ throw 'Found .md wiki link suffix' }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$files=@('wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($f in $files){ $count=(Select-String -Path $f -Pattern '\]\(' | Measure-Object).Count; if($count -lt 2){ throw \"Not enough wiki links in $f\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern 'See Also|Appendix|Navigation' -Quiet){ throw 'Forbidden navigation pattern present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: All entity pages use correct wiki links and enough inline cross-links
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern '\.md\)' -Quiet){ throw 'Found .md wiki link suffix' }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$files=@('wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($f in $files){ $count=(Select-String -Path $f -Pattern '\]\(' | Measure-Object).Count; if($count -lt 2){ throw \"Not enough inline links in $f\" } }; 'OK'"`.
    Expected: links use wiki format without `.md` suffixes and every content page links onward in-context.
    Evidence: .sisyphus/evidence/task-11-link-normalization.txt

  Scenario: Integration pass does not introduce navigation anti-patterns
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern 'See Also|Appendix|Navigation' -Quiet){ throw 'Forbidden navigation pattern present' }; 'OK'"`.
    Expected: no standalone navigation/appendix sections were added.
    Evidence: .sisyphus/evidence/task-11-link-normalization-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): normalize entity wiki cross-links` | Files: [`wiki/wiki/Home.md`, `wiki/wiki/EntitiesExplained.md`, `wiki/wiki/EntityTemplatesExplained.md`, `wiki/wiki/SpawningAndControllersExplained.md`, `wiki/wiki/PacketAndTrackerControlExplained.md`, `wiki/wiki/MetadataSyncExplained.md`, `wiki/wiki/AttachingAndWrappingExplained.md`, `wiki/wiki/VersionSupportExplained.md`, `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md`, `wiki/wiki/EntitySharpEdgesAndTips.md`]

- [x] 12. Style-guide and structure compliance pass

  **What to do**: Perform a whole-set polish pass across the ten target pages. For every non-Home page, enforce the exact H1, opening example block near the top, `## Why?`, `## How?`, and `## Details` headings, user-facing voice, and absence of conclusion sections. For `Home.md`, enforce the single opening paragraph plus category TOC structure only. Across all pages, remove trailing whitespace, keep ATX headings, ensure code fences are correctly typed (`java`, `xml`, `powershell`), preserve blank lines around fences, and ensure no admonition blocks, placeholders, or raw API-dump tone survive.
  **Must NOT do**: Do not add new files or broaden scope beyond the ten target pages during the polish pass.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: whole-set editorial consistency and structure normalization across multiple markdown files
  - Skills: `[]` - no extra skill load needed
  - Omitted: [`playwright`] - no browser validation required

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [13] | Blocked By: [11]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:11-60,379-408` - source of all structure, tone, link, and formatting rules
  - Deliverable List: `entity-wiki-prompt.md:244-375` - page-by-page must-cover requirements
  - Style Pattern: `wiki/wiki/Commands_CommandsExplained.md:1-30`, `wiki/wiki/Commands_Home.md:1-40` - local wiki tone and structure reference points

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$contentPages=@('wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($p in $contentPages){ $lines=Get-Content $p; $firstFence=($lines|Select-String '^```(java|powershell)$'|Select-Object -First 1).LineNumber; foreach($heading in '## Why?','## How?','## Details'){ if(-not ($lines -contains $heading)){ throw \"Missing $heading in $p\" } }; if(-not $firstFence -or $firstFence -gt 15){ throw \"Opening example too late in $p\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$all=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($p in $all){ if(Select-String -Path $p -Pattern '\s+$' -Quiet){ throw \"Trailing whitespace in $p\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Forbidden style pattern present' }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Every content page follows the required code-first and Why/How/Details structure
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$contentPages=@('wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($p in $contentPages){ $lines=Get-Content $p; $firstFence=($lines|Select-String '^```(java|powershell)$'|Select-Object -First 1).LineNumber; foreach($heading in '## Why?','## How?','## Details'){ if(-not ($lines -contains $heading)){ throw \"Missing $heading in $p\" } }; if(-not $firstFence -or $firstFence -gt 15){ throw \"Opening example too late in $p\" } }; 'OK'"`.
    Expected: every non-Home page is code-first and structurally aligned with the prompt.
    Evidence: .sisyphus/evidence/task-12-style-compliance.txt

  Scenario: No forbidden wiki formatting or trailing whitespace remains
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "if(Select-String -Path 'wiki/wiki/*.md' -Pattern '^## Summary$|See Also|Next Steps|TODO|> \[!' -Quiet){ throw 'Forbidden style pattern present' }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$all=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); foreach($p in $all){ if(Select-String -Path $p -Pattern '\s+$' -Quiet){ throw \"Trailing whitespace in $p\" } }; 'OK'"`.
    Expected: forbidden style artifacts are removed and the files are cleanly formatted.
    Evidence: .sisyphus/evidence/task-12-style-compliance-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): enforce entity wiki style compliance` | Files: [`wiki/wiki/Home.md`, `wiki/wiki/EntitiesExplained.md`, `wiki/wiki/EntityTemplatesExplained.md`, `wiki/wiki/SpawningAndControllersExplained.md`, `wiki/wiki/PacketAndTrackerControlExplained.md`, `wiki/wiki/MetadataSyncExplained.md`, `wiki/wiki/AttachingAndWrappingExplained.md`, `wiki/wiki/VersionSupportExplained.md`, `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md`, `wiki/wiki/EntitySharpEdgesAndTips.md`]

- [x] 13. Diff-isolation and prompt-compliance pass

  **What to do**: Run the final pre-review implementation gate. Confirm that only the ten target wiki files changed, `_Sidebar.md` and every other repo file remained untouched, all exact prompt-listed scenario ids and representative server ids appear where required, and no page introduces extra output files, renamed pages, or unsupported claims. This is the last implementation task before the formal review wave.
  **Must NOT do**: Do not use this pass as an excuse to add new content areas or side quests.

  **Recommended Agent Profile**:
  - Category: `unspecified-low` - Reason: mechanical repo-state validation and prompt-compliance auditing
  - Skills: `[]` - no special skills needed
  - Omitted: [`playwright`] - repo validation only

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [F1, F2, F3, F4] | Blocked By: [12]

  **References** (executor has NO interview context - be exhaustive):
  - Prompt: `entity-wiki-prompt.md:244-375,379-408` - exact file list and final output constraints
  - Matrix Scenario Contract: `scripts/entity-matrix/scenarios.json:1-58`, `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java:71-103` - exact scenario ids and assertion-key grounding
  - Matrix Server Contract: `scripts/entity-matrix/servers.json:1-123` - exact representative server ids plus the note that current config contains an extra spigot entry outside the canonical six-row table

  **Acceptance Criteria** (agent-executable only):
  - [ ] `pwsh -NoProfile -Command "$expected=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); $changed=(git diff --name-only -- 'wiki/wiki') | Where-Object { $_ }; $cmp=Compare-Object $expected $changed; if($cmp){ throw ($cmp | Out-String) }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$repoChanged=(git diff --name-only); if($repoChanged -contains 'wiki/wiki/_Sidebar.md'){ throw '_Sidebar.md must remain untouched' }; foreach($path in $repoChanged){ if($path -notin @('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md')){ throw \"Unexpected changed file: $path\" } }; 'OK'"`
  - [ ] `pwsh -NoProfile -Command "$p1='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; foreach($pattern in 'orbit','deathfx-cow','metadata-dirty-zombie','viewer-cycle-zombie','attach-existing-zombie'){ if(-not (Select-String -Path $p1 -Pattern $pattern -Quiet)){ throw \"Missing scenario $pattern\" } }; $p2='wiki/wiki/VersionSupportExplained.md'; foreach($pattern in 'spigot-1\.8\.8','spigot-1\.13\.2','spigot-1\.16\.5','spigot-1\.17\.1','paper-1\.19\.2','paper-1\.21\.11'){ if(-not (Select-String -Path $p2 -Pattern $pattern -Quiet)){ throw \"Missing server $pattern\" } }; 'OK'"`

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Repo diff is isolated to the exact ten target wiki files
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$expected=@('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md'); $changed=(git diff --name-only -- 'wiki/wiki') | Where-Object { $_ }; $cmp=Compare-Object $expected $changed; if($cmp){ throw ($cmp | Out-String) }; 'OK'"`.
      2. Run `pwsh -NoProfile -Command "$repoChanged=(git diff --name-only); if($repoChanged -contains 'wiki/wiki/_Sidebar.md'){ throw '_Sidebar.md changed unexpectedly' }; foreach($path in $repoChanged){ if($path -notin @('wiki/wiki/Home.md','wiki/wiki/EntitiesExplained.md','wiki/wiki/EntityTemplatesExplained.md','wiki/wiki/SpawningAndControllersExplained.md','wiki/wiki/PacketAndTrackerControlExplained.md','wiki/wiki/MetadataSyncExplained.md','wiki/wiki/AttachingAndWrappingExplained.md','wiki/wiki/VersionSupportExplained.md','wiki/wiki/EntityTestingAndMatrixVerificationExplained.md','wiki/wiki/EntitySharpEdgesAndTips.md')){ throw \"Unexpected file changed: $path\" } }; 'OK'"`.
    Expected: only the exact ten target files are modified and navigation/source/code files stay untouched.
    Evidence: .sisyphus/evidence/task-13-diff-isolation.txt

  Scenario: Prompt-critical scenarios and server ids are preserved exactly
    Tool: Bash
    Steps:
      1. Run `pwsh -NoProfile -Command "$p1='wiki/wiki/EntityTestingAndMatrixVerificationExplained.md'; foreach($pattern in 'orbit','deathfx-cow','metadata-dirty-zombie','viewer-cycle-zombie','attach-existing-zombie'){ if(-not (Select-String -Path $p1 -Pattern $pattern -Quiet)){ throw \"Missing scenario $pattern\" } }; $p2='wiki/wiki/VersionSupportExplained.md'; foreach($pattern in 'spigot-1\.8\.8','spigot-1\.13\.2','spigot-1\.16\.5','spigot-1\.17\.1','paper-1\.19\.2','paper-1\.21\.11'){ if(-not (Select-String -Path $p2 -Pattern $pattern -Quiet)){ throw \"Missing server $pattern\" } }; 'OK'"`.
    Expected: the docs preserve the prompt’s exact scenario/server-id evidence set.
    Evidence: .sisyphus/evidence/task-13-diff-isolation-error.txt
  ```

  **Commit**: NO | Message: `docs(entity-wiki): verify entity wiki scope and prompt compliance` | Files: [`wiki/wiki/Home.md`, `wiki/wiki/EntitiesExplained.md`, `wiki/wiki/EntityTemplatesExplained.md`, `wiki/wiki/SpawningAndControllersExplained.md`, `wiki/wiki/PacketAndTrackerControlExplained.md`, `wiki/wiki/MetadataSyncExplained.md`, `wiki/wiki/AttachingAndWrappingExplained.md`, `wiki/wiki/VersionSupportExplained.md`, `wiki/wiki/EntityTestingAndMatrixVerificationExplained.md`, `wiki/wiki/EntitySharpEdgesAndTips.md`]

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
- [x] F2. Code Quality Review — unspecified-high
- [x] F3. Real Manual QA — unspecified-high
- [x] F4. Scope Fidelity Check — deep

## Commit Strategy
- Do not commit during page drafting waves.
- After Wave 3 and the final verification wave pass, present results to the user and wait for explicit approval.
- After approval, create one documentation commit: `docs(entity-wiki): add custom entity runtime wiki pages`.

## Success Criteria
- Exactly ten target wiki files exist and are the only changed wiki files.
- Every page satisfies its page-specific prompt requirements and uses repo-grounded names/examples only.
- `Home.md` sells the feature and routes readers through the exact prompt-defined learning order.
- The packet/tracker split, metadata contract, attach/wrap flow, and matrix verification workflow are all explained in user language with reproducible examples.
- The docs do not over-promise version support and do not present test-plugin demos as if they were the only public API surface.
