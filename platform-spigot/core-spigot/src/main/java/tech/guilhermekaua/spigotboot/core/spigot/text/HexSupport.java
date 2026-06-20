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
package tech.guilhermekaua.spigotboot.core.spigot.text;

/**
 * Version gate + hex encoder for chat/item colour. Probes reflectively for the 1.16-only
 * {@code net.md_5.bungee.api.ChatColor.of(String)} <em>method</em> (the class exists on 1.8 too, as a
 * plain enum — so probe the method, not the class) and caches the result once at class load.
 * <p>
 * On 1.16+ a hex colour is encoded as the native {@code §x§r§r§g§g§b§b} wire sequence; on older
 * servers it is downsampled to the nearest of the 16 legacy colours, so the same template renders
 * acceptably everywhere from 1.8.8 upward.
 */
public final class HexSupport {

    private static final char SECTION = '§';

    /** {@code true} on 1.16+ where native 24-bit chat colour renders. Resolved once at class load. */
    public static final boolean NATIVE_HEX;

    static {
        boolean supported;
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            supported = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            supported = false;
        }
        NATIVE_HEX = supported;
    }

    // Canonical RGB of the 16 legacy colours (§0..§f), used for ≤1.15 downsampling.
    private static final int[][] LEGACY_RGB = {
            {0, 0, 0}, {0, 0, 170}, {0, 170, 0}, {0, 170, 170},
            {170, 0, 0}, {170, 0, 170}, {255, 170, 0}, {170, 170, 170},
            {85, 85, 85}, {85, 85, 255}, {85, 255, 85}, {85, 255, 255},
            {255, 85, 85}, {255, 85, 255}, {255, 255, 85}, {255, 255, 255}
    };
    private static final char[] LEGACY_CHAR = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private HexSupport() {
    }

    /** Encode for the running server version. */
    public static String hex(String hexColor) {
        return encode(hexColor, NATIVE_HEX);
    }

    /** Encode for an explicit version capability (test seam / cross-version rendering). */
    public static String encode(String hexColor, boolean nativeHex) {
        return nativeHex ? toHexSequence(hexColor) : nearestLegacy(hexColor);
    }

    /** {@code "#1a2b3c"}/{@code "1a2b3c"} -&gt; {@code "§x§1§a§2§b§3§c"} (1.16+ native wire format). */
    public static String toHexSequence(String hex) {
        String h = strip(hex);
        StringBuilder sb = new StringBuilder(14).append(SECTION).append('x');
        for (int i = 0; i < 6; i++) {
            sb.append(SECTION).append(Character.toLowerCase(h.charAt(i)));
        }
        return sb.toString();
    }

    /** {@code "#rrggbb"}/{@code "rrggbb"} -&gt; nearest legacy {@code "§<code>"} by unweighted RGB distance. */
    public static String nearestLegacy(String hex) {
        int rgb = Integer.parseInt(strip(hex), 16);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            long dr = r - LEGACY_RGB[i][0];
            long dg = g - LEGACY_RGB[i][1];
            long db = b - LEGACY_RGB[i][2];
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return new StringBuilder(2).append(SECTION).append(LEGACY_CHAR[best]).toString();
    }

    private static String strip(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("bad hex: " + hex);
        }
        String h = hex.charAt(0) == '#' ? hex.substring(1) : hex;
        if (h.length() != 6) {
            throw new IllegalArgumentException("bad hex: " + hex);
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(h.charAt(i), 16) < 0) {
                throw new IllegalArgumentException("bad hex: " + hex);
            }
        }
        return h;
    }
}
