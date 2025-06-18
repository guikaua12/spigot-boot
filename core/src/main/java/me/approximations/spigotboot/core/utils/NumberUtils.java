package me.approximations.spigotboot.core.utils;

import java.text.DecimalFormat;
import java.text.ParseException;

public final class NumberUtils {
    public static final String INVALID_VALUE_STRING = "-1";
    public static final double INVALID_VALUE_NUMBER = -1;
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");

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