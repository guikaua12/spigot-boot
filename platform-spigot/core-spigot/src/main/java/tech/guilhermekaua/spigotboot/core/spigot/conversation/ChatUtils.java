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
