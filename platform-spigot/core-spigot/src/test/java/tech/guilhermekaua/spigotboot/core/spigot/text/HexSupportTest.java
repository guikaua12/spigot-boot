package tech.guilhermekaua.spigotboot.core.spigot.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HexSupportTest {

    @Test
    void encodes_native_hex_sequence_on_modern() {
        assertEquals("§x§1§a§2§b§3§c", HexSupport.encode("#1A2B3C", true));
    }

    @Test
    void accepts_bare_hex_without_leading_hash() {
        assertEquals("§x§1§a§2§b§3§c", HexSupport.encode("1a2b3c", true));
    }

    @Test
    void downsamples_to_nearest_legacy_color_on_old_servers() {
        assertEquals("§2", HexSupport.encode("#00AA00", false));
        assertEquals("§f", HexSupport.encode("#FFFFFF", false));
        assertEquals("§0", HexSupport.encode("#000000", false));
    }

    @Test
    void rejects_malformed_hex() {
        assertThrows(IllegalArgumentException.class, () -> HexSupport.encode("#FFF", true));
    }
}
