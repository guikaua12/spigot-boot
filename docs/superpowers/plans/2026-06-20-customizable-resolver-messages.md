# Customizable Argument-Resolver Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let command argument resolvers raise keyed, overridable failure messages that a downstream plugin can customize per failure reason by registering one `CommandMessageSource` bean.

**Architecture:** A resolver throws a `CommandMessageException` carrying a `CommandMessageKey` (id + default template + documented placeholders) and placeholder values. The binder lets it propagate; the dispatcher catches it and renders the final text via a `CommandMessageRenderer` that consults the active `CommandMessageSource` (downstream override) and falls back to the key's default template. Built-in resolvers (4 core + 3 Spigot) migrate to keyed failures, each exposing its keys through a single discoverable enum. Non-keyed exceptions keep today's generic `invalidArgumentValue` path unchanged.

**Tech Stack:** Java 8-source multi-module Maven, JUnit 5, Mockito, MockBukkit; the framework's own DI (`@Configuration`/`@Bean`, `Context`/`DependencyManager`).

## Global Constraints

- Build/run tests with **JDK 21** (not the shell-default JDK 25 — Lombok 1.18.36 crashes on 25). Pass `dangerouslyDisableSandbox: true` when running `mvnw.cmd`, or edits silently run against a stale sandbox overlay.
- Core module tests: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true`. Spigot module tests: `mvnw.cmd -pl platform-spigot/commands-spigot -am test -Danimal.sniffer.skip=true`.
- Package root: `tech.guilhermekaua.spigotboot.*`. 4-space indent, same-line braces, `UpperCamelCase` types, `lowerCamelCase` members.
- Public/protected APIs get complete Javadoc (`@param`/`@return`/`@throws`). Keep APIs null-safe (`Objects.requireNonNull`, `@NotNull` where the surrounding code does).
- Normal (non-Javadoc) comments start lowercase. Import types with `import` — never fully-qualified inline types.
- New public message-key/SPI types go in the root `tech.guilhermekaua.spigotboot.commands` package (next to `CommandMessages`, `CommandArgumentResolver`); implementation helpers go in `...commands.message` (next to `CommandMessagesProvider`).
- TDD: write the failing test first, watch it fail, implement minimally, watch it pass, commit. One conventional-commit per task.

---

### Task 1: Keyed-failure core types

**Files:**
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageKey.java`
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageException.java`
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageTemplates.java`
- Test: `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageExceptionTest.java`

