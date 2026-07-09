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
