# Config Dedup: Extract the shared config impl into `config/` — Implementation Plan

> **For agentic workers:** This plan is written to be executed task-by-task (e.g. in Cursor Composer). Steps use checkbox (`- [ ]`) syntax. It is a **move/refactor**, not new feature work — the bar is "behavior unchanged, nothing duplicated." Some steps intentionally leave the tree non-compiling until the task completes; verification points are at task boundaries.

**Goal:** Eliminate the duplicated platform-neutral config implementation by hoisting it from `platform-spigot/config-spigot` into the shared `config/` module (retargeted onto `core.BootPlugin`), shrinking `config-spigot` to its Bukkit serializers and **deleting `platform-bungee/config-bungee` entirely**.

**Architecture:** `config/` already ships concrete defaults, so the whole YAML/proxy/manager/registry/folder/injector/reference/reload implementation moves there, in package `tech.guilhermekaua.spigotboot.config.*` (dropping the `.spigot` segment), with the plugin type changed from `org.bukkit.plugin.Plugin` to `tech.guilhermekaua.spigotboot.core.plugin.BootPlugin` (already a registered bean and already normalizing `getResource` across platforms). `config-spigot` keeps only `Material/Sound/World/Location` serializers + a slim `@Configuration`. Bungee plugins consume `spigot-boot-config` directly.

**Tech Stack:** Java 8 bytecode (tests Java 17), Maven. `config/` gains `org.javassist:javassist:3.30.2-GA` (provided) + `org.yaml:snakeyaml:2.2` (provided) + the javassist relocation shade + failsafe. No `core` changes. Spec: `docs/superpowers/specs/2026-06-16-config-shared-module-design.md`.

