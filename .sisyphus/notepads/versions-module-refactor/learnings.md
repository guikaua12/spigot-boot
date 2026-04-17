# Learnings

## 2026-04-14T23:29:52Z Task: api-package-root-and-spi-rename
- ersions/api can be migrated from 	ech.guilhermekaua.spigotboot.entity.api to 	ech.guilhermekaua.spigotboot.versions.api in isolation, including renaming the SPI contract from EntityVersionAdapter to VersionAdapter; mvnw.cmd -pl versions/api -am test passes after moving same-module tests to the new package root.

## 2026-04-14T23:30:30Z Task: api-package-root-and-spi-rename (corrected note)
- `versions/api` can be migrated from `tech.guilhermekaua.spigotboot.entity.api` to `tech.guilhermekaua.spigotboot.versions.api` in isolation, including renaming the SPI contract from `EntityVersionAdapter` to `VersionAdapter`; `mvnw.cmd -pl versions/api -am test` passes after moving same-module tests to the new package root.

## 2026-04-14T23:55:00Z Task: runtime-test-surface-rename
- `versions/runtime` test discovery only settles once the entire runtime test package tree and the `META-INF/services/...VersionAdapter` descriptor move together from `tech.guilhermekaua.spigotboot.entity.runtime` to `tech.guilhermekaua.spigotboot.versions.runtime`; leaving old test helper packages behind keeps Surefire targeting stale runtime classes.

## 2026-04-15T00:15:00Z Task: provider-module-1_17_1-migration
- Version provider modules are moving to a reversed package root like `tech.guilhermekaua.spigotboot.v1_17_1.entity`; the adapter class becomes `SpigotVersionAdapterV1_17_1`, helper types such as `EntityFactoryV1_17_1` stay named as-is, and the ServiceLoader descriptor must be renamed to `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` with the new adapter FQN.

## 2026-04-15T00:18:00Z Task: provider-module-1_16_5-migration
- Targeting a single version-module test through `-pl versions/1.16.5 -am -Dtest=...` requires quoting the Maven properties in PowerShell and adding `-Dsurefire.failIfNoSpecifiedTests=false`, otherwise upstream reactor modules such as `versions/api` fail before the selected provider test class runs.

## 2026-04-15T00:17:00Z Task: provider-module-1_19_2-migration
- ersions/1.19.2 follows the same provider migration pattern as 1.17.1: move the package root to 	ech.guilhermekaua.spigotboot.v1_19_2.entity, rename only the adapter to SpigotVersionAdapterV1_19_2, keep helper classes like EntityFactoryV1_19_2 and EntityHookBinderV1_19_2 unchanged, and rename the service descriptor to META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter so publication-family tests still resolve the relocated provider.

## 2026-04-15T00:20:00Z Task: provider-module-1_21_11-migration
- `versions/1.21.11` follows the same provider migration pattern as the earlier migrated modules: move classes/tests under `tech.guilhermekaua.spigotboot.v1_21_11.entity`, rename only the adapter to `SpigotVersionAdapterV1_21_11`, keep helpers like `EntityFactoryV1_21_11` named as entities, rename the service descriptor to `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter`, and update stale runtime imports such as `EntityVersionEntrypoint` to `VersionEntrypoint`.
## 2026-04-15T00:19:00Z Task: provider-module-1_19_2-migration (corrected note)
- `versions/1.19.2` follows the same provider migration pattern as `1.17.1`: move the package root to `tech.guilhermekaua.spigotboot.v1_19_2.entity`, rename only the adapter to `SpigotVersionAdapterV1_19_2`, keep helper classes like `EntityFactoryV1_19_2` and `EntityHookBinderV1_19_2` unchanged, and rename the service descriptor to `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` so publication-family tests still resolve the relocated provider.

 ## 2026-04-15T00:26:00Z Task: provider-module-1_13_2-migration
 - `versions/1.13.2` follows the same provider migration pattern as the other migrated modules: move sources/tests to `tech.guilhermekaua.spigotboot.v1_13_2.entity`, rename only the adapter to `SpigotVersionAdapterV1_13_2`, keep helpers like `EntityFactoryV1_13_2`, `EntityHookBinderV1_13_2`, and `LegacyTransportSupportV1_13_2` named as-is, and update stale runtime renames such as `EntityRuntimeProfile` -> `VersionRuntimeProfile`; when running a single provider regression via `-pl ... -am -Dtest=...` in PowerShell, quote dotted Maven properties like `-Dsurefire.failIfNoSpecifiedTests=false`.

## 2026-04-15T01:20:00Z Task: shared-dependency-regression-coverage
- After the SPI rename, shaded `test-plugin` artifacts can retain a stale `META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` entry from previous outputs; `./mvnw.cmd --% -pl test-plugin -am clean package -DskipTests` rebuilds the jar cleanly so only `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` remains with the six provider lines.

## 2026-04-15T01:36:00Z Task: task-14-clean-package-follow-up
- Reactor verification commands that touch the same workspace must run sequentially when one of them uses `clean`; running `test` and `clean package` concurrently against `test-plugin` can delete upstream `target/classes` mid-build and fake a missing-classpath compile failure even though `test-plugin` already sees `core/target/classes` correctly.
