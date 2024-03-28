package me.approximations.apxPlugin.testPlugin.test.command;

import me.approximations.apxPlugin.commands.utils.ApacheCommonsLangUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandTest {
//    private ServerMock server;
//    private Main plugin;
//    private CommandManager commandManager;

    @BeforeEach
    public void setUp() {
//        server = MockBukkit.mock();
//        plugin = MockBukkit.load(Main.class);
//        commandManager = plugin.getDependencyManager().getDependency(CommandManager.class);
    }

    @Test
    public void tabComplete() {
        final String arg = "pla";
        final Pattern pattern = Pattern.compile("player (?<player>\\w+) points see");
        final Matcher matcher = pattern.matcher(arg);

        if (matcher.matches()) {
            System.out.println("matches");
            return;
        }

        if (matcher.lookingAt()) {
            System.out.println("partially matches");
            return;
        }

        System.out.println("not match");

//        System.out.println(matcher.group("player"));


//        final List<String> completions = tabComplete("produto", "apxplugin", new String[]{"player", "Approximations", ""});
//        System.out.println(completions);
    }

    @NotNull
    public List<String> tabComplete(@NotNull String sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        final Set<String> cmds = new HashSet<>();
        final int cmdIndex = Math.max(0, args.length - 1);
        String argString = ApacheCommonsLangUtil.join(args, " ").toLowerCase(Locale.ENGLISH);

        final String subAlias = "player {player} points see";
//        final Pattern subPattern = Pattern.compile("\\{(.+?)}");
//
//        if (subAlias.startsWith(argString)) {
//            cmds.add(subAlias.split(" ")[cmdIndex]);
//        }
        cmds.add(subAlias.split(" ")[cmdIndex]);

        return new ArrayList<>(cmds);
    }
}
