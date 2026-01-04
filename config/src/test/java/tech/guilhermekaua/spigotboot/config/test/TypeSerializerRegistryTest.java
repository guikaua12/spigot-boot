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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TypeSerializerRegistry}.
 */
class TypeSerializerRegistryTest {

    private TypeSerializerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = TypeSerializerRegistry.defaults();
    }

    @Test
    void testGetStringSerializer() {
        TypeSerializer<String> serializer = registry.get(String.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetIntSerializer() {
        TypeSerializer<Integer> serializer = registry.get(Integer.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetPrimitiveIntSerializer() {
        TypeSerializer<Integer> serializer = registry.get(int.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetBooleanSerializer() {
        TypeSerializer<Boolean> serializer = registry.get(Boolean.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetDoubleSerializer() {
        TypeSerializer<Double> serializer = registry.get(Double.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetLongSerializer() {
        TypeSerializer<Long> serializer = registry.get(Long.class);
        assertNotNull(serializer);
    }

    @Test
    void testGetUnregisteredType() {
        TypeSerializer<Object> serializer = registry.get(Object.class);
        assertNull(serializer);
    }

    @Test
    void testCopy() {
        TypeSerializerRegistry copy = registry.copy();
        assertNotNull(copy);
        assertNotSame(registry, copy);

        assertNotNull(copy.get(String.class));
        assertNotNull(copy.get(Integer.class));
    }

    @Test
    void testCreateEmpty() {
        TypeSerializerRegistry empty = TypeSerializerRegistry.create();
        assertNotNull(empty);

        // no defaults
        assertNull(empty.get(String.class));
    }
}
