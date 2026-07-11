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
import tech.guilhermekaua.spigotboot.core.validation.annotation.AssertFalse;
import tech.guilhermekaua.spigotboot.core.validation.annotation.AssertTrue;
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

        @NotEmpty
        int[] scores;
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

    static class AssertTrueConfig {
        int min;
        int max;
        Boolean optionalCheck;

        @AssertTrue(message = "max must be >= min", path = "max")
        private boolean isMaxGteMin() {
            return max >= min;
        }

        @AssertTrue(failFast = false)
        private boolean isEnabled() {
            return true;
        }

        @AssertTrue(message = "optional check must be true", failFast = false)
        private Boolean optionalCheckTrue() {
            return optionalCheck;
        }
    }

    static class AssertFalseConfig {
        boolean production;
        boolean debug;

        @AssertFalse(message = "debug must be off in production", path = "debug")
        private boolean isDebugInProduction() {
            return production && debug;
        }

        @AssertFalse(failFast = false)
        private boolean isFlagClear() {
            return false;
        }
    }

    static class AssertTrueDefaultPathConfig {
        boolean ok = false;

        @AssertTrue
        private boolean isOk() {
            return ok;
        }
    }

    static class AssertTrueNestedPathConfig {
        @AssertTrue(message = "nested path failed", path = "a.b")
        private boolean nestedRule() {
            return false;
        }
    }

    static class AssertTrueInvalidSignatureConfig {
        @AssertTrue
        private boolean hasParam(int x) {
            return true;
        }

        @AssertTrue(path = "badReturn")
        private String notBoolean() {
            return "nope";
        }
    }

    static class AssertTrueThrowsConfig {
        @AssertTrue(message = "should not throw")
        private boolean boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    static class InheritedAssertTrueBaseConfig {
        boolean baseEnabled = false;

        @AssertTrue(path = "baseEnabled")
        private boolean isBaseEnabled() {
            return baseEnabled;
        }
    }

    static class InheritedAssertTrueConfig extends InheritedAssertTrueBaseConfig {
        String childName;
    }

    /**
     * Parent and child each declare a private assert with the same signature.
     * Both must be collected and evaluated (private methods do not override).
     */
    static class PrivateAssertSameSignatureBase {
        boolean baseOk = false;

        @AssertTrue(message = "base private failed", path = "baseOk", failFast = false)
        private boolean isValid() {
            return baseOk;
        }
    }

    static class PrivateAssertSameSignatureChild extends PrivateAssertSameSignatureBase {
        boolean childOk = false;

        @AssertTrue(message = "child private failed", path = "childOk", failFast = false)
        private boolean isValid() {
            return childOk;
        }
    }

    interface AssertTrueDefaultMethodIface {
        boolean interfaceEnabled();

        @AssertTrue(message = "interface rule failed", path = "interfaceEnabled")
        default boolean isInterfaceEnabled() {
            return interfaceEnabled();
        }
    }

    interface AssertTrueSuperIface {
        boolean superIfaceEnabled();

        @AssertTrue(message = "superinterface rule failed", path = "superIfaceEnabled")
        default boolean isSuperIfaceEnabled() {
            return superIfaceEnabled();
        }
    }

    interface AssertTrueChildIface extends AssertTrueSuperIface {
        boolean childIfaceEnabled();

        @AssertTrue(message = "child interface rule failed", path = "childIfaceEnabled")
        default boolean isChildIfaceEnabled() {
            return childIfaceEnabled();
        }
    }

    static class InterfaceAssertTrueConfig implements AssertTrueDefaultMethodIface {
        boolean interfaceEnabled = false;

        @Override
        public boolean interfaceEnabled() {
            return interfaceEnabled;
        }
    }

    static class SuperinterfaceAssertTrueConfig implements AssertTrueChildIface {
        boolean childIfaceEnabled = true;
        boolean superIfaceEnabled = false;

        @Override
        public boolean childIfaceEnabled() {
            return childIfaceEnabled;
        }

        @Override
        public boolean superIfaceEnabled() {
            return superIfaceEnabled;
        }
    }

    static class ClassOverridesInterfaceAssertConfig implements AssertTrueDefaultMethodIface {
        boolean interfaceEnabled = false;
        boolean overridePass = true;

        @Override
        public boolean interfaceEnabled() {
            return interfaceEnabled;
        }

        @AssertTrue(message = "class override rule failed", path = "overridePass")
        @Override
        public boolean isInterfaceEnabled() {
            return overridePass;
        }
    }

    /**
     * Superclass declares a public annotated assert; subclass overrides without
     * re-annotating. The ancestor rule must not apply (return false would fail
     * if the annotation were retained under virtual dispatch).
     */
    static class AnnotatedAssertSuperclass {
        @AssertTrue(message = "superclass rule failed", path = "flag")
        public boolean isFlag() {
            return true;
        }
    }

    static class UnannotatedOverrideOfSuperclassAssert extends AnnotatedAssertSuperclass {
        @Override
        public boolean isFlag() {
            return false;
        }
    }

    /**
     * Interface default carries {@code @AssertTrue}; implementing class overrides
     * without the annotation. Ancestor interface rule must not apply.
     */
    static class UnannotatedOverrideOfInterfaceAssert implements AssertTrueDefaultMethodIface {
        @Override
        public boolean interfaceEnabled() {
            return false;
        }

        @Override
        public boolean isInterfaceEnabled() {
            return false;
        }
    }

    static class CoexistFieldAndMethodConfig {
        @NotNull
        String name;

        int min;
        int max;

        @AssertTrue(message = "max must be >= min", path = "max")
        private boolean isMaxGteMin() {
            return max >= min;
        }
    }

    static class CustomSuggestionConfig {
        @NotNull(suggestion = "Set 'name' in config.yml")
        String name;

        @NotEmpty(suggestion = "Provide at least one slot index")
        int[] slots = new int[0];

        @Min(value = 5, suggestion = "pick at least {value}")
        int count = 0;

        @AssertTrue(message = "material required", path = "material",
                suggestion = "Set material or head texture")
        private boolean hasMaterial() {
            return false;
        }
    }

    static class DefaultSuggestionConfig {
        @NotNull
        String name;

        @NotEmpty
        int[] slots = new int[0];

        @Min(5)
        int count = 0;
    }

    private NotEmptyConfig validNotEmptyConfig() {
        NotEmptyConfig config = new NotEmptyConfig();
        config.name = "Test";
        config.tags = new ArrayList<>(List.of("tag1"));
        config.attributes = new HashMap<>(Map.of("key", "value"));
        config.items = new String[]{"item1"};
        config.scores = new int[]{1, 2, 3};
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
    void testNotEmptyPrimitiveArrayValid() {
        NotEmptyConfig config = validNotEmptyConfig();
        // config.scores = new int[]{1, 2, 3} from the baseline fixture

        ValidationResult result = validator.validate(config);
        assertFalse(result.errors().stream().anyMatch(error -> "scores".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyEmptyPrimitiveArrayViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.scores = new int[0]; // violates @NotEmpty - int[] is an array like any other

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "scores".equals(error.getFieldName())));
    }

    @Test
    void testNotEmptyNullPrimitiveArrayViolation() {
        NotEmptyConfig config = validNotEmptyConfig();
        config.scores = null; // violates @NotEmpty - null still fails regardless of component type

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "scores".equals(error.getFieldName())));
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

    @Test
    void testAssertTrueValidObject() {
        AssertTrueConfig config = new AssertTrueConfig();
        config.min = 1;
        config.max = 10;
        config.optionalCheck = true;

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testAssertTrueViolationUsesPathAttribute() {
        AssertTrueConfig config = new AssertTrueConfig();
        config.min = 10;
        config.max = 1; // violates isMaxGteMin with path = "max"

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "max".equals(error.getFieldName())
                        && "max".equals(error.getPath().asString())
                        && "max must be >= min".equals(error.getMessage())
                        && Boolean.FALSE.equals(error.getInvalidValue())
        ));
    }

    @Test
    void testAssertTrueDefaultPathUsesMethodName() {
        AssertTrueDefaultPathConfig config = new AssertTrueDefaultPathConfig();
        config.ok = false;

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "isOk".equals(error.getFieldName())
                        && "isOk".equals(error.getPath().asString())
        ));
    }

    @Test
    void testAssertTrueNestedPath() {
        AssertTrueNestedPathConfig config = new AssertTrueNestedPathConfig();

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "b".equals(error.getFieldName())
                        && "a.b".equals(error.getPath().asString())
        ));
    }

    @Test
    void testAssertTrueNullBooleanReturnIsValid() {
        AssertTrueConfig config = new AssertTrueConfig();
        config.min = 1;
        config.max = 10;
        config.optionalCheck = null; // null Boolean return is valid

        ValidationResult result = validator.validate(config);
        assertFalse(result.errors().stream().anyMatch(error ->
                error.getMessage().contains("optional check")
        ));
    }

    @Test
    void testAssertTrueFailFastDefaultTrue() {
        AssertTrueConfig config = new AssertTrueConfig();
        config.min = 10;
        config.max = 1; // path = "max", failFast default true

        ValidationResult result = validator.validate(config);
        assertTrue(result.getFailFastErrors().stream().anyMatch(error -> "max".equals(error.getFieldName())));
        assertThrows(ValidationException.class, () -> validator.validateOrThrow(config));
    }

    @Test
    void testAssertTrueFailFastFalseOverride() {
        AssertTrueConfig config = new AssertTrueConfig();
        config.min = 1;
        config.max = 10;
        config.optionalCheck = false; // failFast = false on optionalCheckTrue

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "optionalCheckTrue".equals(error.getFieldName())
                        || error.getMessage().contains("optional check")
        ));
        assertFalse(result.getFailFastErrors().stream().anyMatch(error ->
                error.getMessage().contains("optional check")
        ));
        assertDoesNotThrow(() -> validator.validateOrThrow(config));
    }

    @Test
    void testAssertFalseValidObject() {
        AssertFalseConfig config = new AssertFalseConfig();
        config.production = true;
        config.debug = false;

        ValidationResult result = validator.validate(config);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testAssertFalseViolationUsesPathAttribute() {
        AssertFalseConfig config = new AssertFalseConfig();
        config.production = true;
        config.debug = true; // violates isDebugInProduction with path = "debug"

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "debug".equals(error.getFieldName())
                        && "debug".equals(error.getPath().asString())
                        && "debug must be off in production".equals(error.getMessage())
                        && Boolean.TRUE.equals(error.getInvalidValue())
        ));
    }

    @Test
    void testAssertFalseFailFastDefaultTrue() {
        AssertFalseConfig config = new AssertFalseConfig();
        config.production = true;
        config.debug = true;

        ValidationResult result = validator.validate(config);
        assertTrue(result.getFailFastErrors().stream().anyMatch(error -> "debug".equals(error.getFieldName())));
        assertThrows(ValidationException.class, () -> validator.validateOrThrow(config));
    }

    @Test
    void testInheritedAssertTrueViolation() {
        InheritedAssertTrueConfig config = new InheritedAssertTrueConfig();
        config.baseEnabled = false;
        config.childName = "child";

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "baseEnabled".equals(error.getFieldName())
                        && "baseEnabled".equals(error.getPath().asString())
        ));
    }

    @Test
    void testPrivateAssertTrueSameSignatureOnHierarchyBothRetained() {
        PrivateAssertSameSignatureChild config = new PrivateAssertSameSignatureChild();
        config.baseOk = false;
        config.childOk = false;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "childOk".equals(error.getFieldName())
                        && error.getMessage().contains("child private failed")
        ), "child private @AssertTrue must be retained");
        assertTrue(result.errors().stream().anyMatch(error ->
                "baseOk".equals(error.getFieldName())
                        && error.getMessage().contains("base private failed")
        ), "base private @AssertTrue with same signature must also be retained");
    }

    @Test
    void testPrivateAssertTrueSameSignatureBothValid() {
        PrivateAssertSameSignatureChild config = new PrivateAssertSameSignatureChild();
        config.baseOk = true;
        config.childOk = true;

        ValidationResult result = validator.validate(config);

        assertTrue(result.isValid());
    }

    @Test
    void testInterfaceDefaultAssertTrueViolation() {
        InterfaceAssertTrueConfig config = new InterfaceAssertTrueConfig();
        config.interfaceEnabled = false;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "interfaceEnabled".equals(error.getFieldName())
                        && "interfaceEnabled".equals(error.getPath().asString())
                        && error.getMessage().contains("interface rule failed")
        ));
    }

    @Test
    void testInterfaceDefaultAssertTrueValid() {
        InterfaceAssertTrueConfig config = new InterfaceAssertTrueConfig();
        config.interfaceEnabled = true;

        ValidationResult result = validator.validate(config);

        assertTrue(result.isValid());
    }

    @Test
    void testSuperinterfaceDefaultAssertTrueViolation() {
        SuperinterfaceAssertTrueConfig config = new SuperinterfaceAssertTrueConfig();
        config.childIfaceEnabled = true;
        config.superIfaceEnabled = false;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "superIfaceEnabled".equals(error.getFieldName())
                        && error.getMessage().contains("superinterface rule failed")
        ));
        assertFalse(result.errors().stream().anyMatch(error ->
                "childIfaceEnabled".equals(error.getFieldName())
        ));
    }

    @Test
    void testClassOverrideWinsOverInterfaceAssertTrue() {
        ClassOverridesInterfaceAssertConfig config = new ClassOverridesInterfaceAssertConfig();
        config.interfaceEnabled = false;
        config.overridePass = true;

        ValidationResult result = validator.validate(config);

        assertTrue(result.isValid(), "class override with matching signature must suppress interface default");
    }

    @Test
    void testUnannotatedOverrideSuppressesSuperclassAssertTrue() {
        UnannotatedOverrideOfSuperclassAssert config = new UnannotatedOverrideOfSuperclassAssert();

        ValidationResult result = validator.validate(config);

        assertTrue(result.isValid(),
                "unannotated override must suppress superclass @AssertTrue (return false would fail if retained)");
        assertFalse(result.errors().stream().anyMatch(error ->
                error.getMessage().contains("superclass rule failed")
        ));
    }

    @Test
    void testUnannotatedOverrideSuppressesInterfaceAssertTrue() {
        UnannotatedOverrideOfInterfaceAssert config = new UnannotatedOverrideOfInterfaceAssert();

        ValidationResult result = validator.validate(config);

        assertTrue(result.isValid(),
                "unannotated class override must suppress interface default @AssertTrue");
        assertFalse(result.errors().stream().anyMatch(error ->
                error.getMessage().contains("interface rule failed")
        ));
    }

    @Test
    void testClassOverrideAssertTrueViolationUsesClassAnnotation() {
        ClassOverridesInterfaceAssertConfig config = new ClassOverridesInterfaceAssertConfig();
        config.interfaceEnabled = true;
        config.overridePass = false;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "overridePass".equals(error.getFieldName())
                        && error.getMessage().contains("class override rule failed")
        ));
        assertFalse(result.errors().stream().anyMatch(error ->
                error.getMessage().contains("interface rule failed")
        ));
    }

    @Test
    void testAssertTrueInvalidSignatureFailsLoud() {
        AssertTrueInvalidSignatureConfig config = new AssertTrueInvalidSignatureConfig();

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "hasParam".equals(error.getFieldName())
                        && error.getMessage().contains("take no parameters")
        ));
        assertTrue(result.errors().stream().anyMatch(error ->
                "badReturn".equals(error.getFieldName())
                        && error.getMessage().contains("boolean")
        ));
    }

    @Test
    void testAssertTrueInvocationExceptionFailsLoud() {
        AssertTrueThrowsConfig config = new AssertTrueThrowsConfig();

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error ->
                "boom".equals(error.getFieldName())
                        && error.getMessage().contains("kaboom")
        ));
    }

    @Test
    void testAssertMethodCoexistsWithFieldConstraints() {
        CoexistFieldAndMethodConfig config = new CoexistFieldAndMethodConfig();
        config.name = null; // field violation
        config.min = 10;
        config.max = 1; // method violation

        ValidationResult result = validator.validate(config);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "name".equals(error.getFieldName())));
        assertTrue(result.errors().stream().anyMatch(error -> "max".equals(error.getFieldName())));
    }

    /**
     * Cached assert-method discovery must keep hierarchy/interface order and results
     * stable across repeated validations of the same class.
     */
    @Test
    void testAssertMethodDiscoveryCacheIsStableAcrossRepeatedValidation() {
        SuperinterfaceAssertTrueConfig config = new SuperinterfaceAssertTrueConfig();
        config.childIfaceEnabled = false;
        config.superIfaceEnabled = false;

        ValidationResult first = validator.validate(config);
        ValidationResult second = validator.validate(config);

        List<String> firstPaths = first.errors().stream()
                .map(error -> error.getPath().asString() + "|" + error.getMessage())
                .toList();
        List<String> secondPaths = second.errors().stream()
                .map(error -> error.getPath().asString() + "|" + error.getMessage())
                .toList();

        assertEquals(2, firstPaths.size());
        assertEquals(firstPaths, secondPaths);

        PrivateAssertSameSignatureChild privateConfig = new PrivateAssertSameSignatureChild();
        privateConfig.baseOk = false;
        privateConfig.childOk = false;

        ValidationResult privateFirst = validator.validate(privateConfig);
        ValidationResult privateSecond = validator.validate(privateConfig);
        List<String> privateFirstPaths = privateFirst.errors().stream()
                .map(error -> error.getPath().asString() + "|" + error.getMessage())
                .toList();
        List<String> privateSecondPaths = privateSecond.errors().stream()
                .map(error -> error.getPath().asString() + "|" + error.getMessage())
                .toList();

        assertEquals(2, privateFirstPaths.size());
        assertEquals(privateFirstPaths, privateSecondPaths);
    }

    @Test
    void testCustomSuggestionOverridesNotNullDefault() {
        CustomSuggestionConfig config = new CustomSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "name".equals(error.getFieldName())
                        && "Set 'name' in config.yml".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testCustomSuggestionOverridesNotEmptyDefault() {
        CustomSuggestionConfig config = new CustomSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "slots".equals(error.getFieldName())
                        && "Provide at least one slot index".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testCustomSuggestionOnMinIsLiteralNoPlaceholderSubstitution() {
        CustomSuggestionConfig config = new CustomSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "count".equals(error.getFieldName())
                        && "pick at least {value}".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testCustomSuggestionOverridesAssertTrueDefault() {
        CustomSuggestionConfig config = new CustomSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "material".equals(error.getFieldName())
                        && "Set material or head texture".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testDefaultSuggestionUsedWhenNotEmptySuggestionOmitted() {
        DefaultSuggestionConfig config = new DefaultSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "slots".equals(error.getFieldName())
                        && "Provide at least one value".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testDefaultSuggestionUsedWhenMinSuggestionOmitted() {
        DefaultSuggestionConfig config = new DefaultSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "count".equals(error.getFieldName())
                        && "Use a value >= 5".equals(error.getSuggestedFix())
        ));
    }

    @Test
    void testNotNullHasNoDefaultSuggestionWhenOmitted() {
        DefaultSuggestionConfig config = new DefaultSuggestionConfig();

        ValidationResult result = validator.validate(config);
        assertTrue(result.errors().stream().anyMatch(error ->
                "name".equals(error.getFieldName())
                        && error.getSuggestedFix() == null
        ));
    }

    @Test
    void testEmptyIntArrayNotEmptyErrorFormatsWithoutIdentityHash() {
        // End-to-end mirror of the user's report: @NotEmpty int[] slots
        CustomSuggestionConfig config = new CustomSuggestionConfig();

        ValidationResult result = validator.validate(config);
        String formatted = result.formatErrors();

        assertFalse(formatted.contains("[I@"), formatted);
        assertTrue(formatted.contains("(was: [])"), formatted);
    }
}
