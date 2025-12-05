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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;

public final class NumberUtils {
    public static final String INVALID_VALUE_STRING = "-1";
    public static final double INVALID_VALUE_NUMBER = -1;
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##", DecimalFormatSymbols.getInstance(Locale.US));

    public static String format(double value) {
        if (isInvalid(value)) return INVALID_VALUE_STRING;

        return DECIMAL_FORMAT.format(value);
    }

    public static double parse(String string) {
        try {
            final double value = Double.parseDouble(string);
            if (!isInvalid(value)) return value;
        } catch (NumberFormatException ignored) {
        }

        try {
            final double value = DECIMAL_FORMAT.parse(string).doubleValue();
            return isInvalid(value) ? INVALID_VALUE_NUMBER : value;
        } catch (ParseException ignored) {
            return INVALID_VALUE_NUMBER;
        }
    }

    public static boolean isInvalid(double value) {
        return value < 0 || Double.isNaN(value) || Double.isInfinite(value);
    }

}