**Interfaces:**
- Produces:
  - `interface CommandMessageKey { String id(); String defaultTemplate(); List<String> placeholders(); }`
  - `class CommandMessageException extends RuntimeException` with `static CommandMessageException of(CommandMessageKey)`, `CommandMessageException with(String name, Object value)` (chainable), `CommandMessageKey getKey()`, `Map<String,Object> getPlaceholders()` (unmodifiable), and `String getMessage()` returning the interpolated default template.
  - `class CommandMessageTemplates` with `static String interpolate(String template, Map<String,Object> placeholders)` — literal `{name}` → `String.valueOf(value)`, unbound `{name}` left verbatim, null template → `""`.

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageTemplates;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandMessageExceptionTest {
    private enum TestKey implements CommandMessageKey {
        NOT_FOUND("player.not-found", "No player named '{input}' is online.", "input"),
        AMBIGUOUS("player.ambiguous", "'{input}' matches {count} players.", "input", "count");

        private final String id;
        private final String defaultTemplate;
        private final List<String> placeholders;

        TestKey(String id, String defaultTemplate, String... placeholders) {
            this.id = id;
            this.defaultTemplate = defaultTemplate;
            this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
        }

        @Override public String id() { return id; }
        @Override public String defaultTemplate() { return defaultTemplate; }
        @Override public List<String> placeholders() { return placeholders; }
    }

    @Test
    void builderRetainsKeyAndPlaceholders() {
        CommandMessageException exception = CommandMessageException.of(TestKey.AMBIGUOUS)
                .with("input", "al")
                .with("count", 3);

        assertSame(TestKey.AMBIGUOUS, exception.getKey());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("input", "al");
        expected.put("count", 3);
        assertEquals(expected, exception.getPlaceholders());
    }

    @Test
    void getMessageInterpolatesDefaultTemplate() {
        CommandMessageException exception = CommandMessageException.of(TestKey.AMBIGUOUS)
                .with("input", "al")
                .with("count", 3);

        assertEquals("'al' matches 3 players.", exception.getMessage());
    }

    @Test
    void getPlaceholdersIsUnmodifiable() {
        CommandMessageException exception = CommandMessageException.of(TestKey.NOT_FOUND).with("input", "x");
        assertThrows(UnsupportedOperationException.class, () -> exception.getPlaceholders().put("k", "v"));
    }

    @Test
    void interpolateLeavesUnboundPlaceholderVerbatimAndHandlesNull() {
        Map<String, Object> placeholders = Collections.singletonMap("input", "x");
        assertEquals("got x but not {missing}", CommandMessageTemplates.interpolate("got {input} but not {missing}", placeholders));
        assertEquals("", CommandMessageTemplates.interpolate(null, placeholders));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageExceptionTest`
Expected: COMPILE FAILURE — `CommandMessageKey`, `CommandMessageException`, `CommandMessageTemplates` do not exist.

- [ ] **Step 3: Create `CommandMessageKey`**

```java
package tech.guilhermekaua.spigotboot.commands;

import java.util.List;

/**
 * A stable, discoverable identifier for a customizable command message raised by an
 * argument resolver. Implementations are typically enum constants so the full set of keys
 * is discoverable through IDE autocompletion.
 */
public interface CommandMessageKey {
    /**
     * @return the stable identifier of this key, for example {@code "player.not-found"}
     */
    String id();

    /**
     * @return the fallback message template used when no {@link CommandMessageSource}
     *         overrides this key; may contain {@code {placeholder}} tokens
     */
    String defaultTemplate();

    /**
     * @return the documented placeholder names this key's template understands
     */
    List<String> placeholders();
}
```

- [ ] **Step 4: Create `CommandMessageTemplates`**

```java
package tech.guilhermekaua.spigotboot.commands.message;

import java.util.Map;

/**
 * Interpolates {@code {placeholder}} tokens in command message templates.
 */
public final class CommandMessageTemplates {
    private CommandMessageTemplates() {
    }

    /**
     * Replaces every {@code {name}} token in {@code template} with the string form of the
     * matching value. Tokens with no matching value are left verbatim.
     *
     * @param template     the template, may be {@code null}
     * @param placeholders the placeholder values, may be {@code null}
     * @return the interpolated text, or an empty string when {@code template} is {@code null}
     */
    public static String interpolate(String template, Map<String, Object> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
```

- [ ] **Step 5: Create `CommandMessageException`**

```java
package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.message.CommandMessageTemplates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Raised by a {@link CommandArgumentResolver} to signal a specific, customizable failure.
 * The carried {@link CommandMessageKey} and placeholder values are rendered into the final
 * message by the command pipeline, letting a downstream {@link CommandMessageSource}
 * override the text per key.
 */
public class CommandMessageException extends RuntimeException {
    private final CommandMessageKey key;
    private final Map<String, Object> placeholders = new LinkedHashMap<>();

    private CommandMessageException(CommandMessageKey key) {
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    /**
     * @param key the message key describing this failure; must not be {@code null}
     * @return a new exception carrying {@code key} and no placeholders yet
     */
    public static CommandMessageException of(CommandMessageKey key) {
        return new CommandMessageException(key);
    }

    /**
     * Binds a placeholder value for rendering.
     *
     * @param name  the placeholder name (without braces); must not be {@code null}
     * @param value the value; may be {@code null}
     * @return this exception, for chaining
     */
    public CommandMessageException with(String name, Object value) {
        placeholders.put(Objects.requireNonNull(name, "name must not be null"), value);
        return this;
    }

    /**
     * @return the key describing this failure
     */
    public CommandMessageKey getKey() {
        return key;
    }

    /**
     * @return an unmodifiable view of the bound placeholder values
     */
    public Map<String, Object> getPlaceholders() {
        return Collections.unmodifiableMap(placeholders);
    }

    @Override
    public String getMessage() {
        return CommandMessageTemplates.interpolate(key.defaultTemplate(), placeholders);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageExceptionTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageKey.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageException.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageTemplates.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageExceptionTest.java
git commit -m "feat(commands): add keyed CommandMessageException and message key types"
```

---

### Task 2: `CommandMessageSource` SPI + provider

**Files:**
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageSource.java`
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageSourceProvider.java`
- Test: `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageSourceProviderTest.java`

**Interfaces:**
- Consumes: `CommandMessageKey` (Task 1), `Context`/`DependencyManager` bean registry (`context.getDependencyManager().getBeanInstanceRegistry().asMapView()`), `BeanDefinition.isPrimary()`.
- Produces:
  - `interface CommandMessageSource { String resolveTemplate(CommandExecutionContext context, CommandMessageKey key); }` (returns `null` to fall back to the key default).
  - `class CommandMessageSourceProvider { CommandMessageSource resolve(Context context); }` — returns a no-op source (always `null`) when no bean is registered; the single source when one exists; the `@Primary` source when several exist; throws `IllegalStateException` when several exist with none/multiple primary. Mirrors `CommandMessagesProvider`.

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMessageSourceProviderTest {
    private final CommandMessageSourceProvider provider = new CommandMessageSourceProvider();

    private Context contextWith(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        return context;
    }

    @Test
    void noSourceReturnsNoOpThatYieldsNull() {
        Context context = contextWith(new DependencyManager());
        CommandMessageSource resolved = provider.resolve(context);
        assertNull(resolved.resolveTemplate(mock(CommandExecutionContext.class), mock(CommandMessageKey.class)));
    }

    @Test
    void singleSourceIsReturned() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> "x";
        dependencyManager.registerDependency(source, "source", true);

        assertSame(source, provider.resolve(contextWith(dependencyManager)));
    }

    @Test
    void multipleSourcesWithoutPrimaryThrows() {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "a", "a", false);
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "b", "b", false);

        assertThrows(IllegalStateException.class, () -> provider.resolve(contextWith(dependencyManager)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageSourceProviderTest`
Expected: COMPILE FAILURE — `CommandMessageSource` / `CommandMessageSourceProvider` do not exist.

- [ ] **Step 3: Create `CommandMessageSource`**

```java
package tech.guilhermekaua.spigotboot.commands;

/**
 * Customization point for command messages raised via {@link CommandMessageException}.
 * Register an implementation as a bean to override the text for one or more
 * {@link CommandMessageKey}s.
 */
public interface CommandMessageSource {
    /**
     * Resolves the template to use for {@code key}.
     *
     * @param context the current execution context (e.g. for per-sender decisions)
     * @param key     the key being rendered
     * @return the template to use, or {@code null} to fall back to {@code key.defaultTemplate()}
     */
    String resolveTemplate(CommandExecutionContext context, CommandMessageKey key);
}
```

- [ ] **Step 4: Create `CommandMessageSourceProvider`**

```java
package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the active {@link CommandMessageSource} from the dependency context, mirroring
 * {@link CommandMessagesProvider}: no bean yields a no-op source, one bean wins, several
 * require exactly one {@code @Primary}.
 */
public class CommandMessageSourceProvider {
    private static final CommandMessageSource NO_OP = (context, key) -> null;

    /**
     * @param context the command context; must not be {@code null}
     * @return the resolved source, never {@code null} (a no-op source when none is registered)
     */
    public CommandMessageSource resolve(Context context) {
        Collection<Object> instances = context.getDependencyManager().getBeanInstanceRegistry().asMapView().values();
        List<CommandMessageSource> candidates = new ArrayList<>();
        for (Object instance : instances) {
            if (instance instanceof CommandMessageSource) {
                candidates.add((CommandMessageSource) instance);
            }
        }

        List<CommandMessageSource> unique = CommandSupport.deduplicateByIdentity(candidates);
        if (unique.isEmpty()) {
            return NO_OP;
        }
        if (unique.size() == 1) {
            return unique.get(0);
        }

        CommandMessageSource primary = null;
        for (Map.Entry<BeanDefinition, Object> entry : context.getDependencyManager().getBeanInstanceRegistry().asMapView().entrySet()) {
            BeanDefinition definition = entry.getKey();
            Object instance = entry.getValue();
            if (!definition.isPrimary() || !(instance instanceof CommandMessageSource)) {
                continue;
            }

            if (primary != null && primary != instance) {
                throw new IllegalStateException("Multiple primary CommandMessageSource beans were found.");
            }
            primary = (CommandMessageSource) instance;
        }

        if (primary == null) {
            throw new IllegalStateException("Multiple CommandMessageSource beans were found. Mark exactly one as @Primary.");
        }
        return primary;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageSourceProviderTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandMessageSource.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageSourceProvider.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageSourceProviderTest.java
git commit -m "feat(commands): add CommandMessageSource SPI and provider"
```

---

### Task 3: `CommandMessageRenderer`

**Files:**
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageRenderer.java`
- Test: `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageRendererTest.java`

**Interfaces:**
- Consumes: `CommandMessageSourceProvider` (Task 2), `CommandMessageException`/`CommandMessageKey` (Task 1), `CommandMessageTemplates` (Task 1), `CommandExecutionContext.getContext()`.
- Produces: `class CommandMessageRenderer { CommandMessageRenderer(CommandMessageSourceProvider provider); String render(CommandExecutionContext context, CommandMessageException exception); }` — source template if non-null else key default, then interpolate placeholders.

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMessageRendererTest {
    private enum Key implements CommandMessageKey {
        NOT_FOUND;

        @Override public String id() { return "player.not-found"; }
        @Override public String defaultTemplate() { return "No player named '{input}' is online."; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    private final CommandMessageRenderer renderer = new CommandMessageRenderer(new CommandMessageSourceProvider());

    private CommandExecutionContext executionContextWith(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        CommandExecutionContext executionContext = mock(CommandExecutionContext.class);
        when(executionContext.getContext()).thenReturn(context);
        return executionContext;
    }

    @Test
    void usesDefaultTemplateWhenNoSourceRegistered() {
        CommandExecutionContext context = executionContextWith(new DependencyManager());
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("No player named 'bob' is online.", renderer.render(context, exception));
    }

    @Test
    void usesSourceTemplateWhenItReturnsNonNull() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> "&cNao achei '{input}'.";
        dependencyManager.registerDependency(source, "source", true);
        CommandExecutionContext context = executionContextWith(dependencyManager);
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("&cNao achei 'bob'.", renderer.render(context, exception));
    }

    @Test
    void fallsBackToDefaultWhenSourceReturnsNull() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> null;
        dependencyManager.registerDependency(source, "source", true);
        CommandExecutionContext context = executionContextWith(dependencyManager);
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("No player named 'bob' is online.", renderer.render(context, exception));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageRendererTest`
Expected: COMPILE FAILURE — `CommandMessageRenderer` does not exist.

- [ ] **Step 3: Create `CommandMessageRenderer`**

```java
package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;

import java.util.Objects;

/**
 * Renders a {@link CommandMessageException} into the final message string: the active
 * {@link CommandMessageSource} template if it supplies one, otherwise the key's default
 * template, then placeholder interpolation.
 */
public class CommandMessageRenderer {
    private final CommandMessageSourceProvider sourceProvider;

    public CommandMessageRenderer(CommandMessageSourceProvider sourceProvider) {
        this.sourceProvider = Objects.requireNonNull(sourceProvider, "sourceProvider must not be null");
    }

    /**
     * @param context   the current execution context; must not be {@code null}
     * @param exception the keyed failure to render; must not be {@code null}
     * @return the message to send to the sender
     */
    public String render(CommandExecutionContext context, CommandMessageException exception) {
        CommandMessageSource source = sourceProvider.resolve(context.getContext());
        String template = source.resolveTemplate(context, exception.getKey());
        if (template == null) {
            template = exception.getKey().defaultTemplate();
        }
        return CommandMessageTemplates.interpolate(template, exception.getPlaceholders());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageRendererTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageRenderer.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageRendererTest.java
git commit -m "feat(commands): add CommandMessageRenderer"
```

---

### Task 4: Wire keyed failures through binder and dispatcher

**Files:**
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/binding/CommandParameterBinder.java:80-91`
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/execution/CommandDispatcher.java` (constructors near `:28-45`, bind catch near `:156-163`)
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/configuration/CommandsConfiguration.java` (add provider + renderer beans)
- Test: `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageDispatchTest.java`

**Interfaces:**
- Consumes: `CommandMessageException` (Task 1), `CommandMessageRenderer` + `CommandMessageSourceProvider` (Tasks 2-3), existing `CommandDispatcher` 4-arg and 5-arg constructors.
- Produces: a new canonical `CommandDispatcher` 6-arg constructor `(CommandParameterBinder, CommandInvocationExecutor, CommandMessagesProvider, CompletionResolver, CommandInterceptorChain, CommandMessageRenderer)`; the existing 4-arg and 5-arg constructors preserved (the 5-arg now defaults the renderer). New `@Bean`s `commandMessageSourceProvider()` and `commandMessageRenderer(CommandMessageSourceProvider)`.

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Name;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.*;

class CommandMessageDispatchTest {
    enum WidgetKey implements CommandMessageKey {
        BAD;

        @Override public String id() { return "widget.bad"; }
        @Override public String defaultTemplate() { return "No widget '{input}'."; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    static final class Widget {
    }

    static final class WidgetResolver implements CommandArgumentResolver<Widget> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return Widget.class.equals(parameter.getValueType());
        }

        @Override
        public Widget resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            throw CommandMessageException.of(WidgetKey.BAD).with("input", input);
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class WidgetCommands {
        @Command("get <w>")
        public void get(@Name("w") Widget widget) {
        }
    }

    @Test
    void keyedFailureRendersDefaultWhenNoSource() {
        DependencyManager dependencyManager = new DependencyManager();
        TestSender sender = testSender("Console");

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);

        dispatcher.dispatch(context, compileRoot(new WidgetCommands(), dependencyManager), senderHandleFor(sender), "admin", new String[]{"get", "x"});

        assertEquals(Collections.singletonList("No widget 'x'."), sender.getMessages());
    }

    @Test
    void keyedFailureRendersSourceOverride() {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "custom {input}", "source", true);
        TestSender sender = testSender("Console");

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);

        dispatcher.dispatch(context, compileRoot(new WidgetCommands(), dependencyManager), senderHandleFor(sender), "admin", new String[]{"get", "x"});

        assertEquals(Collections.singletonList("custom x"), sender.getMessages());
    }

    private CompiledRootCommand compileRoot(Object handler, DependencyManager dependencyManager) {
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                new DefaultCommandReplacementRegistry(Collections.emptyList()),
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler, dependencyManager);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher() {
        DefaultCommandArgumentResolverRegistry resolverRegistry =
                new DefaultCommandArgumentResolverRegistry(Arrays.asList(new WidgetResolver()), Collections.emptyList());
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(new DefaultCommandCompletionRegistry(Collections.emptyList()), resolverRegistry)
        );
    }

    private Context newContext(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        when(context.getPlugin()).thenReturn(testPlugin());
        return context;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageDispatchTest`
Expected: FAIL — without the binder/dispatcher changes, the `CommandMessageException` is wrapped to `CommandBindingException.invalid` and the sender receives `Invalid value 'x' for argument: w` (or the generic execution error), not `No widget 'x'.`

- [ ] **Step 3: Let `CommandMessageException` propagate in the binder**

In `CommandParameterBinder.bindParsed`, add a catch between the existing two catches:

```java
        try {
            Object resolved = resolver.resolve(context, parameter, resolvedInput);
            if (parameter.isOptionalWrapper()) {
                return Optional.ofNullable(resolved);
            }
            return resolved;
        } catch (CommandBindingException e) {
            throw e;
        } catch (CommandMessageException e) {
            throw e;
        } catch (Exception e) {
            throw CommandBindingException.invalid(parameter, resolvedInput, e);
        }
```

Add the import: `import tech.guilhermekaua.spigotboot.commands.CommandMessageException;`

- [ ] **Step 4: Add the renderer dependency + 6-arg constructor to `CommandDispatcher`**

Add the field and imports:

```java
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
```

```java
    private final CommandMessageRenderer messageRenderer;
```

Replace the two existing constructors so they chain into a new 6-arg canonical constructor:

```java
    public CommandDispatcher(CommandParameterBinder parameterBinder,
                             CommandInvocationExecutor invocationExecutor,
                             CommandMessagesProvider messagesProvider,
                             CompletionResolver completionResolver) {
        this(parameterBinder, invocationExecutor, messagesProvider, completionResolver, new CommandInterceptorChain());
    }

    public CommandDispatcher(CommandParameterBinder parameterBinder,
                             CommandInvocationExecutor invocationExecutor,
                             CommandMessagesProvider messagesProvider,
                             CompletionResolver completionResolver,
                             CommandInterceptorChain interceptorChain) {
        this(parameterBinder, invocationExecutor, messagesProvider, completionResolver, interceptorChain,
                new CommandMessageRenderer(new CommandMessageSourceProvider()));
    }

    public CommandDispatcher(CommandParameterBinder parameterBinder,
                             CommandInvocationExecutor invocationExecutor,
                             CommandMessagesProvider messagesProvider,
                             CompletionResolver completionResolver,
                             CommandInterceptorChain interceptorChain,
                             CommandMessageRenderer messageRenderer) {
        this.parameterBinder = parameterBinder;
        this.invocationExecutor = invocationExecutor;
        this.messagesProvider = messagesProvider;
        this.completionResolver = completionResolver;
        this.interceptorChain = interceptorChain;
        this.messageRenderer = messageRenderer;
    }
```

- [ ] **Step 5: Catch `CommandMessageException` in `CommandDispatcher.execute`**

Add a second catch on the inner bind try (next to the existing `catch (CommandBindingException e)`):

```java
            Object[] arguments;
            try {
                arguments = parameterBinder.bind(context, invocation, context.getParsedArguments());
            } catch (CommandBindingException e) {
                resolvedChain.onError(context, invocation, e);
                handleBindingException(context, messages, e);
                return true;
            } catch (CommandMessageException e) {
                resolvedChain.onError(context, invocation, e);
                context.sendMessage(messageRenderer.render(context, e));
                return true;
            }
```

- [ ] **Step 6: Register the provider + renderer beans**

In `CommandsConfiguration`, add imports:

```java
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
```

Add beans:

```java
    @Bean
    public CommandMessageSourceProvider commandMessageSourceProvider() {
        return new CommandMessageSourceProvider();
    }

    @Bean
    public CommandMessageRenderer commandMessageRenderer(CommandMessageSourceProvider commandMessageSourceProvider) {
        return new CommandMessageRenderer(commandMessageSourceProvider);
    }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageDispatchTest`
Expected: PASS (2 tests).

- [ ] **Step 8: Run the full core module suite to confirm no regressions yet**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true`
Expected: PASS — built-in resolvers are not migrated yet, so existing generic-message tests still pass.

- [ ] **Step 9: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/binding/CommandParameterBinder.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/execution/CommandDispatcher.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/configuration/CommandsConfiguration.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageDispatchTest.java
git commit -m "feat(commands): render keyed resolver failures via CommandMessageSource"
```

---

### Task 5: Resolver key declaration + `CommandMessageCatalog`

**Files:**
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandArgumentResolver.java`
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandArgumentResolverRegistry.java`
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/resolve/DefaultCommandArgumentResolverRegistry.java`
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageCatalog.java`
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/configuration/CommandsConfiguration.java`
- Test: `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageCatalogTest.java`

**Interfaces:**
- Consumes: `CommandMessageKey` (Task 1), `CommandArgumentResolver`, `CommandArgumentResolverRegistry`, `DefaultCommandArgumentResolverRegistry`.
- Produces:
  - `CommandArgumentResolver` default method `default Collection<CommandMessageKey> messageKeys() { return Collections.emptyList(); }`.
  - `CommandArgumentResolverRegistry` method `Collection<CommandArgumentResolver<?>> all();`.
  - `class CommandMessageCatalog { CommandMessageCatalog(CommandArgumentResolverRegistry registry); Collection<CommandMessageKey> all(); Optional<CommandMessageKey> find(String id); }` (logs nothing).

- [ ] **Step 1: Write the failing test**

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageCatalog;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandMessageCatalogTest {
    private enum Key implements CommandMessageKey {
        FOO;

        @Override public String id() { return "foo.bad"; }
        @Override public String defaultTemplate() { return "bad {input}"; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    private static final class KeyedResolver implements CommandArgumentResolver<String> {
        @Override public boolean supports(CommandParameterMetadata parameter) { return false; }
        @Override public String resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) { return input; }
        @Override public Collection<CommandMessageKey> messageKeys() { return Collections.singletonList(Key.FOO); }
    }

    @Test
    void catalogAggregatesKeysFromRegisteredResolvers() {
        DefaultCommandArgumentResolverRegistry registry =
                new DefaultCommandArgumentResolverRegistry(Arrays.asList(new KeyedResolver()), Collections.emptyList());
        CommandMessageCatalog catalog = new CommandMessageCatalog(registry);

        assertTrue(catalog.all().contains(Key.FOO));
        assertSame(Key.FOO, catalog.find("foo.bad").orElseThrow(AssertionError::new));
        assertFalse(catalog.find("missing").isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageCatalogTest`
Expected: COMPILE FAILURE — `messageKeys()` and `CommandMessageCatalog` do not exist.

- [ ] **Step 3: Add `messageKeys()` to `CommandArgumentResolver`**

```java
package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Collection;
import java.util.Collections;

public interface CommandArgumentResolver<T> {
    boolean supports(CommandParameterMetadata parameter);

    T resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) throws Exception;

    default CommandCompletionProvider defaultCompletionProvider() {
        return null;
    }

    /**
     * @return the message keys this resolver can raise via {@link CommandMessageException};
     *         empty by default
     */
    default Collection<CommandMessageKey> messageKeys() {
        return Collections.emptyList();
    }
}
```

- [ ] **Step 4: Add `all()` to the registry interface**

```java
package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Collection;
import java.util.Optional;

public interface CommandArgumentResolverRegistry {
    void register(CommandArgumentResolver<?> resolver);

    Optional<CommandArgumentResolver<?>> resolve(CommandParameterMetadata parameter);

    /**
     * @return an unmodifiable snapshot of all registered resolvers
     */
    Collection<CommandArgumentResolver<?>> all();
}
```

- [ ] **Step 5: Implement `all()` in `DefaultCommandArgumentResolverRegistry`**

Add after the `resolve(...)` method:

```java
    @Override
    public Collection<CommandArgumentResolver<?>> all() {
        ensureSorted();
        return Collections.unmodifiableList(new ArrayList<>(resolvers));
    }
```

(`java.util.*` is already imported.)

- [ ] **Step 6: Create `CommandMessageCatalog`**

```java
package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in, read-only catalog of every {@link CommandMessageKey} declared by the registered
 * argument resolvers. Useful for programmatic enumeration; performs no logging.
 */
public class CommandMessageCatalog {
    private final List<CommandMessageKey> keys = new ArrayList<>();

    public CommandMessageCatalog(CommandArgumentResolverRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandArgumentResolver<?> resolver : registry.all()) {
            keys.addAll(resolver.messageKeys());
        }
    }

    /**
     * @return an unmodifiable view of all declared keys
     */
    public Collection<CommandMessageKey> all() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * @param id the key id to look up
     * @return the first key with a matching {@link CommandMessageKey#id()}, if any
     */
    public Optional<CommandMessageKey> find(String id) {
        for (CommandMessageKey key : keys) {
            if (key.id().equals(id)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 7: Register the catalog bean**

In `CommandsConfiguration`, add the import and bean:

```java
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageCatalog;
```

```java
    @Bean
    public CommandMessageCatalog commandMessageCatalog(CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CommandMessageCatalog(commandArgumentResolverRegistry);
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandMessageCatalogTest`
Expected: PASS (1 test).

- [ ] **Step 9: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandArgumentResolver.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CommandArgumentResolverRegistry.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/resolve/DefaultCommandArgumentResolverRegistry.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessageCatalog.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/configuration/CommandsConfiguration.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandMessageCatalogTest.java
git commit -m "feat(commands): add resolver messageKeys() and CommandMessageCatalog"
```

---

### Task 6: Migrate core built-in resolvers to keyed messages

**Files:**
- Create: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CoreCommandMessages.java`
- Modify: `commands/src/main/java/tech/guilhermekaua/spigotboot/commands/resolve/DefaultCommandArgumentResolverRegistry.java`
- Modify (test): `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandInterceptorExecutionTest.java` (replace the `bindingFailuresTriggerOnErrorAndKeepDefaultBindingMessage` test at `:215-229`; add nested `Widget`/`ThrowingWidgetResolver`/`WidgetCommands` types near `NumericCommands`)
- Modify (test): `commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandCooldownInterceptorTest.java:248`

**Interfaces:**
- Consumes: `CommandMessageException`, `CommandMessageKey` (Task 1); `CommandMessageException` rendering path (Task 4).
- Produces: `enum CoreCommandMessages implements CommandMessageKey { BOOLEAN_INVALID, NUMBER_INVALID, ENUM_INVALID, UUID_INVALID }`. After this task, the core `Boolean`/`Numeric`/`Enum`/`Uuid` resolvers throw keyed failures; the generic `invalidArgumentValue` path is reachable only via non-keyed (downstream) resolvers.

- [ ] **Step 1: Update the failing/affected tests first**

In `CommandInterceptorExecutionTest`, the numeric resolver now raises a keyed failure, so `bindingFailuresTriggerOnErrorAndKeepDefaultBindingMessage` changes meaning. Replace that whole test method (`:215-229`) with two tests — one for the keyed path, one preserving coverage of the generic fallback via a non-keyed custom resolver:

```java
    @Test
    void keyedResolverFailuresTriggerOnErrorAndRenderKeyedMessage() {
        NumericCommands handler = new NumericCommands();
        DependencyManager dependencyManager = new DependencyManager();
        BindingErrorInterceptor interceptor = new BindingErrorInterceptor();
        registerBeans(dependencyManager, interceptor);

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(handler, dependencyManager), senderHandleFor(sender), "admin", new String[]{"number", "oops"});

        assertEquals(Arrays.asList("before", "CommandMessageException"), interceptor.events);
        assertEquals(Collections.singletonList("'oops' is not a valid number."), sender.getMessages());
    }

    @Test
    void nonKeyedBindingFailuresTriggerOnErrorAndKeepGenericBindingMessage() {
        DependencyManager dependencyManager = new DependencyManager();
        BindingErrorInterceptor interceptor = new BindingErrorInterceptor();
        registerBeans(dependencyManager, interceptor);

        DefaultCommandArgumentResolverRegistry resolverRegistry =
                new DefaultCommandArgumentResolverRegistry(Collections.singletonList(new ThrowingWidgetResolver()), Collections.emptyList());
        CommandDispatcher dispatcher = new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(new DefaultCommandCompletionRegistry(Collections.emptyList()), resolverRegistry)
        );
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new WidgetCommands(), dependencyManager), senderHandleFor(sender), "admin", new String[]{"get", "oops"});

        assertEquals(Arrays.asList("before", "CommandBindingException"), interceptor.events);
        assertEquals(Collections.singletonList("Invalid value 'oops' for argument: w"), sender.getMessages());
    }
```

Add these supporting nested types to `CommandInterceptorExecutionTest` (near `NumericCommands`):

```java
    static final class Widget {
    }

    static final class ThrowingWidgetResolver implements CommandArgumentResolver<Widget> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return Widget.class.equals(parameter.getValueType());
        }

        @Override
        public Widget resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            throw new IllegalArgumentException("nope");
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class WidgetCommands {
        @Command("get <w>")
        public void get(@Name("w") Widget widget) {
        }
    }
```

Add the imports this test file now needs (it already imports the `commands` and `annotations` packages on demand; confirm these resolve): `tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver`, `tech.guilhermekaua.spigotboot.commands.CommandExecutionContext`, `tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata`, `tech.guilhermekaua.spigotboot.commands.annotations.Command`, `tech.guilhermekaua.spigotboot.commands.annotations.Name`, `tech.guilhermekaua.spigotboot.commands.annotations.RootCommand`, `tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder`, `tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver`, `tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry`, `tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor`, `tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider`, `tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages`, `tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry`. (The file already uses `import tech.guilhermekaua.spigotboot.commands.annotations.*;` and other on-demand imports — add any missing single imports.)

In `CommandCooldownInterceptorTest`, update line 248:

```java
        assertEquals(Collections.singletonList("'oops' is not a valid number."), sender.messages);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandInterceptorExecutionTest+CommandCooldownInterceptorTest`
Expected: FAIL — the numeric resolver still throws `NumberFormatException` (generic path), so `keyedResolverFailuresTriggerOnErrorAndRenderKeyedMessage` and the updated cooldown assertion fail.

- [ ] **Step 3: Create `CoreCommandMessages`**

```java
package tech.guilhermekaua.spigotboot.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of customizable message keys raised by the framework's built-in
 * argument resolvers. Reference these constants from a {@link CommandMessageSource} to
 * override the corresponding messages.
 */
public enum CoreCommandMessages implements CommandMessageKey {
    /** Raised when a boolean argument cannot be parsed. Placeholders: {@code {input}}. */
    BOOLEAN_INVALID("boolean.invalid", "'{input}' is not a valid true/false value.", "input"),
    /** Raised when a numeric argument cannot be parsed. Placeholders: {@code {input}}. */
    NUMBER_INVALID("number.invalid", "'{input}' is not a valid number.", "input"),
    /** Raised when an enum argument does not match any constant. Placeholders: {@code {input}}, {@code {options}}. */
    ENUM_INVALID("enum.invalid", "'{input}' is not a valid option. Valid: {options}.", "input", "options"),
    /** Raised when a UUID argument cannot be parsed. Placeholders: {@code {input}}. */
    UUID_INVALID("uuid.invalid", "'{input}' is not a valid UUID.", "input");

    private final String id;
    private final String defaultTemplate;
    private final List<String> placeholders;

    CoreCommandMessages(String id, String defaultTemplate, String... placeholders) {
        this.id = id;
        this.defaultTemplate = defaultTemplate;
        this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }

    @Override
    public List<String> placeholders() {
        return placeholders;
    }
}
```

- [ ] **Step 4: Migrate the four resolvers in `DefaultCommandArgumentResolverRegistry`**

The file already has `import tech.guilhermekaua.spigotboot.commands.*;`, so `CommandMessageException`, `CoreCommandMessages`, and `CommandMessageKey` need no new import.

`BooleanArgumentResolver.resolve` — replace the final throw:

```java
            throw CommandMessageException.of(CoreCommandMessages.BOOLEAN_INVALID).with("input", input);
```

and add to `BooleanArgumentResolver`:

```java
        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.BOOLEAN_INVALID);
        }
```

`NumericArgumentResolver.resolve` — wrap the parse chain so a parse failure becomes keyed, leaving the unsupported-type branch as a plain exception:

```java
        @Override
        public Number resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Class<?> type = parameter.getValueType();
            try {
                if (byte.class.equals(type) || Byte.class.equals(type)) {
                    return Byte.parseByte(input);
                }
                if (short.class.equals(type) || Short.class.equals(type)) {
                    return Short.parseShort(input);
                }
                if (int.class.equals(type) || Integer.class.equals(type)) {
                    return Integer.parseInt(input);
                }
                if (long.class.equals(type) || Long.class.equals(type)) {
                    return Long.parseLong(input);
                }
                if (float.class.equals(type) || Float.class.equals(type)) {
                    return Float.parseFloat(input);
                }
                if (double.class.equals(type) || Double.class.equals(type)) {
                    return Double.parseDouble(input);
                }
            } catch (NumberFormatException e) {
                throw CommandMessageException.of(CoreCommandMessages.NUMBER_INVALID).with("input", input);
            }
            throw new IllegalArgumentException("Unsupported numeric type: " + type.getName());
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.NUMBER_INVALID);
        }
