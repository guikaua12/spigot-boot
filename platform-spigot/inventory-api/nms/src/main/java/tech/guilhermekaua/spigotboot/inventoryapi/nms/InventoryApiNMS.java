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
package tech.guilhermekaua.spigotboot.inventoryapi.nms;

import org.bukkit.Bukkit;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.title.BukkitInventoryTitleUpdater;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.title.InventoryTitleUpdater;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_12_R1.title.TitleUpdater_1_12_R1;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_16_R3.title.TitleUpdater_1_16_R3;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_17_R1.title.TitleUpdater_1_17_R1;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_18_R2.title.TitleUpdater_1_18_R2;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_19_R3.title.TitleUpdater_1_19_R3;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_8_R3.title.TitleUpdater_1_8_R3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the right {@link InventoryTitleUpdater} for the running server. The strategy is:
 *
 * <ol>
 *     <li>If the Bukkit minor version is 20 or newer (1.20+), return {@link BukkitInventoryTitleUpdater}
 *         which uses the public Bukkit API ({@code InventoryView#setTitle}) added in 1.20.</li>
 *     <li>Otherwise, look up the CraftBukkit package suffix (e.g. {@code v1_19_R3}) and
 *         instantiate the matching per-version NMS class.</li>
 * </ol>
 *
 * <p>The dispatch uses static references — not reflection — so that the maven-shade-plugin's
 * minimizer keeps each per-version class in the consumer's plugin jar. The JVM still loads
 * each {@code TitleUpdater_1_X_RY} lazily on first use, so a server running 1.16.5 never
 * resolves the {@code net.minecraft.server.v1_19_R3.*} types referenced by
 * {@link TitleUpdater_1_19_R3} — only the chosen branch is linked.
 */
public final class InventoryApiNMS {

    private static final Pattern PACKAGE_SUFFIX = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");
    private static final Pattern BUKKIT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private InventoryApiNMS() {
    }

    /**
     * Resolves the {@link InventoryTitleUpdater} appropriate for the current server.
     *
     * @return the resolved updater
     * @throws IllegalStateException if the server version is older than 1.20 and the matching
     *                               per-version NMS implementation cannot be found on the
     *                               classpath
     */
    public static InventoryTitleUpdater getTitleUpdater() {
        int minor = detectMinorVersion();
        if (minor >= 20) {
            return new BukkitInventoryTitleUpdater();
        }

        String suffix = detectPackageSuffix();
        if (suffix == null) {
            // Object-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
            // getClass() must resolve via the java.* ignore (see root pom)
            Object server = Bukkit.getServer();
            throw new IllegalStateException(
                    "Unable to detect CraftBukkit package suffix (server.class=" +
                            server.getClass().getName() +
                            "); register a custom TitleUpdater bean to bypass the selector"
            );
        }

        // dispatch via static references so shade's minimizer keeps these classes in the
        // consumer jar; the JVM still loads each branch lazily so unused versions never link
        // their NMS imports.
        switch (suffix) {
            case "v1_8_R3":
                return new TitleUpdater_1_8_R3();
            case "v1_12_R1":
                return new TitleUpdater_1_12_R1();
            case "v1_16_R3":
                return new TitleUpdater_1_16_R3();
            case "v1_17_R1":
                return new TitleUpdater_1_17_R1();
            case "v1_18_R2":
                return new TitleUpdater_1_18_R2();
            case "v1_19_R3":
                return new TitleUpdater_1_19_R3();
            default:
                throw new IllegalStateException(
                        "No NMS title updater available for server version " + suffix +
                                "; supported pre-1.20 versions are 1.8.8, 1.12.2, 1.16.5, 1.17.1, " +
                                "1.18.2, 1.19.4. Register a custom TitleUpdater bean to support " +
                                "other versions"
                );
        }
    }

    /**
     * Parses {@code Bukkit.getBukkitVersion()} (e.g. {@code "1.19.4-R0.1-SNAPSHOT"}) and returns
     * the minor component (the {@code 19} above) — the only part we need to decide whether the
     * Bukkit API path is available. Returns {@code -1} if it cannot be parsed, which forces the
     * selector down the NMS branch.
     */
    static int detectMinorVersion() {
        String version = Bukkit.getBukkitVersion();
        if (version == null) {
            return -1;
        }
        Matcher matcher = BUKKIT_VERSION.matcher(version);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Pulls the {@code v1_X_RY} suffix off {@code Bukkit.getServer().getClass().getPackage()}.
     * Returns {@code null} on Paper 1.20.5+ where the package is no longer versioned — callers
     * should already have taken the Bukkit-API branch by that point because Paper 1.20.5+ is
     * always major &ge; 20.
     */
    static String detectPackageSuffix() {
        // Object-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
        // getClass() must resolve via the java.* ignore (see root pom)
        Object server = Bukkit.getServer();
        String pkg = server.getClass().getPackage().getName();
        int lastDot = pkg.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String suffix = pkg.substring(lastDot + 1);
        if (!PACKAGE_SUFFIX.matcher(suffix).matches()) {
            return null;
        }
        return suffix;
    }
}
