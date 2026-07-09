# ChatUtils Temporary Chat Conversation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give plugin code a one-line fluent API to prompt a player in chat and capture their next message(s), without hand-registering a listener, tracking per-player state, hopping off the async chat thread, or leaking the reply into public chat.

**Architecture:** A public static facade `ChatUtils` delegates to an internal `@Component` `ChatConversationManager` (constructor-injected `Plugin` + `PlatformScheduler`). The manager implements `Listener` (auto-registered by `BukkitListenerAutoRegistrar`) and `ContextReadyListener` (installs the facade + registers a shutdown hook). Per-player conversations live in a `ConcurrentHashMap<UUID, ActiveConversation>`. `AsyncPlayerChatEvent` is captured at `LOWEST` priority: the event is cancelled on the async thread and the callback is dispatched to the main/region thread via the scheduler (or inline when `.async()` is set).

**Tech Stack:** Java, Bukkit/Spigot API (`AsyncPlayerChatEvent`, `PlayerQuitEvent`), spigot-boot DI (`@Component`, `@Inject`, `ContextReadyListener`), `PlatformScheduler` (Folia-aware), `ChatMarkup` + `HexSupport` (in-module chat markup), JUnit 5, Mockito, MockBukkit-v1.20 (3.20.2).

## Global Constraints

- **Module:** `platform-spigot/core-spigot`. All production code under package `tech.guilhermekaua.spigotboot.core.spigot.conversation`; tests under `tech.guilhermekaua.spigotboot.core.spigot.conversation` in `src/test/java`.
- **No new dependencies.** `ChatMarkup`, `HexSupport`, `PlatformScheduler`, `Utils`, and the DI annotations already exist in this module or its deps.
- **License header:** every new `.java` file MUST begin with the exact MIT header block below (verbatim, same as every other file in the module):

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
```

- **Test method naming:** camelCase, matching the nearby core-spigot tests (`BukkitListenerAutoRegistrarTest`, `PlatformSchedulersTest`).
- **Build/test runtime:** run Maven with **JDK 21** (`JAVA_HOME` → a JDK 21; JDK 25 crashes Lombok per project memory). In this Claude Code environment the Bash tool must set `dangerouslyDisableSandbox: true` for Maven commands, or edits do not take effect (project memory: "Maven build needs sandbox disabled").
- **Single-class test command:** from repo root
  `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="<ClassName>"`
- **Full-module test command:** `./mvnw -q -pl platform-spigot/core-spigot -am test`
- **Threading contract:** `AsyncPlayerChatEvent` fires async. The only work done synchronously inside the handler is `event.setCancelled(true)` + capturing `getMessage()`. The callback runs on the main/region thread by default (`scheduler.runOnEntity`), or inline on the chat thread when `.async()` is set.
- **Every guarded callback** (user `onChat`, `onTimeout`, `onEnd`) is invoked through `runGuarded(...)` so a thrown exception is logged via `plugin.getLogger()` and never corrupts the registry or the chat pipeline.

## File Structure

| File | Responsibility |
|---|---|
| `.../conversation/EndReason.java` | Public enum: why a conversation ended. |
| `.../conversation/ChatContext.java` | Public interface passed to the callback: `getPlayer()`, `getMessage()`, `end()`. |
| `.../conversation/ChatPrompt.java` | Public fluent builder: `firstPrompt`, `timeout`, `onTimeout`, `onEnd`, `async`, terminal `onChat`. |
| `.../conversation/ChatUtils.java` | Public static facade: `with(player)`, `onChat(player, cb)`, package-private `install`/`uninstall`. |
| `.../conversation/ChatConversationManager.java` | Internal `@Component`: registry, event handlers, dispatch, timeout, lifecycle. Holds nested `ActiveConversation` + `ConversationContext`. |
| `src/test/.../conversation/support/FakeScheduler.java` | Test `PlatformScheduler`: inline no-delay tasks, captured delayed tasks. |
| `src/test/.../conversation/ChatConversationManagerTest.java` | Unit tests: begin/end/replace/dispatch/async/throw. |
| `src/test/.../conversation/ChatContextCaptureTest.java` | Unit tests: event capture/cancel/re-prompt/passthrough/disconnect. |
| `src/test/.../conversation/FirstPromptRenderTest.java` | Unit test: firstPrompt rendered through ChatMarkup + HexSupport. |
| `src/test/.../conversation/TimeoutTest.java` | Unit tests: inactivity timeout + reset. |
| `src/test/.../conversation/ChatUtilsLifecycleTest.java` | MockBukkit + lifecycle: end-to-end capture, install-on-ready, plugin-disable, with()-guard. |

---

### Task 1: `EndReason` enum

**Files:**
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/EndReason.java`
- Test: `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/EndReasonTest.java`

**Interfaces:**
- Produces: `enum EndReason { ENDED, TIMEOUT, DISCONNECT, PLUGIN_DISABLE, REPLACED }` — referenced by `ChatPrompt.onEnd`, `ChatConversationManager`, and every test.

- [ ] **Step 1: Write the failing test**

