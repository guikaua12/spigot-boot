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
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

/**
 * Wraps a BungeeCord {@link CommandSender} into a {@link CommandSenderHandle}. Mirrors the Spigot
 * {@code BukkitCommandPlatformSupport}. {@code ProxiedPlayer} is a {@code CommandSender}, so it is
 * recognised by {@link #isSenderType(Class)} too.
 */
public class BungeeCommandPlatformSupport implements CommandPlatformSupport {

    @Override
    public CommandSenderHandle createSender(Object nativeSender) {
        if (!(nativeSender instanceof CommandSender)) {
            throw new IllegalArgumentException("Expected a BungeeCord CommandSender but got " +
                    (nativeSender == null ? "null" : nativeSender.getClass().getName()) + ".");
        }
        return new BungeeCommandSender((CommandSender) nativeSender);
    }

    @Override
    public boolean isSenderType(Class<?> type) {
        return type != null && CommandSender.class.isAssignableFrom(type);
    }
}