```

`EnumArgumentResolver.resolve` — replace the final throw to include the valid options and add `messageKeys()`:

```java
        @SuppressWarnings({"rawtypes"})
        @Override
        public Enum<?> resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            List<String> validNames = new ArrayList<>();
            for (Object constant : parameter.getValueType().getEnumConstants()) {
                Enum value = (Enum) constant;
                if (value.name().equalsIgnoreCase(input)) {
                    return value;
                }
                validNames.add(value.name());
            }
            throw CommandMessageException.of(CoreCommandMessages.ENUM_INVALID)
                    .with("input", input)
                    .with("options", String.join(", ", validNames));
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.ENUM_INVALID);
        }
```

`UuidArgumentResolver.resolve` — wrap `UUID.fromString` and add `messageKeys()`:

```java
        @Override
        public UUID resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            try {
                return UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                throw CommandMessageException.of(CoreCommandMessages.UUID_INVALID).with("input", input);
            }
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.UUID_INVALID);
        }
```

(`java.util.*` is already imported, covering `Collection`, `Collections`, `ArrayList`, `List`, `UUID`.)

- [ ] **Step 5: Run the updated tests to verify they pass**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true -Dtest=CommandInterceptorExecutionTest+CommandCooldownInterceptorTest+CommandMessageDispatchTest`
Expected: PASS.

