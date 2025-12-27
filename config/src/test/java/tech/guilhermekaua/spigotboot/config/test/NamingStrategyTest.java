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
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link NamingStrategy}.
 */
class NamingStrategyTest {

    @Test
    void testIdentity_toConfig() {
        assertEquals("fieldName", NamingStrategy.IDENTITY.toConfig("fieldName"));
        assertEquals("myVariable", NamingStrategy.IDENTITY.toConfig("myVariable"));
    }

    @Test
    void testIdentity_toJava() {
        assertEquals("field_name", NamingStrategy.IDENTITY.toJava("field_name"));
        assertEquals("my-variable", NamingStrategy.IDENTITY.toJava("my-variable"));
    }

    @Test
    void testSnakeCase_toConfig() {
        assertEquals("field_name", NamingStrategy.SNAKE_CASE.toConfig("fieldName"));
        assertEquals("my_variable_name", NamingStrategy.SNAKE_CASE.toConfig("myVariableName"));
        assertEquals("url", NamingStrategy.SNAKE_CASE.toConfig("url"));
        assertEquals("http_url", NamingStrategy.SNAKE_CASE.toConfig("httpUrl"));
    }

    @Test
    void testSnakeCase_toJava() {
        assertEquals("fieldName", NamingStrategy.SNAKE_CASE.toJava("field_name"));
        assertEquals("myVariableName", NamingStrategy.SNAKE_CASE.toJava("my_variable_name"));
        assertEquals("url", NamingStrategy.SNAKE_CASE.toJava("url"));
    }

    @Test
    void testKebabCase_toConfig() {
        assertEquals("field-name", NamingStrategy.KEBAB_CASE.toConfig("fieldName"));
        assertEquals("my-variable-name", NamingStrategy.KEBAB_CASE.toConfig("myVariableName"));
    }

    @Test
    void testKebabCase_toJava() {
        assertEquals("fieldName", NamingStrategy.KEBAB_CASE.toJava("field-name"));
        assertEquals("myVariableName", NamingStrategy.KEBAB_CASE.toJava("my-variable-name"));
    }

    @Test
    void testLowerCamel_toConfig() {
        assertEquals("fieldName", NamingStrategy.LOWER_CAMEL.toConfig("FieldName"));
        assertEquals("myVariable", NamingStrategy.LOWER_CAMEL.toConfig("MyVariable"));
        assertEquals("fieldName", NamingStrategy.LOWER_CAMEL.toConfig("fieldName"));
    }

    @Test
    void testLowerCamel_toJava() {
        assertEquals("fieldName", NamingStrategy.LOWER_CAMEL.toJava("fieldName"));
    }

    @Test
    void testEmptyString() {
        assertEquals("", NamingStrategy.SNAKE_CASE.toConfig(""));
        assertEquals("", NamingStrategy.KEBAB_CASE.toConfig(""));
        assertEquals("", NamingStrategy.LOWER_CAMEL.toConfig(""));
        assertEquals("", NamingStrategy.IDENTITY.toConfig(""));
    }
}
