# Wire `@OnConfigReload` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `@OnConfigReload` actually fire — scan DI beans for annotated methods and register them as listeners on the matching `ConfigRef`/`FolderConfigRef`, for both simple `@Config` and folder configs.

**Architecture:** Add a reusable core `BeanPostProcessor` extension point (mirroring the existing `CustomInjectorRegistry` factory). In `config-spigot`, an `OnConfigReloadProcessor` (a `BeanPostProcessor`) scans each bean's real class for `@OnConfigReload` methods and an `OnConfigReloadBinder` registers reload listeners on the config manager. Registered from `ConfigConfiguration` via a `BeanPostProcessorRegistryCustomizer` bean. A `ConfigRegistry` guard rejects the annotation on config POJOs.

**Tech Stack:** Java 21, Maven (multi-module), JUnit 5, Mockito, Lombok. Spec: `docs/superpowers/specs/2026-06-13-wire-onconfigreload-design.md`.

---

## Conventions for this plan

- **License header:** every NEW `.java` file must begin with the project's standard MIT license header. Copy it verbatim from any sibling source file (e.g. `core/.../postprocessor/BeanPostProcessor.java`). Code blocks below omit it for brevity — do not skip it in the actual file.
- **Maven runner (Windows, path has spaces):** use the cached binary. Define once per shell:
  ```bash
  MVN="/c/Users/OTI Software/.m2/wrapper/dists/apache-maven-3.9.9/3477a4f1/bin/mvn"
  ROOT="/c/Users/OTI Software/IdeaProjects/spigot-boot/.claude/worktrees/wire-onconfigreload"
  cd "$ROOT"
  ```
- **Run one test class across the reactor** (builds upstream modules from source so cross-module changes are seen; `-DfailIfNoTests=false` stops upstream modules from failing when they lack the class):
  ```bash
  "$MVN" -pl <module> -am test -Dtest=<ClassName> -DfailIfNoTests=false -B
  ```
- Commit after each task. Conventional Commit prefixes (`feat:`, `test:`, `docs:`). Normal comments start lowercase. 4-space indent. Full Javadoc on public/protected APIs.

## File structure

**core (new extension point):**
- Create `core/.../context/dependency/postprocessor/BeanPostProcessorRegistry.java` — narrow registration interface.
- Create `core/.../context/dependency/postprocessor/BeanPostProcessorRegistryCustomizer.java` — module-facing customizer.
- Create `core/.../context/dependency/postprocessor/BeanPostProcessorRegistryFactory.java` — applies customizers in DEFINITIONS_READY.
- Modify `core/.../context/dependency/manager/DependencyManager.java` — `implements BeanPostProcessorRegistry`, add `register(...)` + `getBeanPostProcessorRegistry()`.

**config-spigot (the feature):**
- Modify `platform-spigot/config-spigot/.../config/spigot/SpigotConfigManager.java` — add `getRegisteredFolderConfigItemTypes()`.
- Create `platform-spigot/config-spigot/.../config/spigot/reload/OnConfigReloadInvoker.java` — reflective invoke + error logging.
- Create `platform-spigot/config-spigot/.../config/spigot/reload/OnConfigReloadBinder.java` — validate + resolve targets + register listeners.
- Create `platform-spigot/config-spigot/.../config/spigot/reload/OnConfigReloadProcessor.java` — the `BeanPostProcessor`.
- Modify `platform-spigot/config-spigot/.../config/spigot/configuration/ConfigConfiguration.java` — register the processor.
- Modify `platform-spigot/config-spigot/.../config/spigot/registry/ConfigRegistry.java` — reject `@OnConfigReload` on config POJOs.

**config (docs):** Modify `config/.../config/annotation/OnConfigReload.java` Javadoc.

**wiki (submodule) + repo dev-note:** 3 wiki pages + `config-spigot-unwired-api-notes.md`.

---

## Task 1: Core — `BeanPostProcessor` registration extension point

**Files:**
- Create: `core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistry.java`
- Create: `core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistryCustomizer.java`
- Modify: `core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/manager/DependencyManager.java:50` (class decl) and near `:103` (add methods)
- Test: `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/postprocessor/BeanPostProcessorRegistryTest.java`

- [ ] **Step 1: Write the failing test**

`BeanPostProcessorRegistryTest.java`:
```java
package tech.guilhermekaua.spigotboot.core.test.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeanPostProcessorRegistryTest {

    private static BeanPostProcessor noop(int order) {
        return new BeanPostProcessor() {
            @Override
            public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                               @NotNull Object instance,
                                               @NotNull DependencyManager dependencyManager) {
                return instance;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @Test
    void getBeanPostProcessorRegistry_registersAndSortsByOrder() {
        DependencyManager dm = new DependencyManager();
        BeanPostProcessorRegistry registry = dm.getBeanPostProcessorRegistry();

        BeanPostProcessor high = noop(100);
        BeanPostProcessor low = noop(-100);
        registry.register(high);
        registry.register(low);

        List<BeanPostProcessor> processors = dm.getBeanPostProcessors();
        // the constructor already registers MethodHandlerProxyBeanPostProcessor (order 0)
        assertTrue(processors.contains(high));
        assertTrue(processors.contains(low));
        // sorted ascending: low(-100) must come before high(100)
        assertTrue(processors.indexOf(low) < processors.indexOf(high));
    }

    @Test
    void register_rejectsNull() {
        DependencyManager dm = new DependencyManager();
        assertThrows(NullPointerException.class, () -> dm.getBeanPostProcessorRegistry().register(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl core -am test -Dtest=BeanPostProcessorRegistryTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `BeanPostProcessorRegistry` does not exist / `getBeanPostProcessorRegistry()` not found.

- [ ] **Step 3: Create `BeanPostProcessorRegistry`**

```java
package tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;

/**
 * Registration surface for {@link BeanPostProcessor} instances.
 * <p>
 * Exposed to modules through {@link BeanPostProcessorRegistryCustomizer} so they can contribute
 * post-processors without depending on the whole dependency manager.
 */
public interface BeanPostProcessorRegistry {

    /**
     * Registers a post-processor. Implementations keep processors ordered by
     * {@link BeanPostProcessor#getOrder()}.
     *
     * @param beanPostProcessor the post-processor to register, not null
     */
    void register(@NotNull BeanPostProcessor beanPostProcessor);
}
```

- [ ] **Step 4: Create `BeanPostProcessorRegistryCustomizer`**

```java
package tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

