# `@ConfigValue` Injector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the declared-but-unused `@ConfigValue` annotation so a single config value can be injected into a DI-managed bean's field or constructor parameter, resolved once at construction with a fail-fast contract.

**Architecture:** A thin `ConfigValueInjector` implements the existing `CustomInjector` SPI and delegates to a dedicated `ConfigValueResolver`. The resolver detects the addressing mode (`"configName:path"` string vs. type-safe `config()`+`path()`), looks the value up through three new generic helpers on `SpigotConfigManager` (`getConfigName`, `deserializeAt`, `coerceDefault`) that read through the `TypeSerializerRegistry`, and either returns the value/default or throws. The injector is registered alongside the existing config injectors.

**Tech Stack:** Java 8-compatible source, Maven (multi-module reactor), JUnit 5, Mockito, MockBukkit-style `@Mock Plugin`. Annotation lives in module `config`; everything else in `platform-spigot/config-spigot`.

**Reference spec:** `docs/superpowers/specs/2026-06-13-configvalue-injector-design.md` (registry-backed approach approved).

---

## Running Maven in this repo

`mvnw.cmd` cannot be invoked from the Bash tool (Windows `.cmd`, and the Unix `mvnw` script breaks on the space in the path). Use the cached binary. Run every command from the repo root:

```bash
MVN="/c/Users/OTI Software/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn"
cd "/c/Users/OTI Software/IdeaProjects/spigot-boot"
```

(If that exact wrapper dir no longer exists, `ls "/c/Users/OTI Software/.m2/wrapper/dists/"*/*/bin/mvn` to find it.)

Single-test-class command form used throughout: `"$MVN" -q -pl <module> -am test -Dtest=<ClassName>`.

---

## File Structure

| File | Responsibility |
|---|---|
| `config/.../annotation/ConfigValue.java` *(modify)* | Annotation contract: optional `value()`, `DEFAULT_NONE` sentinel for `defaultValue()`, expanded Javadoc |
| `platform-spigot/config-spigot/.../SpigotConfigManager.java` *(modify)* | Add `getConfigName`, `deserializeAt`, `coerceDefault`; extract node navigation into private `resolveNode` |
| `platform-spigot/config-spigot/.../injector/ConfigValueResolver.java` *(create)* | All `@ConfigValue` resolution logic: mode detection, path building, lookup, default coercion, error messages |
| `platform-spigot/config-spigot/.../injector/ConfigValueInjector.java` *(create)* | Thin `CustomInjector`: `supports()` on annotation presence; `resolve()` delegates; `getOrder() == -100` |
| `platform-spigot/config-spigot/.../configuration/ConfigConfiguration.java` *(modify)* | Register `ConfigValueInjector`; rename bean `folderConfigInjector` → `configInjectors` |
| `platform-spigot/config-spigot/.../registry/ConfigRegistry.java` *(modify)* | Reject `@ConfigValue` on `@Config`/`@FolderConfig` POJO fields at startup |
| `config-spigot-unwired-api-notes.md` *(modify)* | Drop the `@ConfigValue` entry |
| Tests (create): `ConfigValueAnnotationTest` (config), `SpigotConfigManagerValueAccessTest`, `ConfigValueResolverTest`, `ConfigValueInjectorTest`, `ConfigConfigurationTest`; *(modify)* `ConfigRegistryTest` | Coverage per task |

**License header:** every new `.java` file must start with the standard MIT header — copy it verbatim from the top of `config/src/main/java/tech/guilhermekaua/spigotboot/config/annotation/ConfigValue.java` (lines 1-22). It is shown once below in Task 1's file and abbreviated as `/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */` thereafter.

---

### Task 1: Annotation — optional `value()` + `DEFAULT_NONE` sentinel

**Files:**
- Modify: `config/src/main/java/tech/guilhermekaua/spigotboot/config/annotation/ConfigValue.java`
- Test: `config/src/test/java/tech/guilhermekaua/spigotboot/config/test/annotation/ConfigValueAnnotationTest.java`

- [ ] **Step 1: Write the failing test**

Create `config/src/test/java/tech/guilhermekaua/spigotboot/config/test/annotation/ConfigValueAnnotationTest.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.test.annotation;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValueAnnotationTest {

    @Test
    void valueDefaultsToEmptyString() throws NoSuchMethodException {
        Method value = ConfigValue.class.getDeclaredMethod("value");
        assertEquals("", value.getDefaultValue());
    }

    @Test
    void defaultValueDefaultsToSentinel() throws NoSuchMethodException {
        Method defaultValue = ConfigValue.class.getDeclaredMethod("defaultValue");
        assertEquals(ConfigValue.DEFAULT_NONE, defaultValue.getDefaultValue());
    }

    @Test
    void sentinelIsNotAPlausibleRealValue() {
        // The sentinel must be something no human would type as a real default.
        assertNotEquals("", ConfigValue.DEFAULT_NONE);
        assertTrue(ConfigValue.DEFAULT_NONE.contains("DEFAULT_NONE"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"$MVN" -q -pl config -am test -Dtest=ConfigValueAnnotationTest
```

