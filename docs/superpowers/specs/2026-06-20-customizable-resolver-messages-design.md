# Customizable Argument-Resolver Messages Design

Date: 2026-06-20
Status: Approved design, pending spec review
Target modules: `commands` (core mechanism + core built-in resolver migration),
`platform-spigot/commands-spigot` (Spigot resolver migration)

## Background

A command argument resolver turns a raw token into a typed value. When the input is
invalid, the resolver throws — for example
[`BukkitPlayerArgumentResolver`](../../../platform-spigot/commands-spigot/src/main/java/tech/guilhermekaua/spigotboot/commands/spigot/resolve/BukkitPlayerArgumentResolver.java)
throws `new IllegalArgumentException("Player not found: " + input)` for an offline player
and `"Ambiguous player name: " + input` when the input matches several players.

The player never sees those messages. The pipeline discards them:

1. [`CommandParameterBinder.bindParsed`](../../../commands/src/main/java/tech/guilhermekaua/spigotboot/commands/binding/CommandParameterBinder.java)
   calls `resolver.resolve(...)`, catches the thrown `Exception`, and wraps it in
   `CommandBindingException.invalid(parameter, input, e)` — the resolver's message
   survives only as the (never-rendered) exception cause.
2. [`CommandDispatcher.handleBindingException`](../../../commands/src/main/java/tech/guilhermekaua/spigotboot/commands/execution/CommandDispatcher.java)
   maps `INVALID_ARGUMENT` to `messages.invalidArgumentValue(context, parameter, input)`,
   producing the generic `Invalid value 'player123' for argument: target`.

A downstream plugin **can** already replace the whole `CommandMessages` bean (the
[`CommandMessagesProvider`](../../../commands/src/main/java/tech/guilhermekaua/spigotboot/commands/message/CommandMessagesProvider.java)
picks up any `CommandMessages` / `@Primary` bean), but that yields **one**
`invalidArgumentValue` string for *every* type and *every* failure reason. It cannot tell
"not found" from "ambiguous", and it receives none of the resolver's context.

This effort lets a resolver raise a **keyed, customizable** failure that the downstream
can override per reason — programmatically, via a focused SPI bean — while non-keyed
failures keep today's exact behavior.

## Goals

- A resolver can signal a *specific* failure (`player.not-found` vs `player.ambiguous`)
  carrying named placeholders and a default English template.
- A downstream plugin overrides the message for any key by registering one bean, with no
  per-resolver glue and no edits to framework code.
- The set of keys is **discoverable** without reading resolver source: a single enum per
  key-owner (autocomplete + Javadoc) for compile-time reference, plus an opt-in runtime
  catalog for programmatic enumeration across all registered resolvers.
- Backward compatible: resolvers that throw ordinary exceptions still render through
  `CommandMessages.invalidArgumentValue` exactly as today.

## Non-goals

- Config-file-backed messages (a `CommandMessageSource` reading `messages.yml`). The
  design leaves room for it (see "Future") but it is out of scope; the chosen
  customization path is programmatic.
- Startup logging of registered keys. Explicitly rejected as noise. The catalog is a
  passive injectable that logs nothing.
- BungeeCord built-in resolver migration. The core mechanism is platform-agnostic and
  lives in `commands`; BungeeCord parity (its own key enum + dispatcher wiring) is a
  trivial follow-on, out of scope here.
- Color/format handling (`&c`, MiniMessage). Templates are raw strings; whatever
  `CommandSenderHandle.sendMessage` does today with them is unchanged.

## Design overview

```
resolver.resolve(...)                      throws CommandMessageException (key + placeholders)
   │
   ▼
CommandParameterBinder.bindParsed          catches CommandMessageException, rethrows as-is
   │                                       (ordinary Exception still → CommandBindingException.invalid)
   ▼
CommandDispatcher.execute                  catches CommandMessageException
   │                                       notifies interceptor chain (onError), then renders
   ▼
CommandMessageRenderer.render(ctx, ex)     template = source.resolveTemplate(ctx, key)
   │                                                ?? key.defaultTemplate()
   │                                       interpolate {placeholders}
   ▼
context.sendMessage(text)
```

### New core types (module `commands`)

**`CommandMessageKey`** — the abstraction (interface), so keys are open for extension:

