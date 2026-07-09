# ChatUtils — temporary chat conversation utility

Date: 2026-07-09
Module: `platform-spigot/core-spigot`
Branch: `feat/chatutils-conversation`

## Problem

Plugin code frequently needs to ask a player a question in chat and capture their
next message: "type the amount to deposit", "enter the new nickname", "type `cancel`
to abort". Doing this by hand means registering a listener, tracking which players are
mid-prompt, cancelling the event so the reply does not broadcast to public chat, and
hopping off the async chat thread before touching the world. `ChatUtils` collapses all
of that into a one-liner.

## Goal / measurable outcome

A developer starts a captured chat prompt in a single fluent call and never writes a
listener, a per-player map, or a thread hop. Verified by the gate-test suite: every
behavior below (capture, cancel, re-prompt, end, timeout, disconnect, replace,
passthrough, async) has a passing JUnit test in the same commit.

## Public API

```java
// Fluent builder
ChatUtils.with(player)
    .firstPrompt("§eEnter an amount, or type cancel")   // optional: message sent on start
    .timeout(30, TimeUnit.SECONDS)                       // optional: inactivity timeout
    .onTimeout(p -> p.sendMessage("§cTimed out."))       // optional
    .onEnd((p, reason) -> reopenMenu(p))                 // optional: fired on ANY end
    .async()                                             // optional: run callback on chat thread
    .onChat(ctx -> {                                     // terminal: starts the conversation
        Player p = ctx.getPlayer();
        String msg = ctx.getMessage();

        if (msg.equalsIgnoreCase("cancel")) {
            ctx.end();
            return;
        }
        try {
            int amount = Integer.parseInt(msg);
            // ... do something, then:
            ctx.end();
        } catch (NumberFormatException e) {
            p.sendMessage("§cInvalid number, keep trying."); // does NOT end → next message re-fires
        }
    });

// Shorthand for the no-options case
ChatUtils.onChat(player, ctx -> { /* ... */ });
```

`onChat(...)` returns `void`. The conversation is ended by `ctx.end()`, timeout,
disconnect, plugin disable, or being replaced — there is no external handle (chosen for
minimal surface).

### Types

| Type | Kind | Role |
|---|---|---|
| `ChatUtils` | public final, static-only | Facade. `with(Player)` → `ChatPrompt`; `onChat(Player, Consumer<ChatContext>)` shorthand. Holds the installed `ChatConversationManager`. |
| `ChatPrompt` | public final | Builder. `firstPrompt`, `timeout`, `onTimeout`, `onEnd`, `async`, terminal `onChat`. |
| `ChatContext` | public interface | Per-message context: `Player getPlayer()`, `String getMessage()`, `void end()`. |
| `EndReason` | public enum | `ENDED`, `TIMEOUT`, `DISCONNECT`, `PLUGIN_DISABLE`, `REPLACED`. |
| `ChatConversationManager` | `@Component`, `@ApiStatus.Internal` | `implements Listener, ContextReadyListener`. Owns the registry, scheduler, plugin. |
| `ActiveConversation` | package-private | Internal per-player state: callbacks, async flag, timeout `PlatformTask`, timeout config. |

## Architecture

### Why a manager plus a static facade

A pure-static utility cannot obtain the `Plugin` (to register a listener) or the
`PlatformScheduler` (to hop to the main thread). So the real work lives in a normal
DI component, and `ChatUtils` is a thin static facade over it.

- `ChatConversationManager` is a `@Component` with constructor injection of
  `Plugin` and `PlatformScheduler`. It implements `Listener`, so
  `BukkitListenerAutoRegistrar` auto-registers its `@EventHandler`s at context-ready
  and unregisters them at shutdown — identical to `ViewListener` in inventory-api.
- It also implements `ContextReadyListener`. On `onContextReady` it calls
  `ChatUtils.install(this)` and `context.registerShutdownHook(...)` to end every active
  conversation with `PLUGIN_DISABLE` and call `ChatUtils.uninstall()`.
- `ChatUtils.with(...)` / `onChat(...)` throw `IllegalStateException` with a clear
  message if the manager is not installed (context not ready yet).

Because core-spigot is shaded and relocated into each host plugin, each plugin has its
own `ChatUtils` class and its own manager instance — no cross-plugin static collision.

### Registry

`ConcurrentHashMap<UUID, ActiveConversation>` on the manager. Starting a prompt for a
player who already has one ends the previous with `EndReason.REPLACED`, then installs
the new one.

### Event flow (threading)

`AsyncPlayerChatEvent` fires on an async thread. Handler at `EventPriority.LOWEST`,
`ignoreCancelled = false`:

1. Look up `ActiveConversation` by `event.getPlayer().getUniqueId()`. Absent → return,
   event untouched (non-participants chat normally).
2. Present → `event.setCancelled(true)` **on the async thread** (this is the only work
   that must happen synchronously inside the event, and it stops the broadcast).
