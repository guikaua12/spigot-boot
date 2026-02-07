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
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PropertyPath}.
 */
class PropertyPathTest {

    @Test
    void testRoot() {
        PropertyPath root = PropertyPath.root();
        assertTrue(root.isEmpty());
        assertEquals(0, root.size());
        assertEquals("", root.asString());
    }

    @Test
    void testOf() {
        PropertyPath path = PropertyPath.of("database", "connection", "host");
        assertFalse(path.isEmpty());
        assertEquals(3, path.size());
        assertEquals("database.connection.host", path.asString());
    }

    @Test
    void testParse() {
        PropertyPath path = PropertyPath.parse("database.connection.host");
        assertEquals(3, path.size());
        assertEquals("database.connection.host", path.asString());

        Object[] elements = path.elements();
        assertEquals("database", elements[0]);
        assertEquals("connection", elements[1]);
        assertEquals("host", elements[2]);
    }

    @Test
    void testParseWithIndex() {
        PropertyPath path = PropertyPath.parse("items.0.name");
        assertEquals(3, path.size());

        Object[] elements = path.elements();
        assertEquals("items", elements[0]);
        assertEquals(0, elements[1]);
        assertEquals("name", elements[2]);
    }

    @Test
    void testParseEmpty() {
        PropertyPath path = PropertyPath.parse("");
        assertTrue(path.isEmpty());
    }

    @Test
    void testChild() {
        PropertyPath parent = PropertyPath.of("database");
        PropertyPath child = parent.child("host");

        assertEquals(2, child.size());
        assertEquals("database.host", child.asString());
    }

    @Test
    void testParent() {
        PropertyPath path = PropertyPath.of("database", "connection", "host");
        PropertyPath parent = path.parent();

        assertEquals(2, parent.size());
        assertEquals("database.connection", parent.asString());
    }

    @Test
    void testParentOfRoot() {
        PropertyPath root = PropertyPath.root();
        PropertyPath parent = root.parent();
        assertTrue(parent.isEmpty());
        assertSame(PropertyPath.root(), parent);
    }

    @Test
    void testLast() {
        PropertyPath path = PropertyPath.of("database", "host");
        assertEquals("host", path.last());
    }

    @Test
    void testLastOfRoot() {
        PropertyPath root = PropertyPath.root();
        assertNull(root.last());
    }

    @Test
    void testEquals() {
        PropertyPath path1 = PropertyPath.of("database", "host");
        PropertyPath path2 = PropertyPath.parse("database.host");

        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    void testNotEquals() {
        PropertyPath path1 = PropertyPath.of("database", "host");
        PropertyPath path2 = PropertyPath.of("database", "port");

        assertNotEquals(path1, path2);
    }

    @Test
    void testToString() {
        PropertyPath path = PropertyPath.of("database", "host");
        assertEquals("ConfigPath[database.host]", path.toString());
    }
}
