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
import tech.guilhermekaua.spigotboot.core.validation.annotation.Range;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Primary plugin configuration, backed by {@code config.yml}. Demonstrates comments, range
 * validation, a nested config POJO, and a {@link Duration} field (cross-config Duration support).
 */
@Config("config.yml")
@Data
public class NetworkConfig {
    @Comment("Server players are sent to by /lobby")
    private String defaultServer = "lobby";

    @Comment("Maximum players allowed across the whole network")
    @Range(min = 1, max = 1000)
    private int maxNetworkPlayers = 500;

    @Comment("Periodic broadcast announcement settings")
    private AnnouncementsConfig announcements = new AnnouncementsConfig();

    /**
     * Nested configuration block for periodic announcements.
     */
    @Data
    public static class AnnouncementsConfig {
        @Comment("Whether periodic announcements are broadcast")
        private boolean enabled = true;

        @Comment("Delay between announcements")
        private Duration interval = Duration.ofMinutes(5);

        @Comment("Messages cycled through, one per interval; supports & colour codes")
        private List<String> messages = Arrays.asList(
                "&7Welcome to the network! Use &b/server &7to move around.",
                "&7Type &b/lobby &7to return to the main hub."
        );
    }
}
