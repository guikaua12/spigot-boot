/*
 * The MIT License
 * Copyright © 2026 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.core.spigot.test.utils;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.utils.TypeUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeUtilTest {

    @Test
    void getMaterialFromLegacy_resolvesModernMaterialNotLegacyVariant() {
        Material material = TypeUtil.getMaterialFromLegacy("BEDROCK");

        assertEquals(Material.BEDROCK, material);
        assertFalse(material.isLegacy(), "expected the modern BEDROCK, not LEGACY_BEDROCK");
    }

    @Test
    void getMaterialFromLegacy_resolvesModernOnlyMaterial() {
        // BAMBOO has no LEGACY_ counterpart, so it only resolves via the modern lookup
        assertEquals(Material.BAMBOO, TypeUtil.getMaterialFromLegacy("BAMBOO"));
    }

    @Test
    void getMaterialFromLegacy_returnsNullForUnknownMaterial() {
        assertNull(TypeUtil.getMaterialFromLegacy("DEFINITELY_NOT_A_MATERIAL"));
    }

    @Test
    void getMaterialFromLegacy_returnsNullForNullOrEmpty() {
        assertNull(TypeUtil.getMaterialFromLegacy(null));
        assertNull(TypeUtil.getMaterialFromLegacy(""));
    }
}
