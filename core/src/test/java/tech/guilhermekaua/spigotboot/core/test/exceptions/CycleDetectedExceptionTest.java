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
package tech.guilhermekaua.spigotboot.core.test.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CycleDetectedExceptionTest {

    @Test
    @DisplayName("constructor should handle null initial prefix")
    void constructorHandlesNullInitialPrefix() {
        List<String> cycle = Arrays.asList("configA", "configB", "configA");

        CycleDetectedException exception = assertDoesNotThrow(
                () -> new CycleDetectedException(cycle, Function.identity(), null)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("configA -> configB -> configA"));
        assertFalse(message.startsWith("null"), "Message should not start with the literal 'null'");
    }

    @Test
    @DisplayName("formatCycle should handle null initial prefix")
    void formatCycleHandlesNullInitialPrefix() {
        List<String> cycle = Arrays.asList("configA", "configB", "configA");

        CycleDetectedException exception = assertDoesNotThrow(
                () -> new CycleDetectedException(cycle, Function.identity(), null)
        );

        String formatted = assertDoesNotThrow(exception::formatCycle);
        assertNotNull(formatted);
        assertTrue(formatted.contains("configA -> configB -> configA"));
        assertFalse(formatted.startsWith("null"), "Formatted cycle should not start with the literal 'null'");
        assertEquals(exception.getMessage(), formatted);
    }
}
