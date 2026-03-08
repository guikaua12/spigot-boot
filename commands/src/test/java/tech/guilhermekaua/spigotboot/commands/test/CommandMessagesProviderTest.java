package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessages;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMessagesProviderTest {
    @Test
    void selectsPrimaryConcreteMessageBean() {
        DependencyManager dependencyManager = new DependencyManager();
        DefaultCommandMessages fallback = new DefaultCommandMessages();
        PrimaryMessages primary = new PrimaryMessages();
        dependencyManager.registerDependency(SecondaryMessages.class, new SecondaryMessages(), null, false);
        dependencyManager.registerDependency(PrimaryMessages.class, primary, null, true);

        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);

        CommandMessagesProvider provider = new CommandMessagesProvider(fallback);
        assertSame(primary, provider.resolve(context));
    }

    static class PrimaryMessages implements CommandMessages {
        @Override
        public String missingRequiredArgument(CommandExecutionContext context, CommandParameterMetadata parameter) {
            return "primary";
        }

        @Override
        public String invalidArgumentValue(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            return "primary";
        }

        @Override
        public String noPermission(CommandExecutionContext context, String permission) {
            return "primary";
        }

        @Override
        public String senderTypeMismatch(CommandExecutionContext context, Class<?> expectedSenderType) {
            return "primary";
        }

        @Override
        public String unknownSubcommand(CommandExecutionContext context) {
            return "primary";
        }

        @Override
        public String usage(CommandExecutionContext context, String usage) {
            return "primary";
        }

        @Override
        public String executionError(CommandExecutionContext context, Throwable throwable) {
            return "primary";
        }
    }

    static class SecondaryMessages extends PrimaryMessages {
    }
}
