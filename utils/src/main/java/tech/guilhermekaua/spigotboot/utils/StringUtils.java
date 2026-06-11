package tech.guilhermekaua.spigotboot.utils;

import org.jetbrains.annotations.NotNull;

public final class StringUtils {
    public static boolean containsWhitespace(@NotNull String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