Expected: **BUILD FAILURE** — compilation error, `cannot find symbol: variable DEFAULT_NONE` (and `value()` has no default yet).

- [ ] **Step 3: Modify the annotation**

Replace the body of `ConfigValue.java` (keep the existing MIT header and package). The full file:

```java
/* <MIT license header — copy verbatim, it is already at the top of this file> */
package tech.guilhermekaua.spigotboot.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a single configuration value into a field or constructor parameter, similar to Spring's
 * {@code @Value}. Honored only on dependency-injection-managed beans (e.g. {@code @Component} /
 * {@code @Bean}); it has no effect on fields of {@code @Config} or {@code @FolderConfig} classes
 * (those are bound by the config binder, not the DI container).
 * <p>
 * Two addressing modes are supported:
 * <ul>
 *   <li><b>String mode</b> — {@link #value()} holds {@code "configName:path.to.value"} (or just
 *       {@code "path.to.value"} when exactly one config is registered).</li>
 *   <li><b>Type-safe mode</b> — {@link #config()} names a {@code @Config} class and {@link #path()}
 *       the path within it. When {@link #config()} is set it takes precedence over {@link #value()}.</li>
 * </ul>
 * <p>
 * The value is resolved <b>once at injection time</b> and does <b>not</b> refresh on config reload.
 * For values that must track reloads, inject {@code ConfigRef<T>} or the {@code @Config} class itself.
 * <p>
 * If the path resolves to no value and no {@link #defaultValue()} was supplied, injection fails fast
 * with a {@code ConfigException} naming the path and the injection site.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigValue {

    /**
     * Sentinel meaning "no {@link #defaultValue()} was supplied". Lets an empty string be a valid
     * explicit default while still distinguishing the unset case. Not intended to be referenced by
     * user code.
     */
    String DEFAULT_NONE = "\n\t\t\t  @ConfigValue#DEFAULT_NONE  \t\t\t\n";

    /**
     * Config path in {@code "configName:path"} or bare {@code "path"} form. Ignored when
     * {@link #config()} is set.
     *
     * @return the config path, or empty if {@link #config()} is used instead
     */
    String value() default "";

    /**
     * Default value applied (and coerced to the target type) when the path resolves to nothing.
     * Defaults to {@link #DEFAULT_NONE}, which means "no default" — absence then fails fast.
     *
     * @return the default value
     */
    String defaultValue() default DEFAULT_NONE;

    /**
     * Alternative to {@link #value()}: reference the {@code @Config} class directly. Takes precedence
     * over {@link #value()} when set to anything other than {@code Void.class}.
     *
     * @return the config class
     */
    Class<?> config() default Void.class;

    /**
     * Path within the {@link #config()} class. Required when {@link #config()} is set.
     *
     * @return the path
     */
    String path() default "";
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
"$MVN" -q -pl config -am test -Dtest=ConfigValueAnnotationTest
```

Expected: **BUILD SUCCESS**, `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add config/src/main/java/tech/guilhermekaua/spigotboot/config/annotation/ConfigValue.java \
        config/src/test/java/tech/guilhermekaua/spigotboot/config/test/annotation/ConfigValueAnnotationTest.java
git commit -m "feat(config): make @ConfigValue value() optional and add DEFAULT_NONE sentinel"
```

---

