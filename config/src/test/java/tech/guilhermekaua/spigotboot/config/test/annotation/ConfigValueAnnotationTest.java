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
package tech.guilhermekaua.spigotboot.config.test.annotation;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValueAnnotationTest {

    @Test
    void valueDefaultsToEmptyString() throws NoSuchMethodException {
        Method value = ConfigValue.class.getDeclaredMethod("value");
        assertEquals("", value.getDefaultValue());
    }

    @Test
    void defaultValueDefaultsToSentinel() throws NoSuchMethodException {
        Method defaultValue = ConfigValue.class.getDeclaredMethod("defaultValue");
        assertEquals(ConfigValue.DEFAULT_NONE, defaultValue.getDefaultValue());
    }

    @Test
    void sentinelIsNotAPlausibleRealValue() {
        assertNotEquals("", ConfigValue.DEFAULT_NONE);
        assertTrue(ConfigValue.DEFAULT_NONE.contains("DEFAULT_NONE"));
    }
}
