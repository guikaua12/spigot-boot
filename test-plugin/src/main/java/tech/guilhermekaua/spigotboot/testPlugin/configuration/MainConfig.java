package tech.guilhermekaua.spigotboot.testPlugin.configuration;

import lombok.Data;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Range;

@Config("config.yml")
@Data
public class MainConfig {
    @Comment("The server's display name shown to players")
    private String serverName = "My Awesome Server";

    @Comment("Maximum players allowed on the server")
    @Range(min = 1, max = 1000)
    private int maxPlayers = 100;
}