```java
public interface CommandMessageKey {
    String id();                  // stable identifier, e.g. "player.not-found"
    String defaultTemplate();     // fallback text, e.g. "No player named '{input}' is online."
    List<String> placeholders();  // documented placeholder names, e.g. ["input"]
}
```

**`CommandMessageException extends RuntimeException`** — raised by a resolver to signal a
keyed failure. Immutable bag of (key, placeholder values):

```java
CommandMessageException.of(SpigotCommandMessages.PLAYER_NOT_FOUND)
        .with("input", input);          // chainable; values are Object, rendered via String.valueOf
```

It exposes `getKey()` and `getPlaceholders()` (a `Map<String, Object>`). Its
`getMessage()` returns the *interpolated default template* so logs/stack traces remain
readable even though final delivery goes through the renderer.

**`CommandMessageSource`** — the SPI a downstream implements (the customization point):

```java
public interface CommandMessageSource {
    /**
     * @return the template to use for {@code key}, or {@code null} to fall through to
     *         {@code key.defaultTemplate()}.
     */
    String resolveTemplate(CommandExecutionContext context, CommandMessageKey key);
}
```

`context` is passed so an implementation can branch on the sender (forward room for
per-player locale) — not used by the framework itself.

**`CommandMessageSourceProvider`** — mirrors `CommandMessagesProvider`: resolves the active
`CommandMessageSource` bean from the dependency context (dedupe by identity; require
exactly one `@Primary` if several exist). Returns a no-op source (always `null`) when none
is registered, so the default templates are used.

**`CommandMessageRenderer`** — single responsibility: turn a `CommandMessageException`
into the final string.

```java
String render(CommandExecutionContext context, CommandMessageException ex) {
    CommandMessageSource source = sourceProvider.resolve(context.getContext());
    String template = source.resolveTemplate(context, ex.getKey());
    if (template == null) template = ex.getKey().defaultTemplate();
    return interpolate(template, ex.getPlaceholders());
}
```

Interpolation is literal `{name}` → `String.valueOf(value)` replacement. A `{name}` with
no bound value is left verbatim (documented behavior). The interpolation helper is a small
package-private utility shared by the renderer and `CommandMessageException.getMessage()`
so both produce identical text.

**`CommandMessageCatalog`** — opt-in injectable bean aggregating
`CommandArgumentResolver.messageKeys()` across all registered resolvers. Exposes
`Collection<CommandMessageKey> all()` and `Optional<CommandMessageKey> find(String id)`.
**Logs nothing.** It must source resolvers from the **`CommandArgumentResolverRegistry`**,
not the injected `List<CommandArgumentResolver<?>>` — the five core built-ins are registered
*inside* `DefaultCommandArgumentResolverRegistry.registerBuiltIns()` and are absent from the
bean list, so building the catalog from the list would silently miss `CoreCommandMessages`
keys. This requires a small read accessor on the registry (e.g.
`Collection<CommandArgumentResolver<?>> all()`), which `DefaultCommandArgumentResolverRegistry`
already has the data for.

### Resolver SPI addition (module `commands`)

`CommandArgumentResolver` gains an optional declaration of the keys it can emit, mirroring
the existing optional `defaultCompletionProvider()`:

```java
default Collection<CommandMessageKey> messageKeys() {
    return Collections.emptyList();
}
```

Only used by the catalog; resolvers that never raise keyed failures ignore it.

### Pipeline changes (module `commands`)

- **`CommandParameterBinder.bindParsed`**: add `catch (CommandMessageException e) { throw e; }`
  *before* the existing generic `catch (Exception e)` (the same shape as the existing
  `catch (CommandBindingException e) { throw e; }`). Keyed failures propagate untouched;
  every other exception still becomes `CommandBindingException.invalid(...)`.

- **`CommandDispatcher.execute`**: add a `catch (CommandMessageException e)` next to the
  existing `catch (CommandBindingException e)` around `parameterBinder.bind(...)`. It calls
  `resolvedChain.onError(context, invocation, e)` (parity with binding-exception handling),
  then `context.sendMessage(messageRenderer.render(context, e))`, then `return true`.
  `CommandDispatcher` gains a `CommandMessageRenderer` constructor dependency.

`CommandBindingException` and `handleBindingException` are **untouched** — keyed messages
are intentionally not modeled as a binding-exception `Kind`, keeping the message concept
decoupled from binding semantics.

