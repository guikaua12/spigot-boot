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
package tech.guilhermekaua.spigotboot.config.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.collection.ConfigNodeHash;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigNodeHash}.
 */
class ConfigNodeHashTest {

    @Test
    void testSameMapContentDifferentOrder_produceSameHash() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("alpha", "value1");
        map1.put("beta", "value2");
        map1.put("gamma", "value3");

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("gamma", "value3");
        map2.put("alpha", "value1");
        map2.put("beta", "value2");

        ConfigNode node1 = new TestConfigNode(map1);
        ConfigNode node2 = new TestConfigNode(map2);

        String hash1 = ConfigNodeHash.sha256(node1);
        String hash2 = ConfigNodeHash.sha256(node2);

        assertEquals(hash1, hash2, "Same map content with different key order should produce same hash");
    }

    @Test
    void testDifferentMapContent_produceDifferentHash() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("key", "value1");

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("key", "value2");

        ConfigNode node1 = new TestConfigNode(map1);
        ConfigNode node2 = new TestConfigNode(map2);

        String hash1 = ConfigNodeHash.sha256(node1);
        String hash2 = ConfigNodeHash.sha256(node2);

        assertNotEquals(hash1, hash2, "Different map content should produce different hash");
    }

    @Test
    void testListOrderMatters() {
        List<Object> list1 = Arrays.asList("a", "b", "c");
        List<Object> list2 = Arrays.asList("c", "b", "a");

        ConfigNode node1 = new TestConfigNode(list1);
        ConfigNode node2 = new TestConfigNode(list2);

        String hash1 = ConfigNodeHash.sha256(node1);
        String hash2 = ConfigNodeHash.sha256(node2);

        assertNotEquals(hash1, hash2, "Different list order should produce different hash");
    }

    @Test
    void testSameListContent_produceSameHash() {
        List<Object> list1 = Arrays.asList("a", "b", "c");
        List<Object> list2 = Arrays.asList("a", "b", "c");

        ConfigNode node1 = new TestConfigNode(list1);
        ConfigNode node2 = new TestConfigNode(list2);

        String hash1 = ConfigNodeHash.sha256(node1);
        String hash2 = ConfigNodeHash.sha256(node2);

        assertEquals(hash1, hash2, "Same list content should produce same hash");
    }

    @Test
    void testScalarValues() {
        ConfigNode stringNode = new TestConfigNode("test");
        ConfigNode intNode = new TestConfigNode(42);
        ConfigNode boolNode = new TestConfigNode(true);

        String stringHash = ConfigNodeHash.sha256(stringNode);
        String intHash = ConfigNodeHash.sha256(intNode);
        String boolHash = ConfigNodeHash.sha256(boolNode);

        // all different types should produce different hashes
        assertNotEquals(stringHash, intHash);
        assertNotEquals(intHash, boolHash);
        assertNotEquals(stringHash, boolHash);
    }

    @Test
    void testNestedMaps() {
        Map<String, Object> inner1 = new LinkedHashMap<>();
        inner1.put("z", 1);
        inner1.put("a", 2);

        Map<String, Object> inner2 = new LinkedHashMap<>();
        inner2.put("a", 2);
        inner2.put("z", 1);

        Map<String, Object> outer1 = new LinkedHashMap<>();
        outer1.put("nested", inner1);

        Map<String, Object> outer2 = new LinkedHashMap<>();
        outer2.put("nested", inner2);

        ConfigNode node1 = new TestConfigNode(outer1);
        ConfigNode node2 = new TestConfigNode(outer2);

        String hash1 = ConfigNodeHash.sha256(node1);
        String hash2 = ConfigNodeHash.sha256(node2);

        assertEquals(hash1, hash2, "Nested maps with same content but different order should produce same hash");
    }

    @Test
    void testNullValue() {
        ConfigNode nullNode = new TestConfigNode(null);
        String hash = ConfigNodeHash.sha256(nullNode);

        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    void testCanonicalizeFormat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("count", 5);

        ConfigNode node = new TestConfigNode(map);
        String canonical = ConfigNodeHash.canonicalize(node);

        assertTrue(canonical.startsWith("M{"), "Canonical form should start with M{ for maps");
    }
}
