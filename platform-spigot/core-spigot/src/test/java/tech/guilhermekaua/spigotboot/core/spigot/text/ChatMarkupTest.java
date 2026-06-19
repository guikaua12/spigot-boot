package tech.guilhermekaua.spigotboot.core.spigot.text;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatMarkupTest {

    @Test
    void legacy_translates_ampersand_codes() {
        assertEquals("§aHello", ChatMarkup.legacy("&aHello"));
    }

    @Test
    void legacy_downsamples_hex_on_old_servers() {
        assertEquals("§2Hi", ChatMarkup.legacy("#00AA00Hi", false));
    }

    @Test
    void legacy_emits_native_hex_on_modern() {
        assertEquals("§x§1§a§2§b§3§cHi", ChatMarkup.legacy("#1a2b3cHi", true));
    }

    @Test
    void legacy_strips_interactive_tags_keeping_inner_text() {
        assertEquals("Go", ChatMarkup.legacy("[click=run:/x]Go[/click]"));
        assertEquals("abc", ChatMarkup.legacy("a[hover=&btip]b[/hover]c"));
    }

    @Test
    void legacy_backslash_escapes_next_char() {
        assertEquals("&c", ChatMarkup.legacy("\\&c"));
    }

    @Test
    void parse_colors_a_single_run() {
        BaseComponent[] out = ChatMarkup.parse("&aHi");
        assertEquals(1, out.length);
        assertEquals("Hi", out[0].toPlainText());
        assertEquals(ChatColor.GREEN, out[0].getColor());
    }

    @Test
    void parse_attaches_click_event() {
        BaseComponent[] out = ChatMarkup.parse("[click=run:/trade]X[/click]");
        assertEquals(1, out.length);
        ClickEvent click = out[0].getClickEvent();
        assertNotNull(click);
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.getAction());
        assertEquals("/trade", click.getValue());
        assertEquals("X", out[0].toPlainText());
    }

    @Test
    void parse_empty_yields_single_empty_component() {
        BaseComponent[] out = ChatMarkup.parse("");
        assertEquals(1, out.length);
        assertEquals("", out[0].toPlainText());
    }
}