### Built-in keys + resolver migration (module `commands`)

The core built-in resolvers are private nested classes in
[`DefaultCommandArgumentResolverRegistry`](../../../commands/src/main/java/tech/guilhermekaua/spigotboot/commands/resolve/DefaultCommandArgumentResolverRegistry.java).
They get a single discoverable enum, mirroring the Spigot one:

```java
public enum CoreCommandMessages implements CommandMessageKey {
    BOOLEAN_INVALID ("boolean.invalid", "'{input}' is not a valid true/false value.", "input"),
    NUMBER_INVALID  ("number.invalid",  "'{input}' is not a valid number.",           "input"),
    ENUM_INVALID    ("enum.invalid",    "'{input}' is not a valid option. Valid: {options}.", "input", "options"),
    UUID_INVALID    ("uuid.invalid",    "'{input}' is not a valid UUID.",             "input");
    // constructor stores id/defaultTemplate/placeholders; implements the interface
}
```

Migrate the four *user-input-validating* nested resolvers to throw keyed failures and
declare `messageKeys()`:

- `BooleanArgumentResolver` → `BOOLEAN_INVALID` (`{input}`).
- `NumericArgumentResolver` → `NUMBER_INVALID` (`{input}`); catch `NumberFormatException`
  around the `parseX` calls and rethrow as the keyed exception.
- `EnumArgumentResolver` → `ENUM_INVALID` (`{input}`, `{options}` = comma-joined valid
  constant names, which it already enumerates for completion).
- `UuidArgumentResolver` → `UUID_INVALID` (`{input}`); catch the `IllegalArgumentException`
  from `UUID.fromString` and rethrow as keyed.

`StringArgumentResolver` never fails, so it gets no key. `NumericArgumentResolver`'s
internal `"Unsupported numeric type"` branch stays a plain `IllegalArgumentException` — it
is a programming error (unsupported parameter type), not bad user input, so it must not be
presented as a user-facing keyed message.

### Built-in keys + resolver migration (module `commands-spigot`)

A single enum is the one discoverable entry point for all built-in Spigot keys:

```java
public enum SpigotCommandMessages implements CommandMessageKey {
    PLAYER_NOT_FOUND  ("player.not-found",  "No player named '{input}' is online.", "input"),
    PLAYER_AMBIGUOUS  ("player.ambiguous",  "'{input}' matches {count} players.",   "input", "count"),
    WORLD_NOT_FOUND   ("world.not-found",   "World '{input}' does not exist.",       "input"),
    OFFLINE_NOT_FOUND ("offline-player.not-found", "Never seen a player named '{input}'.", "input");
    // constructor stores id/defaultTemplate/placeholders; implements the interface
}
```

Migrate the three Bukkit resolvers to throw keyed failures and declare `messageKeys()`:

- `BukkitPlayerArgumentResolver`: `PLAYER_NOT_FOUND` (`{input}`), `PLAYER_AMBIGUOUS`
  (`{input}`, `{count}`).
- `BukkitWorldArgumentResolver`: `WORLD_NOT_FOUND` (`{input}`).
- `BukkitOfflinePlayerArgumentResolver`: `OFFLINE_NOT_FOUND` (`{input}`).

### Bean wiring

- `CommandsConfiguration` (core): add `@Bean` for `CommandMessageSourceProvider`,
  `CommandMessageRenderer` (depends on the provider), and `CommandMessageCatalog` (depends
  on `CommandArgumentResolverRegistry`); add the `all()` read accessor to the
  `CommandArgumentResolverRegistry` interface and `DefaultCommandArgumentResolverRegistry`.
- `SpigotCommandsConfiguration`: pass the `CommandMessageRenderer` into the
  `CommandDispatcher` `@Bean`.

## Downstream usage (the deliverable)

Override "player not found" and "ambiguous" with a single bean — no config, no framework
edits:

```java
@Component
public class MyCommandMessages implements CommandMessageSource {
    @Override
    public String resolveTemplate(CommandExecutionContext context, CommandMessageKey key) {
        if (key instanceof SpigotCommandMessages) {
            switch ((SpigotCommandMessages) key) {
                case PLAYER_NOT_FOUND: return "&cO jogador '{input}' não está online.";
                case PLAYER_AMBIGUOUS: return "&e'{input}' corresponde a {count} jogadores.";
                default: break;
            }
        }
        return null; // any key left unhandled keeps its default template
    }
}
```