- [ ] **Step 6: Run the full core module suite**

Run: `mvnw.cmd -pl commands -am test -Danimal.sniffer.skip=true`
Expected: PASS. If any other test asserts a pre-migration generic message for boolean/enum/uuid/number args, update it to the new default (search the module for `Invalid value '`).

- [ ] **Step 7: Commit**

```bash
git add commands/src/main/java/tech/guilhermekaua/spigotboot/commands/CoreCommandMessages.java \
        commands/src/main/java/tech/guilhermekaua/spigotboot/commands/resolve/DefaultCommandArgumentResolverRegistry.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandInterceptorExecutionTest.java \
        commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandCooldownInterceptorTest.java
git commit -m "feat(commands): migrate built-in core resolvers to keyed messages"
```

---

### Task 7: Migrate Spigot resolvers + wire the renderer bean

**Files:**
- Create: `platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/SpigotCommandMessages.java`
- Modify: `platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitPlayerArgumentResolver.java`
- Modify: `platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitWorldArgumentResolver.java`
- Modify: `platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitOfflinePlayerArgumentResolver.java`
- Modify: `platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/configuration/SpigotCommandsConfiguration.java:76-89`
- Modify (test): `platform-spigot/commands-spigot/src/test/java/tech/guilhermekaua/spigotboot/commands/test/BukkitOfflinePlayerArgumentResolverTest.java:68-87`
- Test: `platform-spigot/commands-spigot/src/test/java/tech/guilhermekaua/spigotboot/commands/test/BukkitPlayerArgumentResolverMessageTest.java`

