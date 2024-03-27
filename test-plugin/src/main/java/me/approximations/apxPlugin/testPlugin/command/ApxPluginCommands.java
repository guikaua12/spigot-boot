package me.approximations.apxPlugin.testPlugin.command;

import me.approximations.apxPlugin.commands.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("apxplugin|apx")
@CommandDescription("ApxPlugin command")
public class ApxPluginCommands extends RootCommand {

    @SubCommand("points see")
    public void pointsSee(Player sender) {
        sender.sendMessage(ChatColor.GREEN + "Your points: 0");
    }

    @SubCommand("player {player} points see")
    public void pointsSeeOther(CommandSender commandSender, @CommandArgument("player") String player) {
        commandSender.sendMessage(ChatColor.GREEN + player + "'s points: 0");
    }
}