Discoverability: type `CoreCommandMessages.` or `SpigotCommandMessages.` for autocomplete of
every built-in key, with Javadoc documenting each key's meaning and placeholders. The same
`resolveTemplate` bean overrides core keys (e.g. `CoreCommandMessages.NUMBER_INVALID`) and
Spigot keys alike. A downstream's own resolver implements `CommandMessageKey` for its own
keys (its own enum), and—if desired—injects `CommandMessageCatalog` to enumerate everything
registered at runtime.

## Backward compatibility

- `CommandMessages.invalidArgumentValue` and the `CommandBindingException.INVALID_ARGUMENT`
  path are **retained** as the generic fallback. Any resolver that throws an ordinary
  exception — notably **downstream/custom resolvers** that don't opt into keyed messages —
  still routes through `invalidArgumentValue` exactly as today. Keyed messages are the
  opt-in nicer path, not a replacement.
- All **built-in** resolvers (the four core ones above plus the three Spigot ones) now opt
  in, so their default output changes. Examples with **no** `CommandMessageSource`
  registered:
  - `amount` (int) bad input: was `Invalid value 'oops' for argument: amount`, now
    `'oops' is not a valid number.`
  - offline player: was `Invalid value 'x' for argument: target`, now
    `No player named 'x' is online.`
- Existing tests asserting the old generic text for migrated resolvers must be updated to
  the new defaults. Known cases:
  [`CommandInterceptorExecutionTest`](../../../commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandInterceptorExecutionTest.java)
  and
  [`CommandCooldownInterceptorTest`](../../../commands/src/test/java/tech/guilhermekaua/spigotboot/commands/test/CommandCooldownInterceptorTest.java)
  assert `Invalid value 'oops' for argument: amount` for an integer arg; the latter also
  defines a test `CommandMessages` overriding `invalidArgumentValue`, whose expectation must
  move to the keyed path (`CommandMessageSource` / the `number.invalid` default).

## Testing

Core (`commands`):
- `CommandMessageException` builder: key retained, placeholders accumulate, `getMessage()`
  interpolates the default.
- `CommandMessageRenderer`: (a) source returns a template → used + interpolated; (b) source
  returns `null` → default template + interpolated; (c) no source bean → default; (d)
  `{unbound}` placeholder left verbatim.
- `CommandMessageSourceProvider`: none → no-op; one → it; multiple + one `@Primary` → the
  primary; multiple + none primary → `IllegalStateException` (parity with
  `CommandMessagesProvider`).
- `CommandMessageCatalog`: aggregates keys from multiple resolvers; `find(id)`; logs
  nothing.
- Built-in core resolvers: `Boolean`/`Numeric`/`Enum`/`Uuid` throw the correct
  `CoreCommandMessages` key with expected placeholders (`enum.invalid` includes
  `{options}`); `NumericArgumentResolver`'s unsupported-type branch still throws a plain
  `IllegalArgumentException` (not keyed).
- Dispatcher integration: a resolver throwing `CommandMessageException` with a registered
  source → sender sees the overridden text; without a source → default text; interceptor
  `onError` is invoked.
- Update `CommandInterceptorExecutionTest` / `CommandCooldownInterceptorTest` to the new
  `number.invalid` default (and re-point the latter's `invalidArgumentValue` override test
  onto the keyed path), confirming the migration's behavior change is intentional.

Spigot (`commands-spigot`):
- Each migrated resolver throws the correct key with the expected placeholder values
  (MockBukkit).
- End-to-end via the existing command test harness: `/trade <offline>` and `/trade <ambiguous>`
  yield distinct, overridable messages.

## Future (explicitly out of scope)

- A `messages.yml`-backed `CommandMessageSource` shipped in `commands-spigot` (reads keys
  from config via the config module). Drops in behind the same SPI with zero changes to
  core or resolvers; the catalog can generate a starter file.
- BungeeCord parity: a `BungeeCommandMessages` enum + migrating
  `BungeeProxiedPlayerArgumentResolver` / `BungeeServerArgumentResolver`, and passing the
  renderer into the BungeeCord `CommandDispatcher` wiring.
- Per-player locale selection inside a `CommandMessageSource` (the `context` argument
  already supports it).
