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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;
import tech.guilhermekaua.spigotboot.core.validation.ValidationError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ValidationError} formatting, focused on how the invalid value
 * is rendered in the {@code (was: ...)} segment. Arrays must show their elements,
 * not their {@code Object#toString()} identity (e.g. {@code [I@7f80aba6}).
 */
class ValidationErrorTest {

    private static ValidationError error(Object invalidValue) {
        return new ValidationError(
                PropertyPath.root().child("slots"),
                "slots",
                invalidValue,
                "slots cannot be empty/unset.",
                true,
                "Provide at least one value"
        );
    }

    @Test
    void testFormatIntArrayShowsElementsNotIdentity() {
        String formatted = error(new int[]{1, 2, 3}).format();

        assertTrue(formatted.contains("(was: [1, 2, 3])"), formatted);
        assertFalse(formatted.contains("[I@"), "int[] must not render as identity hash: " + formatted);
    }

    @Test
    void testFormatEmptyIntArrayShowsEmptyBrackets() {
        // Reproduces the user's report: @NotEmpty int[] slots = new int[0] rendered as [I@7f80aba6
        String formatted = error(new int[0]).format();

        assertTrue(formatted.contains("(was: [])"), formatted);
        assertFalse(formatted.contains("[I@"), formatted);
    }

    @Test
    void testFormatStringArrayShowsElements() {
        String formatted = error(new String[]{"a", "b"}).format();

        assertTrue(formatted.contains("(was: [a, b])"), formatted);
        assertFalse(formatted.contains("[Ljava"), formatted);
    }

    @Test
    void testFormatNestedObjectArrayUsesDeepToString() {
        String formatted = error(new Object[]{new int[]{1, 2}, "x"}).format();

        assertTrue(formatted.contains("(was: [[1, 2], x])"), formatted);
    }

    @Test
    void testFormatDoubleArrayShowsElements() {
        String formatted = error(new double[]{1.5, 2.5}).format();

        assertTrue(formatted.contains("(was: [1.5, 2.5])"), formatted);
        assertFalse(formatted.contains("[D@"), formatted);
    }

    @Test
    void testFormatBooleanArrayShowsElements() {
        String formatted = error(new boolean[]{true, false}).format();

        assertTrue(formatted.contains("(was: [true, false])"), formatted);
        assertFalse(formatted.contains("[Z@"), formatted);
    }

    @Test
    void testFormatCharArrayShowsElements() {
        String formatted = error(new char[]{'a', 'b'}).format();

        assertTrue(formatted.contains("(was: [a, b])"), formatted);
        assertFalse(formatted.contains("[C@"), formatted);
    }

    @Test
    void testFormatLongArrayShowsElements() {
        String formatted = error(new long[]{10L, 20L}).format();

        assertTrue(formatted.contains("(was: [10, 20])"), formatted);
        assertFalse(formatted.contains("[J@"), formatted);
    }

    @Test
    void testFormatNullValueRendersNull() {
        String formatted = error(null).format();

        assertTrue(formatted.contains("(was: null)"), formatted);
    }

    @Test
    void testFormatScalarValueUnchanged() {
        String formatted = error(Boolean.FALSE).format();

        assertTrue(formatted.contains("(was: false)"), formatted);
    }

    @Test
    void testFormatIncludesPathMessageAndSuggestion() {
        String formatted = error(new int[0]).format();

        assertEquals(
                "[slots] slots cannot be empty/unset. (was: []) - Suggestion: Provide at least one value",
                formatted
        );
    }
}
