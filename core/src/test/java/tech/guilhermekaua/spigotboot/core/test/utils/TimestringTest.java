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
package tech.guilhermekaua.spigotboot.core.test.utils;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.utils.Timestring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimestringTest {

    private static final double DELTA = 0.001;

    // ==================== Basic Unit Parsing ====================

    @Test
    void testMilliseconds() {
        assertEquals(1000.0, Timestring.duration("1000ms", "ms"), DELTA);
        assertEquals(1000.0, Timestring.duration("1000", "ms"), DELTA);
        assertEquals(500.0, Timestring.duration("500milliseconds", "ms"), DELTA);
    }

    @Test
    void testSeconds() {
        assertEquals(1000.0, Timestring.duration("1s", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("1000ms", "s"), DELTA);
        assertEquals(5000.0, Timestring.duration("5sec", "ms"), DELTA);
        assertEquals(3000.0, Timestring.duration("3seconds", "ms"), DELTA);
    }

    @Test
    void testMinutes() {
        assertEquals(60000.0, Timestring.duration("1m", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("60s", "m"), DELTA);
        assertEquals(2.5, Timestring.duration("150s", "m"), DELTA);
        assertEquals(120000.0, Timestring.duration("2min", "ms"), DELTA);
        assertEquals(180000.0, Timestring.duration("3minutes", "ms"), DELTA);
    }

    @Test
    void testHours() {
        assertEquals(3600000.0, Timestring.duration("1h", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("60m", "h"), DELTA);
        assertEquals(2.0, Timestring.duration("7200s", "h"), DELTA);
        assertEquals(3600000.0, Timestring.duration("1hr", "ms"), DELTA);
        assertEquals(7200000.0, Timestring.duration("2hours", "ms"), DELTA);
    }

    @Test
    void testDays() {
        assertEquals(86400000.0, Timestring.duration("1d", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("24h", "d"), DELTA);
        assertEquals(172800000.0, Timestring.duration("2days", "ms"), DELTA);
    }

    @Test
    void testWeeks() {
        assertEquals(604800000.0, Timestring.duration("1w", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("7d", "w"), DELTA);
        assertEquals(604800000.0, Timestring.duration("1wk", "ms"), DELTA);
        assertEquals(1209600000.0, Timestring.duration("2weeks", "ms"), DELTA);
    }

    @Test
    void testMonths() {
        double expectedMonthMs = 86400000.0 * (365.25 / 12);
        assertEquals(expectedMonthMs, Timestring.duration("1mo", "ms"), DELTA);
        assertEquals(expectedMonthMs, Timestring.duration("1month", "ms"), DELTA);
        assertEquals(expectedMonthMs * 2, Timestring.duration("2months", "ms"), DELTA);
    }

    @Test
    void testYears() {
        double expectedYearMs = 86400000.0 * 365.25;
        assertEquals(expectedYearMs, Timestring.duration("1y", "ms"), DELTA);
        assertEquals(expectedYearMs, Timestring.duration("1yr", "ms"), DELTA);
        assertEquals(expectedYearMs, Timestring.duration("1year", "ms"), DELTA);
        assertEquals(expectedYearMs * 2, Timestring.duration("2years", "ms"), DELTA);
    }

    // ==================== Sub-millisecond Units ====================

    @Test
    void testNanoseconds() {
        assertEquals(0.001, Timestring.duration("1000ns", "ms"), DELTA);
        assertEquals(0.000001, Timestring.duration("1nanosecond", "ms"), 0.0000001);
    }

    @Test
    void testMicroseconds() {
        assertEquals(1.0, Timestring.duration("1000us", "ms"), DELTA);
        assertEquals(0.001, Timestring.duration("1microsecond", "ms"), DELTA);
        // Unicode variants
        assertEquals(1.0, Timestring.duration("1000µs", "ms"), DELTA); // U+00B5
        assertEquals(1.0, Timestring.duration("1000μs", "ms"), DELTA); // U+03BC
    }

    // ==================== Combined Durations ====================

    @Test
    void testCombinedDurations() {
        assertEquals(330.0, Timestring.duration("5m 30s", "s"), DELTA);
        assertEquals(330000.0, Timestring.duration("5m 30s", "ms"), DELTA);

        assertEquals(90.0, Timestring.duration("1h30m", "m"), DELTA);

        assertEquals(36.0, Timestring.duration("1d 12h", "h"), DELTA);

        assertEquals(9045.0, Timestring.duration("2h 30m 45s", "s"), DELTA);
    }

    @Test
    void testCombinedDurationsNoSpaces() {
        assertEquals(90.0, Timestring.duration("1h30m", "m"), DELTA);
        assertEquals(3661.0, Timestring.duration("1h1m1s", "s"), DELTA);
    }

    // ==================== Decimal Values ====================

    @Test
    void testDecimalValues() {
        assertEquals(1500.0, Timestring.duration("1.5s", "ms"), DELTA);
        assertEquals(2.5, Timestring.duration("150s", "m"), DELTA);
        assertEquals(150000.0, Timestring.duration("2.5m", "ms"), DELTA);
        assertEquals(9000000.0, Timestring.duration("2.5h", "ms"), DELTA);
    }

    // ==================== Scientific Notation ====================

    @Test
    void testScientificNotation() {
        assertEquals(1000.0, Timestring.duration("1e3ms", "ms"), DELTA);
        assertEquals(1.0, Timestring.duration("1e3ms", "s"), DELTA);
        assertEquals(100.0, Timestring.duration("1e2s", "s"), DELTA);
        assertEquals(0.001, Timestring.duration("1e-3s", "s"), DELTA);
    }

    // ==================== Negative Values ====================

    @Test
    void testNegativeValues() {
        assertEquals(-5000.0, Timestring.duration("-5s", "ms"), DELTA);
        assertEquals(-60.0, Timestring.duration("-1m", "s"), DELTA);
        assertEquals(-330.0, Timestring.duration("-5m 30s", "s"), DELTA);
    }

    // ==================== Number Formatting ====================

    @Test
    void testCommasInNumbers() {
        assertEquals(1000000.0, Timestring.duration("1,000,000ms", "ms"), DELTA);
        assertEquals(1000.0, Timestring.duration("1,000s", "s"), DELTA);
    }

    @Test
    void testUnderscoresInNumbers() {
        assertEquals(1000000.0, Timestring.duration("1_000_000ms", "ms"), DELTA);
        assertEquals(1000.0, Timestring.duration("1_000s", "s"), DELTA);
    }

    // ==================== Case Insensitivity ====================

    @Test
    void testCaseInsensitivity() {
        assertEquals(1000.0, Timestring.duration("1S", "ms"), DELTA);
        assertEquals(60000.0, Timestring.duration("1M", "ms"), DELTA);
        assertEquals(3600000.0, Timestring.duration("1H", "ms"), DELTA);
        assertEquals(1000.0, Timestring.duration("1SECOND", "ms"), DELTA);
        assertEquals(60000.0, Timestring.duration("1MINUTE", "ms"), DELTA);
    }

    // ==================== Null Format (Default to ms) ====================

    @Test
    void testNullFormat() {
        assertEquals(5000.0, Timestring.duration("5s", null), DELTA);
        assertEquals(60000.0, Timestring.duration("1m", null), DELTA);
    }

    // ==================== durationLong ====================

    @Test
    void testDurationLong() {
        assertEquals(5000L, Timestring.durationLong("5s", "ms"));
        assertEquals(60L, Timestring.durationLong("1m", "s"));
        assertEquals(330L, Timestring.durationLong("5m 30s", "s"));
    }

    @Test
    void testDurationLongTruncation() {
        // 1.5 seconds = 1500ms, but as long = 1500
        assertEquals(1500L, Timestring.durationLong("1.5s", "ms"));
        // 1.9 seconds = truncates to 1 second
        assertEquals(1L, Timestring.durationLong("1.9s", "s"));
    }

    // ==================== Error Cases ====================

    @Test
    void testNullInput() {
        assertThrows(NullPointerException.class, () -> Timestring.duration(null, "ms"));
    }

    @Test
    void testEmptyString() {
        assertEquals(-1.0, Timestring.duration("", "ms"), DELTA);
    }

    @Test
    void testBlankString() {
        assertEquals(-1.0, Timestring.duration("         ", "ms"), DELTA);
    }

    @Test
    void testInvalidInput() {
        assertEquals(-1.0, Timestring.duration("abc", "ms"), DELTA);
        assertEquals(-1.0, Timestring.duration("not a duration", "ms"), DELTA);
    }

    @Test
    void testUnknownUnit() {
        assertEquals(-1.0, Timestring.duration("5x", "ms"), DELTA);
    }

    @Test
    void testPartiallyValidInput() {
        // "5s abc" = 5s is valid, "abc" is ignored
        assertEquals(5000.0, Timestring.duration("5s abc", "ms"), DELTA);
    }

    // ==================== Edge Cases ====================

    @Test
    void testZeroDuration() {
        assertEquals(0.0, Timestring.duration("0s", "ms"), DELTA);
        assertEquals(0.0, Timestring.duration("0m", "s"), DELTA);
    }

    @Test
    void testVeryLargeValues() {
        // 1000 years in milliseconds
        double expectedMs = 86400000.0 * 365.25 * 1000;
        assertEquals(expectedMs, Timestring.duration("1000y", "ms"), 1.0);
    }

    @Test
    void testVerySmallValues() {
        assertEquals(0.000001, Timestring.duration("1ns", "ms"), 0.0000001);
    }

    @Test
    void testWhitespaceHandling() {
        assertEquals(330000.0, Timestring.duration("  5m   30s  ", "ms"), DELTA);
    }

    // ==================== Plural Forms ====================

    @Test
    void testPluralForms() {
        assertEquals(5000.0, Timestring.duration("5seconds", "ms"), DELTA);
        assertEquals(300000.0, Timestring.duration("5minutes", "ms"), DELTA);
        assertEquals(18000000.0, Timestring.duration("5hours", "ms"), DELTA);
        assertEquals(432000000.0, Timestring.duration("5days", "ms"), DELTA);
        assertEquals(3024000000.0, Timestring.duration("5weeks", "ms"), DELTA);
    }
}
