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
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

import java.util.Objects;
import java.util.Optional;

/**
 * Adapts a BungeeCord {@link CommandSender} to the platform-neutral {@link CommandSenderHandle}.
 * Mirrors the Spigot {@code BukkitCommandSender}.
 */
public class BungeeCommandSender implements CommandSenderHandle {
    private final CommandSender sender;

    public BungeeCommandSender(CommandSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender cannot be null.");
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public String getIdentity() {
        if (sender instanceof ProxiedPlayer) {
            return ((ProxiedPlayer) sender).getUniqueId().toString();
        }
        // BungeeCord exposes no console sender type in its API; identify non-players by class + name.
        String name = sender.getName();
        return sender.getClass().getName() + ":" + (name == null ? "" : name);
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void sendMessage(String message) {
        // the String overload is deprecated in favour of BaseComponent, but the CommandSenderHandle
        // contract is String-based; the legacy overload renders legacy colour codes correctly.
        sender.sendMessage(message);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type == null || !type.isInstance(sender)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(sender));
    }
}
