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
package me.approximations.spigotboot.test.utils;

import me.approximations.spigotboot.core.utils.NumberUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NumberUtilsTest {
    @Test
    public void shouldFormatNumber() {
        final double number = 123456.789;
        final String formatted = NumberUtils.format(number);
        Assertions.assertEquals("123,456.79", formatted);
    }

    @Test
    public void shouldParseNumberFormatted() {
        final String number = "123,456.789";
        final double parsed = NumberUtils.parse(number);
        Assertions.assertEquals(123456.789, parsed);
    }

    @Test
    public void shouldParseNumber() {
        final String number = "123456789";
        final double parsed = NumberUtils.parse(number);
        Assertions.assertEquals(123456789, parsed);
    }

    @Test
    public void shouldNotParseInvalidNumber() {
        final String number = "invalid";
        final double parsed = NumberUtils.parse(number);
        Assertions.assertEquals(-1, parsed);
    }

    @Test
    public void shouldNotParseNegativeNumber() {
        final String number = "-3";
        final double parsed = NumberUtils.parse(number);
        Assertions.assertEquals(-1, parsed);
    }
}
