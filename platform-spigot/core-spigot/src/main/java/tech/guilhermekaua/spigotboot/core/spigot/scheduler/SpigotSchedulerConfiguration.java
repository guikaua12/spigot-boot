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
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

/**
 * Registers the platform {@link PlatformScheduler}. Guarded by {@code @ConditionalOnMissingBean}
 * so a plugin may register its own implementation.
 */
@Configuration
public class SpigotSchedulerConfiguration {

    /**
     * @param plugin the host plugin
     * @return the {@link PlatformScheduler} for the running server — Folia-aware when the modern
     *         scheduler API is present, otherwise a legacy Bukkit-backed implementation
     */
    @Bean
    @ConditionalOnMissingBean(PlatformScheduler.class)
    public PlatformScheduler platformScheduler(Plugin plugin) {
        return PlatformSchedulers.create(plugin);
    }
}
