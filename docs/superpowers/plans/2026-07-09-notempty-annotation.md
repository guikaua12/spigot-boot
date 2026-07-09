# @NotEmpty Validation Annotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `@NotEmpty` field-level validation annotation to `core`'s validation package that rejects both `null` and empty `String`/`CharSequence`/`Collection`/`Map`/array values in a single declaration.

**Architecture:** One new annotation type (`NotEmpty`) plus one new `ConstraintFactory<NotEmpty>` registered in `DefaultValidator.registerDefaultFactories()`, following the exact pattern already used for `NotNull` and `Size` in that file. No changes to `Constraint`, `ConstraintFactory`, `ValidationResult`, `ValidationError`, `PropertyPath`, or `Validator` — the reflection-driven dispatch loop in `DefaultValidator.validate(...)` already activates any annotation present in the `factories` map.

**Tech Stack:** Java 17, JUnit 5 (existing `ValidatorTest.java` suite).

**Branch:** `feat/notempty-annotation`, created off `dev`.

## Global Constraints

- Java source/target: 17 (`pom.xml` / `core/pom.xml`, `maven.compiler.source`/`target`).
- Build must run under JDK 21 for the shell (Lombok 1.18.36 crashes under JDK 25 — `TypeTag :: UNKNOWN`). If running Maven yourself, ensure `JAVA_HOME` points at a JDK 21 install.
- `null` → `@NotEmpty` invalid (matches Jakarta's `@NotEmpty`, approved design decision).
- `failFast()` attribute defaults to `true` (matches `@NotNull`; unlike `@Size`/`@Min`/`@Max`/`@Pattern`/`@OneOf` which hardcode `false`).
- Unsupported/unmeasurable non-null types → **invalid** (fail loud), not silently valid like `@Size`'s fallback. This is an intentional, approved divergence — do not "fix" it to match `@Size`.
- No message-token substitution needed (`message()` has no `{min}`/`{max}`-style placeholders, unlike `Size`/`Range`).
- License header: every new/modified `.java` file in this package uses the existing MIT header block (copy verbatim from `NotNull.java` lines 1–22).

---

### Task 1: `@NotEmpty` annotation, factory wiring, and tests

**Files:**
- Create: `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/annotation/NotEmpty.java`
- Modify: `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/DefaultValidator.java` (insert a new `factories.put(NotEmpty.class, ...)` block inside `registerDefaultFactories()`, immediately after the existing `Size` block which ends at line 286 with `});`, before the closing `}` of `registerDefaultFactories()` at line 287)
- Modify: `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/validation/ValidatorTest.java`

**Interfaces:**
- Consumes: `Constraint<T>` (`core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/Constraint.java`) — `isValid(T)`, `message(T)`, `isFailFast()`, `suggestedFix(T)`. `ConstraintFactory<A extends Annotation>` (`core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/ConstraintFactory.java`) — `create(A)`, `getAnnotationType()`.
- Produces: `tech.guilhermekaua.spigotboot.core.validation.annotation.NotEmpty` — `@interface` with `String message() default "Value cannot be empty"` and `boolean failFast() default true`, `@Retention(RUNTIME)`, `@Target(FIELD)`. Once registered, any field annotated `@NotEmpty` is checked automatically by `DefaultValidator.validate(...)` — no other task depends on this, this is the full feature.

- [ ] **Step 1: Write the failing tests in `ValidatorTest.java`**

Add these imports at the top of the file (after the existing `import` block, alongside the other `tech.guilhermekaua...validation.annotation.*` imports):

```java
import tech.guilhermekaua.spigotboot.core.validation.annotation.NotEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
```

Add these fixture classes inside `ValidatorTest`, after the existing `GrandParentConfig`/`ParentConfig`/`ChildConfig` block (after line 89, before `private Validator validator;`):

```java
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
```

Add these test methods at the end of the class, before the final closing `}`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `./mvnw -pl core test -Dtest=ValidatorTest -am`
Expected: `BUILD FAILURE` — compilation error `cannot find symbol: class NotEmpty`. This confirms the tests genuinely depend on code that doesn't exist yet.

- [ ] **Step 3: Create the `NotEmpty` annotation**

Create `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/annotation/NotEmpty.java`:

```java
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
package tech.guilhermekaua.spigotboot.core.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a field is not null and not empty.
 * <p>
 * Supports {@link CharSequence} (empty when {@code length() == 0}),
 * {@link java.util.Collection} and {@link java.util.Map} (empty when
 * {@code isEmpty()}), and arrays (empty when their length is zero).
 * A {@code null} value always fails this constraint. Any other non-null
 * type whose emptiness cannot be measured is also treated as invalid.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotEmpty {

    /**
     * The error message.
     *
     * @return the message
     */
    String message() default "Value cannot be empty";

    /**
     * Whether validation failure should fail fast (prevent plugin enable).
     *
     * @return true to fail fast
     */
    boolean failFast() default true;
}
```

- [ ] **Step 4: Run the tests to verify they now compile and fail on missing behavior**

Run: `./mvnw -pl core test -Dtest=ValidatorTest -am`
Expected: compiles successfully, but the new `testNotEmpty*`/`testInheritedNotEmptyViolation` tests **FAIL** (`@NotEmpty` isn't registered in `DefaultValidator` yet, so `result.isValid()` is always `true` — assertions like `assertFalse(result.isValid())` fail). Confirms the tests correctly detect the missing wiring.

- [ ] **Step 5: Register the `NotEmpty` constraint factory in `DefaultValidator`**

In `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/DefaultValidator.java`, insert the following block immediately after line 286 (the `});` closing the `Size` factory registration) and before line 287 (the closing `}` of `registerDefaultFactories()`):

```java

        factories.put(NotEmpty.class, new ConstraintFactory<NotEmpty>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull NotEmpty annotation) {
                return new Constraint<Object>() {
                    @Override
                    public boolean isValid(Object value) {
                        if (value == null) return false;
                        return !isEmpty(value);
                    }

                    private boolean isEmpty(Object value) {
                        if (value instanceof CharSequence) {
                            return ((CharSequence) value).length() == 0;
                        } else if (value instanceof Collection) {
                            return ((Collection<?>) value).isEmpty();
                        } else if (value instanceof Map) {
                            return ((Map<?, ?>) value).isEmpty();
                        } else if (value.getClass().isArray()) {
                            return java.lang.reflect.Array.getLength(value) == 0;
                        }
                        return true; // unmeasurable type: treat as empty -> invalid
                    }

                    @Override
                    public @NotNull String message(Object value) {
                        return annotation.message();
                    }

                    @Override
                    public boolean isFailFast() {
                        return annotation.failFast();
                    }

                    @Override
                    public String suggestedFix(Object value) {
                        return "Provide a non-empty value";
                    }
                };
            }

            @Override
            public @NotNull Class<NotEmpty> getAnnotationType() {
                return NotEmpty.class;
            }
        });
```

No new imports are needed: `NotEmpty` resolves via the existing wildcard import `tech.guilhermekaua.spigotboot.core.validation.annotation.*;` (line 27), and `Collection`/`Map` resolve via the existing `java.util.*` wildcard import (line 31). `java.lang.reflect.Array` is used fully-qualified inline, matching the exact style of the neighboring `Size` factory's `getSize` helper (line 263).

- [ ] **Step 6: Run the tests to verify everything passes**

Run: `./mvnw -pl core test -Dtest=ValidatorTest -am`
Expected: `BUILD SUCCESS`, all tests pass including the new `testNotEmpty*` and `testInheritedNotEmptyViolation` tests, and all pre-existing tests (`testValidObject`, `testNotNullViolation`, etc.) still pass unchanged.

- [ ] **Step 7: Run the full core module test suite to confirm no regressions**

Run: `./mvnw -pl core test -am`
Expected: `BUILD SUCCESS`, all tests in the `core` module pass (this catches any unintended interaction with other suites that exercise `DefaultValidator` or the annotation package, e.g. `ConfigurationProcessorTest`).

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/annotation/NotEmpty.java
git add core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/DefaultValidator.java
git add core/src/test/java/tech/guilhermekaua/spigotboot/core/test/validation/ValidatorTest.java
git commit -m "feat(core): add @NotEmpty validation annotation"
```

---

## Post-plan checklist (do not skip)

- [ ] Push the `feat/notempty-annotation` branch to the remote.
- [ ] Report completion status per `CLAUDE.md` (`DONE` / `DONE_WITH_CONCERNS` / `BLOCKED` / `NEEDS_CONTEXT`), with evidence (test output) for every claim.
- [ ] State explicitly that no service/process needs restarting — this is a library-level `core` module change with no running process affected. If any consuming module (e.g. `platform-spigot`) needs a rebuild to pick up the new `core` artifact, say so.
