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
     * <p>To run without a timeout, simply do not call this method (the default). Passing a value
     * that resolves to less than one millisecond is rejected rather than silently disabling the
     * timeout.
     *
     * @param duration the amount; must resolve to at least one millisecond
     * @param unit     the time unit
     * @return this builder
     * @throws IllegalArgumentException if {@code duration} is not positive or is smaller than one
     *                                  millisecond in {@code unit}
     */
    public ChatPrompt timeout(long duration, @NotNull TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        long millis = unit.toMillis(duration);
        if (millis <= 0) {
            throw new IllegalArgumentException(
                    "timeout must be at least 1ms, got " + duration + " " + unit + " (" + millis + "ms)");
        }
        this.timeoutMillis = millis;
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