3. Capture `event.getMessage()`. Build a `ChatContext`.
4. Reset the inactivity timeout task (if configured).
5. Dispatch the callback:
   - default → `scheduler.runOnEntity(player, () -> callback.accept(ctx), null)`
     (main thread on legacy, player's region thread on Folia — safe for world/inventory).
   - `.async()` → invoke `callback.accept(ctx)` inline on the chat thread.

`ctx.end()` removes the conversation from the registry, cancels the timeout task, and
fires `onEnd(ENDED)`. It is idempotent (ending an already-ended conversation is a no-op).

### Timeout

`.timeout(duration, unit)` sets an inactivity window. On start and after every captured
message the manager (re)schedules `scheduler.runOnEntityLater(player, timeoutTask,
delayTicks)` where `delayTicks = Utils.millisToTicks(unit.toMillis(duration))`,
minimum 1. When it fires and the conversation is still active: remove it, fire
`onTimeout(player)` then `onEnd(TIMEOUT)`. Cancelled whenever the conversation ends for
any other reason.

### Lifecycle end reasons

| Reason | Trigger |
|---|---|
| `ENDED` | `ctx.end()` called from the callback |
| `TIMEOUT` | inactivity timeout elapsed |
| `DISCONNECT` | `PlayerQuitEvent` for the player |
| `PLUGIN_DISABLE` | context shutdown hook (framework/plugin disabling) |
| `REPLACED` | a new prompt started for a player already in one |

`onEnd(player, reason)` fires exactly once per conversation, for every reason.
`onTimeout` fires only for `TIMEOUT`, immediately before `onEnd(TIMEOUT)`.

### Event API choice

Uses `org.bukkit.event.player.AsyncPlayerChatEvent`. It is deprecated on Paper in favor
of the Adventure `AsyncChatEvent`, but it is present on every Spigot/Paper build the
framework targets and is supported by MockBukkit. Documented as a known constraint; a
future Adventure-aware path can be added behind the same facade without changing the
public API.

## Error handling

- Callback throwing is caught and logged via the plugin logger (a bad prompt callback
  must not corrupt the registry or kill the chat pipeline); the conversation stays
  active unless the callback called `end()`.
- `end()` is idempotent and thread-safe (registry is concurrent; removal is atomic
  `remove(uuid, conversation)` so a stale end cannot evict a newer conversation).
- Rapid double messages: the async handler cancels and dispatches per message; callback
  ordering follows the scheduler. Acceptable; documented.

## Testing (gate tests — deterministic, MockBukkit + JUnit 5 + Mockito)

A synchronous `PlatformScheduler` test double runs submitted runnables inline so
main-thread dispatch is deterministic without ticking a scheduler.

1. `captureAndCancel` — player in a prompt: firing `AsyncPlayerChatEvent` invokes the
   callback with the exact message and the event ends cancelled.
2. `nonParticipantPassesThrough` — a player without a prompt: event not cancelled,
   callback never invoked.
3. `repromptKeepsActive` — callback that does not call `end()`: a second message
   re-fires the callback.
4. `endStopsCapture` — after `ctx.end()`, a further message is not captured and the
   event is not cancelled; `onEnd(ENDED)` fired once.
5. `firstPromptSent` — `.firstPrompt("...")` sends the message to the player on start.
6. `timeoutFires` — inactivity timeout removes the conversation and fires
   `onTimeout` then `onEnd(TIMEOUT)`; a message before expiry resets the window.
7. `disconnectEnds` — `PlayerQuitEvent` fires `onEnd(DISCONNECT)` and stops capture.
8. `replaceEndsPrevious` — starting a second prompt fires `onEnd(REPLACED)` on the
   first and the new callback receives subsequent messages.
9. `asyncRunsInline` — `.async()` runs the callback on the calling (event) thread, no
   scheduler dispatch.
10. `withBeforeInstallThrows` — `ChatUtils.with(player)` before the manager is installed
    throws `IllegalStateException`.
11. `pluginDisableEndsAll` — the shutdown hook ends every active conversation with
    `PLUGIN_DISABLE` and uninstalls the facade.
12. `callbackThrowKeepsConversation` — a throwing callback is logged and the
    conversation survives (unless it called `end()`).

## No eval suite

This is 100% deterministic code with no LLM call. Per CLAUDE.md's latent-vs-deterministic
rule the JUnit gate tests are the complete verification; an LLM eval suite does not apply.

## Out of scope (YAGNI)

- Adventure `AsyncChatEvent` path (facade leaves room; not built now).
- Multi-step conversation graphs / prompt chaining (callback re-prompts by not ending).
- Per-message attempt counters, regex validators, numeric parsers (caller does this).
- External `Conversation` handle (chose void return).
- Bungee/proxy port (no player chat capture concept there in the same shape).
