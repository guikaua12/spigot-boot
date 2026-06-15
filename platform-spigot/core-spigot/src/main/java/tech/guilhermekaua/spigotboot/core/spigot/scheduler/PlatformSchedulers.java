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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Detects the platform and builds the matching {@link PlatformScheduler}. */
public final class PlatformSchedulers {

    private static final String FOLIA_SCHEDULER_CLASS = "io.papermc.paper.threadedregions.scheduler.RegionScheduler";

    private PlatformSchedulers() {
    }

    /**
     * Builds the scheduler for the running server.
     *
     * @param plugin the owning plugin
     * @return a Folia scheduler when the modern scheduler API is present, else a legacy one
     */
    public static @NotNull PlatformScheduler create(@NotNull Plugin plugin) {
        return create(plugin, isFoliaSchedulerApiPresent());
    }

    // package-private seam so the branch is unit-testable without a live server
    static @NotNull PlatformScheduler create(@NotNull Plugin plugin, boolean foliaApiPresent) {
        Objects.requireNonNull(plugin, "plugin");
        return foliaApiPresent ? new FoliaPlatformScheduler(plugin) : new BukkitPlatformScheduler(plugin);
    }

    static boolean isFoliaSchedulerApiPresent() {
        try {
            Class.forName(FOLIA_SCHEDULER_CLASS, false, PlatformSchedulers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
