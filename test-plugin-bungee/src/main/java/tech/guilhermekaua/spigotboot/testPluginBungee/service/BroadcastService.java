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
package tech.guilhermekaua.spigotboot.testPluginBungee.service;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;

/**
 * Sends prefixed, colour-translated broadcasts to the whole proxy. Reaches the proxy through the
 * injected {@link Plugin} ({@code ProxyServer} is not an injectable bean).
 */
@Service
@RequiredArgsConstructor
public class BroadcastService {
    private final Plugin plugin;
    private final MessagesConfig messages;

    /**
     * Broadcasts a message to every player on the proxy.
     *
     * @param message the message body; {@code &} colour codes are translated and the configured
     *                prefix is prepended.
     */
    public void broadcast(String message) {
        String rendered = ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message);
        plugin.getProxy().broadcast(TextComponent.fromLegacyText(rendered));
    }

    /**
     * Logs when messages.yml is reloaded (e.g. via /network reload, which calls ConfigManager.reloadAll()),
     * demonstrating live config-reload callbacks.
     */
    @OnConfigReload(MessagesConfig.class)
    public void onMessagesReload() {
        plugin.getLogger().info("messages.yml reloaded.");
    }
}
