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
import tech.guilhermekaua.spigotboot.core.validation.annotation.NotEmpty;
import tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Range;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    static class InheritedNotNullBaseConfig {
        @NotNull
        String baseName;
    }

    static class InheritedNotNullConfig extends InheritedNotNullBaseConfig {
        String childName;
    }

    static class NestedConfig {
        @NotNull
        String nestedValue;
    }

    static class InheritedValidBaseConfig {
        @Valid
        NestedConfig nested;
    }

    static class InheritedValidConfig extends InheritedValidBaseConfig {
        String childName;
    }

    static class GrandParentConfig {
        @NotNull
        String grandParentName;
    }

    static class ParentConfig extends GrandParentConfig {
        String parentValue;
    }

    static class ChildConfig extends ParentConfig {
        String childValue;
    }

    static class NotEmptyConfig {
        @NotEmpty
        String name;

        @NotEmpty(failFast = false)
        List<String> tags;

        @NotEmpty
        Map<String, String> attributes;

        @NotEmpty
        String[] items;
    }

    static class NotEmptyUnsupportedTypeConfig {
        @NotEmpty
        Integer count;
    }

    static class InheritedNotEmptyBaseConfig {
        @NotEmpty
        String baseName;
    }

    static class InheritedNotEmptyConfig extends InheritedNotEmptyBaseConfig {
        String childName;
    }

    private NotEmptyConfig validNotEmptyConfig() {
        NotEmptyConfig config = new NotEmptyConfig();
        config.name = "Test";
        config.tags = new ArrayList<>(List.of("tag1"));
        config.attributes = new HashMap<>(Map.of("key", "value"));
        config.items = new String[]{"item1"};
        return config;
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

    @Test
    void testInheritedNotNullViolation() {
        InheritedNotNullConfig config = new InheritedNotNullConfig();
        config.baseName = null; // violates @NotNull in superclass
        config.childName = "child";

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> "baseName".equals(error.getFieldName())));
        assertTrue(result.errors().stream().anyMatch(error -> "baseName".equals(error.getPath().asString())));
    }

    @Test
    void testInheritedValidTriggersNestedValidation() {
        InheritedValidConfig config = new InheritedValidConfig();
        NestedConfig nested = new NestedConfig();
        nested.nestedValue = null; // violates nested @NotNull

        config.nested = nested;
        config.childName = "child";

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> "nestedValue".equals(error.getFieldName())));
        assertTrue(result.errors().stream().anyMatch(error -> "nested.nestedValue".equals(error.getPath().asString())));
    }

    @Test
    void testGrandParentFieldIsValidated() {
        ChildConfig config = new ChildConfig();
        config.grandParentName = null; // violates @NotNull declared two levels up
        config.parentValue = "parent";
        config.childValue = "child";

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> "grandParentName".equals(error.getFieldName())));
        assertTrue(result.errors().stream().anyMatch(error -> "grandParentName".equals(error.getPath().asString())));
    }

    @Test
    void testNotEmptyValidObject() {
        NotEmptyConfig config = validNotEmptyConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testNotEmptyNullViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.name = null; // violates @NotEmpty (null treated as empty)

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "name".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyEmptyStringViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.name = ""; // violates @NotEmpty

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "name".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyBlankStringIsValid() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.name = " "; // length > 0, not a whitespace-trim check

        ValidationResult result = validator.validate(config);
        assertFalse(result.errors().stream().anyMatch(error -> "name".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyEmptyListViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.tags = new ArrayList<>(); // violates @NotEmpty(failFast = false)

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "tags".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyEmptyMapViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.attributes = new HashMap<>(); // violates @NotEmpty

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "attributes".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyEmptyArrayViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.items = new String[0]; // violates @NotEmpty

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "items".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyUnsupportedTypeIsAlwaysInvalid() {
        NotEmptyUnsupportedTypeConfig config = new NotEmptyUnsupportedTypeConfig();
        config.count = 5; // non-null but unmeasurable type -> always invalid by design

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "count".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyFailFastDefaultTrue() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.name = null; // @NotEmpty on `name` has default failFast = true

        ValidationResult result = validator.validate(config);
        assertTrue(result.getFailFastErrors().stream().anyMatch(error -> "name".equals(error.getFieldName())));
        assertThrows(ValidationException.class, () -> validator.validateOrThrow(config));
    }

    @Test
    void testNotEmptyFailFastFalseOverride() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.tags = new ArrayList<>(); // @NotEmpty(failFast = false) on `tags`

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error -> "tags".equals(error.getFieldName())));
        assertFalse(result.getFailFastErrors().stream().anyMatch(error -> "tags".equals(error.getFieldName())));
        assertDoesNotThrow(() -> validator.validateOrThrow(config));
    }

    @Test
    void testInheritedNotEmptyViolation() {
        InheritedNotEmptyConfig config = new InheritedNotEmptyConfig();
        config.baseName = null; // violates @NotEmpty declared in superclass
        config.childName = "child";

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> "baseName".equals(error.getFieldName())));
        assertTrue(result.errors().stream().anyMatch(error -> "baseName".equals(error.getPath().asString())));
    }
}
