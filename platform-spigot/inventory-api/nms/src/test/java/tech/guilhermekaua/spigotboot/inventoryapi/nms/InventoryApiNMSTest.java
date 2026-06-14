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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.title.BukkitInventoryTitleUpdater;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.title.InventoryTitleUpdater;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the title-updater selection logic in {@link InventoryApiNMS#resolveTitleUpdater(int, String)}.
 *
 * <p>The legacy per-version branches (e.g. {@code v1_8_R3}) are intentionally not asserted here:
 * instantiating them links {@code net.minecraft.server.vX_Y_RZ} types that are absent from a plain
 * test JVM. The cases below exercise only the modern Bukkit-API branch and the unknown-suffix
 * failure, which is where the behavior change lives.
 */
class InventoryApiNMSTest {

    @Test
    void unversioned_modern_package_selects_bukkit_api_even_when_minor_parses_low() {
        // regression: Paper/Folia "26.x" reports an un-versioned org.bukkit.craftbukkit package
        // (suffix null) and a version string whose minor parses as 1. The old selector threw
        // "Unable to detect CraftBukkit package suffix"; it must now pick the public Bukkit API.
        InventoryTitleUpdater updater = InventoryApiNMS.resolveTitleUpdater(1, null);

        assertInstanceOf(BukkitInventoryTitleUpdater.class, updater);
    }

    @Test
    void unparseable_version_with_unversioned_package_selects_bukkit_api() {
        assertInstanceOf(BukkitInventoryTitleUpdater.class, InventoryApiNMS.resolveTitleUpdater(-1, null));
    }

    @Test
    void modern_minor_selects_bukkit_api() {
        assertInstanceOf(BukkitInventoryTitleUpdater.class, InventoryApiNMS.resolveTitleUpdater(21, null));
        // even if a versioned suffix is somehow still reported, minor >= 20 wins
        assertInstanceOf(BukkitInventoryTitleUpdater.class, InventoryApiNMS.resolveTitleUpdater(20, "v1_20_R1"));
    }

    @Test
    void legacy_versioned_package_without_mapping_throws() {
        assertThrows(IllegalStateException.class, () -> InventoryApiNMS.resolveTitleUpdater(15, "v1_15_R1"));
    }
}
