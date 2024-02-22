package me.approximations.apxPlugin.test.utils;

import me.approximations.apxPlugin.utils.NumberUtils;
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
