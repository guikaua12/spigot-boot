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
package tech.guilhermekaua.spigotboot.core.test.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.exceptions.ValidationException;
import tech.guilhermekaua.spigotboot.core.validation.ValidationResult;
import tech.guilhermekaua.spigotboot.core.validation.Validator;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Min;
import tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Range;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Validator.
 */
class ValidatorTest {

    static class ValidConfig {
        @NotNull
        String name;

        @Min(1)
        int count;
    }

    static class RangeConfig {
        @Range(min = 1, max = 100)
        int value;
    }

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validator.create();
    }

    @Test
    void testValidObject() {
        ValidConfig config = new ValidConfig();
        config.name = "Test";
        config.count = 10;

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testNotNullViolation() {
        ValidConfig config = new ValidConfig();
        config.name = null; // violates @NotNull
        config.count = 10;

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(1, result.errors().size());
    }

    @Test
    void testMinViolation() {
        ValidConfig config = new ValidConfig();
        config.name = "Test";
        config.count = 0; // violates @Min(1)

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
    }

    @Test
    void testRangeViolation() {
        RangeConfig config = new RangeConfig();
        config.value = 150; // violates @Range(min=1, max=100)

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
    }

    @Test
    void testValidateOrThrow_valid() {
        ValidConfig config = new ValidConfig();
        config.name = "Test";
        config.count = 10;

        assertDoesNotThrow(() -> validator.validateOrThrow(config));
    }

    @Test
    void testValidateOrThrow_invalid() {
        ValidConfig config = new ValidConfig();
        config.name = null;
        config.count = 10;

        assertThrows(ValidationException.class, () -> validator.validateOrThrow(config));
    }

    @Test
    void testFormatErrors() {
        ValidConfig config = new ValidConfig();
        config.name = null;
        config.count = 0;

        ValidationResult result = validator.validate(config);
        String formatted = result.formatErrors();

        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
    }
}
