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
package tech.guilhermekaua.spigotboot.testPluginBungee.config;

import lombok.Data;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;
import tech.guilhermekaua.spigotboot.config.annotation.Config;

/**
 * Player-facing message templates, backed by {@code messages.yml}. Placeholders {@code %player%}
 * and {@code %server%} are substituted by the consuming services and listeners.
 */
@Config("messages.yml")
@Data
public class MessagesConfig {
    @Comment("Prefix prepended to every plugin message; supports & colour codes")
    private String prefix = "&7[&bNetwork&7] ";

    @Comment("Sent to a player when they join; %player% is replaced")
    private String welcome = "&aWelcome, %player%&a!";

    @Comment("Broadcast when a player leaves; %player% is replaced")
    private String disconnect = "&7%player% left the network.";

    @Comment("Broadcast when a player changes server; %player% and %server% are replaced")
    private String serverSwitch = "&7%player% moved to &b%server%&7.";
}