`EndReasonTest.java` (prepend the MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EndReasonTest {

    @Test
    void hasExactlyTheFiveDocumentedReasons() {
        assertEquals(5, EndReason.values().length,
                "EndReason must stay a closed set the manager exhaustively handles");
        assertNotNull(EndReason.valueOf("ENDED"));
        assertNotNull(EndReason.valueOf("TIMEOUT"));
        assertNotNull(EndReason.valueOf("DISCONNECT"));
        assertNotNull(EndReason.valueOf("PLUGIN_DISABLE"));
        assertNotNull(EndReason.valueOf("REPLACED"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="EndReasonTest"`
Expected: FAIL — compilation error, `EndReason` does not exist.

- [ ] **Step 3: Write minimal implementation**

`EndReason.java` (prepend the MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

/**
 * Why a {@link ChatUtils} conversation ended. Passed to the {@code onEnd} hook.
 */
public enum EndReason {
    /** The callback called {@link ChatContext#end()}. */
    ENDED,
    /** The inactivity timeout elapsed with no message. */
    TIMEOUT,
    /** The player left the server ({@code PlayerQuitEvent}). */
    DISCONNECT,
    /** The owning plugin/context is shutting down. */
    PLUGIN_DISABLE,
    /** A new prompt was started for a player already in one. */
    REPLACED
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="EndReasonTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/EndReason.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/EndReasonTest.java
git commit -m "feat(core-spigot): add EndReason for chat conversations"
```

---

### Task 2: Conversation core — `ChatContext`, `ChatPrompt`, `ChatUtils`, `ChatConversationManager`

This task builds the whole conversation engine except the chat-event handler (Task 3), firstPrompt rendering (Task 4), timeout (Task 5), and lifecycle wiring (Task 6). It ends with a testable engine: start a prompt, dispatch a message into it, end it, replace it.

**Files:**
- Create: `.../conversation/ChatContext.java`
- Create: `.../conversation/ChatPrompt.java`
- Create: `.../conversation/ChatUtils.java`
- Create: `.../conversation/ChatConversationManager.java`
- Create (test util): `src/test/java/.../conversation/support/FakeScheduler.java`
- Test: `src/test/java/.../conversation/ChatConversationManagerTest.java`

**Interfaces:**
- Consumes: `EndReason` (Task 1); `PlatformScheduler` / `PlatformTask` (`runOnEntity(Entity, Runnable, Runnable)`, `runOnEntityLater(Entity, Runnable, Runnable, long)`, `cancel()`, `isCancelled()`); `org.bukkit.plugin.Plugin`.
- Produces (relied on by Tasks 3-6):
  - `interface ChatContext { Player getPlayer(); String getMessage(); void end(); }`
  - `final class ChatPrompt` with `ChatPrompt firstPrompt(String)`, `ChatPrompt timeout(long, TimeUnit)`, `ChatPrompt onTimeout(Consumer<Player>)`, `ChatPrompt onEnd(BiConsumer<Player, EndReason>)`, `ChatPrompt async()`, `void onChat(Consumer<ChatContext>)`; package-private ctor `ChatPrompt(Player, ChatConversationManager)`; package-visible fields read by the manager.
  - `final class ChatUtils` with `static ChatPrompt with(Player)`, `static void onChat(Player, Consumer<ChatContext>)`, package-private `static void install(ChatConversationManager)`, `static void uninstall()`.
  - `class ChatConversationManager` with `@Inject ChatConversationManager(Plugin, PlatformScheduler)`, package-private `void begin(ChatPrompt)`, private `end(...)`/`dispatch(...)`, nested `ActiveConversation`. Tasks 3-6 add `onChat`, `onQuit`, `onContextReady`, `scheduleTimeout`, `sendFirstPrompt`.

- [ ] **Step 1: Create the `ChatContext` interface**

`ChatContext.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;

/**
 * The per-message context handed to a {@link ChatUtils} callback. A fresh instance is
 * created for every captured message.
 */
public interface ChatContext {

    /** @return the player in this conversation. */
    Player getPlayer();

    /** @return the exact message the player just typed. */
    String getMessage();

    /**
     * Ends the conversation now. Idempotent; safe to call more than once and safe to call
     * from a stale context. Fires the {@code onEnd} hook with {@link EndReason#ENDED}.
     */
    void end();
}
```

- [ ] **Step 2: Create the `FakeScheduler` test double**

`src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/support/FakeScheduler.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation.support;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic {@link PlatformScheduler} for tests: no-delay tasks run inline; delayed tasks
 * are captured so a test can fire them on demand. Counts inline dispatches so tests can prove
 * the async path bypasses the scheduler.
 */
public final class FakeScheduler implements PlatformScheduler {

    public int runOnEntityCount = 0;
    private final List<FakeTask> pendingLater = new ArrayList<>();

    /** Fires every captured, not-yet-cancelled delayed task, then clears the queue. */
    public void fireAllLater() {
        List<FakeTask> snapshot = new ArrayList<>(pendingLater);
        pendingLater.clear();
        for (FakeTask task : snapshot) {
            if (!task.isCancelled()) {
                task.run();
            }
        }
    }

    public int pendingLaterCount() {
        int n = 0;
        for (FakeTask t : pendingLater) {
            if (!t.isCancelled()) {
                n++;
            }
        }
        return n;
    }

    private FakeTask later(Runnable task) {
        FakeTask handle = new FakeTask(task);
        pendingLater.add(handle);
        return handle;
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        runOnEntityCount++;
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return later(task);
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        return true;
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        return true;
    }

    /** A cancellable captured task. */
    public static final class FakeTask implements PlatformTask {
        private final Runnable task;
        private boolean cancelled = false;

        FakeTask(Runnable task) {
            this.task = task;
        }

        void run() {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
```

- [ ] **Step 3: Write the failing `ChatConversationManagerTest`**

`ChatConversationManagerTest.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatConversationManagerTest {

    private FakeScheduler scheduler;
    private ChatConversationManager manager;
    private Player player;

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        ChatUtils.uninstall();
    }

    private ChatPrompt prompt() {
        return new ChatPrompt(player, manager);
    }

    @Test
    void dispatchDeliversMessageOnMainThreadByDefault() {
        AtomicReference<String> seen = new AtomicReference<>();
        prompt().onChat(ctx -> seen.set(ctx.getMessage()));

        manager.deliver(player.getUniqueId(), "hello");

        assertEquals("hello", seen.get());
        assertEquals(1, scheduler.runOnEntityCount, "default dispatch must hop through the scheduler");
    }

    @Test
    void asyncDispatchBypassesTheScheduler() {
        AtomicReference<String> seen = new AtomicReference<>();
        prompt().async().onChat(ctx -> seen.set(ctx.getMessage()));

        manager.deliver(player.getUniqueId(), "hi");

        assertEquals("hi", seen.get());
        assertEquals(0, scheduler.runOnEntityCount, "async dispatch must run inline, not via the scheduler");
    }

    @Test
    void callbackWithoutEndStaysActiveForNextMessage() {
        AtomicInteger hits = new AtomicInteger();
        prompt().onChat(ctx -> hits.incrementAndGet()); // never ends

        manager.deliver(player.getUniqueId(), "one");
        manager.deliver(player.getUniqueId(), "two");

        assertEquals(2, hits.get(), "a callback that does not end must keep capturing");
    }

    @Test
    void ctxEndStopsCaptureAndFiresOnEndOnce() {
        AtomicInteger ends = new AtomicInteger();
        AtomicReference<EndReason> reason = new AtomicReference<>();
        prompt()
                .onEnd((p, r) -> { ends.incrementAndGet(); reason.set(r); })
                .onChat(ChatContext::end);

        manager.deliver(player.getUniqueId(), "bye");
        // second message must not reach a callback because the conversation is gone
        manager.deliver(player.getUniqueId(), "again");

        assertEquals(1, ends.get());
        assertEquals(EndReason.ENDED, reason.get());
        assertNull(manager.activeFor(player.getUniqueId()), "ended conversation must be removed");
    }

    @Test
    void startingASecondPromptReplacesTheFirst() {
        AtomicReference<EndReason> firstReason = new AtomicReference<>();
        prompt().onEnd((p, r) -> firstReason.set(r)).onChat(ctx -> {});

        AtomicReference<String> secondSaw = new AtomicReference<>();
        prompt().onChat(ctx -> secondSaw.set(ctx.getMessage()));

        assertEquals(EndReason.REPLACED, firstReason.get(), "the superseded prompt ends with REPLACED");

        manager.deliver(player.getUniqueId(), "routed");
        assertEquals("routed", secondSaw.get(), "messages now reach the new prompt");
    }

    @Test
    void doubleEndIsIdempotent() {
        AtomicInteger ends = new AtomicInteger();
        prompt().onEnd((p, r) -> ends.incrementAndGet()).onChat(ctx -> {
            ctx.end();
            ctx.end();
        });

        manager.deliver(player.getUniqueId(), "x");

        assertEquals(1, ends.get(), "onEnd fires exactly once even if end() is called repeatedly");
    }

    @Test
    void throwingCallbackDoesNotKillTheConversation() {
        AtomicInteger hits = new AtomicInteger();
        prompt().onChat(ctx -> {
            if (hits.incrementAndGet() == 1) {
                throw new RuntimeException("boom");
            }
        });

        manager.deliver(player.getUniqueId(), "first");  // throws, is logged
        manager.deliver(player.getUniqueId(), "second"); // still delivered

        assertEquals(2, hits.get(), "a throwing callback is guarded; the conversation survives");
    }

    @Test
    void withReturnsAPromptBoundToThePlayer() {
        ChatUtils.install(manager);
        AtomicReference<Player> seen = new AtomicReference<>();
        ChatUtils.with(player).onChat(ctx -> seen.set(ctx.getPlayer()));

        manager.deliver(player.getUniqueId(), "hey");
        assertSame(player, seen.get());
    }

    @Test
    void withBeforeInstallThrows() {
        ChatUtils.uninstall();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ChatUtils.with(player));
    }
}
```

Note: `manager.deliver(UUID, String)` is a package-private test seam that performs the exact capture/dispatch a real chat event would (look up the conversation, dispatch the message). Task 3's `onChat(AsyncPlayerChatEvent)` delegates to it. `manager.activeFor(UUID)` is a package-private read of the registry for assertions.

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatConversationManagerTest"`
Expected: FAIL — `ChatPrompt`, `ChatUtils`, `ChatConversationManager` do not exist.

- [ ] **Step 5: Implement `ChatConversationManager`**

`ChatConversationManager.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Internal engine behind {@link ChatUtils}. Owns the per-player conversation registry, dispatches
 * captured messages, and fires lifecycle hooks. Auto-registered as a Bukkit {@code Listener} and a
 * {@code ContextReadyListener} by spigot-boot (annotations/handlers added in later tasks).
 */
@Component
@ApiStatus.Internal
public class ChatConversationManager {

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final ConcurrentHashMap<UUID, ActiveConversation> conversations = new ConcurrentHashMap<>();

    @Inject
    public ChatConversationManager(@NotNull Plugin plugin, @NotNull PlatformScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Starts a conversation from a completed builder, replacing any existing one for the player. */
    void begin(@NotNull ChatPrompt prompt) {
        ActiveConversation conv = new ActiveConversation(prompt);
        ActiveConversation previous = conversations.put(conv.playerId, conv);
        if (previous != null) {
            end(previous, EndReason.REPLACED);
        }
    }

    /**
     * Test/handler seam: routes a captured message to the player's conversation, if any.
     * Task 3's {@code onChat(AsyncPlayerChatEvent)} calls this after cancelling the event.
     */
    void deliver(@NotNull UUID playerId, @NotNull String message) {
        ActiveConversation conv = conversations.get(playerId);
        if (conv == null) {
            return;
        }
        dispatch(conv, message);
    }

    /** Package-private registry read for tests. */
    ActiveConversation activeFor(@NotNull UUID playerId) {
        return conversations.get(playerId);
    }

    private void dispatch(ActiveConversation conv, String message) {
        ChatContext ctx = new ConversationContext(conv, message);
        Runnable run = () -> {
            if (conv.ended.get()) {
                return;
            }
            runGuarded("onChat callback", () -> conv.callback.accept(ctx));
        };
        if (conv.async) {
            run.run();
        } else {
            scheduler.runOnEntity(conv.player, run, null);
        }
    }

    /** Ends a conversation exactly once, cancelling its timeout and firing its hooks. */
    void end(ActiveConversation conv, EndReason reason) {
        if (!conv.ended.compareAndSet(false, true)) {
            return;
        }
        conversations.remove(conv.playerId, conv);
        PlatformTask timeout = conv.timeoutTask;
        if (timeout != null) {
            timeout.cancel();
        }
        if (reason == EndReason.TIMEOUT && conv.onTimeout != null) {
            runGuarded("onTimeout callback", () -> conv.onTimeout.accept(conv.player));
        }
        if (conv.onEnd != null) {
            runGuarded("onEnd callback", () -> conv.onEnd.accept(conv.player, reason));
        }
    }

    private void runGuarded(String what, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "ChatUtils " + what + " threw", t);
        }
    }

    /** Immutable-ish snapshot of a running conversation plus its mutable timeout handle. */
    static final class ActiveConversation {
        final UUID playerId;
        final Player player;
        final Consumer<ChatContext> callback;
        final Consumer<Player> onTimeout;
        final BiConsumer<Player, EndReason> onEnd;
        final boolean async;
        final long timeoutMillis;
        final String firstPrompt;
        final AtomicBoolean ended = new AtomicBoolean(false);
        volatile PlatformTask timeoutTask;
        volatile int timeoutGeneration;

        ActiveConversation(ChatPrompt p) {
            this.player = p.player;
            this.playerId = p.player.getUniqueId();
            this.callback = Objects.requireNonNull(p.callback, "callback");
            this.onTimeout = p.onTimeout;
            this.onEnd = p.onEnd;
            this.async = p.async;
            this.timeoutMillis = p.timeoutMillis;
            this.firstPrompt = p.firstPrompt;
        }
    }

    /** Per-message context; {@code end()} routes back through the manager. */
    private final class ConversationContext implements ChatContext {
        private final ActiveConversation conv;
        private final String message;

        ConversationContext(ActiveConversation conv, String message) {
            this.conv = conv;
            this.message = message;
        }

        @Override
        public Player getPlayer() {
            return conv.player;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public void end() {
            ChatConversationManager.this.end(conv, EndReason.ENDED);
        }
    }
}
```

- [ ] **Step 6: Implement `ChatPrompt`**

`ChatPrompt.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fluent builder for a chat conversation. Obtain one from {@link ChatUtils#with(Player)}. The
 * terminal {@link #onChat(Consumer)} call starts the conversation.
 *
 * <p>While a conversation is active the player's chat is intercepted (never broadcast) and each
 * message is delivered to the callback until it calls {@link ChatContext#end()}, the timeout
 * elapses, or the player disconnects.
 */
public final class ChatPrompt {

    final Player player;
    private final ChatConversationManager manager;

    String firstPrompt;
    long timeoutMillis;
    Consumer<Player> onTimeout;
    BiConsumer<Player, EndReason> onEnd;
    boolean async;
    Consumer<ChatContext> callback;

    ChatPrompt(@NotNull Player player, @NotNull ChatConversationManager manager) {
        this.player = Objects.requireNonNull(player, "player");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    /**
     * A message sent to the player the moment the conversation starts. Treated as
     * {@code ChatMarkup} source (colours, {@code #rrggbb} hex, styles, click/hover).
     *
     * @param markup the prompt text; {@code null}/blank sends nothing
     * @return this builder
     */
    public ChatPrompt firstPrompt(String markup) {
        this.firstPrompt = markup;
        return this;
    }

    /**
     * Inactivity timeout: if the player sends no message within this window the conversation ends
     * with {@link EndReason#TIMEOUT}. The window resets on every captured message.
     *
     * @param duration the amount
     * @param unit     the time unit
     * @return this builder
     */
    public ChatPrompt timeout(long duration, @NotNull TimeUnit unit) {
        this.timeoutMillis = unit.toMillis(duration);
        return this;
    }

    /**
     * @param onTimeout run (with the player) when the timeout elapses, just before {@code onEnd}
     * @return this builder
     */
    public ChatPrompt onTimeout(Consumer<Player> onTimeout) {
        this.onTimeout = onTimeout;
        return this;
    }

    /**
     * @param onEnd run when the conversation ends for any reason
     * @return this builder
     */
    public ChatPrompt onEnd(BiConsumer<Player, EndReason> onEnd) {
        this.onEnd = onEnd;
        return this;
    }

    /**
     * Run the callback inline on the async chat thread instead of hopping to the main/region
     * thread. Only safe when the callback touches thread-safe API only.
     *
     * @return this builder
     */
    public ChatPrompt async() {
        this.async = true;
        return this;
    }

    /**
     * Starts the conversation. Any conversation already active for this player ends with
     * {@link EndReason#REPLACED}.
     *
     * @param callback invoked for each captured message
     */
    public void onChat(@NotNull Consumer<ChatContext> callback) {
        this.callback = Objects.requireNonNull(callback, "callback");
        manager.begin(this);
    }
}
```

- [ ] **Step 7: Implement `ChatUtils`**

`ChatUtils.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Entry point for temporary server-to-player chat conversations.
 *
 * <pre>{@code
 * ChatUtils.with(player)
 *     .firstPrompt("#ffaa00Enter an amount, &7or type &ccancel")
 *     .timeout(30, TimeUnit.SECONDS)
 *     .onTimeout(p -> p.sendMessage("Timed out."))
 *     .onChat(ctx -> {
 *         if (ctx.getMessage().equalsIgnoreCase("cancel")) { ctx.end(); return; }
 *         // ... handle input; call ctx.end() when done, or leave active to re-prompt
 *     });
 * }</pre>
 *
 * <p>Backed by an internal {@code @Component} installed when the spigot-boot context becomes ready.
 * Calling {@link #with(Player)} before that point throws {@link IllegalStateException}.
 */
public final class ChatUtils {

    private static volatile ChatConversationManager manager;

    private ChatUtils() {
    }

    static void install(@NotNull ChatConversationManager m) {
        manager = m;
    }

    static void uninstall() {
        manager = null;
    }

    /**
     * @param player the player to converse with
     * @return a fluent builder for the conversation
     * @throws IllegalStateException if the spigot-boot context is not ready yet
     */
    public static @NotNull ChatPrompt with(@NotNull Player player) {
        ChatConversationManager m = manager;
        if (m == null) {
            throw new IllegalStateException(
                    "ChatUtils is not initialized yet; the spigot-boot context is not ready. "
                            + "Call ChatUtils.with(...) from your plugin logic, not during early startup.");
        }
        return new ChatPrompt(player, m);
    }

    /**
     * Shorthand for {@code with(player).onChat(callback)} with no options.
     *
     * @param player   the player to converse with
     * @param callback invoked for each captured message
     */
    public static void onChat(@NotNull Player player, @NotNull Consumer<ChatContext> callback) {
        with(player).onChat(callback);
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatConversationManagerTest"`
Expected: PASS (9 tests).

- [ ] **Step 9: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatContext.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManager.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatPrompt.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatUtils.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/support/FakeScheduler.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManagerTest.java
git commit -m "feat(core-spigot): conversation engine, builder and ChatUtils facade"
```

---

### Task 3: Chat-event capture — `onChat(AsyncPlayerChatEvent)` + `onQuit(PlayerQuitEvent)`

**Files:**
- Modify: `.../conversation/ChatConversationManager.java` (add the two `@EventHandler`s + `implements Listener`)
- Test: `src/test/java/.../conversation/ChatContextCaptureTest.java`

**Interfaces:**
- Consumes: `deliver(UUID, String)`, `end(ActiveConversation, EndReason)`, `activeFor(UUID)` (Task 2).
- Produces: `class ChatConversationManager implements Listener` with `public void onChat(AsyncPlayerChatEvent)` at `EventPriority.LOWEST` and `public void onQuit(PlayerQuitEvent)`.

- [ ] **Step 1: Write the failing test**

`ChatContextCaptureTest.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatContextCaptureTest {

    private FakeScheduler scheduler;
    private ChatConversationManager manager;
    private Player player;

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private AsyncPlayerChatEvent chat(Player who, String message) {
        return new AsyncPlayerChatEvent(false, who, message, new HashSet<>());
    }

    @Test
    void participantMessageIsCapturedAndCancelled() {
        AtomicReference<String> seen = new AtomicReference<>();
        new ChatPrompt(player, manager).onChat(ctx -> seen.set(ctx.getMessage()));

        AsyncPlayerChatEvent event = chat(player, "42");
        manager.onChat(event);

        assertEquals("42", seen.get());
        assertTrue(event.isCancelled(), "a captured message must not broadcast to public chat");
    }

    @Test
    void nonParticipantMessagePassesThrough() {
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());

        AtomicInteger hits = new AtomicInteger();
        new ChatPrompt(player, manager).onChat(ctx -> hits.incrementAndGet());

        AsyncPlayerChatEvent event = chat(other, "hello world");
        manager.onChat(event);

        assertEquals(0, hits.get(), "a player without a conversation is not captured");
        assertFalse(event.isCancelled(), "normal chat must not be cancelled");
    }

    @Test
    void repromptCapturesTheSecondMessage() {
        AtomicInteger hits = new AtomicInteger();
        new ChatPrompt(player, manager).onChat(ctx -> hits.incrementAndGet()); // never ends

        manager.onChat(chat(player, "one"));
        manager.onChat(chat(player, "two"));

        assertEquals(2, hits.get());
    }

    @Test
    void afterEndMessageIsNotCaptured() {
        new ChatPrompt(player, manager).onChat(ChatContext::end);

        manager.onChat(chat(player, "done"));
        AsyncPlayerChatEvent afterEnd = chat(player, "late");
        manager.onChat(afterEnd);

        assertFalse(afterEnd.isCancelled(), "once ended, chat flows normally again");
        assertNull(manager.activeFor(player.getUniqueId()));
    }

    @Test
    void quitEndsConversationWithDisconnect() {
        AtomicReference<EndReason> reason = new AtomicReference<>();
        new ChatPrompt(player, manager).onEnd((p, r) -> reason.set(r)).onChat(ctx -> {});

        when(player.getName()).thenReturn("Steve");
        manager.onQuit(new PlayerQuitEvent(player, "left"));

        assertEquals(EndReason.DISCONNECT, reason.get());
        assertNull(manager.activeFor(player.getUniqueId()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatContextCaptureTest"`
Expected: FAIL — `manager.onChat(AsyncPlayerChatEvent)` / `onQuit(PlayerQuitEvent)` do not exist.

- [ ] **Step 3: Add the event handlers to `ChatConversationManager`**

Add these imports near the top:

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
```

Change the class declaration to implement `Listener`:

```java
public class ChatConversationManager implements Listener {
```

Add these two methods to the class body (after `deliver`):

```java
/**
 * Captures chat from players in a conversation: cancels the broadcast on the async thread and
 * routes the message to the callback. Players without a conversation are untouched.
 *
 * @param event the async chat event
 */
@EventHandler(priority = EventPriority.LOWEST)
public void onChat(@NotNull AsyncPlayerChatEvent event) {
    if (activeFor(event.getPlayer().getUniqueId()) == null) {
        return;
    }
    event.setCancelled(true);
    deliver(event.getPlayer().getUniqueId(), event.getMessage());
}

/**
 * Ends a disconnecting player's conversation with {@link EndReason#DISCONNECT}.
 *
 * @param event the quit event
 */
@EventHandler
public void onQuit(@NotNull PlayerQuitEvent event) {
    ActiveConversation conv = activeFor(event.getPlayer().getUniqueId());
    if (conv != null) {
        end(conv, EndReason.DISCONNECT);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatContextCaptureTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManager.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatContextCaptureTest.java
git commit -m "feat(core-spigot): capture chat + end on quit for conversations"
```

---

### Task 4: `firstPrompt` rendering through `ChatMarkup` + `HexSupport`

**Files:**
- Modify: `.../conversation/ChatConversationManager.java` (send firstPrompt in `begin`)
- Test: `src/test/java/.../conversation/FirstPromptRenderTest.java`

**Interfaces:**
- Consumes: `ChatMarkup.parse(String) -> BaseComponent[]` (package `...core.spigot.text`); `player.spigot().sendMessage(BaseComponent...)`.
- Produces: `begin(...)` sends the rendered firstPrompt when non-blank; `sendFirstPrompt(Player, String)` private helper.

- [ ] **Step 1: Write the failing test**

`FirstPromptRenderTest.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.text.HexSupport;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstPromptRenderTest {

    private ChatConversationManager manager;
    private Player player;
    private Player.Spigot spigot;

    @BeforeEach
    void setUp() {
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, new FakeScheduler());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
    }

    @Test
    void firstPromptRendersHexAndColourCodes() {
        // precondition: MockBukkit-v1.20 reports a 1.16+ server, so native hex is on
        assertTrue(HexSupport.NATIVE_HEX, "test server must support native hex for this assertion");

        new ChatPrompt(player, manager)
                .firstPrompt("#ff0000Enter &aamount")
                .onChat(ctx -> {});

        ArgumentCaptor<BaseComponent[]> captor = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot).sendMessage(captor.capture());
        BaseComponent[] sent = captor.getValue();

        String legacy = TextComponent.toLegacyText(sent);
        assertTrue(legacy.contains("Enter"), "visible text must survive: " + legacy);
        assertTrue(legacy.contains("amount"), "visible text must survive: " + legacy);

        // the "#ff0000" token must have flowed through HexSupport into a real red hex colour
        assertEquals(ChatColor.of("#ff0000"), sent[0].getColor(),
                "first run must carry the parsed hex colour");
    }

    @Test
    void blankFirstPromptSendsNothing() {
        new ChatPrompt(player, manager).firstPrompt("   ").onChat(ctx -> {});
        new ChatPrompt(player, manager).onChat(ctx -> {}); // null firstPrompt

        verify(spigot, never()).sendMessage(org.mockito.ArgumentMatchers.<BaseComponent[]>any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="FirstPromptRenderTest"`
Expected: FAIL — `begin` does not send the firstPrompt yet, so `verify(spigot).sendMessage(...)` fails with "Wanted but not invoked".

- [ ] **Step 3: Send the firstPrompt from `begin`**

Add imports to `ChatConversationManager`:

```java
import net.md_5.bungee.api.chat.BaseComponent;
import tech.guilhermekaua.spigotboot.core.spigot.text.ChatMarkup;
```

In `begin(...)`, after the replace block, add the firstPrompt send:

```java
void begin(@NotNull ChatPrompt prompt) {
    ActiveConversation conv = new ActiveConversation(prompt);
    ActiveConversation previous = conversations.put(conv.playerId, conv);
    if (previous != null) {
        end(previous, EndReason.REPLACED);
    }
    if (conv.firstPrompt != null && !conv.firstPrompt.isBlank()) {
        sendFirstPrompt(conv.player, conv.firstPrompt);
    }
}
```

Add the helper method:

```java
// ChatMarkup.parse is the "for players" front door; it routes #rrggbb through HexSupport
// (native §x… on 1.16+, nearest-legacy below) and handles &/§ codes and click/hover tags.
private void sendFirstPrompt(Player player, String markup) {
    BaseComponent[] components = ChatMarkup.parse(markup);
    player.spigot().sendMessage(components);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="FirstPromptRenderTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManager.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/FirstPromptRenderTest.java
git commit -m "feat(core-spigot): render firstPrompt via ChatMarkup + HexSupport"
```

---

### Task 5: Inactivity timeout + reset

**Files:**
- Modify: `.../conversation/ChatConversationManager.java` (schedule timeout in `begin`, reset in `dispatch`)
- Test: `src/test/java/.../conversation/TimeoutTest.java`

**Interfaces:**
- Consumes: `scheduler.runOnEntityLater(Entity, Runnable, Runnable, long)`, `PlatformTask.cancel()`, `Utils.millisToTicks(long)`; `FakeScheduler.fireAllLater()` / `pendingLaterCount()`.
- Produces: `begin(...)` schedules a timeout when `timeoutMillis > 0`; `dispatch(...)` reschedules it; generation-guarded `scheduleTimeout(ActiveConversation)`.

- [ ] **Step 1: Write the failing test**

`TimeoutTest.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimeoutTest {

    private FakeScheduler scheduler;
    private ChatConversationManager manager;
    private Player player;

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void timeoutFiresOnTimeoutThenOnEnd() {
        AtomicInteger order = new AtomicInteger();
        AtomicReference<Integer> timeoutOrder = new AtomicReference<>();
        AtomicReference<Integer> endOrder = new AtomicReference<>();
        AtomicReference<EndReason> endReason = new AtomicReference<>();

        new ChatPrompt(player, manager)
                .timeout(30, TimeUnit.SECONDS)
                .onTimeout(p -> timeoutOrder.set(order.incrementAndGet()))
                .onEnd((p, r) -> { endOrder.set(order.incrementAndGet()); endReason.set(r); })
                .onChat(ctx -> {});

        scheduler.fireAllLater(); // simulate the timeout elapsing

        assertEquals(1, timeoutOrder.get(), "onTimeout fires first");
        assertEquals(2, endOrder.get(), "onEnd fires second");
        assertEquals(EndReason.TIMEOUT, endReason.get());
        assertNull(manager.activeFor(player.getUniqueId()), "timed-out conversation is removed");
    }

    @Test
    void aMessageResetsTheTimeoutWindow() {
        AtomicInteger timeouts = new AtomicInteger();
        new ChatPrompt(player, manager)
                .timeout(30, TimeUnit.SECONDS)
                .onTimeout(p -> timeouts.incrementAndGet())
                .onChat(ctx -> {}); // never ends

        manager.deliver(player.getUniqueId(), "still here"); // resets: old timeout task is stale

        assertEquals(1, scheduler.pendingLaterCount(),
                "the superseded timeout task must be cancelled, leaving one live task");

        scheduler.fireAllLater(); // fires both the stale (cancelled) and the fresh task
        assertEquals(1, timeouts.get(), "only the fresh timeout may fire, exactly once");
    }

    @Test
    void noTimeoutWhenNotConfigured() {
        new ChatPrompt(player, manager).onChat(ctx -> {});
        assertEquals(0, scheduler.pendingLaterCount(), "no timeout scheduled when timeout() is not called");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="TimeoutTest"`
Expected: FAIL — no timeout is scheduled, so `fireAllLater()` does nothing and `onTimeout` never runs.

- [ ] **Step 3: Implement scheduling + reset**

Add import to `ChatConversationManager`:

```java
import tech.guilhermekaua.spigotboot.core.spigot.utils.Utils;
```

In `begin(...)`, schedule the timeout (place before the firstPrompt send so a zero-tick edge still races cleanly):

```java
void begin(@NotNull ChatPrompt prompt) {
    ActiveConversation conv = new ActiveConversation(prompt);
    ActiveConversation previous = conversations.put(conv.playerId, conv);
    if (previous != null) {
        end(previous, EndReason.REPLACED);
    }
    if (conv.timeoutMillis > 0) {
        scheduleTimeout(conv);
    }
    if (conv.firstPrompt != null && !conv.firstPrompt.isBlank()) {
        sendFirstPrompt(conv.player, conv.firstPrompt);
    }
}
```

In `dispatch(...)`, reset the timeout at the top:

```java
private void dispatch(ActiveConversation conv, String message) {
    if (conv.timeoutMillis > 0) {
        scheduleTimeout(conv);
    }
    ChatContext ctx = new ConversationContext(conv, message);
    Runnable run = () -> {
        if (conv.ended.get()) {
            return;
        }
        runGuarded("onChat callback", () -> conv.callback.accept(ctx));
    };
    if (conv.async) {
        run.run();
    } else {
        scheduler.runOnEntity(conv.player, run, null);
    }
}
```

Add the generation-guarded scheduler:

```java
// Each (re)schedule bumps the generation and cancels the prior task; a fired task no-ops unless
// it is still the current generation and the conversation is live. This makes reset robust even
// if a stale task slips past cancellation across threads.
private void scheduleTimeout(ActiveConversation conv) {
    PlatformTask previous = conv.timeoutTask;
    if (previous != null) {
        previous.cancel();
    }
    int gen = ++conv.timeoutGeneration;
    long ticks = Math.max(1L, Utils.millisToTicks(conv.timeoutMillis));
    conv.timeoutTask = scheduler.runOnEntityLater(conv.player, () -> {
        if (conv.ended.get() || conv.timeoutGeneration != gen) {
            return;
        }
        end(conv, EndReason.TIMEOUT);
    }, null, ticks);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="TimeoutTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManager.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/TimeoutTest.java
git commit -m "feat(core-spigot): inactivity timeout with reset for conversations"
```

---

### Task 6: Lifecycle wiring + end-to-end MockBukkit test

**Files:**
- Modify: `.../conversation/ChatConversationManager.java` (implement `ContextReadyListener`: install + shutdown hook)
- Test: `src/test/java/.../conversation/ChatUtilsLifecycleTest.java`

**Interfaces:**
- Consumes: `ContextReadyListener.onContextReady(Context)`, `Context.registerShutdownHook(Runnable)`, `ChatUtils.install/uninstall`; MockBukkit (`MockBukkit.mock`, `ServerMock`, `createMockPlugin`, `addPlayer`); `AsyncPlayerChatEvent`, `PlayerQuitEvent`.
- Produces: `class ChatConversationManager implements Listener, ContextReadyListener` with `public void onContextReady(Context)` installing the facade and registering a shutdown hook that ends every conversation with `PLUGIN_DISABLE` then uninstalls.

- [ ] **Step 1: Write the failing test**

`ChatUtilsLifecycleTest.java` (MIT header, then):

```java
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformSchedulers;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ChatUtilsLifecycleTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private ChatConversationManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
        manager = new ChatConversationManager(plugin, PlatformSchedulers.create(plugin));
    }

    @AfterEach
    void tearDown() {
        ChatUtils.uninstall();
        HandlerList.unregisterAll();
        MockBukkit.unmock();
    }

    @Test
    void endToEndCaptureThroughBukkit() {
        server.getPluginManager().registerEvents(manager, plugin);
        ChatUtils.install(manager);
        PlayerMock player = server.addPlayer();

        AtomicReference<String> seen = new AtomicReference<>();
        // .async() so the callback runs inline on the event call, no scheduler tick needed
        ChatUtils.with(player).async().onChat(ctx -> seen.set(ctx.getMessage()));

        AsyncPlayerChatEvent event =
                new AsyncPlayerChatEvent(false, player, "deposit 100", new HashSet<>());
        server.getPluginManager().callEvent(event);

        assertEquals("deposit 100", seen.get());
        assertTrue(event.isCancelled(), "captured chat must be cancelled");
    }

    @Test
    void quitThroughBukkitEndsConversation() {
        server.getPluginManager().registerEvents(manager, plugin);
        ChatUtils.install(manager);
        PlayerMock player = server.addPlayer();

        AtomicReference<EndReason> reason = new AtomicReference<>();
        ChatUtils.with(player).onEnd((p, r) -> reason.set(r)).onChat(ctx -> {});

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, "left"));

        assertEquals(EndReason.DISCONNECT, reason.get());
    }

    @Test
    void onContextReadyInstallsTheFacade() {
        Context context = mock(Context.class);
        manager.onContextReady(context);

        PlayerMock player = server.addPlayer();
        // must not throw now that the facade is installed
        ChatUtils.with(player).onChat(ctx -> {});
    }

    @Test
    void shutdownHookEndsAllWithPluginDisableAndUninstalls() {
        Context context = mock(Context.class);
        AtomicReference<Runnable> hook = new AtomicReference<>();
        doAnswer(inv -> { hook.set(inv.getArgument(0)); return null; })
                .when(context).registerShutdownHook(org.mockito.ArgumentMatchers.any(Runnable.class));

        manager.onContextReady(context);

        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        AtomicInteger disables = new AtomicInteger();
        ChatUtils.with(a).onEnd((p, r) -> { if (r == EndReason.PLUGIN_DISABLE) disables.incrementAndGet(); }).onChat(ctx -> {});
        ChatUtils.with(b).onEnd((p, r) -> { if (r == EndReason.PLUGIN_DISABLE) disables.incrementAndGet(); }).onChat(ctx -> {});

        hook.get().run(); // simulate context shutdown

        assertEquals(2, disables.get(), "every active conversation ends with PLUGIN_DISABLE");
        assertThrows(IllegalStateException.class, () -> ChatUtils.with(a),
                "the facade is uninstalled after shutdown");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatUtilsLifecycleTest"`
Expected: FAIL — `manager.onContextReady(...)` does not exist and the facade is never installed (`onContextReadyInstallsTheFacade` throws `IllegalStateException`).

- [ ] **Step 3: Implement `ContextReadyListener`**

Add imports to `ChatConversationManager`:

```java
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import java.util.ArrayList;
```

Change the class declaration:

```java
public class ChatConversationManager implements Listener, ContextReadyListener {
```

Add the handler:

```java
/**
 * Installs the {@link ChatUtils} facade and registers a shutdown hook that ends every active
 * conversation with {@link EndReason#PLUGIN_DISABLE} and uninstalls the facade. The Bukkit
 * event registration is handled separately by {@code BukkitListenerAutoRegistrar}.
 *
 * @param context the ready context
 */
@Override
public void onContextReady(@NotNull Context context) {
    ChatUtils.install(this);
    context.registerShutdownHook(() -> {
        for (ActiveConversation conv : new ArrayList<>(conversations.values())) {
            end(conv, EndReason.PLUGIN_DISABLE);
        }
        conversations.clear();
        ChatUtils.uninstall();
    });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test -Dtest="ChatUtilsLifecycleTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the whole module suite**

Run: `./mvnw -q -pl platform-spigot/core-spigot -am test`
Expected: PASS — all conversation tests plus the pre-existing core-spigot suite are green.

- [ ] **Step 6: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatConversationManager.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/conversation/ChatUtilsLifecycleTest.java
git commit -m "feat(core-spigot): install ChatUtils on context ready, end all on disable"
```

---

### Task 7: Documentation — module README / wiki note

**Files:**
- Modify: `platform-spigot/core-spigot/README.md` if present; otherwise add a short `## ChatUtils` section to the nearest module doc. If neither exists, skip (the Javadoc on `ChatUtils` is the primary doc) and note it in the completion report.

**Interfaces:** none (docs only).

- [ ] **Step 1: Check for an existing module doc**

Run: `ls platform-spigot/core-spigot/README.md 2>/dev/null; ls platform-spigot/core-spigot/*.md 2>/dev/null`
Expected: either a path prints (edit it) or nothing prints (skip, per the file note above).

- [ ] **Step 2: If a doc exists, add a usage section**

Append a `## ChatUtils — temporary chat conversations` section that (a) states the one-line
purpose ("prompt a player in chat and capture their reply without writing a listener"), (b)
shows a fenced Java example mirroring the `ChatUtils` class Javadoc — the fluent
`with(player).firstPrompt(...).timeout(...).onTimeout(...).onEnd(...).onChat(ctx -> ...)`
chain including the `cancel` early-return and the `NumberFormatException` re-prompt (no
`ctx.end()` in the catch), and (c) notes the three behaviors: the message is hidden from public
chat while a prompt is active; the callback runs on the main/region thread by default, with
`.async()` to run on the chat thread (thread-safe API only); the conversation auto-ends on
disconnect and on plugin disable.

Copy the example body verbatim from the `ChatUtils` Javadoc written in Task 2 Step 7 so the doc
and the code cannot drift.

- [ ] **Step 3: Commit (only if a doc was edited)**

```bash
git add platform-spigot/core-spigot/README.md
git commit -m "docs(core-spigot): document ChatUtils conversation usage"
```

---

## Verification (after all tasks)

- [ ] Full module suite green: `./mvnw -q -pl platform-spigot/core-spigot -am test`
- [ ] Reactor still builds: `./mvnw -q -T1C -DskipTests install` (or at least `-pl platform-spigot/core-spigot -am install`)
- [ ] Manual walk of failure modes (state out loud, per CLAUDE.md): async double-message ordering, quit-before-dispatch (guarded by `conv.ended` check in `dispatch`), timeout-vs-message race (generation guard), replace mid-conversation (REPLACED then new routes).
- [ ] Push branch and open PR against `dev`.

## Notes for the implementer

- `AsyncPlayerChatEvent` is deprecated on Paper (Adventure `AsyncChatEvent`) but is present on every Spigot/Paper build this framework targets and is what MockBukkit-v1.20 fires. Do not swap it for the Adventure event; that is explicitly out of scope.
- The manager is constructed directly in unit tests (`new ChatConversationManager(plugin, scheduler)`); the `@Component`/`@Inject`/`ContextReadyListener` wiring is only exercised in `ChatUtilsLifecycleTest`. Keep the `@Inject` constructor the single injectable constructor (no second constructor) so `DependencyManager` selects it.
- Test isolation: any test that calls `ChatUtils.install(...)` MUST `ChatUtils.uninstall()` in `@AfterEach`, because the facade holds a static reference.
```
