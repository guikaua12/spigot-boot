/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.bootstrap;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Detects the active server runtime flavor without linking Paper classes directly.
 *
 * @since 2.0.2
 */
public final class RuntimeServerFlavorDetector {
    private static final String[] PAPER_MARKERS = new String[]{
            "com/destroystokyo/paper/PaperConfig.class",
            "com/destroystokyo/paper/PaperMCConfig.class",
            "io/papermc/paper/configuration/Configuration.class"
    };

    private RuntimeServerFlavorDetector() {
    }

    /**
     * Detects the runtime flavor using the supplied class loader.
     *
     * @param classLoader the class loader to inspect
     * @return the detected server flavor
     */
    public static @NotNull RuntimeServerFlavor detect(@NotNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");
        return hasPaperMarker(classLoader) ? RuntimeServerFlavor.PAPER : RuntimeServerFlavor.SPIGOT;
    }

    /**
     * Detects the runtime flavor using the supplied class loaders.
     *
     * @param classLoaders the class loaders to inspect
     * @return the detected server flavor
     */
    public static @NotNull RuntimeServerFlavor detect(@NotNull Iterable<? extends ClassLoader> classLoaders) {
        Objects.requireNonNull(classLoaders, "classLoaders cannot be null");
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null && hasPaperMarker(classLoader)) {
                return RuntimeServerFlavor.PAPER;
            }
        }
        return RuntimeServerFlavor.SPIGOT;
    }

    private static boolean hasPaperMarker(@NotNull ClassLoader classLoader) {
        for (String marker : PAPER_MARKERS) {
            if (classLoader.getResource(marker) != null) {
                return true;
            }
        }
        return false;
    }
}
