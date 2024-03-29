package me.approximations.apxPlugin.commands;

import java.util.Collection;

public interface CommandCompletionHandler {
    Collection<String> handle(CommandCompletionContext context);
}
