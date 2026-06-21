package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageTemplates;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandMessageExceptionTest {
    private enum TestKey implements CommandMessageKey {
        NOT_FOUND("player.not-found", "No player named '{input}' is online.", "input"),
        AMBIGUOUS("player.ambiguous", "'{input}' matches {count} players.", "input", "count");

        private final String id;
        private final String defaultTemplate;
        private final List<String> placeholders;

        TestKey(String id, String defaultTemplate, String... placeholders) {
            this.id = id;
            this.defaultTemplate = defaultTemplate;
            this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
        }

        @Override public String id() { return id; }
        @Override public String defaultTemplate() { return defaultTemplate; }
        @Override public List<String> placeholders() { return placeholders; }
    }

    @Test
    void builderRetainsKeyAndPlaceholders() {
        CommandMessageException exception = CommandMessageException.of(TestKey.AMBIGUOUS)
                .with("input", "al")
                .with("count", 3);

        assertSame(TestKey.AMBIGUOUS, exception.getKey());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("input", "al");
        expected.put("count", 3);
        assertEquals(expected, exception.getPlaceholders());
    }

    @Test
    void getMessageInterpolatesDefaultTemplate() {
        CommandMessageException exception = CommandMessageException.of(TestKey.AMBIGUOUS)
                .with("input", "al")
                .with("count", 3);

        assertEquals("'al' matches 3 players.", exception.getMessage());
    }

    @Test
    void getPlaceholdersIsUnmodifiable() {
        CommandMessageException exception = CommandMessageException.of(TestKey.NOT_FOUND).with("input", "x");
        assertThrows(UnsupportedOperationException.class, () -> exception.getPlaceholders().put("k", "v"));
    }

    @Test
    void interpolateLeavesUnboundPlaceholderVerbatimAndHandlesNull() {
        Map<String, Object> placeholders = Collections.singletonMap("input", "x");
        assertEquals("got x but not {missing}", CommandMessageTemplates.interpolate("got {input} but not {missing}", placeholders));
        assertEquals("", CommandMessageTemplates.interpolate(null, placeholders));
    }
}