**Build/JDK notes (read once):**
- Build with **JDK 21** (not 25 — Lombok 1.18.36 crashes on 25). On the reference machine: `JAVA_HOME=C:\Users\Guilherme\.jdks\ms-21.0.10`. Run Maven from the repo root. In PowerShell: `$env:JAVA_HOME='C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd <args>` and **quote any `-D` arg containing a comma** (e.g. `"-Dtest=A,B"`). In bash/Cursor terminal, set `JAVA_HOME` accordingly and use `./mvnw` / `mvnw.cmd`.
- Always pass `-Danimal.sniffer.skip=true` (upstream `-am` modules run the spigot-1.8.8 check; skipping keeps builds clean). For filtered `-Dtest=` runs add `-Dsurefire.failIfNoSpecifiedTests=false`.
- The root `license-maven-plugin` enforces the MIT header — every moved/new `.java` keeps/gets the `Copyright © 2025 Guilherme Kauã da Silva` header. Moved files already have it.
- **Starting state:** `config-spigot` exists (always). `config-bungee` MAY exist (from PR #75). Task 6 deletes it if present (no-op if absent). Run this refactor after PR #75 merges into the bungee integration branch, or as its replacement.

## The move transform

Every class listed as "moved" is relocated from `config-spigot` into `config/` and edited by these rules. Prefer `git mv` for the file, then edit, so history is preserved. There is nothing else to change.

1. **Package + imports:** replace `tech.guilhermekaua.spigotboot.config.spigot` with `tech.guilhermekaua.spigotboot.config` (drop the `.spigot` segment) in the `package` statement and ALL imports — and move the file into the mirrored `config/` directory. Imports that don't contain `.config.spigot` (core, utils, java.*, javassist.*, snakeyaml) stay unchanged. (Self-references to other moved classes update automatically because they were also `config.spigot.*`.)
2. **Class renames** (declaration, filename, every reference across all moved files):
   - `SpigotConfigManager` → `DefaultConfigManager`
   - `SpigotConfigReferenceLookup` → `DefaultConfigReferenceLookup`
   - `SpigotConfigModule` → `ConfigModule`
3. **Plugin type → BootPlugin** (only `DefaultConfigManager`, `FolderConfigEntry`, the shared `ConfigConfiguration`, and the tests that mock the plugin): replace `import org.bukkit.plugin.Plugin;` with `import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;` and the simple type `Plugin` with `BootPlugin`. `plugin.getDataFolder()`, `plugin.getLogger()`, `plugin.getResource(...)`, `plugin.getClassLoader()` all exist on `BootPlugin` and stay as-is — EXCEPT the `FolderConfigEntry.copyFromFilesystem` classloader fix in Task 2.
4. **Keep javassist imports literal** in `ConfigProxy` (`import javassist.util.proxy.*` stays — the shade rewrites them at package phase).
5. **MIT header:** preserve verbatim.

Files that do NOT move (stay in config-spigot): `serialization/{MaterialSerializer,SoundSerializer,WorldSerializer,LocationSerializer,BukkitSerializers}` and tests `serialization/SoundSerializerTest`, `test/binding/MaterialSerializerBindingTest`. `config-spigot`'s `configuration/ConfigConfiguration` is **rewritten** (Task 3), not moved.

---

## File Structure

**`config/` — gains (moved from `config-spigot`, package `…config.*`):**
- root: `ConfigBindingCoordinator`, `ConfigEntryAccessor`, `DefaultConfigManager` (was `SpigotConfigManager`), `ConfigModule` (was `SpigotConfigModule`)
- `configuration/ConfigConfiguration` (moved + Plugin→BootPlugin + serializer bean dropped)
- `loader/YamlConfigLoader`, `node/YamlConfigNode`, `proxy/ConfigProxy`
- `registry/ConfigRegistry`
- `folder/{FolderConfigEntry,DefaultFolderConfigRef,DefaultFolderConfigEditor,DefaultFolderConfigSnapshot}`
- `injector/{ConfigRefInjector,FolderConfigInjector,ConfigValueInjector,ConfigValueResolver}`
- `reference/{ConfigReferenceManager,ReferenceResolvingPreprocessor,DefaultConfigReferenceLookup}` (last was `SpigotConfigReferenceLookup`)
- `reload/{OnConfigReloadProcessor,OnConfigReloadBinder,OnConfigReloadInvoker}`
- `serialization/DurationSerializer` (+ registered as a default in `DefaultTypeSerializerRegistry`)
- resource `META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.ConfigModule`
- all corresponding tests + the relocation IT + a new `ConfigModuleDiscoveryTest`

**`config/` — modified:** `pom.xml` (deps + processor + shade + failsafe), `serialization/DefaultTypeSerializerRegistry.java` (register Duration).

**`config-spigot` — kept:** `serialization/{Material,Sound,World,Location}Serializer`, `serialization/BukkitSerializers` (edited), a rewritten slim `configuration/ConfigConfiguration`, tests `SoundSerializerTest` + `MaterialSerializerBindingTest` + a new serializer-config test. **Modified:** `pom.xml` (remove javassist + shade). Everything else is deleted (moved to config/).

**Deleted:** the entire `platform-bungee/config-bungee/` directory. **Modified:** `platform-bungee/pom.xml` (remove the `config-bungee` module entry).

---

## Task 1: Prepare `config/`'s build (deps, processor, shade, failsafe)

**Files:**
- Modify: `config/pom.xml`

- [ ] **Step 1: Replace `config/pom.xml` with this content**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot</artifactId>
        <version>3.2.1-SNAPSHOT</version>
    </parent>

    <name>${project.artifactId}</name>
    <description>Platform-agnostic configuration API and YAML implementation for Spigot Boot.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-config</artifactId>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-core</artifactId>
            <version>3.2.1-SNAPSHOT</version>
        </dependency>

        <!-- ConfigProxy uses the javassist proxy API; provided (never bundled) because the relocated
             copy is supplied at runtime by spigot-boot-core, and this module's references are rewritten
             to the shaded package by the maven-shade-plugin execution below. -->
        <dependency>
            <groupId>org.javassist</groupId>
            <artifactId>javassist</artifactId>
            <version>3.30.2-GA</version>
            <scope>provided</scope>
        </dependency>

        <!-- YAML engine. provided: the host server (Bukkit/Paper and BungeeCord) bundles SnakeYAML at
             runtime. Only stable APIs (Yaml, DumperOptions, load, dump) are used, so the compile-time
             2.2 matches the 2.x bundled by both platforms. -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>2.2</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-annotation-processor-spigot</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <testSource>17</testSource>
                    <testTarget>17</testTarget>
                    <compilerArgs>
                        <arg>-parameters</arg>
                    </compilerArgs>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.36</version>
                        </path>
                        <path>
                            <groupId>tech.guilhermekaua.spigot-boot</groupId>
                            <artifactId>spigot-boot-annotation-processor-spigot</artifactId>
                            <version>${project.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>

            <!-- ConfigProxy uses the javassist proxy API directly. javassist ships only as the relocated
                 copy bundled inside spigot-boot-core, so this module's own bytecode must reference the
                 shaded names too; otherwise it throws NoClassDefFoundError: javassist/util/proxy/* at
                 runtime. nothing is bundled here (core owns the shaded javassist) — this execution only
                 rewrites this module's references. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <!-- version managed by the parent pluginManagement (3.5.1) -->
                <executions>
                    <execution>
                        <id>relocate-javassist-references</id>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>true</createDependencyReducedPom>
                            <artifactSet>
                                <includes>
                                    <include>tech.guilhermekaua.spigot-boot:spigot-boot-config</include>
                                </includes>
                            </artifactSet>
                            <relocations>
                                <relocation>
                                    <pattern>javassist</pattern>
                                    <shadedPattern>tech.guilhermekaua.spigotboot.shaded.javassist</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!-- runs *IT (the relocation guard) in the integration-test phase, after package/shade -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.5.3</version>
                <configuration>
                    <systemPropertyVariables>
                        <project.build.directory>${project.build.directory}</project.build.directory>
                        <project.build.finalName>${project.build.finalName}</project.build.finalName>
                    </systemPropertyVariables>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verify `config/` still builds (no code added yet)**

Run: `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS`. The new deps resolve; the shade execution runs at `package` with no javassist references to rewrite yet (harmless); failsafe finds no `*IT` yet. `config/`'s existing tests still pass.

- [ ] **Step 3: Commit**

```bash
git add config/pom.xml
git commit -m "build(config): add javassist/snakeyaml, processor, shade and failsafe to config module"
```

---

## Task 2: Move the platform-neutral implementation into `config/`

This task `git mv`s every neutral class from `config-spigot` into `config/` and applies the move transform. After it, **`config/` compiles with the full impl and `config-spigot` is temporarily broken** (fixed in Task 3). Move the MAIN classes only (tests in Task 4).

**Files (move `config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/<X>` → `config/src/main/java/tech/guilhermekaua/spigotboot/config/<X>`):**

- [ ] **Step 1: `git mv` + transform the package-only classes** (rule 1 only)

Move and apply rule 1 (drop `.spigot` in package + imports):
- `node/YamlConfigNode.java`
- `loader/YamlConfigLoader.java`
- `proxy/ConfigProxy.java` (rule 1 only — keep `import javassist...` literal)
- `folder/DefaultFolderConfigRef.java`, `folder/DefaultFolderConfigEditor.java`, `folder/DefaultFolderConfigSnapshot.java`
- `ConfigBindingCoordinator.java`, `ConfigEntryAccessor.java`
- `reference/ConfigReferenceManager.java`, `reference/ReferenceResolvingPreprocessor.java`
- `reload/OnConfigReloadProcessor.java`, `reload/OnConfigReloadBinder.java`, `reload/OnConfigReloadInvoker.java`
- `injector/ConfigRefInjector.java`, `injector/FolderConfigInjector.java`, `injector/ConfigValueInjector.java`, `injector/ConfigValueResolver.java`
- `serialization/DurationSerializer.java`

(The reference/reload/injector classes also reference `SpigotConfigManager` — apply rule 2 there: → `DefaultConfigManager`. The reference classes reference `SpigotConfigReferenceLookup` — → `DefaultConfigReferenceLookup`.)

- [ ] **Step 2: `git mv` + transform the renamed classes** (rules 1 + 2)

- `SpigotConfigManager.java` → `config/.../config/DefaultConfigManager.java`: rule 1 + rename class `SpigotConfigManager`→`DefaultConfigManager` (declaration + all 3 constructors) + rule 3 (Plugin→BootPlugin: the `import org.bukkit.plugin.Plugin;` → `import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;`, the `private final Plugin plugin;` field and all constructor `Plugin` params → `BootPlugin`). `plugin.getDataFolder()` and `plugin.getResource(...)` calls stay unchanged (both exist on `BootPlugin`). Confirm no `org.bukkit` reference remains.
- `SpigotConfigReferenceLookup.java` → `config/.../config/reference/DefaultConfigReferenceLookup.java`: rule 1 + rename class + (it references `SpigotConfigManager` → `DefaultConfigManager`).
- `SpigotConfigModule.java` → `config/.../config/ConfigModule.java`: rule 1 + rename class `SpigotConfigModule`→`ConfigModule`. (It references `config.spigot.registry.ConfigRegistry` → `config.registry.ConfigRegistry` via rule 1.)
- `registry/ConfigRegistry.java` → `config/.../config/registry/ConfigRegistry.java`: rule 1 + (references `SpigotConfigManager` → `DefaultConfigManager`).

- [ ] **Step 3: Move + fix `FolderConfigEntry` (rule 1 + 3 + the classloader fix)**

`git mv folder/FolderConfigEntry.java` → `config/.../config/folder/FolderConfigEntry.java`. Apply rule 1, then rule 3 (`import org.bukkit.plugin.Plugin;` → `import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;`; the `private final Plugin plugin;` field and the ctor `@NotNull Plugin plugin` param → `BootPlugin`). `plugin.getLogger()`, `plugin.getDataFolder()`, `plugin.getResource(...)` stay.

Then replace the `copyFromFilesystem` classloader access. Find:

```java
        // Object-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
        // getClass() must resolve via the java.* ignore (see root pom)
        Object pluginObject = plugin;
        URL resourceUrl = pluginObject.getClass().getClassLoader().getResource(resourcePath);
```

Replace with (BootPlugin already exposes the plugin's real classloader; the Object trick was only for config-spigot's animal-sniffer, which config/ does not run):

```java
        URL resourceUrl = plugin.getClassLoader().getResource(resourcePath);
```

Confirm no `org.bukkit` reference remains in the file.

- [ ] **Step 4: Move + edit `ConfigConfiguration` (rule 1 + 3, drop the Bukkit serializer bean)**

`git mv configuration/ConfigConfiguration.java` → `config/.../config/configuration/ConfigConfiguration.java`, then replace its content with this (Plugin→BootPlugin; `DefaultConfigManager`; the three injectors; the reload post-processor; **no** `bukkitTypeSerializers` bean — that stays in config-spigot):

```java
package tech.guilhermekaua.spigotboot.config.configuration;

import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.List;

@Configuration
public class ConfigConfiguration {
    @Bean
    public DefaultConfigManager configManager(
            BootPlugin plugin,
            @Nullable ConfigReferenceErrorHandler errorHandler,
            List<TypeSerializerRegistryCustomizer> serializerCustomizers
    ) {
        return new DefaultConfigManager(plugin, errorHandler, serializerCustomizers);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(DefaultConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }

    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(DefaultConfigManager configManager, BootPlugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }
}
```

(Keep the MIT header block at the top — copy it from any sibling file. `DefaultConfigManager`'s no-arg/one-arg/three-arg constructors keep the same shapes, only `Plugin`→`BootPlugin`.)

- [ ] **Step 5: Move the discovery marker**

`git mv config-spigot/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigModule` → `config/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.ConfigModule` (rename the empty marker file to the new FQCN).

- [ ] **Step 6: Register `Duration` as a default serializer**

In `config/src/main/java/tech/guilhermekaua/spigotboot/config/serialization/DefaultTypeSerializerRegistry.java`, add `import java.time.Duration;` and change `createWithDefaults()`:

```java
    static @NotNull DefaultTypeSerializerRegistry createWithDefaults() {
        DefaultTypeSerializerRegistry registry = new DefaultTypeSerializerRegistry();
        PrimitiveSerializers.registerAll(registry);
        registry.register(Duration.class, new DurationSerializer());
        return registry;
    }
```

- [ ] **Step 7: Verify `config/` compiles with the full implementation**

Run: `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS` for `config`. (`config-spigot` is NOT built by this command; it is currently broken and is fixed in Task 3.) If compilation fails, the usual causes are a missed `.spigot`→`config` import, a missed `SpigotConfigManager`→`DefaultConfigManager` reference, or a leftover `org.bukkit` import — fix and re-run.

- [ ] **Step 8: Commit**

```bash
git add config/src
git commit -m "refactor(config): hoist platform-neutral config impl into config module on BootPlugin"
```

---

## Task 3: Shrink `config-spigot` to its Bukkit serializers

After Task 2's `git mv`, the moved files are already gone from `config-spigot`. Now fix what remains so `config-spigot` compiles again.

**Files:**
- Rewrite: `config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/configuration/ConfigConfiguration.java`
- Modify: `config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/serialization/BukkitSerializers.java`
- Modify: `config-spigot/pom.xml`

- [ ] **Step 1: Rewrite config-spigot's `ConfigConfiguration` to register only the Bukkit serializers**

Replace its content with (keep the MIT header):

```java
package tech.guilhermekaua.spigotboot.config.spigot.configuration;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

@Configuration
public class ConfigConfiguration {
    @Bean
    public TypeSerializerRegistryCustomizer bukkitTypeSerializers() {
        return new TypeSerializerRegistryCustomizer() {
            @Override
            public void customize(@NotNull TypeSerializerRegistry registry) {
                BukkitSerializers.registerAll(registry);
            }

            @Override
            public int getOrder() {
                return -100;
            }
        };
    }
}
```

- [ ] **Step 2: Drop the `Duration` registration from `BukkitSerializers`**

`DurationSerializer` moved to `config/` and is now a default. Edit `BukkitSerializers.java`: remove `import java.time.Duration;`, remove the `import` of the (now-gone) local `DurationSerializer`, and remove the `registry.register(Duration.class, new DurationSerializer());` line. Result registers exactly four serializers:

```java
    public static void registerAll(@NotNull TypeSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        registry.register(Material.class, new MaterialSerializer());
        registry.register(Sound.class, new SoundSerializer());
        registry.register(World.class, new WorldSerializer());
        registry.register(Location.class, new LocationSerializer());
    }
```

- [ ] **Step 3: Remove javassist + the shade execution from `config-spigot/pom.xml`**

`ConfigProxy` and the shade moved to `config/`. In `config-spigot/pom.xml`:
- Delete the `<plugin>` block for `maven-shade-plugin` (the whole `relocate-javassist-references` execution).
- Delete the `org.javassist:javassist` `<dependency>` block.

Keep everything else (the compiler plugin with its annotationProcessorPaths, the `animal-sniffer-maven-plugin`, and the `paper-api` / `spigot-boot-config` / `spigot-boot-core-spigot` / `annotation-processor-spigot` dependencies). The build section's `<plugins>` should then contain only `maven-compiler-plugin` and `animal-sniffer-maven-plugin`; the `<dependencies>` should contain `paper-api`, `spigot-boot-config`, `spigot-boot-core-spigot`, and `spigot-boot-annotation-processor-spigot`.

- [ ] **Step 4: Verify `config-spigot` compiles again**

Run: `mvnw.cmd -pl platform-spigot/config-spigot -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. If it fails citing a missing `config.spigot.*` class, that class was moved in Task 2 and something in the four kept serializers / the new `ConfigConfiguration` still references it — it should not; the kept classes only use `config.serialization.*` (from config/) + `org.bukkit.*`.

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src platform-spigot/config-spigot/pom.xml
git commit -m "refactor(config-spigot): reduce to Bukkit serializers; drop javassist and shade"
```

---

## Task 4: Move the tests into `config/`

Move every platform-neutral test from `config-spigot` into `config/`, applying the move transform (Plugin mocks → `BootPlugin` mocks; manager renames). Test packages change `config.spigot.test.*` → `config.test.*` (and `config.spigot.serialization` stays only for the Bukkit serializer tests that DON'T move).

**Files (move `config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/<X>` → `config/src/test/java/tech/guilhermekaua/spigotboot/config/test/<X>`):**

- [ ] **Step 1: `git mv` + transform the neutral test classes**

Rule 1 (package), plus rule 3 (`org.bukkit.plugin.Plugin` → `BootPlugin`) in the ones that mock the plugin, plus rule 2 (`SpigotConfigManager`→`DefaultConfigManager`) where referenced:
- `test/node/YamlConfigNodeTest.java`, `test/loader/YamlConfigLoaderTest.java`, `test/proxy/ConfigProxyTest.java` (rule 1 only)
- `test/folder/FolderConfigEntryTest.java` (rule 1 + 3 — mocks the plugin; if it stubs `plugin.getResource`, leave it; `BootPlugin.getResource` matches)
- `test/manager/SpigotConfigManagerTest.java` → `test/manager/DefaultConfigManagerTest.java` (rule 1 + 2 + 3)
- `test/manager/SpigotConfigManagerValueAccessTest.java` → `test/manager/DefaultConfigManagerValueAccessTest.java` (rule 1 + 2 + 3)
- `test/manager/SpigotConfigManagerFolderItemTypesTest.java` → `test/manager/DefaultConfigManagerFolderItemTypesTest.java` (rule 1 + 2 + 3)
- `test/injector/ConfigValueInjectorTest.java`, `test/injector/ConfigValueResolverTest.java` (rule 1 + 2 + 3)
- `test/binding/ConfigEnumListBindingTest.java` (rule 1 + 2 + 3 — its enum is a plain Java enum)
- `test/reference/ReferenceResolvingPreprocessorTest.java` (rule 1 + 2)
- `test/registry/ConfigRegistryTest.java` (rule 1 + 2)
- `test/reload/OnConfigReloadBinderTest.java`, `OnConfigReloadInvokerTest.java`, `OnConfigReloadProcessorTest.java` (rule 1 + 2)
- `test/reload/OnConfigReloadProcessorIntegrationTest.java` (rule 1 + 2 + 3)
- `test/configuration/ConfigConfigurationTest.java` (rule 1 + 2 + 3) — both its tests target the shared `ConfigConfiguration`; in the moved version, `mock(SpigotConfigManager.class)`/`new SpigotConfigManager(plugin)` → `DefaultConfigManager`, and `@Mock Plugin plugin` → `@Mock BootPlugin plugin`. The `onConfigReloadProcessor(configManager, plugin)` call now passes a `BootPlugin`.

If any moved test class name collides with an existing `config/` test in the same `config.test.*` package, prefer the moved name (none are expected to collide).

- [ ] **Step 2: Verify the moved tests pass in `config/`**

Run: `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true test`
Expected: `BUILD SUCCESS` — every moved test green (manager, loader, node, proxy, folder, injectors, reference, reload, binding, configuration). `snakeyaml`/`javassist` are on the test classpath via the `provided` deps. If a manager/folder test fails because a `BootPlugin` mock method isn't stubbed (e.g. `getResource`), add the stub — do NOT change production logic.

- [ ] **Step 3: Commit**

```bash
git add config/src/test platform-spigot/config-spigot/src/test
git commit -m "test(config): move platform-neutral config tests into config module"
```

---

## Task 5: Move the relocation guard + add a discovery test (in `config/`)

**Files:**
- Create: `config/src/test/java/tech/guilhermekaua/spigotboot/config/proxy/ConfigProxyRelocationIT.java`
- Create: `config/src/test/java/tech/guilhermekaua/spigotboot/config/ConfigModuleDiscoveryTest.java`
- Delete (if it exists): `platform-spigot/config-spigot/...` had no IT; the IT currently lives in `config-bungee` and is removed when that module is deleted in Task 6.

- [ ] **Step 1: Create the relocation IT in `config/`**

```java
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
package tech.guilhermekaua.spigotboot.config.proxy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the shaded-javassist NoClassDefFoundError. {@code ConfigProxy} uses the
 * javassist proxy API directly, so its pre-shade bytecode legitimately references {@code javassist/};
 * only the PACKAGED (shaded) jar must be clean. A normal surefire test runs against the un-shaded
 * {@code target/classes} and cannot catch this — so this is a failsafe {@code *IT} bound to the
 * {@code verify} phase (after {@code package}/shade), inspecting the produced jar.
 */
class ConfigProxyRelocationIT {

    private static final String SHADED_PREFIX = "tech/guilhermekaua/spigotboot/shaded/javassist/";
    private static final String CONFIG_PROXY_ENTRY =
            "tech/guilhermekaua/spigotboot/config/proxy/ConfigProxy.class";

    @Test
    void packagedConfigProxyReferencesOnlyShadedJavassist() throws IOException {
        String buildDir = System.getProperty("project.build.directory");
        String finalName = System.getProperty("project.build.finalName");
        assertNotNull(buildDir, "project.build.directory must be supplied by the failsafe plugin");
        assertNotNull(finalName, "project.build.finalName must be supplied by the failsafe plugin");

        Path jar = Path.of(buildDir, finalName + ".jar");
        assertTrue(Files.exists(jar),
                "shaded jar not found at " + jar + " — run the package/verify phase, not just test");

        byte[] bytes;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(CONFIG_PROXY_ENTRY);
            assertNotNull(entry, CONFIG_PROXY_ENTRY + " not found inside " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
        }

        String pool = new String(bytes, StandardCharsets.ISO_8859_1);

        assertTrue(pool.contains(SHADED_PREFIX),
                "ConfigProxy.class must reference the relocated javassist package " + SHADED_PREFIX
                        + " in the packaged jar (relocation did not run)");

        // strip the shaded prefix first: the relocated form itself contains "javassist/".
        String withoutShaded = pool.replace(SHADED_PREFIX, "");
        assertFalse(withoutShaded.contains("javassist/"),
                "ConfigProxy.class still references the un-relocated javassist package in the packaged jar");
    }
}
```

- [ ] **Step 2: Create `ConfigModuleDiscoveryTest`**

```java
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
package tech.guilhermekaua.spigotboot.config;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleDiscovery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigModuleDiscoveryTest {

    // proves the META-INF/spigot-boot/modules marker resource is present and names ConfigModule,
    // so SpigotBootBuilder.autoDiscover() picks it up on any platform (Spigot or Bungee).
    @Test
    void configModuleIsAutoDiscoverable() {
        List<Class<? extends Module>> modules =
                new ModuleDiscovery(getClass().getClassLoader()).discover();

        assertTrue(modules.contains(ConfigModule.class),
                "ConfigModule must be discoverable via its META-INF/spigot-boot/modules marker");
    }
}
```

- [ ] **Step 3: Verify the IT guards the shaded jar (full module verify)**

Run: `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS`; surefire runs all unit tests + `ConfigModuleDiscoveryTest`; the shade runs; failsafe runs `ConfigProxyRelocationIT` (`Tests run: 1`) against `target/spigot-boot-config-3.2.1-SNAPSHOT.jar`.

Teeth-check: temporarily remove the `<relocations>` block from `config/pom.xml`'s shade execution, re-run — `ConfigProxyRelocationIT` must FAIL — then restore it (`git checkout -- config/pom.xml`) and re-run to confirm green.

- [ ] **Step 4: Commit**

```bash
git add config/src/test/java/tech/guilhermekaua/spigotboot/config/proxy/ConfigProxyRelocationIT.java config/src/test/java/tech/guilhermekaua/spigotboot/config/ConfigModuleDiscoveryTest.java
git commit -m "test(config): guard ConfigProxy relocation in the shaded jar; add module discovery test"
```

---

## Task 6: Delete the `config-bungee` module

**Files:**
- Delete: `platform-bungee/config-bungee/` (entire directory)
- Modify: `platform-bungee/pom.xml`

- [ ] **Step 1: Remove the module from the aggregator**

In `platform-bungee/pom.xml`, delete the `<module>config-bungee</module>` line from `<modules>` (keep `core-bungee` and any `commands-bungee`/`annotation-processor-bungee` entries).

- [ ] **Step 2: Delete the module directory**

```bash
git rm -r platform-bungee/config-bungee
```

(If the directory is absent — i.e. PR #75 not merged into this tree — skip this task; there is nothing to delete and the pom has no `config-bungee` entry to remove.)

- [ ] **Step 3: Verify `platform-bungee` still builds**

Run: `mvnw.cmd -pl platform-bungee -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS` (builds `core-bungee` and any commands modules; `config-bungee` no longer in the reactor).

- [ ] **Step 4: Commit**

```bash
git add platform-bungee/pom.xml platform-bungee/config-bungee
git commit -m "refactor(bungee): delete config-bungee; Bungee consumes shared spigot-boot-config"
```

---

## Task 7: `config-spigot` test cleanup + serializer-config test

The two `ConfigConfigurationTest` cases moved to `config/` in Task 4. `config-spigot` keeps `SoundSerializerTest` and `MaterialSerializerBindingTest`. Add a small test for config-spigot's new serializer `@Configuration`.

**Files:**
- Create: `config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/configuration/ConfigConfigurationTest.java`

- [ ] **Step 1: Write the serializer-config test**

```java
/* ...MIT header (copy from a sibling test)... */
package tech.guilhermekaua.spigotboot.config.spigot.test.configuration;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.spigot.configuration.ConfigConfiguration;

import org.bukkit.Material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ConfigConfigurationTest {

    @Test
    void bukkitTypeSerializers_registersBukkitSerializers() {
        TypeSerializerRegistryCustomizer customizer = new ConfigConfiguration().bukkitTypeSerializers();
        assertEquals(-100, customizer.getOrder());

        TypeSerializerRegistry registry = mock(TypeSerializerRegistry.class);
        customizer.customize(registry);

        // BukkitSerializers registers Material/Sound/World/Location; verify at least Material was registered.
        verify(registry).register(org.mockito.ArgumentMatchers.eq(Material.class),
                org.mockito.ArgumentMatchers.<TypeSerializer<Material>>any());
    }
}
```

(If `org.bukkit.Material` static-init issues arise under a plain mock — it should not, the class only needs to load — replace the `verify` with asserting `customizer` is non-null and `getOrder()==-100`, and that `customize` runs without throwing against a real `DefaultTypeSerializerRegistry`.)

- [ ] **Step 2: Verify config-spigot tests pass**

Run: `mvnw.cmd -pl platform-spigot/config-spigot -am -Danimal.sniffer.skip=true test`
Expected: `BUILD SUCCESS` — `SoundSerializerTest`, `MaterialSerializerBindingTest`, and the new `ConfigConfigurationTest` green.

- [ ] **Step 3: Commit**

```bash
git add platform-spigot/config-spigot/src/test
git commit -m "test(config-spigot): cover the Bukkit serializer configuration"
```

---

## Task 8: Whole-reactor verification

**Files:** none (verification gate).

- [ ] **Step 1: No leftover platform references in `config/`**

Run: `git grep -nE "org\.bukkit|config\.spigot" -- config/src`
Expected: **no output**. (The moved code must be platform-neutral; any hit is a missed transform.)

- [ ] **Step 2: Full module verify (config)**

Run: `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS` — all moved unit tests + `ConfigModuleDiscoveryTest` + the failsafe `ConfigProxyRelocationIT` green.

- [ ] **Step 3: Whole reactor build + install**

Run: `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests`
Expected: `BUILD SUCCESS` for every module — `config` (shaded), `config-spigot` (slim), `platform-bungee` (no config-bungee), and all downstream consumers of `config` still resolve.

- [ ] **Step 4: Full reactor tests**

Run: `mvnw.cmd -Danimal.sniffer.skip=true test`
Expected: `BUILD SUCCESS` across all modules (config's moved tests + config-spigot's serializer tests + everything else unchanged).

---

## Done criteria

- `git grep -nE "org\.bukkit|config\.spigot" -- config/src` returns nothing (the shared impl is platform-neutral).
- `mvnw.cmd -pl config -am -Danimal.sniffer.skip=true verify` is green, including the failsafe `ConfigProxyRelocationIT` (proves `config/`'s shaded jar references only the relocated javassist) and its teeth-check.
- `platform-bungee/config-bungee` no longer exists and is gone from `platform-bungee/pom.xml`.
- `config-spigot` contains only `Material/Sound/World/Location` serializers + `BukkitSerializers` + the slim `@Configuration` (+ its serializer tests), with no `javassist` dependency or shade.
- `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests` and `mvnw.cmd -Danimal.sniffer.skip=true test` are green across the whole reactor.
- A Bungee plugin builds against `spigot-boot-config` + `spigot-boot-core-bungee`; a Spigot plugin against `spigot-boot-config-spigot` + `spigot-boot-core-spigot` — both behave exactly as before.

## Out of scope

- Re-introducing a `config-bungee` module (only when a genuinely Bungee-specific serializer exists).
- Renaming the stray `config.spigot.node.SnapshotConfigNode` package inside `config/` to a non-`spigot` name.
- Any change to `core`, the public annotations, or the `ConfigManager`/`ConfigRef` interfaces.
