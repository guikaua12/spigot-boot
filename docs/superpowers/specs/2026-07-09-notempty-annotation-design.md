# `@NotEmpty` validation annotation — design

Date: 2026-07-09
Status: Approved

## Problem

The core validation package (`core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/`) has field-level constraint annotations for null-checking (`@NotNull`), numeric bounds (`@Min`, `@Max`, `@Range`), string matching (`@Pattern`, `@OneOf`), and collection/string sizing (`@Size`). There is no single annotation that expresses "this field must be present and non-empty" — today that requires stacking `@NotNull` with `@Size(min = 1)`, which is verbose and easy to forget half of.

## Goal

Add a `@NotEmpty` annotation that, in one declaration, rejects both `null` and empty values for strings, collections, maps, and arrays — matching the ergonomics developers expect from Jakarta Bean Validation's `@NotEmpty`, but implemented with this codebase's existing `Constraint`/`ConstraintFactory` machinery.

## Non-goals

- No changes to `Size`, `NotNull`, or any other existing annotation.
- No compile-time / annotation-processor enforcement of "this annotation only applies to String/Collection/Map/array fields" — this codebase validates at runtime via reflection, and `@NotEmpty` follows that same runtime-only model.
- No class-level or method-level target — `@Target(ElementType.FIELD)` only, matching every other constraint annotation in this package.

## Design

### Annotation

New file: `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/annotation/NotEmpty.java`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotEmpty {

    /**
     * The error message.
     */
    String message() default "Value cannot be empty";

    /**
     * Whether validation failure should fail fast (prevent plugin enable).
     */
    boolean failFast() default true;
}
```

This mirrors `NotNull`'s attribute shape exactly (`message()` + `failFast()` default `true`), since `@NotEmpty` is a presence constraint like `@NotNull`, not a soft/reportable constraint like `@Size`/`@Min`/`@Max`.

### Semantics

A value is **invalid** (fails `@NotEmpty`) when:

| Value type | Invalid when |
|---|---|
| `null` (any type) | always |
| `CharSequence` (covers `String`) | `length() == 0` |
| `Collection` | `isEmpty()` |
| `Map` | `isEmpty()` |
| array (any component type) | `Array.getLength(value) == 0` |
| anything else (non-null, unmeasurable type) | always — see note below |

**Design note — unsupported types fail loud, not silent.** `@Size` currently treats an unmeasurable type as size `0` and, combined with its default `min=0`, silently passes. `@NotEmpty` intentionally does *not* replicate that: an unmeasurable, non-null value is treated as not-empty-checkable and therefore invalid. Rationale: `@NotEmpty` slapped on a field type it can't reason about (an `int`, a POJO, etc.) is almost certainly a mistake, and failing loudly surfaces that misuse instead of the annotation quietly doing nothing. This is a deliberate, approved divergence from `@Size`'s behavior — not an oversight.

### Wiring into `DefaultValidator`

Register a new anonymous `ConstraintFactory<NotEmpty>` in `DefaultValidator.registerDefaultFactories()`, following the exact pattern already used for `NotNull` and `Size`:

- `isValid(Object value)`: implements the semantics table above (a private `isEmpty(Object)` helper, structurally parallel to `Size`'s private `getSize(Object)` helper).
- `message(Object value)`: returns `annotation.message()` (no placeholder substitution needed — no `{min}`/`{max}`-style tokens).
- `isFailFast()`: returns `annotation.failFast()` (reads from the annotation, matching `NotNull`'s factory — unlike `Size`/`Min`/`Max`/etc. which hardcode `false`).
- `suggestedFix(Object value)`: `"Provide a non-empty value"`.

No changes needed to `Constraint`, `ConstraintFactory`, `ValidationResult`, `ValidationError`, `PropertyPath`, or `Validator` — the existing reflection loop in `DefaultValidator.validate(...)` already dispatches any annotation present in the `factories` map, so adding the map entry is sufficient to activate the annotation.

### Testing

Extend `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/validation/ValidatorTest.java` with a dedicated `NotEmptyConfig` test fixture and cases covering:

- `null` string → invalid
- empty string (`""`) → invalid
- blank-but-non-empty string (`" "`) → **valid** (length > 0; `@NotEmpty` is not a whitespace-trim check)
- non-empty string → valid
- empty `List`/empty `Map`/empty array → invalid
- non-empty `List` → valid
- `failFast = false` override → error present but not in `getFailFastErrors()`
- default `failFast = true` → error present in `getFailFastErrors()`, and `validateOrThrow` throws `ValidationException`
- inherited field (superclass-declared `@NotEmpty` field) → validated, matching the existing `testInheritedNotNullViolation`/`testGrandParentFieldIsValidated` coverage pattern for `@NotNull`

These are gate tests: deterministic, no I/O, run under the existing JUnit 5 suite — no eval suite needed since this is a pure deterministic reflection/constraint check, not an LLM-facing behavior.

## Files touched

- `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/annotation/NotEmpty.java` (new)
- `core/src/main/java/tech/guilhermekaua/spigotboot/core/validation/DefaultValidator.java` (add factory registration)
- `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/validation/ValidatorTest.java` (add test fixture + cases)

## Branch

New branch off `dev`: `feat/notempty-annotation`