/**
 * Callback interface for contributing {@link BeanPostProcessor} instances to the framework.
 * <p>
 * Provide beans of this type via {@code @Configuration} classes with {@code @Bean} methods to
 * register custom post-processors during context initialization. Customizers are applied in the
 * DEFINITIONS_READY phase, before bean instantiation begins. Implement {@link Ordered#getOrder()}
 * to control application order; lower values are applied first.
 *
 * @see BeanPostProcessorRegistry
 * @see BeanPostProcessor
 */
@FunctionalInterface
public interface BeanPostProcessorRegistryCustomizer extends Ordered {

    /**
     * Customize the given registry by registering post-processors.
     *
     * @param registry the registry to customize, not null
     */
    void customize(@NotNull BeanPostProcessorRegistry registry);
}
```

- [ ] **Step 5: Wire `DependencyManager`**

Change the class declaration at `DependencyManager.java:50` from:
```java
public class DependencyManager {
```
to:
```java
public class DependencyManager implements BeanPostProcessorRegistry {
```

Add this import near the other `...postprocessor` imports:
```java
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistry;
```

Immediately AFTER the existing `getBeanPostProcessors()` method (ends at `DependencyManager.java:105`), add:
```java
    @Override
    public void register(@NotNull BeanPostProcessor beanPostProcessor) {
        registerBeanPostProcessor(beanPostProcessor);
    }

    public @NotNull BeanPostProcessorRegistry getBeanPostProcessorRegistry() {
        return this;
    }
```

(`register` delegates to the existing `registerBeanPostProcessor`, which null-checks and re-sorts by order.)

- [ ] **Step 6: Run the test to verify it passes**

Run: `"$MVN" -pl core -am test -Dtest=BeanPostProcessorRegistryTest -DfailIfNoTests=false -B`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistry.java \
        core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistryCustomizer.java \
        core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/manager/DependencyManager.java \
        core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/postprocessor/BeanPostProcessorRegistryTest.java
git commit -m "feat(core): add BeanPostProcessor registration extension point"
```

---

## Task 2: Core — `BeanPostProcessorRegistryFactory`

Mirrors `CustomInjectorRegistryFactory`: in DEFINITIONS_READY, resolve all `BeanPostProcessorRegistryCustomizer` beans and apply them to the registry.

**Files:**
- Create: `core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistryFactory.java`
- Test: `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/postprocessor/BeanPostProcessorRegistryFactoryTest.java`

- [ ] **Step 1: Write the failing test** (mirrors `CustomInjectorRegistryFactoryTest`)

```java
package tech.guilhermekaua.spigotboot.core.test.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryFactory;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BeanPostProcessorRegistryFactoryTest {
    private DependencyManager dependencyManager;
    private BeanDefinitionRegistry definitionRegistry;
    private Context mockContext;
    private BeanRegistrar mockRegistrar;

    private static BeanPostProcessor noop(int order) {
        return new BeanPostProcessor() {
            @Override
            public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                               @NotNull Object instance,
                                               @NotNull DependencyManager dm) {
                return instance;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        definitionRegistry = dependencyManager.getBeanDefinitionRegistry();
        mockContext = mock(Context.class);
        mockRegistrar = mock(BeanRegistrar.class);

        BootPlugin plugin = mock(BootPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(mockContext.getDependencyManager()).thenReturn(dependencyManager);
        when(mockContext.getPlugin()).thenReturn(plugin);
    }

    @Test
    void appliesCustomizersInOrderAndRegistersProcessors() {
        List<String> callOrder = new ArrayList<>();
        BeanPostProcessor markerLow = noop(5);
        BeanPostProcessor markerHigh = noop(6);

        BeanPostProcessorRegistryCustomizer first = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                callOrder.add("first");
                registry.register(markerLow);
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };
        BeanPostProcessorRegistryCustomizer second = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                callOrder.add("second");
                registry.register(markerHigh);
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(second, "second", false);
        dependencyManager.registerDependency(first, "first", false);

        new BeanPostProcessorRegistryFactory().onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);

        assertEquals(List.of("first", "second"), callOrder);
        assertTrue(dependencyManager.getBeanPostProcessors().contains(markerLow));
        assertTrue(dependencyManager.getBeanPostProcessors().contains(markerHigh));
    }

    @Test
    void continuesWhenCustomizerThrows() {
        BeanPostProcessor marker = noop(7);
        BeanPostProcessorRegistryCustomizer failing = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                throw new RuntimeException("boom");
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };
        BeanPostProcessorRegistryCustomizer succeeding = new BeanPostProcessorRegistryCustomizer() {
            @Override
            public void customize(@NotNull BeanPostProcessorRegistry registry) {
                registry.register(marker);
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(failing, "failing", false);
        dependencyManager.registerDependency(succeeding, "succeeding", false);

        BeanPostProcessorRegistryFactory factory = new BeanPostProcessorRegistryFactory();
        assertDoesNotThrow(() -> factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar));
        assertTrue(dependencyManager.getBeanPostProcessors().contains(marker));
    }

    @Test
    void emptyIsNoOp() {
        int before = dependencyManager.getBeanPostProcessors().size();
        new BeanPostProcessorRegistryFactory().onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);
        assertEquals(before, dependencyManager.getBeanPostProcessors().size());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl core -am test -Dtest=BeanPostProcessorRegistryFactoryTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `BeanPostProcessorRegistryFactory` does not exist.

- [ ] **Step 3: Create `BeanPostProcessorRegistryFactory`** (near-copy of `CustomInjectorRegistryFactory`)

```java
package tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.BeanDefinitionsReadyListener;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies {@link BeanPostProcessorRegistryCustomizer} beans to the framework's
 * {@link BeanPostProcessorRegistry} during the DEFINITIONS_READY phase, before instantiation begins,
 * so contributed post-processors run on every bean.
 *
 * @see BeanPostProcessorRegistryCustomizer
 */
@Component
public class BeanPostProcessorRegistryFactory implements BeanDefinitionsReadyListener, Ordered {

    private static final int ORDER = -1000;

    @Override
    public void onBeanDefinitionsReady(@NotNull Context context,
                                       @NotNull BeanDefinitionRegistry definitionRegistry,
                                       @NotNull BeanRegistrar registrar) {
        DependencyManager dependencyManager = context.getDependencyManager();
        BeanPostProcessorRegistry registry = dependencyManager.getBeanPostProcessorRegistry();
        Logger logger = context.getPlugin().getLogger();

        List<BeanPostProcessorRegistryCustomizer> customizers = resolveCustomizers(dependencyManager, definitionRegistry, logger);
        if (customizers.isEmpty()) {
            return;
        }

        customizers.sort(Comparator.comparingInt(Ordered::getOrder));
        applyCustomizers(customizers, registry, logger);
    }