**Interfaces:**
- Consumes: `CommandMessageException`/`CommandMessageKey` (Task 1); `CommandMessageRenderer` bean (Task 4); resolver `messageKeys()` (Task 5).
- Produces: `enum SpigotCommandMessages implements CommandMessageKey { PLAYER_NOT_FOUND, PLAYER_AMBIGUOUS, WORLD_NOT_FOUND, OFFLINE_NOT_FOUND }`; the three Bukkit resolvers throw keyed failures; `SpigotCommandsConfiguration.commandDispatcher` passes the renderer.

- [ ] **Step 1: Write the failing tests**

Create `BukkitPlayerArgumentResolverMessageTest`:

```java
package tech.guilhermekaua.spigotboot.commands.test;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.SpigotCommandMessages;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BukkitPlayerArgumentResolverMessageTest {
    private final BukkitPlayerArgumentResolver resolver = new BukkitPlayerArgumentResolver();
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "target", "target", Player.class, Player.class, false, false, false, null, null);

    @Test
    void unknownPlayerThrowsNotFoundKey() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("ghost")).thenReturn(null);
            bukkit.when(() -> Bukkit.matchPlayer("ghost")).thenReturn(Collections.emptyList());

            CommandMessageException exception = assertThrows(
                    CommandMessageException.class,
                    () -> resolver.resolve(context, parameter, "ghost"));

            assertSame(SpigotCommandMessages.PLAYER_NOT_FOUND, exception.getKey());
            assertEquals("ghost", exception.getPlaceholders().get("input"));
        }
    }
}
```

