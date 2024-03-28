package me.approximations.apxPlugin.commands.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandUtils {
    public static Set<String> getVariables(String subCommand) {
        final Set<String> variables = new HashSet<>();
        final Matcher matcher = Pattern.compile("\\{(.+?)}").matcher(subCommand);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }
}
