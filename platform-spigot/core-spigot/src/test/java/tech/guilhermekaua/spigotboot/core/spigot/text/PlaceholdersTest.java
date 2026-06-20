package tech.guilhermekaua.spigotboot.core.spigot.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholdersTest {

    @Test
    void replaces_key_value_pairs() {
        assertEquals("Hi Bob, you have 5",
                Placeholders.apply("Hi %name%, you have %n%", "name", "Bob", "n", 5));
    }

    @Test
    void returns_template_when_no_pairs() {
        assertEquals("x %y%", Placeholders.apply("x %y%"));
    }

    @Test
    void ignores_trailing_unpaired_key() {
        assertEquals("1%b%", Placeholders.apply("%a%%b%", "a", "1"));
    }

    @Test
    void null_value_renders_as_string_null() {
        assertEquals("null", Placeholders.apply("%x%", "x", null));
    }
}
