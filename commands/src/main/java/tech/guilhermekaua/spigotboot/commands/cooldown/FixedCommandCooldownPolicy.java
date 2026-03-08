package tech.guilhermekaua.spigotboot.commands.cooldown;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.annotations.Cooldown;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.utils.Timestring;

import java.time.Duration;

public class FixedCommandCooldownPolicy implements CommandCooldownPolicy {
    @Override
    public void validate(Cooldown annotation,
                         Context context,
                         CommandInvocationPlan invocation) {
        parse(annotation, invocation);
    }

    @Override
    public Duration resolve(Cooldown annotation,
                            CommandExecutionContext context,
                            CommandInvocationPlan invocation) {
        return parse(annotation, invocation);
    }

    private Duration parse(Cooldown annotation, CommandInvocationPlan invocation) {
        String time = annotation.time() == null ? "" : annotation.time().trim();
        if (time.isEmpty()) {
            throw new IllegalStateException(
                    "@Cooldown on " + invocation.getMethod().toGenericString() +
                            " requires a non-blank time() when using " + getClass().getName() + "."
            );
        }

        long millis = Timestring.durationLong(time, "ms");
        if (millis <= 0L) {
            throw new IllegalStateException(
                    "@Cooldown on " + invocation.getMethod().toGenericString() +
                            " must resolve to a positive duration, but got '" + annotation.time() + "'."
            );
        }

        return Duration.ofMillis(millis);
    }
}