Update `BukkitOfflinePlayerArgumentResolverTest.resolveRejectsUnknownOfflinePlayerAfterDirectLookup` (`:78-81`) to expect the keyed exception, and add the imports `tech.guilhermekaua.spigotboot.commands.CommandMessageException` and `tech.guilhermekaua.spigotboot.commands.spigot.resolve.SpigotCommandMessages`:

```java
            CommandMessageException exception = assertThrows(
                    CommandMessageException.class,
                    () -> resolver.resolve(context, parameter, "Missing")
            );
            assertSame(SpigotCommandMessages.OFFLINE_NOT_FOUND, exception.getKey());
```

Add `import static org.junit.jupiter.api.Assertions.assertSame;` if not present.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-spigot/commands-spigot -am test -Danimal.sniffer.skip=true -Dtest=BukkitPlayerArgumentResolverMessageTest+BukkitOfflinePlayerArgumentResolverTest`
Expected: COMPILE FAILURE (`SpigotCommandMessages` missing) / assertion failure.

- [ ] **Step 3: Create `SpigotCommandMessages`**

```java
package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of customizable message keys raised by the Spigot built-in argument
 * resolvers. Reference these constants from a {@code CommandMessageSource} to override the
 * corresponding messages.
 */
public enum SpigotCommandMessages implements CommandMessageKey {
    /** Raised when no online player matches the input. Placeholders: {@code {input}}. */
    PLAYER_NOT_FOUND("player.not-found", "No player named '{input}' is online.", "input"),
    /** Raised when the input matches several online players. Placeholders: {@code {input}}, {@code {count}}. */
    PLAYER_AMBIGUOUS("player.ambiguous", "'{input}' matches {count} players.", "input", "count"),
    /** Raised when no world matches the input. Placeholders: {@code {input}}. */
    WORLD_NOT_FOUND("world.not-found", "World '{input}' does not exist.", "input"),
    /** Raised when no known offline player matches the input. Placeholders: {@code {input}}. */
    OFFLINE_NOT_FOUND("offline-player.not-found", "Never seen a player named '{input}'.", "input");

