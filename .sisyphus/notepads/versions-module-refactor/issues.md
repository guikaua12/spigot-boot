# Issues

## 2026-04-14T21:54:51.315Z Task: planning-baseline
- Java LSP (`jdtls`) is not installed in this environment, so Java LSP diagnostics/references are unavailable. Use file reads plus Maven compile/test/package as primary verification.
- Root Maven build inherits `maven-shade-plugin`; api/shared/provider modules must explicitly avoid unintended shading.
- ServiceLoader resource filenames are tied to the SPI FQN and must be renamed together with provider class names.

## 2026-04-14T22:10:00Z Task: versions-parent-coordinate-rename
- Adding `<module>shared</module>` to `versions/pom.xml` makes `mvnw.cmd -pl versions -am -DskipTests validate` fail until a real `versions/shared` module exists; Task 1 correctly updates the parent/module list but must not create that directory yet.

## 2026-04-14T23:16:00Z Task: versions-shared-consumer-module
- `mvnw.cmd -pl versions/shared -am package -DskipTests` is currently blocked before the new module packages because `versions/runtime` fails compilation with pre-existing invalid classfile / `RuntimeInvisibleParameterAnnotations` errors from `spigot-boot-versions-api` and `spigot-api` dependencies; `spigot-boot-versions` is skipped by the reactor.

## 2026-04-14T23:44:00Z Task: runtime-package-and-infrastructure-rename
- `./mvnw.cmd -pl versions/runtime -am -DskipTests compile` now succeeds after moving runtime main sources to `tech.guilhermekaua.spigotboot.versions.runtime`; the remaining verification issue is test discovery only, where Surefire still loads `tech.guilhermekaua.spigotboot.entity.runtime.EntityNetworkRuntimeBundleSelectorTest` and fails with `NoClassDefFoundError: tech/guilhermekaua/spigotboot/entity/runtime/capability/EntityVersionCapabilities` until Task 5 migrates runtime tests/resources to the renamed surface.

## 2026-04-15T00:15:00Z Task: provider-module-1_17_1-migration
- The required verification command `./mvnw.cmd -pl versions/1.17.1 -am test` is still blocked upstream in `versions/runtime`: `NetworkMetadataContractTest` hardcodes the old `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java` path and fails before the `versions/1.17.1` module executes.
- 2026-04-14: mvnw.cmd -pl versions/1.8.8 -am test is blocked by ersions-runtime source-inspection tests that hardcode provider file paths (first ersions/1.8.8/.../EntityFactoryV1_8_8.java, then ersions/1.21.11/.../EntityFactoryV1_21_11.java). Keep migrated packages/FQNs updated, but expect unrelated reactor failures until those tests or file locations are aligned across all version modules.

## 2026-04-15T00:20:00Z Task: provider-module-1_21_11-migration
- The exact verification command from the plan, `./mvnw.cmd -pl versions/1.21.11 -am test -Dtest=EntityFactoryV1_21_11Test`, still aborts in upstream reactor modules because Surefire treats the missing targeted test in `versions/api` as a failure; in PowerShell the fallback flag must be passed via `--%` (`-Dsurefire.failIfNoSpecifiedTests=false`) so Maven can reach `versions/1.21.11` and run the intended test class.

## 2026-04-15T01:01:00Z Task: test-plugin-versions-surface-rename
- In this PowerShell-based workspace, repo-root Maven wrapper commands must be invoked as `.\mvnw.cmd ...`; calling `mvnw.cmd ...` directly from the shell can fail with "The term 'mvnw.cmd' is not recognized" even when the wrapper exists at the repository root.
