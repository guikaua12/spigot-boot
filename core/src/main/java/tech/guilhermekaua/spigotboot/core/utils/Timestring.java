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
package tech.guilhermekaua.spigotboot.core.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for parsing duration strings into numeric values.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Timestring {
    private static final Map<String, Double> UNIT_RATIOS = new HashMap<>();

    /**
     * Regex for matching duration components.
     * Group 1: number (including optional scientific notation)
     * Group 2: unit letters (including Unicode µ/μ for microseconds)
     */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(-?(?:\\d+\\.?\\d*|\\d*\\.?\\d+)(?:e[-+]?\\d+)?)\\s*([a-zA-Zµμ]*)",
            Pattern.CASE_INSENSITIVE);

    static {
        UNIT_RATIOS.put("nanosecond", 1 / 1e6);
        UNIT_RATIOS.put("ns", 1 / 1e6);
        UNIT_RATIOS.put("µs", 1 / 1e3);  // U+00B5
        UNIT_RATIOS.put("μs", 1 / 1e3);  // U+03BC
        UNIT_RATIOS.put("us", 1 / 1e3);
        UNIT_RATIOS.put("microsecond", 1 / 1e3);

        UNIT_RATIOS.put("millisecond", 1.0);
        UNIT_RATIOS.put("ms", 1.0);
        UNIT_RATIOS.put("", 1.0);

        double second = 1000.0;
        UNIT_RATIOS.put("second", second);
        UNIT_RATIOS.put("sec", second);
        UNIT_RATIOS.put("s", second);

        double minute = second * 60;
        UNIT_RATIOS.put("minute", minute);
        UNIT_RATIOS.put("min", minute);
        UNIT_RATIOS.put("m", minute);

        double hour = minute * 60;
        UNIT_RATIOS.put("hour", hour);
        UNIT_RATIOS.put("hr", hour);
        UNIT_RATIOS.put("h", hour);

        double day = hour * 24;
        UNIT_RATIOS.put("day", day);
        UNIT_RATIOS.put("d", day);

        double week = day * 7;
        UNIT_RATIOS.put("week", week);
        UNIT_RATIOS.put("wk", week);
        UNIT_RATIOS.put("w", week);

        double month = day * (365.25 / 12);
        UNIT_RATIOS.put("month", month);
        UNIT_RATIOS.put("mo", month);

        double year = day * 365.25;
        UNIT_RATIOS.put("year", year);
        UNIT_RATIOS.put("yr", year);
        UNIT_RATIOS.put("y", year);
    }

    /**
     * Convert a duration string to the specified time unit.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code duration("5m 30s", "s")} returns {@code 330.0}</li>
     *   <li>{@code duration("1h30m", "m")} returns {@code 90.0}</li>
     *   <li>{@code duration("2.5d", "h")} returns {@code 60.0}</li>
     *   <li>{@code duration("1e3ms", "s")} returns {@code 1.0}</li>
     * </ul>
     *
     * @param str    the string to parse (e.g., "5m 30s", "1h30m", "2.5d")
     * @param format the unit to return the result in (e.g., "s", "ms", "h"). If null, returns milliseconds.
     * @return the parsed value in the specified format, or -1 if parsing fails
     */
    public static double duration(@NotNull String str, @Nullable String format) {
        Objects.requireNonNull(str, "str cannot be null");

        if (str.isEmpty()) {
            return -1;
        }

        try {
            double result = 0;
            boolean hasMatch = false;

            // ignore commas/placeholders
            str = str.replaceAll("(\\d)[,_](\\d)", "$1$2");

            final boolean isNegative = str.trim().charAt(0) == '-';
            final Matcher matcher = DURATION_PATTERN.matcher(str);

            while (matcher.find()) {
                String numberPart = matcher.group(1);
                String unitPart = matcher.group(2);

                if (numberPart == null || numberPart.isEmpty()) {
                    continue;
                }

                double n = Double.parseDouble(numberPart);
                Double unitRatio = unitRatio(unitPart);

                if (unitRatio != null) {
                    result += Math.abs(n) * unitRatio;
                    hasMatch = true;
                }
            }

            if (!hasMatch) {
                return -1;
            }

            Double formatRatio = unitRatio(format);
            double divisor = (formatRatio == null) ? 1.0 : formatRatio;

            return (result / divisor) * (isNegative ? -1 : 1);

        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Convert a duration string to the specified time unit as a long.
     *
     * @param str    the string to parse
     * @param format the unit to return the result in
     * @return the parsed value in the specified format, or -1 if parsing fails
     */
    public static long durationLong(@NotNull String str, @Nullable String format) {
        return (long) duration(str, format);
    }

    /**
     * Get the ratio (in milliseconds) for a unit string.
     *
     * @param str the unit to get the ratio for
     * @return the ratio for the unit in milliseconds, or null if not found
     */
    @Nullable
    private static Double unitRatio(@Nullable String str) {
        if (str == null) {
            return null;
        }

        if (UNIT_RATIOS.containsKey(str)) {
            return UNIT_RATIOS.get(str);
        }

        String lower = str.toLowerCase();
        if (UNIT_RATIOS.containsKey(lower)) {
            return UNIT_RATIOS.get(lower);
        }

        if (lower.endsWith("s") && lower.length() > 1) {
            String singular = lower.substring(0, lower.length() - 1);
            return UNIT_RATIOS.getOrDefault(singular, null);
        }

        return null;
    }
}
