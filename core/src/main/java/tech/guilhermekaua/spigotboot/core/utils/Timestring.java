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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for parsing durations
 */
public class Timestring {

    /**
     * conversion ratios
     */
    private static final Map<String, Double> parse = new HashMap<>();
    /**
     * regex for matching durations
     */
    private static final Pattern durationRE = Pattern.compile(
            "(-?(?:\\d+\\.?\\d*|\\d*\\.?\\d+)(?:e[-+]?\\d+)?)\\s*([\\p{L}]*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static {
        parse.put("nanosecond", 1 / 1e6);
        parse.put("ns", 1 / 1e6);
        parse.put("µs", 1 / 1e3);
        parse.put("μs", 1 / 1e3);
        parse.put("us", 1 / 1e3);
        parse.put("microsecond", 1 / 1e3);
        parse.put("millisecond", 1.0);
        parse.put("ms", 1.0);
        parse.put("", 1.0);
        parse.put("second", parse.get("ms") * 1000);
        parse.put("sec", parse.get("ms") * 1000);
        parse.put("s", parse.get("ms") * 1000);
        parse.put("minute", parse.get("s") * 60);
        parse.put("min", parse.get("s") * 60);
        parse.put("m", parse.get("s") * 60);
        parse.put("hour", parse.get("m") * 60);
        parse.put("hr", parse.get("m") * 60);
        parse.put("h", parse.get("m") * 60);
        parse.put("day", parse.get("h") * 24);
        parse.put("d", parse.get("h") * 24);
        parse.put("week", parse.get("d") * 7);
        parse.put("wk", parse.get("d") * 7);
        parse.put("w", parse.get("d") * 7);
        parse.put("month", parse.get("d") * (365.25 / 12));
        parse.put("b", parse.get("d") * (365.25 / 12));
    }

    /**
     * convert `str` to ms
     *
     * @param str    the string to parse
     * @param format the format to return
     * @return the parsed value in the specified format
     */
    public static double duration(String str, String format) {
        try {
            Double result = null;
            // ignore commas/placeholders
            str = (str).replaceAll("(\\d),_", "$1$2");
            final boolean isNegative = str.charAt(0) == '-';
            final Matcher matcher = durationRE.matcher(str.toLowerCase());
            while (matcher.find()) {
                final String group = matcher.group();
                final double n = Double.parseDouble(group.replaceAll("[\\p{L}]+", ""));
                final String units = group.replaceAll("[0-9.]+", "");
                final Double unitRatio = unitRatio(units);
                if (unitRatio != null) {
                    result = (result == null ? 0 : result) + Math.abs(n) * unitRatio;
                }
            }

            if (result != null) {
                return result / (unitRatio(format) == null ? 1 : unitRatio(format)) *
                        (isNegative ? -1 : 1);
            } else {
                return result;
            }
        } catch (Throwable throwable) {
            return -1;
        }
    }

    /**
     * convert `str` to ms
     *
     * @param str    the string to parse
     * @param format the format to return
     * @return the parsed value in the specified format
     */
    public static long durationLong(String str, String format) {
        return (long) duration(str, format);
    }

    /**
     * get the ratio for a unit
     *
     * @param str the unit to get the ratio for
     * @return the ratio for the unit
     */
    private static Double unitRatio(String str) {
        return str == null ? null : (parse.containsKey(str) ?
                parse.get(str) :
                ((parse.getOrDefault(str.toLowerCase().replaceAll(
                        "s$", ""), null))));
    }
}