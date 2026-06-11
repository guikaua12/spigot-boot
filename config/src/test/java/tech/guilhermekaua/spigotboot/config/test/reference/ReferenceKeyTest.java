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
package tech.guilhermekaua.spigotboot.config.test.reference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceKeyTest {

    @Test
    @DisplayName("SingleConfigKey equality")
    void singleConfigKeyEquality() {
        ReferenceKey key1 = ReferenceKey.singleConfig("config");
        ReferenceKey key2 = ReferenceKey.singleConfig("config");
        ReferenceKey key3 = ReferenceKey.singleConfig("other");

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
    }

    @Test
    @DisplayName("FolderConfigItemKey equality")
    void folderConfigItemKeyEquality() {
        ReferenceKey key1 = ReferenceKey.folderConfigItem("folder", "item");
        ReferenceKey key2 = ReferenceKey.folderConfigItem("folder", "item");
        ReferenceKey key3 = ReferenceKey.folderConfigItem("folder", "other");
        ReferenceKey key4 = ReferenceKey.folderConfigItem("other", "item");

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
    }

    @Test
    @DisplayName("SingleConfigKey and FolderConfigItemKey are not equal")
    void differentKeyTypesNotEqual() {
        ReferenceKey single = ReferenceKey.singleConfig("name");
        ReferenceKey folderConfig = ReferenceKey.folderConfigItem("name", "item");

        assertNotEquals(single, folderConfig);
    }

    @Test
    @DisplayName("getDisplayName returns readable string")
    void displayName() {
        ReferenceKey single = ReferenceKey.singleConfig("myconfig");
        ReferenceKey folderConfigItem = ReferenceKey.folderConfigItem("boosters", "2x");

        assertEquals("config:myconfig", single.getDisplayName());
        assertEquals("folder-config:boosters.2x", folderConfigItem.getDisplayName());
    }

    @Test
    @DisplayName("isSingleConfig and isFolderConfigItem work correctly")
    void typeChecks() {
        ReferenceKey single = ReferenceKey.singleConfig("config");
        ReferenceKey folderConfigItem = ReferenceKey.folderConfigItem("folder", "item");

        assertTrue(single.isSingleConfig());
        assertFalse(single.isFolderConfigItem());

        assertFalse(folderConfigItem.isSingleConfig());
        assertTrue(folderConfigItem.isFolderConfigItem());
    }
}