### Task 2: `SpigotConfigManager` value-access helpers

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/SpigotConfigManager.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/manager/SpigotConfigManagerValueAccessTest.java`

- [ ] **Step 1: Write the failing test**

Create `SpigotConfigManagerValueAccessTest.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.test.manager;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SpigotConfigManagerValueAccessTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() throws IOException {
        Logger logger = Logger.getLogger(SpigotConfigManagerValueAccessTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"),
                "name: hello\nport: 25565\nenabled: true\nratio: 1.5\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
    }

    @Test
    void getConfigName_returnsRegisteredName() {
        assertEquals("app", configManager.getConfigName(AppConfig.class));
    }

    @Test
    void getConfigName_whenUnregistered_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configManager.getConfigName(UnregisteredConfig.class));
        assertTrue(ex.getMessage().contains(UnregisteredConfig.class.getName()));
    }

    @Test
    void deserializeAt_readsScalarsOfEachType() {
        assertEquals("hello", configManager.deserializeAt("app:name", String.class));
        assertEquals(Integer.valueOf(25565), configManager.deserializeAt("app:port", Integer.class));
        assertEquals(Integer.valueOf(25565), configManager.deserializeAt("app:port", int.class));
        assertEquals(Boolean.TRUE, configManager.deserializeAt("app:enabled", Boolean.class));
        assertEquals(Double.valueOf(1.5), configManager.deserializeAt("app:ratio", Double.class));
    }

    @Test
    void deserializeAt_whenKeyAbsent_returnsNull() {
        assertNull(configManager.deserializeAt("app:missing", String.class));
    }

    @Test
    void deserializeAt_whenTypeUnsupported_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configManager.deserializeAt("app:name", List.class));
        assertTrue(ex.getMessage().contains(List.class.getName()));
    }

    @Test
    void coerceDefault_coercesToTargetType() {
        assertEquals(Integer.valueOf(42), configManager.coerceDefault("42", Integer.class));
        assertEquals("", configManager.coerceDefault("", String.class));
    }

    @Test
    void coerceDefault_whenUnparseable_throws() {
        assertThrows(ConfigException.class, () -> configManager.coerceDefault("abc", Integer.class));
    }

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        private boolean enabled;
        private double ratio;

        public AppConfig() {
        }
    }

    @Config(value = "unregistered.yml", name = "unregistered", generateDefaults = false)
    public static class UnregisteredConfig {
        private String value;

        public UnregisteredConfig() {
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=SpigotConfigManagerValueAccessTest
```

Expected: **BUILD FAILURE** — compilation error, `cannot find symbol: method getConfigName / deserializeAt / coerceDefault`.

- [ ] **Step 3: Add the `TypeSerializer` import**

In `SpigotConfigManager.java`, add to the imports (next to the other `...serialization.` imports near line 47):

```java
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
```

- [ ] **Step 4: Refactor `get(String, Class)` to extract `resolveNode`, and add the three helpers**

In `SpigotConfigManager.java`, **replace** the existing `get(@NotNull String path, @NotNull Class<T> type)` method (currently lines ~426-469) with the following — the navigation moves into a private `resolveNode`, and `get` now delegates to it, preserving identical behavior:

```java
    @Override
    public <T> @Nullable T get(@NotNull String path, @NotNull Class<T> type) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        ConfigNode node = resolveNode(path);
        return node == null ? null : node.get(type);
    }

    /**
     * Resolves the raw {@link ConfigNode} at a {@code "configName:path"} (or bare {@code "path"})
     * location.
     *
     * @param path the lookup path
     * @return the node (which may be virtual when the path is absent), or null when the config name
     *         is unknown / not loaded / no configs are registered
     * @throws ConfigException if a bare path is given while multiple configs are registered
     */
    private @Nullable ConfigNode resolveNode(@NotNull String path) {
        String configName;
        String nodePath;

        int colonIndex = path.indexOf(':');
        if (colonIndex > 0) {
            configName = path.substring(0, colonIndex);
            nodePath = path.substring(colonIndex + 1);
        } else {
            if (configsByName.isEmpty()) {
                return null;
            }
            if (configsByName.size() > 1) {
                List<String> availableConfigs = new ArrayList<>(configsByName.keySet());
                Collections.sort(availableConfigs);
                throw new ConfigException("Ambiguous config path '" + path + "': multiple configs are registered " +
                        availableConfigs + ". Use 'configName:path' format.");
            }
            configName = configsByName.keySet().iterator().next();
            nodePath = path;
        }

        Class<?> configClass = configsByName.get(configName);
        if (configClass == null) {
            return null;
        }

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null || entry.getNode() == null) {
            return null;
        }

        String[] parts = nodePath.split("\\.");
        Object[] pathSegments = new Object[parts.length];
        System.arraycopy(parts, 0, pathSegments, 0, parts.length);

        return entry.getNode().node(pathSegments);
    }

    /**
     * Returns the registered config name for a {@code @Config} class.
     *
     * @param configClass the config class
     * @return the registered config name
     * @throws ConfigException if the class is not a registered config
     */
    public @NotNull String getConfigName(@NotNull Class<?> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }
        return entry.getConfigName();
    }

    /**
     * Reads the value at a path and deserializes it to the target type using the serializer registry.
     * Unlike {@link #get(String, Class)} (which uses the node's built-in scalar conversion), this
     * supports every type with a registered {@link TypeSerializer}.
     *
     * @param path the {@code "configName:path"} (or bare {@code "path"}) location
     * @param type the target type
     * @param <T>  the target type parameter
     * @return the deserialized value, or null when the path is absent / null
     * @throws ConfigException if no serializer is registered for the type, or on a bad bare path
     */
    public <T> @Nullable T deserializeAt(@NotNull String path, @NotNull Class<T> type) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        ConfigNode node = resolveNode(path);
        if (node == null || node.isVirtual() || node.isNull()) {
            return null;
        }

        TypeSerializer<T> serializer = serializers.getWithInheritance(type);
        if (serializer == null) {
            throw new ConfigException("No serializer registered for type: " + type.getName());
        }
        return serializer.deserialize(node, type);
    }

    /**
     * Coerces a raw string into the target type using the serializer registry (used for
     * {@code @ConfigValue} defaults).
     *
     * @param raw  the raw string
     * @param type the target type
     * @param <T>  the target type parameter
     * @return the coerced value
     * @throws ConfigException if no serializer is registered for the type, or the value cannot be parsed
     */
    public <T> T coerceDefault(@NotNull String raw, @NotNull Class<T> type) {
        Objects.requireNonNull(raw, "raw cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        TypeSerializer<T> serializer = serializers.getWithInheritance(type);
        if (serializer == null) {
            throw new ConfigException("No serializer registered for type: " + type.getName());
        }
        MutableConfigNode node = loader.createNode();
        node.set(raw);
        return serializer.deserialize(node, type);
    }
```

> Note: `coerceDefault("abc", Integer.class)` throws `SerializationException` (a `ConfigException` subclass) via `IntegerSerializer`; the test asserts `ConfigException`, which covers it.

- [ ] **Step 5: Run test to verify it passes**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=SpigotConfigManagerValueAccessTest
```

Expected: **BUILD SUCCESS**, `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run the existing manager test to confirm the `get()` refactor didn't regress**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=SpigotConfigManagerTest
```

Expected: **BUILD SUCCESS**, all existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/SpigotConfigManager.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/manager/SpigotConfigManagerValueAccessTest.java
git commit -m "feat(config-spigot): add value-access helpers to SpigotConfigManager"
```

---

### Task 3: `ConfigValueResolver`

**Files:**
- Create: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueResolver.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/injector/ConfigValueResolverTest.java`

- [ ] **Step 1: Write the failing test**

Create `ConfigValueResolverTest.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.test.injector;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigValueResolverTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;
    private ConfigValueResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Logger logger = Logger.getLogger(ConfigValueResolverTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"),
                "name: hello\nport: 25565\nenabled: true\nratio: 1.5\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
        resolver = new ConfigValueResolver(configManager);
    }

    private Object resolveField(Class<?> beanType, String fieldName) throws NoSuchFieldException {
        return resolver.resolve(InjectionPoint.fromField(beanType.getDeclaredField(fieldName)));
    }

    @Test
    void valueMode_resolvesScalars() throws NoSuchFieldException {
        assertEquals("hello", resolveField(ValueModeBean.class, "name"));
        assertEquals(25565, ((Number) resolveField(ValueModeBean.class, "port")).intValue());
        assertEquals(Boolean.TRUE, resolveField(ValueModeBean.class, "enabled"));
        assertEquals(1.5, ((Number) resolveField(ValueModeBean.class, "ratio")).doubleValue());
    }

    @Test
    void configMode_resolvesByClassAndPath() throws NoSuchFieldException {
        assertEquals("hello", resolveField(ConfigModeBean.class, "name"));
    }

    @Test
    void default_appliedWhenAbsent() throws NoSuchFieldException {
        assertEquals("fallback", resolveField(DefaultBean.class, "missing"));
        assertEquals(7, ((Number) resolveField(DefaultBean.class, "missingNum")).intValue());
        assertEquals("", resolveField(DefaultBean.class, "emptyDefault"));
    }

    @Test
    void requiredMissing_throwsNamingSite() throws NoSuchFieldException {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(RequiredMissingBean.class, "missing"));
        assertTrue(ex.getMessage().contains("missing"));
        assertTrue(ex.getMessage().contains(RequiredMissingBean.class.getName()));
    }

    @Test
    void neitherValueNorConfig_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(NeitherBean.class, "x"));
        assertTrue(ex.getMessage().contains("value() or config()"));
    }

    @Test
    void configWithoutPath_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(ConfigNoPathBean.class, "x"));
        assertTrue(ex.getMessage().contains("path()"));
    }

    @Test
    void unsupportedType_throws() {
        assertThrows(ConfigException.class, () -> resolveField(UnsupportedTypeBean.class, "list"));
    }

    @Test
    void unknownConfigClass_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(UnknownConfigBean.class, "x"));
        assertTrue(ex.getMessage().contains(UnregisteredConfig.class.getName()));
    }

    @Test
    void notInitialized_throws() throws IOException, NoSuchFieldException {
        Files.writeString(tempDir.resolve("late.yml"), "name: x\n");
        SpigotConfigManager notInit = new SpigotConfigManager(plugin);
        notInit.register(LateConfig.class); // registered but NOT initialized
        ConfigValueResolver lateResolver = new ConfigValueResolver(notInit);

        ConfigException ex = assertThrows(ConfigException.class, () -> lateResolver.resolve(
                InjectionPoint.fromField(LateBean.class.getDeclaredField("name"))));
        assertTrue(ex.getMessage().contains("not initialized"));
    }

    @Test
    void barePathWithMultipleConfigs_throwsNamingSite() throws IOException, NoSuchFieldException {
        Files.writeString(tempDir.resolve("a.yml"), "k: 1\n");
        Files.writeString(tempDir.resolve("b.yml"), "k: 2\n");
        SpigotConfigManager multi = new SpigotConfigManager(plugin);
        multi.register(AConfig.class);
        multi.register(BConfig.class);
        multi.initializeAll();
        ConfigValueResolver multiResolver = new ConfigValueResolver(multi);

        ConfigException ex = assertThrows(ConfigException.class, () -> multiResolver.resolve(
                InjectionPoint.fromField(BarePathBean.class.getDeclaredField("k"))));
        assertTrue(ex.getMessage().contains("Ambiguous"));
        assertTrue(ex.getMessage().contains(BarePathBean.class.getName()));
    }

    // ---- fixtures ----

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        private boolean enabled;
        private double ratio;
        public AppConfig() {}
    }

    @Config(value = "late.yml", name = "late", generateDefaults = false)
    public static class LateConfig {
        private String name;
        public LateConfig() {}
    }

    @Config(value = "unregistered.yml", name = "unregistered", generateDefaults = false)
    public static class UnregisteredConfig {
        private String name;
        public UnregisteredConfig() {}
    }

    @Config(value = "a.yml", name = "a", generateDefaults = false)
    public static class AConfig {
        private int k;
        public AConfig() {}
    }

    @Config(value = "b.yml", name = "b", generateDefaults = false)
    public static class BConfig {
        private int k;
        public BConfig() {}
    }

    static class ValueModeBean {
        @ConfigValue("app:name") String name;
        @ConfigValue("app:port") int port;
        @ConfigValue("app:enabled") boolean enabled;
        @ConfigValue("app:ratio") double ratio;
    }

    static class ConfigModeBean {
        @ConfigValue(config = AppConfig.class, path = "name") String name;
    }

    static class DefaultBean {
        @ConfigValue(value = "app:missing", defaultValue = "fallback") String missing;
        @ConfigValue(value = "app:missingNum", defaultValue = "7") int missingNum;
        @ConfigValue(value = "app:emptyDefault", defaultValue = "") String emptyDefault;
    }

    static class RequiredMissingBean {
        @ConfigValue("app:missing") String missing;
    }

    static class NeitherBean {
        @ConfigValue String x;
    }

    static class ConfigNoPathBean {
        @ConfigValue(config = AppConfig.class) String x;
    }

    static class UnsupportedTypeBean {
        @ConfigValue("app:name") List<String> list;
    }

    static class UnknownConfigBean {
        @ConfigValue(config = UnregisteredConfig.class, path = "name") String x;
    }

    static class LateBean {
        @ConfigValue("late:name") String name;
    }

    static class BarePathBean {
        @ConfigValue("k") int k;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigValueResolverTest
```

Expected: **BUILD FAILURE** — `cannot find symbol: class ConfigValueResolver`.

- [ ] **Step 3: Create `ConfigValueResolver`**

Create `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueResolver.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.injector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Objects;

/**
 * Resolves the value for a {@code @ConfigValue}-annotated injection point.
 * <p>
 * Holds all resolution logic so {@link ConfigValueInjector} stays a thin adapter. Detects the
 * addressing mode, builds the lookup path, reads the value (or the default) through
 * {@link SpigotConfigManager}, and fails fast with a {@link ConfigException} naming the injection
 * site on any misconfiguration.
 */
public class ConfigValueResolver {

    private final SpigotConfigManager configManager;

    /**
     * Creates a resolver.
     *
     * @param configManager the config manager, not null
     */
    public ConfigValueResolver(@NotNull SpigotConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    /**
     * Resolves the value to inject for the given {@code @ConfigValue} injection point.
     *
     * @param injectionPoint the injection point, not null
     * @return the resolved value (may be null only if a registered serializer yields null)
     * @throws ConfigException on any misconfiguration or missing required value
     */
    public @Nullable Object resolve(@NotNull InjectionPoint injectionPoint) {
        Objects.requireNonNull(injectionPoint, "injectionPoint cannot be null");

        ConfigValue annotation = injectionPoint.getAnnotatedElement().getAnnotation(ConfigValue.class);
        if (annotation == null) {
            throw new ConfigException("ConfigValueResolver invoked for an element without @ConfigValue");
        }

        String site = describeSite(injectionPoint);

        Class<?> targetType = injectionPoint.getRawType();
        if (targetType == null) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": cannot determine target type (type variables, wildcards and arrays are not supported)");
        }

        if (!configManager.isInitialized()) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": the config system is not initialized yet. Beans using @ConfigValue must be " +
                    "constructed at or after SpigotConfigModule (@Order(-500)).");
        }

        String combinedPath = buildPath(annotation, site);

        Object value;
        try {
            value = configManager.deserializeAt(combinedPath, targetType);
        } catch (ConfigException e) {
            throw new ConfigException("@ConfigValue on " + site + ": " + e.getMessage(), e);
        }

        if (value != null) {
            return value;
        }

        if (annotation.defaultValue().equals(ConfigValue.DEFAULT_NONE)) {
            throw new ConfigException("@ConfigValue on " + site +
                    ": no value found for config path '" + combinedPath + "' and no defaultValue was provided");
        }

        try {
            return configManager.coerceDefault(annotation.defaultValue(), targetType);
        } catch (ConfigException e) {
            throw new ConfigException("@ConfigValue on " + site + ": cannot coerce defaultValue '" +
                    annotation.defaultValue() + "' to " + targetType.getName() + ": " + e.getMessage(), e);
        }
    }

    private @NotNull String buildPath(@NotNull ConfigValue annotation, @NotNull String site) {
        if (annotation.config() != Void.class) {
            if (annotation.path().isEmpty()) {
                throw new ConfigException("@ConfigValue on " + site + ": config() is set but path() is empty");
            }
            String configName;
            try {
                configName = configManager.getConfigName(annotation.config());
            } catch (ConfigException e) {
                throw new ConfigException("@ConfigValue on " + site + ": config class " +
                        annotation.config().getName() + " is not a registered @Config", e);
            }
            return configName + ":" + annotation.path();
        }
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        throw new ConfigException("@ConfigValue on " + site + ": must specify either value() or config()");
    }

    private @NotNull String describeSite(@NotNull InjectionPoint injectionPoint) {
        AnnotatedElement element = injectionPoint.getAnnotatedElement();
        if (element instanceof Field) {
            Field field = (Field) element;
            return field.getDeclaringClass().getName() + "#" + field.getName();
        }
        if (element instanceof Parameter) {
            Parameter parameter = (Parameter) element;
            return parameter.getDeclaringExecutable().getDeclaringClass().getName() + "#" +
                    parameter.getDeclaringExecutable().getName() + "(" + parameter.getName() + ")";
        }
        return element.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigValueResolverTest
```

Expected: **BUILD SUCCESS**, `Tests run: 10, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueResolver.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/injector/ConfigValueResolverTest.java
git commit -m "feat(config-spigot): add ConfigValueResolver"
```

---

### Task 4: `ConfigValueInjector` + DI end-to-end

**Files:**
- Create: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueInjector.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/injector/ConfigValueInjectorTest.java`

- [ ] **Step 1: Write the failing test**

Create `ConfigValueInjectorTest.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.test.injector;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigValueInjectorTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;
    private ConfigValueInjector injector;

    @BeforeEach
    void setUp() throws IOException {
        MethodHandlerRegistry.clear();
        Logger logger = Logger.getLogger(ConfigValueInjectorTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"), "name: hello\nport: 25565\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
        injector = new ConfigValueInjector(configManager);
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Test
    void supports_onlyWhenAnnotationPresent() throws NoSuchFieldException {
        assertTrue(injector.supports(InjectionPoint.fromField(FieldBean.class.getDeclaredField("name"))));
        assertFalse(injector.supports(InjectionPoint.fromField(FieldBean.class.getDeclaredField("untouched"))));
    }

    @Test
    void order_isMinus100() {
        assertEquals(-100, injector.getOrder());
    }

    @Test
    void resolve_returnsHandledValue() throws NoSuchFieldException {
        InjectionResult result = injector.resolve(InjectionPoint.fromField(FieldBean.class.getDeclaredField("name")));
        assertTrue(result.isHandled());
        assertEquals("hello", result.getValue());
    }

    @Test
    void di_injectsFieldWithoutInjectAnnotation() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(FieldBean.class, null, false, null, null);

        FieldBean bean = dm.resolveDependency(FieldBean.class, null);

        assertNotNull(bean);
        assertEquals("hello", bean.name);
        assertEquals(25565, bean.port);
        assertNull(bean.untouched);
    }

    @Test
    void di_injectsConstructorParameter() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(CtorBean.class, null, false, null, null);

        CtorBean bean = dm.resolveDependency(CtorBean.class, null);

        assertNotNull(bean);
        assertEquals("hello", bean.name);
    }

    @Test
    void di_requiredMissing_failsBeanCreation() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(RequiredMissingBean.class, null, false, null, null);

        assertThrows(RuntimeException.class, () -> dm.resolveDependency(RequiredMissingBean.class, null));
    }

    // ---- fixtures ----

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        public AppConfig() {}
    }

    static class FieldBean {
        @ConfigValue("app:name") String name;
        @ConfigValue("app:port") int port;
        String untouched;
    }

    static class CtorBean {
        final String name;
        CtorBean(@ConfigValue("app:name") String name) {
            this.name = name;
        }
    }

    static class RequiredMissingBean {
        @ConfigValue("app:missing") String missing;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigValueInjectorTest
```

Expected: **BUILD FAILURE** — `cannot find symbol: class ConfigValueInjector`.

- [ ] **Step 3: Create `ConfigValueInjector`**

Create `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueInjector.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.util.Objects;

/**
 * Custom injector that wires {@link ConfigValue} onto fields and constructor parameters of
 * DI-managed beans.
 * <p>
 * {@link #supports(InjectionPoint)} matches strictly on the presence of {@link ConfigValue} so it
 * never hijacks unrelated injection points. {@link #resolve(InjectionPoint)} always returns a
 * {@linkplain InjectionResult#handled(Object) handled} result (or throws) — it never returns
 * {@linkplain InjectionResult#notHandled() not-handled}, because that would let the framework fall
 * back to by-type bean resolution and silently mis-inject or null the field.
 */
public class ConfigValueInjector implements CustomInjector {

    private final ConfigValueResolver resolver;

    /**
     * Creates the injector.
     *
     * @param configManager the config manager, not null
     */
    public ConfigValueInjector(@NotNull SpigotConfigManager configManager) {
        Objects.requireNonNull(configManager, "configManager cannot be null");
        this.resolver = new ConfigValueResolver(configManager);
    }

    @Override
    public boolean supports(@NotNull InjectionPoint injectionPoint) {
        return injectionPoint.getAnnotatedElement().isAnnotationPresent(ConfigValue.class);
    }

    @Override
    public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
        return InjectionResult.handled(resolver.resolve(injectionPoint));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigValueInjectorTest
```

Expected: **BUILD SUCCESS**, `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/injector/ConfigValueInjector.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/injector/ConfigValueInjectorTest.java
git commit -m "feat(config-spigot): wire @ConfigValue via ConfigValueInjector"
```

---

### Task 5: Register the injector in `ConfigConfiguration`

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/configuration/ConfigConfiguration.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/configuration/ConfigConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

Create `ConfigConfigurationTest.java`:

```java
/* <MIT license header — copy verbatim from ConfigValue.java lines 1-22> */
package tech.guilhermekaua.spigotboot.config.spigot.test.configuration;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.configuration.ConfigConfiguration;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.DefaultCustomInjectorRegistry;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigConfigurationTest {

    @Mock
    Plugin plugin;

    @Test
    void configInjectors_registersConfigValueInjector() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger(ConfigConfigurationTest.class.getName()));
        SpigotConfigManager configManager = new SpigotConfigManager(plugin);

        CustomInjectorRegistryCustomizer customizer = new ConfigConfiguration().configInjectors(configManager);
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        customizer.customize(registry);

        assertTrue(registry.getInjectors().stream().anyMatch(i -> i instanceof ConfigValueInjector));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigConfigurationTest
```

Expected: **BUILD FAILURE** — `cannot find symbol: method configInjectors` (the bean is still named `folderConfigInjector`).

- [ ] **Step 3: Update `ConfigConfiguration`**

In `ConfigConfiguration.java`, add the import:

```java
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueInjector;
```

Then **replace** the `folderConfigInjector` bean method (lines ~30-36) with:

```java
    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(SpigotConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigConfigurationTest
```

Expected: **BUILD SUCCESS**, `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/configuration/ConfigConfiguration.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/configuration/ConfigConfigurationTest.java
git commit -m "feat(config-spigot): register ConfigValueInjector in ConfigConfiguration"
```

---

### Task 6: Reject `@ConfigValue` on `@Config`/`@FolderConfig` fields

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/registry/ConfigRegistry.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/registry/ConfigRegistryTest.java` *(add tests + fixtures)*

- [ ] **Step 1: Add the failing tests**

In `ConfigRegistryTest.java`, add the imports if missing:

```java
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
```

Add these two fixtures alongside the other `@Config` fixtures (after `InvalidConfigWithPublicField`):

```java
    @Config("config-with-configvalue.yml")
    public static class ConfigWithConfigValueField {
        @ConfigValue("other:x")
        private String injected;

        public ConfigWithConfigValueField() {
        }
    }

    @FolderConfig(name = "items_with_configvalue", folder = "items-with-configvalue")
    public static class FolderItemWithConfigValueField {
        @ConfigValue("other:x")
        private String injected;

        public FolderItemWithConfigValueField() {
        }
    }
```

Add these two test methods:

```java
    @Test
    void processConfigClass_whenConfigValueField_throwsConfigException() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configRegistry.processConfigClass(ConfigWithConfigValueField.class, context, configManager));
        assertTrue(ex.getMessage().contains("@ConfigValue"));
        assertTrue(ex.getMessage().contains("injected"));
    }

    @Test
    void processFolderConfigClass_whenConfigValueField_throwsConfigException() {
        SpigotConfigManager spigotConfigManager = mock(SpigotConfigManager.class);
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configRegistry.processFolderConfigClass(
                        FolderItemWithConfigValueField.class,
                        spigotConfigManager,
                        Logger.getLogger(ConfigRegistryTest.class.getName())));
        assertTrue(ex.getMessage().contains("@ConfigValue"));
        assertTrue(ex.getMessage().contains("injected"));
    }
```

Add the import for `Logger` if missing:

```java
import java.util.logging.Logger;
```

> Note: `processFolderConfigClass` normally swallows generic exceptions into a `logger.warning`, but it rethrows `ConfigException` (`catch (ConfigException e) { throw e; }`), so the guard's `ConfigException` propagates.

- [ ] **Step 2: Run tests to verify they fail**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigRegistryTest
```

Expected: **BUILD FAILURE** — the two new tests fail (no `ConfigException` thrown; the field is silently accepted).

- [ ] **Step 3: Add the guard to `ConfigRegistry`**

In `ConfigRegistry.java`, add the import:

```java
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
```

Add this private helper (e.g. just below `validateConfigClass`):

```java
    private void rejectConfigValueFields(Class<?> configClass) {
        for (Field field : configClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigValue.class)) {
                throw new ConfigException(
                        "Config class " + configClass.getName() + " has a @ConfigValue field '" +
                                field.getName() + "'. @ConfigValue is only honored on DI-managed beans " +
                                "(@Component/@Bean), not on @Config/@FolderConfig classes. Read the value " +
                                "via this config's own field instead."
                );
            }
        }
    }
```

Call it from `validateConfigClass` (append after the existing loop, inside the method, lines ~186-196):

```java
    private void validateConfigClass(Class<?> configClass) {
        for (Field field : configClass.getDeclaredFields()) {
            if (!Modifier.isPrivate(field.getModifiers())) {
                throw new ConfigException(
                        "Config class " + configClass.getName() +
                                " has non-private field '" + field.getName() + "'. " +
                                "All fields must be private to ensure reload safety."
                );
            }
        }
        rejectConfigValueFields(configClass);
    }
```

Call it at the start of `processFolderConfigClass` (immediately inside the `try`, before `getFolderConfigAnnotations`, lines ~148-149):

```java
        try {
            rejectConfigValueFields(itemClass);

            List<FolderConfig> annotations = getFolderConfigAnnotations(itemClass);
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test -Dtest=ConfigRegistryTest
```

Expected: **BUILD SUCCESS**, all `ConfigRegistryTest` tests pass (existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/registry/ConfigRegistry.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/registry/ConfigRegistryTest.java
git commit -m "feat(config-spigot): reject @ConfigValue on @Config and @FolderConfig fields"
```

---

### Task 7: Remove `@ConfigValue` from the unwired-API notes

**Files:**
- Modify: `config-spigot-unwired-api-notes.md`

- [ ] **Step 1: Delete the `@ConfigValue` section**

In `config-spigot-unwired-api-notes.md`, delete the entire section (the heading `## \`@ConfigValue\` (config/.../annotation/ConfigValue.java)` and its three body lines describing the missing injector — currently lines 9-13). Leave the file's intro and the remaining sections intact.

- [ ] **Step 2: Commit**

```bash
git add config-spigot-unwired-api-notes.md
git commit -m "docs: drop @ConfigValue from config-spigot unwired-api notes"
```

---

### Task 8: Full module build + verification

**Files:** none (verification only)

- [ ] **Step 1: Build and test the whole config-spigot module (with upstream deps)**

```bash
"$MVN" -q -pl platform-spigot/config-spigot -am test
```

Expected: **BUILD SUCCESS**. All config, core, and config-spigot tests pass — confirms the annotation change, the manager refactor, the new injector/resolver, the wiring, and the registry guard all integrate cleanly.

- [ ] **Step 2: Confirm the working tree is clean**

```bash
git status --short
```

Expected: no modified/untracked files from this work (everything committed across Tasks 1-7).

---

## Self-Review

**1. Spec coverage** (each spec section → task):
- Annotation changes (`value()` default, `DEFAULT_NONE`, Javadoc) → **Task 1**.
- `ConfigValueInjector` (strict `supports`, never `notHandled`, order −100) → **Task 4**.
- `ConfigValueResolver` (mode detection, precedence, not-initialized check, null-rawType, missing→throw, default coercion, site-wrapped errors) → **Task 3**.
- `SpigotConfigManager` helpers (`getConfigName`, `deserializeAt`, `coerceDefault`, navigation refactor) → **Task 2**.
- Registry-backed lookup/coercion + unsupported-type fail-fast → **Task 2** (helpers) + **Task 3** (`UnsupportedTypeBean`).
- Wiring in `ConfigConfiguration` (+ rename) → **Task 5**.
- `@Config`/`@FolderConfig` field guard → **Task 6**.
- Drop unwired-API note → **Task 7**.
- Snapshot / raw-node / bare-path behavior notes → encoded in Task 1 Javadoc + Task 3 tests (`barePathWithMultipleConfigs`, `notInitialized`). No code beyond what's planned.
- All spec test cases are present across Tasks 2-6.

**2. Placeholder scan:** No `TBD`/`TODO`/"add error handling"/"similar to Task N". The only abbreviation is the MIT license header, which is concrete known content with an exact source reference (ConfigValue.java lines 1-22), not a logic placeholder.

**3. Type consistency:** Method names/signatures are consistent across tasks — `getConfigName(Class<?>)→String`, `deserializeAt(String, Class<T>)→T`, `coerceDefault(String, Class<T>)→T` (defined Task 2, called Task 3); `ConfigValueResolver(SpigotConfigManager)` + `resolve(InjectionPoint)→Object` (defined Task 3, used Task 4); `ConfigValueInjector(SpigotConfigManager)` (defined Task 4, used Task 5); bean renamed to `configInjectors` (Task 5) matches its test. `ConfigValue.DEFAULT_NONE` referenced identically in Tasks 1 and 3. Test fixture `AppConfig` is re-declared per test class (nested static) so there's no cross-file coupling. DI calls (`registerInjector`, `registerDependency(Class,null,false,null,null)`, `resolveDependency(Class,null)`) match the signatures used by the existing `CustomInjectorTest`.

No issues found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-13-configvalue-injector.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
