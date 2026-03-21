package tech.guilhermekaua.spigotboot.commands.config.spigot.configuration;

import tech.guilhermekaua.spigotboot.commands.CommandTextResolver;
import tech.guilhermekaua.spigotboot.commands.config.spigot.ConfigBackedCommandTextResolver;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

@Configuration
public class CommandsConfigSpigotConfiguration {
    @Bean
    public CommandTextResolver configBackedCommandTextResolver() {
        return new ConfigBackedCommandTextResolver();
    }
}