    private @NotNull List<BeanPostProcessorRegistryCustomizer> resolveCustomizers(
            @NotNull DependencyManager dependencyManager,
            @NotNull BeanDefinitionRegistry definitionRegistry,
            @NotNull Logger logger) {
        List<BeanDefinition> definitions = definitionRegistry.getDefinitions(BeanPostProcessorRegistryCustomizer.class);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<BeanPostProcessorRegistryCustomizer> customizers = new ArrayList<>(definitions.size());
        for (BeanDefinition definition : definitions) {
            try {
                BeanPostProcessorRegistryCustomizer customizer = dependencyManager.resolveDependency(
                        BeanPostProcessorRegistryCustomizer.class,
                        definition.getQualifierName()
                );
                if (customizer != null) {
                    customizers.add(customizer);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to resolve BeanPostProcessorRegistryCustomizer: " + definition.identifier(), e);
            }
        }
        return customizers;
    }

    private void applyCustomizers(@NotNull List<BeanPostProcessorRegistryCustomizer> customizers,
                                  @NotNull BeanPostProcessorRegistry registry,
                                  @NotNull Logger logger) {
        for (BeanPostProcessorRegistryCustomizer customizer : customizers) {
            try {
                customizer.customize(registry);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error applying BeanPostProcessorRegistryCustomizer: " + customizer.getClass().getName(), e);
            }
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl core -am test -Dtest=BeanPostProcessorRegistryFactoryTest -DfailIfNoTests=false -B`
Expected: PASS (3 tests).

- [ ] **Step 5: Install core so downstream modules see the new API**

Run: `"$MVN" -pl core -am install -DskipTests -B`
Expected: BUILD SUCCESS (publishes updated `core` to local `~/.m2`).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/tech/guilhermekaua/spigotboot/core/context/dependency/postprocessor/BeanPostProcessorRegistryFactory.java \
        core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/postprocessor/BeanPostProcessorRegistryFactoryTest.java
git commit -m "feat(core): apply BeanPostProcessorRegistryCustomizer beans in DEFINITIONS_READY"
```

---

## Task 3: config-spigot — enumerate folder-config item types

`getRegisteredConfigs()` lists only simple configs; the "fire on any config" zero-arg case needs folder item types too.

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/SpigotConfigManager.java` (add method next to `getAllFolderConfigNames()` ~`:946`)
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/manager/SpigotConfigManagerFolderItemTypesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.config.spigot.test.manager;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SpigotConfigManagerFolderItemTypesTest {

    @TempDir
    Path tempDir;
    @Mock
    Plugin plugin;
    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(SpigotConfigManagerFolderItemTypesTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
    }

    @Test
    void getRegisteredFolderConfigItemTypes_emptyWhenNoneRegistered() {
        Set<Class<?>> types = configManager.getRegisteredFolderConfigItemTypes();
        assertTrue(types.isEmpty());
    }
}
```

(This proves the method exists and returns an empty set with no folder configs; population is covered indirectly by the binder tests via mocks.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=SpigotConfigManagerFolderItemTypesTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — method `getRegisteredFolderConfigItemTypes()` not found.

- [ ] **Step 3: Add the method**

Insert immediately AFTER `getAllFolderConfigNames()` (ends ~`SpigotConfigManager.java:952`):
```java
    /**
     * Returns the distinct item types across all registered folder configs.
     *
     * @return an unmodifiable set of folder-config item types, never null
     */
    public @NotNull Set<Class<?>> getRegisteredFolderConfigItemTypes() {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (FolderConfigKey key : folderConfigs.keySet()) {
            types.add(key.itemType);
        }
        return Collections.unmodifiableSet(types);
    }
```

(`LinkedHashSet`, `Collections`, `Set` are already imported in this file — confirmed by the existing `getFolderConfigNames`/`getAllFolderConfigNames` methods. `key.itemType` is the same package-private field access those methods use.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=SpigotConfigManagerFolderItemTypesTest -DfailIfNoTests=false -B`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/SpigotConfigManager.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/manager/SpigotConfigManagerFolderItemTypesTest.java
git commit -m "feat(config-spigot): expose registered folder-config item types"
```

---

## Task 4: config-spigot — `OnConfigReloadInvoker`

Reflective invoke with error isolation (logs, never throws out of a reload listener).

**Files:**
- Create: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadInvoker.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadInvokerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.config.spigot.test.reload;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadInvoker;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class OnConfigReloadInvokerTest {

    static class Bean {
        Object received = "unset";
        boolean noArgCalled = false;

        private void withArg(String value) {
            this.received = value;
        }

        void noArg() {
            this.noArgCalled = true;
        }

        void boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    private final OnConfigReloadInvoker invoker = new OnConfigReloadInvoker(Logger.getLogger("test"));

    @Test
    void invokesPrivateMethodWithArg() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("withArg", String.class);
        invoker.invoke(bean, m, new Object[]{"hello"});
        assertEquals("hello", bean.received);
    }

    @Test
    void invokesNoArgMethod() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("noArg");
        invoker.invoke(bean, m, new Object[0]);
        assertTrue(bean.noArgCalled);
    }

    @Test
    void swallowsAndLogsCallbackException() throws Exception {
        Bean bean = new Bean();
        Method m = Bean.class.getDeclaredMethod("boom");
        // must not propagate — a throwing callback cannot break the reload listener loop
        assertDoesNotThrow(() -> invoker.invoke(bean, m, new Object[0]));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadInvokerTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `OnConfigReloadInvoker` does not exist.

- [ ] **Step 3: Create `OnConfigReloadInvoker`**

```java
package tech.guilhermekaua.spigotboot.config.spigot.reload;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Invokes {@code @OnConfigReload} callback methods reflectively, isolating any failure so it never
 * escapes a config reload listener.
 */
public class OnConfigReloadInvoker {

    private final Logger logger;

    /**
     * Creates the invoker.
     *
     * @param logger the plugin logger, not null
     */
    public OnConfigReloadInvoker(@NotNull Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Invokes the given callback. Exceptions thrown by the callback are logged, not rethrown.
     *
     * @param bean   the bean instance to invoke on, not null
     * @param method the callback method, not null
     * @param args   the arguments to pass, not null (may be empty)
     */
    public void invoke(@NotNull Object bean, @NotNull Method method, @NotNull Object[] args) {
        try {
            method.setAccessible(true);
            method.invoke(bean, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.log(Level.SEVERE, describe(bean, method), cause);
        } catch (Exception e) {
            logger.log(Level.SEVERE, describe(bean, method), e);
        }
    }

    private static String describe(Object bean, Method method) {
        return "Error invoking @OnConfigReload method " + method.getName()
                + " on bean " + bean.getClass().getName();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadInvokerTest -DfailIfNoTests=false -B`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadInvoker.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadInvokerTest.java
git commit -m "feat(config-spigot): add OnConfigReloadInvoker"
```

---

## Task 5: config-spigot — `OnConfigReloadBinder`

The core logic: validate a `@OnConfigReload` method, resolve its target config(s) and payload, and register reload listeners on the manager. Tested against a mocked `SpigotConfigManager` with `ArgumentCaptor` on the listeners.

**Files:**
- Create: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadBinder.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadBinderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.config.spigot.test.reload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigChangeListener;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigRef;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigSnapshot;
import tech.guilhermekaua.spigotboot.config.folder.ItemChangeType;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadBinder;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadInvoker;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnConfigReloadBinderTest {

    // ---- fixtures ----
    static class MainConfig { }
    static class Mob { }

    static class SimpleBean {
        Object received = "unset";
        int noArgCount = 0;

        @OnConfigReload(MainConfig.class)
        void onReloadNoArg() { noArgCount++; }

        @OnConfigReload
        void onReloadWithArg(MainConfig cfg) { received = cfg; }
    }

    static class FolderBean {
        FolderConfigItemChange<Mob> change;
        FolderConfigSnapshot<Mob> snapshot;

        @OnConfigReload
        void onChange(FolderConfigItemChange<Mob> change) { this.change = change; }

        @OnConfigReload
        void onSnapshot(FolderConfigSnapshot<Mob> snapshot) { this.snapshot = snapshot; }
    }

    static class BadBean {
        @OnConfigReload
        void twoParams(MainConfig a, MainConfig b) { }

        @OnConfigReload
        String nonVoid() { return ""; }

        @OnConfigReload(MainConfig.class)
        void folderPayloadOnSimpleTarget(FolderConfigItemChange<Mob> change) { }

        @OnConfigReload(Mob.class)
        void simplePayloadOnFolderTarget(Mob mob) { }

        @OnConfigReload(String.class)
        void unregisteredTarget() { }
    }

    private SpigotConfigManager cm;
    private OnConfigReloadBinder binder;

    @BeforeEach
    void setUp() {
        cm = mock(SpigotConfigManager.class);
        when(cm.getRegisteredConfigs()).thenReturn(Set.<Class<?>>of(MainConfig.class));
        when(cm.getRegisteredFolderConfigItemTypes()).thenReturn(Set.<Class<?>>of(Mob.class));
        when(cm.getFolderConfigNames(Mob.class)).thenReturn(Set.of("mobs"));
        binder = new OnConfigReloadBinder(cm, new OnConfigReloadInvoker(Logger.getLogger("test")));
    }

    private static Method method(Class<?> type, String name, Class<?>... params) throws Exception {
        return type.getDeclaredMethod(name, params);
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleInstanceMethodReceivesNewValue() throws Exception {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        SimpleBean bean = new SimpleBean();
        binder.bind(bean, method(SimpleBean.class, "onReloadWithArg", MainConfig.class));

        ArgumentCaptor<Consumer<MainConfig>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(ref).addListener(captor.capture());

        MainConfig fresh = new MainConfig();
        captor.getValue().accept(fresh);
        assertSame(fresh, bean.received);
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleNoArgMethodFiresOnReload() throws Exception {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        SimpleBean bean = new SimpleBean();
        binder.bind(bean, method(SimpleBean.class, "onReloadNoArg"));

        ArgumentCaptor<Consumer<MainConfig>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(ref).addListener(captor.capture());
        captor.getValue().accept(new MainConfig());
        assertEquals(1, bean.noArgCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    void folderChangeMethodReceivesChange() throws Exception {
        FolderConfigRef<Mob> ref = mock(FolderConfigRef.class);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(ref);

        FolderBean bean = new FolderBean();
        binder.bind(bean, method(FolderBean.class, "onChange", FolderConfigItemChange.class));

        ArgumentCaptor<FolderConfigChangeListener<Mob>> captor = ArgumentCaptor.forClass(FolderConfigChangeListener.class);
        verify(ref).addListener(captor.capture());

        FolderConfigItemChange<Mob> change = new FolderConfigItemChange<>(
                "mobs", Mob.class, "zombie", ItemChangeType.MODIFIED, new Mob(), new Mob());
        captor.getValue().onItemChange(change);
        assertSame(change, bean.change);
    }

    @Test
    @SuppressWarnings("unchecked")
    void folderSnapshotMethodReceivesCurrentSnapshot() throws Exception {
        FolderConfigRef<Mob> ref = mock(FolderConfigRef.class);
        FolderConfigSnapshot<Mob> snapshot = mock(FolderConfigSnapshot.class);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(ref);
        when(ref.get()).thenReturn(snapshot);

        FolderBean bean = new FolderBean();
        binder.bind(bean, method(FolderBean.class, "onSnapshot", FolderConfigSnapshot.class));

        ArgumentCaptor<FolderConfigChangeListener<Mob>> captor = ArgumentCaptor.forClass(FolderConfigChangeListener.class);
        verify(ref).addListener(captor.capture());

        captor.getValue().onItemChange(new FolderConfigItemChange<>(
                "mobs", Mob.class, "zombie", ItemChangeType.ADDED, null, new Mob()));
        assertSame(snapshot, bean.snapshot);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyValueZeroArgRegistersOnEverySimpleAndFolderConfig() throws Exception {
        ConfigRef<MainConfig> simpleRef = mock(ConfigRef.class);
        FolderConfigRef<Mob> folderRef = mock(FolderConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(simpleRef);
        when(cm.getFolderConfigRef(Mob.class, "mobs")).thenReturn(folderRef);

        class AnyBean {
            @OnConfigReload
            void onAny() { }
        }

        binder.bind(new AnyBean(), method(AnyBean.class, "onAny"));

        verify(simpleRef).addListener(any());
        verify(folderRef).addListener(any());
    }

    @Test
    void rejectsTwoParameters() {
        BadBean bean = new BadBean();
        assertThrows(ConfigException.class,
                () -> binder.bind(bean, method(BadBean.class, "twoParams", MainConfig.class, MainConfig.class)));
    }

    @Test
    void rejectsNonVoid() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "nonVoid")));
    }

    @Test
    void rejectsFolderPayloadOnSimpleTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "folderPayloadOnSimpleTarget", FolderConfigItemChange.class)));
    }

    @Test
    void rejectsSimplePayloadOnFolderTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "simplePayloadOnFolderTarget", Mob.class)));
    }

    @Test
    void rejectsUnregisteredTarget() {
        assertThrows(ConfigException.class,
                () -> binder.bind(new BadBean(), method(BadBean.class, "unregisteredTarget")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadBinderTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `OnConfigReloadBinder` does not exist.

- [ ] **Step 3: Create `OnConfigReloadBinder`**

```java
package tech.guilhermekaua.spigotboot.config.spigot.reload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigRef;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigSnapshot;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a single {@code @OnConfigReload} method and registers the reload listener(s) it implies
 * on the {@link SpigotConfigManager}.
 */
public class OnConfigReloadBinder {

    private static final Object[] NO_ARGS = new Object[0];

    private enum PayloadType { NONE, SIMPLE_INSTANCE, FOLDER_CHANGE, FOLDER_SNAPSHOT }

    private enum ConfigKind { SIMPLE, FOLDER }

    private final SpigotConfigManager configManager;
    private final OnConfigReloadInvoker invoker;

    /**
     * Creates the binder.
     *
     * @param configManager the config manager, not null
     * @param invoker       the reflective invoker, not null
     */
    public OnConfigReloadBinder(@NotNull SpigotConfigManager configManager, @NotNull OnConfigReloadInvoker invoker) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker cannot be null");
    }

    /**
     * Validates the method and registers reload listeners for every target config it declares.
     *
     * @param bean   the owning bean instance, not null
     * @param method a method annotated with {@link OnConfigReload}, not null
     * @throws ConfigException if the method signature or targets are invalid
     */
    public void bind(@NotNull Object bean, @NotNull Method method) {
        validateSignature(bean, method);

        PayloadType payload = resolvePayloadType(method);
        Class<?> paramItemType = (payload == PayloadType.FOLDER_CHANGE || payload == PayloadType.FOLDER_SNAPSHOT)
                ? extractItemType(method) : null;
        Class<?> simpleParamType = (payload == PayloadType.SIMPLE_INSTANCE)
                ? method.getParameterTypes()[0] : null;

        Set<Class<?>> targets = resolveTargets(bean, method, payload, simpleParamType, paramItemType);

        for (Class<?> target : targets) {
            ConfigKind kind = configKind(target);
            if (kind == null) {
                throw fail(bean, method, "targets unregistered config " + target.getName());
            }
            validatePayloadForKind(bean, method, payload, kind, target, simpleParamType, paramItemType);
            if (kind == ConfigKind.SIMPLE) {
                bindSimple(bean, method, payload, target);
            } else {
                bindFolder(bean, method, payload, target);
            }
        }
    }

    private void validateSignature(Object bean, Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw fail(bean, method, "must be a non-static instance method");
        }
        if (!void.class.equals(method.getReturnType())) {
            throw fail(bean, method, "must return void");
        }
        if (method.getParameterCount() > 1) {
            throw fail(bean, method, "must declare zero or one parameter");
        }
    }

    private PayloadType resolvePayloadType(Method method) {
        if (method.getParameterCount() == 0) {
            return PayloadType.NONE;
        }
        Class<?> p = method.getParameterTypes()[0];
        if (FolderConfigItemChange.class.equals(p)) {
            return PayloadType.FOLDER_CHANGE;
        }
        if (FolderConfigSnapshot.class.equals(p)) {
            return PayloadType.FOLDER_SNAPSHOT;
        }
        return PayloadType.SIMPLE_INSTANCE;
    }

    private Set<Class<?>> resolveTargets(Object bean, Method method, PayloadType payload,
                                         @Nullable Class<?> simpleParamType, @Nullable Class<?> paramItemType) {
        Class<?>[] declared = method.getAnnotation(OnConfigReload.class).value();
        if (declared.length > 0) {
            return new LinkedHashSet<>(java.util.Arrays.asList(declared));
        }
        switch (payload) {
            case SIMPLE_INSTANCE:
                return Set.of(simpleParamType);
            case FOLDER_CHANGE:
            case FOLDER_SNAPSHOT:
                if (paramItemType == null) {
                    throw fail(bean, method, "cannot infer the folder item type from a raw parameter; specify value()");
                }
                return Set.of(paramItemType);
            case NONE:
            default:
                Set<Class<?>> all = new LinkedHashSet<>(configManager.getRegisteredConfigs());
                all.addAll(configManager.getRegisteredFolderConfigItemTypes());
                return all;
        }
    }

    private @Nullable ConfigKind configKind(Class<?> target) {
        if (configManager.getRegisteredConfigs().contains(target)) {
            return ConfigKind.SIMPLE;
        }
        if (configManager.getRegisteredFolderConfigItemTypes().contains(target)) {
            return ConfigKind.FOLDER;
        }
        return null;
    }

    private void validatePayloadForKind(Object bean, Method method, PayloadType payload, ConfigKind kind,
                                        Class<?> target, @Nullable Class<?> simpleParamType, @Nullable Class<?> paramItemType) {
        if (kind == ConfigKind.SIMPLE) {
            if (payload == PayloadType.FOLDER_CHANGE || payload == PayloadType.FOLDER_SNAPSHOT) {
                throw fail(bean, method, "simple config " + target.getName()
                        + " cannot be received as FolderConfigItemChange/FolderConfigSnapshot");
            }
            if (payload == PayloadType.SIMPLE_INSTANCE && !simpleParamType.isAssignableFrom(target)) {
                throw fail(bean, method, "parameter type " + simpleParamType.getName()
                        + " is not assignable from config " + target.getName());
            }
        } else {
            if (payload == PayloadType.SIMPLE_INSTANCE) {
                throw fail(bean, method, "folder config " + target.getName()
                        + " must be received via no parameter, FolderConfigItemChange<" + target.getSimpleName()
                        + "> or FolderConfigSnapshot<" + target.getSimpleName() + ">");
            }
            if (paramItemType != null && !paramItemType.equals(target)) {
                throw fail(bean, method, "parameter item type " + paramItemType.getName()
                        + " does not match folder config " + target.getName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void bindSimple(Object bean, Method method, PayloadType payload, Class<?> target) {
        ConfigRef<Object> ref = (ConfigRef<Object>) configManager.getRef((Class<Object>) target);
        ref.addListener(newValue -> {
            Object[] args = (payload == PayloadType.NONE) ? NO_ARGS : new Object[]{newValue};
            invoker.invoke(bean, method, args);
        });
    }

    @SuppressWarnings("unchecked")
    private void bindFolder(Object bean, Method method, PayloadType payload, Class<?> target) {
        for (String name : configManager.getFolderConfigNames(target)) {
            FolderConfigRef<Object> ref =
                    (FolderConfigRef<Object>) configManager.getFolderConfigRef((Class<Object>) target, name);
            ref.addListener(change -> {
                Object[] args;
                switch (payload) {
                    case FOLDER_CHANGE:
                        args = new Object[]{change};
                        break;
                    case FOLDER_SNAPSHOT:
                        args = new Object[]{ref.get()};
                        break;
                    case NONE:
                        args = NO_ARGS;
                        break;
                    default:
                        return;
                }
                invoker.invoke(bean, method, args);
            });
        }
    }

    private static @Nullable Class<?> extractItemType(Method method) {
        Type t = method.getGenericParameterTypes()[0];
        if (t instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) t).getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class) {
                return (Class<?>) args[0];
            }
        }
        return null;
    }

    private static ConfigException fail(Object bean, Method method, String reason) {
        return new ConfigException("@OnConfigReload method " + method.getName()
                + " on " + bean.getClass().getName() + " " + reason);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadBinderTest -DfailIfNoTests=false -B`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadBinder.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadBinderTest.java
git commit -m "feat(config-spigot): add OnConfigReloadBinder (target/payload resolution + validation)"
```

---

## Task 6: config-spigot — `OnConfigReloadProcessor`

The `BeanPostProcessor` that scans each bean and delegates to the binder. Runs after the proxy post-processor (order > 0).

**Files:**
- Create: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadProcessor.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadProcessorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.config.spigot.test.reload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnConfigReloadProcessorTest {

    static class MainConfig { }

    static class Base {
        // public so the getMethods()-based scan (inherited public + own declared) finds it
        @OnConfigReload(MainConfig.class)
        public void inherited() { }
    }

    static class Bean extends Base {
        @OnConfigReload(MainConfig.class)
        void own() { }
    }

    static class Plain { }

    private SpigotConfigManager cm;
    private OnConfigReloadProcessor processor;

    @BeforeEach
    void setUp() {
        cm = mock(SpigotConfigManager.class);
        when(cm.getRegisteredConfigs()).thenReturn(Set.<Class<?>>of(MainConfig.class));
        when(cm.getRegisteredFolderConfigItemTypes()).thenReturn(Set.<Class<?>>of());
        processor = new OnConfigReloadProcessor(cm, Logger.getLogger("test"));
    }

    @Test
    void runsAfterProxyProcessor() {
        assertTrue(processor.getOrder() > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registersListenersForOwnAndInheritedMethodsAndReturnsSameInstance() {
        ConfigRef<MainConfig> ref = mock(ConfigRef.class);
        when(cm.getRef(MainConfig.class)).thenReturn(ref);

        Bean bean = new Bean();
        BeanDefinition def = mock(BeanDefinition.class);
        DependencyManager dm = mock(DependencyManager.class);

        Object result = processor.postProcess(def, bean, dm);

        assertSame(bean, result);
        // one binding for own() + one for inherited() = 2 listeners on the same ref
        verify(ref, times(2)).addListener(any());
    }

    @Test
    void ignoresBeansWithoutAnnotatedMethods() {
        Plain plain = new Plain();
        Object result = processor.postProcess(mock(BeanDefinition.class), plain, mock(DependencyManager.class));
        assertSame(plain, result);
        verify(cm, never()).getRef(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadProcessorTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `OnConfigReloadProcessor` does not exist.

- [ ] **Step 3: Create `OnConfigReloadProcessor`**

```java
package tech.guilhermekaua.spigotboot.config.spigot.reload;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link BeanPostProcessor} that wires {@link OnConfigReload} methods of DI-managed beans to config
 * reload listeners. Runs after {@code MethodHandlerProxyBeanPostProcessor} so it binds to the final
 * (possibly proxied) bean instance.
 */
public class OnConfigReloadProcessor implements BeanPostProcessor {

    private final OnConfigReloadBinder binder;

    /**
     * Creates the processor.
     *
     * @param configManager the config manager, not null
     * @param logger        the plugin logger, not null
     */
    public OnConfigReloadProcessor(@NotNull SpigotConfigManager configManager, @NotNull Logger logger) {
        Objects.requireNonNull(configManager, "configManager cannot be null");
        Objects.requireNonNull(logger, "logger cannot be null");
        this.binder = new OnConfigReloadBinder(configManager, new OnConfigReloadInvoker(logger));
    }

    @Override
    public @NotNull Object postProcess(@NotNull BeanDefinition definition,
                                       @NotNull Object instance,
                                       @NotNull DependencyManager dependencyManager) {
        Class<?> realClass = ProxyUtils.getRealClass(instance);
        for (Method method : collectMethods(realClass)) {
            if (method.isAnnotationPresent(OnConfigReload.class)) {
                binder.bind(instance, method);
            }
        }
        return instance;
    }

    @Override
    public int getOrder() {
        // after MethodHandlerProxyBeanPostProcessor (default Ordered order 0) so we bind to the proxy
        return 100;
    }

    // mirrors the bukkit listener discovery: public (incl. inherited) plus declared methods, so
    // private @OnConfigReload methods are picked up too. a set dedupes the overlap.
    private static Set<Method> collectMethods(Class<?> realClass) {
        Set<Method> methods = new LinkedHashSet<>();
        methods.addAll(Arrays.asList(realClass.getMethods()));
        methods.addAll(Arrays.asList(realClass.getDeclaredMethods()));
        return methods;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=OnConfigReloadProcessorTest -DfailIfNoTests=false -B`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/reload/OnConfigReloadProcessor.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/reload/OnConfigReloadProcessorTest.java
git commit -m "feat(config-spigot): add OnConfigReloadProcessor bean post-processor"
```

---

## Task 7: config-spigot — register the processor in `ConfigConfiguration`

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/configuration/ConfigConfiguration.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/configuration/ConfigConfigurationTest.java`

- [ ] **Step 1: Add the failing test method**

Append this test to the existing `ConfigConfigurationTest` class (add the imports it needs):
```java
    @org.junit.jupiter.api.Test
    void onConfigReloadProcessor_registersProcessor() {
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        org.mockito.Mockito.when(plugin.getLogger())
                .thenReturn(java.util.logging.Logger.getLogger("test"));
        tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager configManager =
                org.mockito.Mockito.mock(tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager.class);

        ConfigConfiguration configuration = new ConfigConfiguration();
        tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer customizer =
                configuration.onConfigReloadProcessor(configManager, plugin);

        java.util.List<tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessor> registered =
                new java.util.ArrayList<>();
        customizer.customize(registered::add);

        assertEquals(1, registered.size());
        assertTrue(registered.get(0) instanceof tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadProcessor);
    }
```

(`BeanPostProcessorRegistry` is a single-method interface, so `registered::add` is a valid lambda for `customize`.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=ConfigConfigurationTest -DfailIfNoTests=false -B`
Expected: COMPILE FAILURE — `onConfigReloadProcessor` not found.

- [ ] **Step 3: Add the `@Bean` to `ConfigConfiguration`**

Add these imports:
```java
import tech.guilhermekaua.spigotboot.config.spigot.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;
```

Add this method to the class (after `configInjectors`):
```java
    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(SpigotConfigManager configManager, Plugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }
```

(`Plugin` and `SpigotConfigManager` are already imported in this file.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=ConfigConfigurationTest -DfailIfNoTests=false -B`
Expected: PASS (existing tests + the new one).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/configuration/ConfigConfiguration.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/configuration/ConfigConfigurationTest.java
git commit -m "feat(config-spigot): register OnConfigReloadProcessor in ConfigConfiguration"
```

---

## Task 8: config-spigot — reject `@OnConfigReload` on config POJOs

`@Config`/`@FolderConfig` classes are built by the binder, not the DI container, so a callback there would silently never fire. Fail fast at registration, next to `rejectConfigValueUsage`.

**Files:**
- Modify: `platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/registry/ConfigRegistry.java`
- Test: `platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/registry/ConfigRegistryTest.java`

- [ ] **Step 1: Add the failing test**

Append to `ConfigRegistryTest` (add imports as needed):
```java
    static class ConfigWithReloadHook {
        @tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload
        void onReload() { }
    }

    @org.junit.jupiter.api.Test
    void processConfigClass_rejectsOnConfigReloadOnConfigPojo() {
        assertThrows(
                tech.guilhermekaua.spigotboot.config.exception.ConfigException.class,
                () -> configRegistry.processConfigClass(ConfigWithReloadHook.class, context, configManager)
        );
    }
```

(`ConfigWithReloadHook` has only private fields, so it passes the existing field guard and reaches the new method guard.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=ConfigRegistryTest -DfailIfNoTests=false -B`
Expected: FAIL — no exception thrown (the guard does not exist yet).

- [ ] **Step 3: Add the guard**

Add imports if missing:
```java
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import java.lang.reflect.Method;
```

Add this method to `ConfigRegistry` (next to `validateConfigClass`):
```java
    private void rejectOnConfigReloadUsage(Class<?> configClass) {
        for (Class<?> current = configClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(OnConfigReload.class)) {
                    throw new ConfigException(
                            "Config class " + configClass.getName() +
                                    " declares @OnConfigReload on method '" + method.getName() + "'. " +
                                    "@OnConfigReload is only processed on DI-managed beans, not on @Config/@FolderConfig classes."
                    );
                }
            }
        }
    }
```

Call it from `validateConfigClass` (so simple configs are covered) — add at the end of that method:
```java
        rejectOnConfigReloadUsage(configClass);
```

And in `processFolderConfigClass`, call it before registration. Change the start of the `try` block from:
```java
            List<FolderConfig> annotations = getFolderConfigAnnotations(itemClass);
```
to:
```java
            rejectOnConfigReloadUsage(itemClass);

            List<FolderConfig> annotations = getFolderConfigAnnotations(itemClass);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -Dtest=ConfigRegistryTest -DfailIfNoTests=false -B`
Expected: PASS (existing tests + the new one).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/config-spigot/src/main/java/tech/guilhermekaua/spigotboot/config/spigot/registry/ConfigRegistry.java \
        platform-spigot/config-spigot/src/test/java/tech/guilhermekaua/spigotboot/config/spigot/test/registry/ConfigRegistryTest.java
git commit -m "feat(config-spigot): reject @OnConfigReload on @Config/@FolderConfig classes"
```

---

## Task 9: Full module verification

- [ ] **Step 1: Build + test the whole config-spigot reactor**

Run: `"$MVN" -pl platform-spigot/config-spigot -am test -B`
Expected: BUILD SUCCESS, 0 failures (all new + existing tests).

- [ ] **Step 2: Build + test the whole project** (catches any module that consumes the changed core API)

Run: `"$MVN" test -B`
Expected: BUILD SUCCESS across all modules.

- [ ] **Step 3: If anything fails, fix and re-run before continuing.** Do not proceed to docs with a red build.

---

## Task 10: Annotation Javadoc + dev-note cleanup

**Files:**
- Modify: `config/src/main/java/tech/guilhermekaua/spigotboot/config/annotation/OnConfigReload.java`
- Modify: `config-spigot-unwired-api-notes.md` (repo root)

- [ ] **Step 1: Enrich the annotation Javadoc**

Replace the type Javadoc (lines 30-33) of `OnConfigReload.java` with:
```java
/**
 * Marks a method on a DI-managed bean to be invoked after a config is reloaded.
 * <p>
 * For a simple {@code @Config} target, the method may take zero parameters or accept the new config
 * instance. For a folder-config target, it may take zero parameters or accept a
 * {@code FolderConfigItemChange<T>} or {@code FolderConfigSnapshot<T>}.
 * <p>
 * It is not invoked on initial load, only on subsequent reloads. Place it on a bean, never on a
 * {@code @Config}/{@code @FolderConfig} class itself.
 */
```

- [ ] **Step 2: Remove the now-wired entry from the dev-note**

In `config-spigot-unwired-api-notes.md`, delete the `## `@OnConfigReload`` section (the heading and its paragraph). Leave the other entries untouched.

- [ ] **Step 3: Re-verify the config module still compiles**

Run: `"$MVN" -pl config -am test -Dtest=NoSuchTest -DfailIfNoTests=false -B`
Expected: BUILD SUCCESS (compiles `config`; no tests selected).

- [ ] **Step 4: Commit**

```bash
git add config/src/main/java/tech/guilhermekaua/spigotboot/config/annotation/OnConfigReload.java config-spigot-unwired-api-notes.md
git commit -m "docs(config): document @OnConfigReload signatures; drop it from unwired notes"
```

---

## Task 11: GitHub wiki (submodule)

The `wiki/` submodule may be uninitialized in this worktree. Initialize it, edit, and commit there. **Do not push** to the wiki remote.

**Files (inside `wiki/wiki/`):**
- Modify: `Config_ReloadingExplained.md`
- Modify: `Config_FolderConfigsExplained.md`
- Modify: `Config_ConfigSharpEdgesAndTips.md`

- [ ] **Step 1: Initialize the submodule in this worktree**

```bash
cd "$ROOT"
git submodule update --init wiki
cd wiki && git switch -c docs/wire-onconfigreload && cd "$ROOT"
```
Expected: `wiki/` populated; a new branch in the submodule.

- [ ] **Step 2: Edit `Config_ReloadingExplained.md`**

In the *"Reacting to a reload"* section, AFTER the paragraph ending "...use the plain injected class when you only need the current value." (the `ConfigRef` paragraph), insert:
```markdown
For a declarative alternative, annotate a method on any bean with `@OnConfigReload`. The framework
registers it as a reload listener for you:

```java
@Service
public class AutoSaveTask {

    @OnConfigReload(MainConfig.class)
    private void onReload(MainConfig config) {
        reschedule(config.getAutoSaveInterval());
    }
}
```

The method must return `void` and take either no parameters or the new config instance.
`@OnConfigReload` with no `value()` and a single config parameter infers the target from that
parameter; with no `value()` and no parameters it fires for every registered config. It runs on
reloads only, not on initial load, and like every reload reader it must go through getters (the proxy)
to see fresh values. Put it on a bean — never on the `@Config` class itself, which is rejected at
registration.
```

Then change the folder paragraph (currently: "Folder configs have their own listener type, `FolderConfigChangeListener`, which reports per item adds, modifications, and removals. It is covered in [Folder Configs Explained](Config_FolderConfigsExplained#reacting-to-changes).") to:
```markdown
Folder configs have their own listener type, `FolderConfigChangeListener`, which reports per item adds,
modifications, and removals, and `@OnConfigReload` works for them too (accepting a
`FolderConfigItemChange<T>` or `FolderConfigSnapshot<T>`). Both are covered in
[Folder Configs Explained](Config_FolderConfigsExplained#reacting-to-changes).
```

- [ ] **Step 3: Edit `Config_FolderConfigsExplained.md`**

In its *"Reacting to changes"* section, after the `FolderConfigChangeListener` explanation, add:
```markdown
You can also react declaratively with `@OnConfigReload` on a bean method that accepts a
`FolderConfigItemChange<T>` (the per-item change) or a `FolderConfigSnapshot<T>` (the snapshot after
the change), or no parameter at all. The annotation fires once per item change — the same granularity
as `FolderConfigChangeListener`. See [Reloading Explained](Config_ReloadingExplained#reacting-to-a-reload).
```

- [ ] **Step 4: Edit `Config_ConfigSharpEdgesAndTips.md`**

Add a new bullet/section consistent with the page's format:
```markdown
### `@OnConfigReload` only fires on reloads, only on beans

`@OnConfigReload` is not called on initial load — only when a config is reloaded afterwards. It is
processed on DI-managed beans, so putting it on a `@Config`/`@FolderConfig` class is rejected at
registration. For folder configs it fires once per item change (add/modify/remove), not once per
`reloadAll()`.
```

- [ ] **Step 5: Verify the claims match the shipped code**

Re-read the three edits against the implementation (signatures, "not on initial load", "rejected on POJOs", "per item change"). Fix any wording that overstates behavior.

- [ ] **Step 6: Commit inside the submodule (do NOT push)**

```bash
cd "$ROOT/wiki"
git add Config_ReloadingExplained.md Config_FolderConfigsExplained.md Config_ConfigSharpEdgesAndTips.md
git commit -m "docs: document @OnConfigReload in config reloading/folder/sharp-edges pages"
cd "$ROOT"
```

- [ ] **Step 7: Record the submodule pointer bump in the feature branch**

```bash
cd "$ROOT"
git add wiki
git commit -m "docs(wiki): bump wiki submodule for @OnConfigReload docs"
```

---

## Task 12: Finalize and open the PR

- [ ] **Step 1: Final full build**

Run: `"$MVN" test -B`
Expected: BUILD SUCCESS, all modules green.

- [ ] **Step 2: Review the diff**

```bash
cd "$ROOT"
git log --oneline dev..HEAD
git diff --stat dev..HEAD
```
Confirm: core extension point, config-spigot feature + tests, config Javadoc, dev-note cleanup, wiki pointer bump.

- [ ] **Step 3: Hand off to the finishing skill**

Use `superpowers:finishing-a-development-branch` to push `feat/wire-onconfigreload` and open a PR **targeting `dev`**. The PR body should: summarize the new core `BeanPostProcessor` extension point and the config-spigot wiring; note the wiki submodule commit is staged but **not pushed to the live wiki** pending confirmation; link the spec and plan.

---

## Self-review notes (for the implementer)

- **Cross-module builds:** after any `core` change, the per-task commands use `-pl <module> -am` so `core` is rebuilt from source in the reactor. Task 2 Step 5 additionally `install`s core so a non-reactor `-pl config-spigot` run would also work.
- **Proxy binding:** `OnConfigReloadProcessor.getOrder()` is `100` (> the proxy processor's default `0`), so `instance` is the final proxy when scanned; methods come from `ProxyUtils.getRealClass(instance)` (the proxy's superclass).
- **Type consistency:** `OnConfigReloadBinder(SpigotConfigManager, OnConfigReloadInvoker)`, `OnConfigReloadInvoker(Logger)`, `OnConfigReloadProcessor(SpigotConfigManager, Logger)`, `BeanPostProcessorRegistryCustomizer.customize(BeanPostProcessorRegistry)`, `BeanPostProcessorRegistry.register(BeanPostProcessor)` — used identically across tasks and tests.
- **Spec coverage:** core hook (T1-T2), folder item-type enumeration (T3), invoker (T4), targets/payload/validation (T5), scanning/order/proxy (T6), registration (T7), POJO guard (T8), verification (T9), docs (T10-T11), PR (T12).