    private final String id;
    private final String defaultTemplate;
    private final List<String> placeholders;

    SpigotCommandMessages(String id, String defaultTemplate, String... placeholders) {
        this.id = id;
        this.defaultTemplate = defaultTemplate;
        this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }

    @Override
    public List<String> placeholders() {
        return placeholders;
    }
}
```

- [ ] **Step 4: Migrate `BukkitPlayerArgumentResolver`**

Add imports:

```java
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.Arrays;
import java.util.Collection;
```

Replace the two throws inside `resolve`:

```java
            } else if (matches.size() > 1) {
                throw CommandMessageException.of(SpigotCommandMessages.PLAYER_AMBIGUOUS)
                        .with("input", input)
                        .with("count", matches.size());
            }
        }
        if (player == null) {
            throw CommandMessageException.of(SpigotCommandMessages.PLAYER_NOT_FOUND).with("input", input);
        }
```

Add the override:

```java
    @Override
    public Collection<CommandMessageKey> messageKeys() {
        return Arrays.asList(SpigotCommandMessages.PLAYER_NOT_FOUND, SpigotCommandMessages.PLAYER_AMBIGUOUS);
    }
```

- [ ] **Step 5: Migrate `BukkitWorldArgumentResolver`**

Add imports:

```java
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.Collection;
import java.util.Collections;
```

Replace the throw and add the override:

```java
        if (world == null) {
            throw CommandMessageException.of(SpigotCommandMessages.WORLD_NOT_FOUND).with("input", input);
        }
        return world;
    }

    @Override
    public Collection<CommandMessageKey> messageKeys() {
        return Collections.singletonList(SpigotCommandMessages.WORLD_NOT_FOUND);
    }
