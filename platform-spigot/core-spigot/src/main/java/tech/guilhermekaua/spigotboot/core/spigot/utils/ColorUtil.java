package tech.guilhermekaua.spigotboot.core.spigot.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import tech.guilhermekaua.spigotboot.core.spigot.text.HexSupport;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    /** Translates {@code &} codes and {@code #rrggbb} hex for the running server version. */
    public static String colored(String message) {
        return colored(message, HexSupport.NATIVE_HEX);
    }

    /**
     * Translates {@code &} codes and {@code #rrggbb} hex, encoding hex for an explicit version
     * capability: the native {@code §x…} sequence when {@code nativeHex} is true, otherwise
     * downsampled to the nearest legacy colour so it still renders on ≤1.15.
     */
    public static String colored(String message, boolean nativeHex) {
        if (message == null) {
            return null;
        }
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer(message.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(HexSupport.encode(matcher.group(), nativeHex)));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }


    public static String[] colored(String... messages) {
        for (int i = 0; i < messages.length; i++) {
            messages[i] = colored(messages[i]);
        }

        return messages;
    }

    public static List<String> colored(List<String> description) {
        return description.stream()
                .map(ColorUtil::colored)
                .collect(Collectors.toList());
    }

    public static java.awt.Color getColorByHex(String hex) {
        return java.awt.Color.decode(hex);
    }

    public static Color getBukkitColorByHex(String hex) {
        val decode = getColorByHex(hex);
        return Color.fromRGB(decode.getRed(), decode.getGreen(), decode.getBlue());
    }

}
