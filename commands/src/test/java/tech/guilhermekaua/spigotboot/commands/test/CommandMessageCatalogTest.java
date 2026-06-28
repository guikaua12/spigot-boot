package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageCatalog;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandMessageCatalogTest {
    private enum Key implements CommandMessageKey {
        FOO;

        @Override public String id() { return "foo.bad"; }
        @Override public String defaultTemplate() { return "bad {input}"; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    private static final class KeyedResolver implements CommandArgumentResolver<String> {
        @Override public boolean supports(CommandParameterMetadata parameter) { return false; }
        @Override public String resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) { return input; }
        @Override public Collection<CommandMessageKey> messageKeys() { return Collections.singletonList(Key.FOO); }
    }

    @Test
    void catalogAggregatesKeysFromRegisteredResolvers() {
        DefaultCommandArgumentResolverRegistry registry =
                new DefaultCommandArgumentResolverRegistry(Arrays.asList(new KeyedResolver()), Collections.emptyList());
        CommandMessageCatalog catalog = new CommandMessageCatalog(registry);

        assertTrue(catalog.all().contains(Key.FOO));
        assertSame(Key.FOO, catalog.find("foo.bad").orElseThrow(AssertionError::new));
        assertFalse(catalog.find("missing").isPresent());
    }
}