```

- [ ] **Step 6: Migrate `BukkitOfflinePlayerArgumentResolver`**

Add imports:

```java
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import java.util.Collection;
```

Replace the throw and add the override:

```java
        if (fallback.getName() == null && !fallback.hasPlayedBefore()) {
            throw CommandMessageException.of(SpigotCommandMessages.OFFLINE_NOT_FOUND).with("input", input);
        }
        return fallback;
    }

    @Override
    public Collection<CommandMessageKey> messageKeys() {
        return Collections.singletonList(SpigotCommandMessages.OFFLINE_NOT_FOUND);
    }
```

(`java.util.Collections` is already imported in this file.)

- [ ] **Step 7: Pass the renderer into the Spigot dispatcher**

In `SpigotCommandsConfiguration`, add the import:

```java
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
```

Replace the `commandDispatcher` bean (`:76-89`) so it accepts and forwards the renderer:

```java
    @Bean
    public CommandDispatcher commandDispatcher(CommandParameterBinder commandParameterBinder,
                                               CommandInvocationExecutor commandInvocationExecutor,
                                               CommandMessagesProvider commandMessagesProvider,
                                               tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver completionResolver,
                                               CommandInterceptorChain commandInterceptorChain,
                                               CommandMessageRenderer commandMessageRenderer) {
        return new CommandDispatcher(
                commandParameterBinder,
                commandInvocationExecutor,
                commandMessagesProvider,
                completionResolver,
                commandInterceptorChain,
                commandMessageRenderer
        );
    }
```

(The existing file already uses one fully-qualified `CompletionResolver` reference; keep that style as-is to match surrounding code.)

- [ ] **Step 8: Run the Spigot module tests**

Run: `mvnw.cmd -pl platform-spigot/commands-spigot -am test -Danimal.sniffer.skip=true`
Expected: PASS — new message tests pass, the updated offline-player test passes, no other Spigot test asserts the old generic player/world text.

- [ ] **Step 9: Commit**

```bash
git add platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/SpigotCommandMessages.java \
        platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitPlayerArgumentResolver.java \
        platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitWorldArgumentResolver.java \
        platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitOfflinePlayerArgumentResolver.java \
        platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/configuration/SpigotCommandsConfiguration.java \
        platform-spigot/commands-spigot/src/test/java/tech/guilhermekaua/spigotboot/commands/test/BukkitOfflinePlayerArgumentResolverTest.java \
        platform-spigot/commands-spigot/src/test/java/tech/guilhermekaua/spigotboot/commands/test/BukkitPlayerArgumentResolverMessageTest.java
git commit -m "feat(commands-spigot): migrate Bukkit resolvers to keyed messages"
```

---

### Task 8: Full reactor verification

**Files:** none (verification only).

- [ ] **Step 1: Build and test the whole reactor**

Run: `mvnw.cmd test -B -Danimal.sniffer.skip=true`
Expected: BUILD SUCCESS across all modules. BungeeCord modules are unchanged (their dispatcher uses the preserved 5-arg constructor and their resolvers still throw plain exceptions through the generic path), so they stay green.

- [ ] **Step 2: If any module fails, triage**

If a BungeeCord or test-plugin assertion fails on a built-in *core* resolver message (e.g. a number/enum arg), update it to the new keyed default (`'<input>' is not a valid number.` etc.) — this is the intended, spec-approved behavior change. Do not change BungeeCord resolvers; their migration is out of scope.

- [ ] **Step 3: Commit any test fixes**

```bash
git add -A
git commit -m "test: update assertions for keyed built-in resolver messages"
```
