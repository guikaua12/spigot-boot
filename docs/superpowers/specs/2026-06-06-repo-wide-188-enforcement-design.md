# Design: repo-wide spigot-api 1.8.8 enforcement

Date: 2026-06-06
Status: approved
Scope: promote the existing inventory-api 1.8.8 animal-sniffer check (see
`2026-06-05-animal-sniffer-188-design.md` for the core mechanics, which are
unchanged) to repo-level infrastructure and activate it in every runtime module
that compiles against the Bukkit/Paper API.

## Problem

The 1.8.8 enforcement currently lives inside `modules/inventory-api`: the
signature generator is a child of the inventory-api parent and the shared
`pluginManagement` sits in that parent's pom. The `platform-spigot` subtree —
whose parent pom gives all 7 children a provided `paper-api 1.20.1` dependency —
has no enforcement, and cannot simply opt in: the root reactor builds
`platform-spigot` BEFORE `modules/`, so the signature would not exist in-reactor
when those checks run (cold-cache CI failure).

## Scope decision (user-approved)

Checked: the 6 runtime platform-spigot modules — `core-spigot`,
`commands-spigot`, `commands-config-spigot`, `config-spigot`, `pmc`,
`placeholder` — plus the 3 already-checked inventory-api modules.

Not checked (deliberate):
- `platform-spigot/annotation-processor` — build-time tool, never loaded on a
  server; the code it GENERATES is checked in the consuming modules.
- `test-plugin` — dev tooling; keeping latest-API convenience for local testing.
- `core`, `commands`, `config`, `data`, `utils` — no Bukkit/Paper dependency.
- `nms-1_8_R3` … `nms-1_19_R3` — version-specific by design.

## Design

### 1. Signature module promotion

`git mv modules/inventory-api/spigot-api-1_8-signature spigot-api-1_8-signature`
(top level). Pom changes, all minimal:
- `<parent>` becomes the root `spigot-boot` pom (same groupId/version).
- Gains its own `<repositories>` entry for the SpigotMC snapshot repo
  (`https://hub.spigotmc.org/nexus/content/repositories/snapshots/`) — the root
  pom declares no repositories and only this module needs that one.
- Description reworded to repo-wide.
- Unchanged: artifactId `spigot-boot-spigot-api-1_8-signature` (managed GAV
  references stay valid), `maven.deploy.skip` + central-publishing
  `skipPublishing`, `includeJavaHome=false`, `combine.self="override"` config
  pinning, the `check-spigot-188-api` unbind execution, the `build` goal at
  `generate-resources`.

### 2. Root pom

- `<modules>`: `<module>spigot-api-1_8-signature</module>` listed FIRST, with
  the comment that declaration order guarantees build-before-consumers only for
  sequential builds (`-T` unsupported).
- `<build><pluginManagement>`: the animal-sniffer block moves here verbatim
  from the inventory-api parent, with the documented install remedy updated to
  the simpler `mvnw.cmd -pl spigot-api-1_8-signature install`.

The inventory-api parent loses its signature `<module>` entry and its entire
`<build>` section (it contained only the pluginManagement). The three existing
inventory-api activations keep working unchanged — Maven resolves plugin
management down the parent chain (inventory-api-parent → modules → root).

### 3. Activation

The established 3-line opt-in (plus its one-line comment) added to each of the
6 modules' `<build><plugins>`:

```xml
<!-- activates the managed spigot-api 1.8.8 check (config in the root pom) -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>animal-sniffer-maven-plugin</artifactId>
</plugin>
```

(Comment text references the root pom now; the three inventory-api activation
comments are updated from "inventory-api parent" to "root pom" for accuracy.)

### 4. Per-module rollout discipline

For each of the 6 modules, in order:
1. **Mask scan** (the MockBukkit lesson): inspect the module's full dependency
   tree — test scope included, since the check resolves `ResolutionScope.TEST` —
   for jars bundling `org.bukkit` classes; any hit joins the managed
   `excludeDependencies`.
2. **Red run**: activate, run the check, collect violations.
3. **Triage by established policy**:
   - JDK-supertype false positives (inherited `Object`/`Enum` members invoked
     on Bukkit-typed receivers) → fix at source in a `refactor` commit placed
     BEFORE the enforcement commit (precedents: `InventoryType` `==`,
     `Object`-typed server hoists).
   - Intentional version-gated newer-API usage → targeted module-level
     `<ignores combine.children="append">` entry with an explanatory comment
     (precedent: `InventoryView` in nms).
   - Genuine ungated 1.8.8 incompatibility → STOP and report to the user for a
     decision; never silently ignore real incompatibilities away.
4. **Green run.**

Plus one red canary (`Inventory#getStorageContents()`) in `core-spigot` proving
the relocated, root-managed wiring is non-vacuous.

### 5. Verification

- Cold cache: purge `spigot-boot-spigot-api-1_8-signature` from `~/.m2`, run
  full `mvnw.cmd test -B` — expect the signature generation FIRST, then exactly
  NINE `check (check-spigot-188-api)` executions (3 inventory-api + 6
  platform-spigot), zero `Undefined reference` lines, BUILD SUCCESS.
- `mvnw.cmd -pl spigot-api-1_8-signature install` from the root works (the
  documented remedy), and the signature is left installed locally afterwards.
- CI remains untouched: `mvn test -B` picks everything up via the same
  generate-resources / process-test-classes phase bindings.

### 6. Documentation updates

- CLAUDE.md: the fresh-clone note next to `mvnw.cmd -pl test-plugin -am
  package` switches to the new install command.
- The 2026-06-05 spec gets a one-line topology pointer to this spec (its
  mechanics sections remain accurate).
- Root pom / signature pom comments carry the updated remedy and ordering
  rationale.

## Risks

- Unknown violation count in the 6 modules; the per-module rollout (fix/gate
  before enforce, every commit green) contains the blast radius, and genuine
  incompatibilities escalate to the user instead of being masked.
- Module-specific test dependencies could mask checks (MockBukkit-style);
  mitigated by the mandatory mask scan per module.
- Reactor ordering still relies on declaration order for sequential builds —
  unchanged from the existing design, now documented at the root listing.
