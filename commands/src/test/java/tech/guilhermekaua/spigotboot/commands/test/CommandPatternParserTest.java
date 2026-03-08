package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternException;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CommandPatternParserTest {
    private final CommandPatternParser parser = new CommandPatternParser();

    @Test
    void parseRequiredAndOptionalArguments() {
        CommandPattern pattern = parser.parse("ban <player> [reason]");

        assertEquals(3, pattern.getSegments().size());
        assertEquals("ban", ((CommandPattern.LiteralSegment) pattern.getSegments().get(0)).getLiteral());
        assertInstanceOf(CommandPattern.RequiredArgumentSegment.class, pattern.getSegments().get(1));
        assertInstanceOf(CommandPattern.OptionalArgumentSegment.class, pattern.getSegments().get(2));
        assertEquals("ban <arg> [arg]", pattern.normalizedSignature());
    }

    @Test
    void expandAliasesBuildsCartesianProduct() {
        assertEquals(
                Arrays.asList(
                        "ban foo <player>",
                        "ban bar <player>",
                        "block foo <player>",
                        "block bar <player>"
                ),
                parser.expandAliases("ban|block foo|bar <player>")
        );
    }

    @Test
    void rejectsRequiredArgumentAfterOptionalArgument() {
        assertThrows(CommandPatternException.class, () -> parser.parse("ban [reason] <player>"));
    }
}
