package tech.guilhermekaua.spigotboot.testPlugin.configuration;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;

import java.io.File;
import java.util.Objects;

@Component
public class CustomPersistenceConfig implements PersistenceConfig {
    private final MainConfig config;
    private final JavaPlugin plugin;

    public CustomPersistenceConfig(@NotNull MainConfig config, @NotNull JavaPlugin plugin) {
        this.config = Objects.requireNonNull(config, "config");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getAddress() {
        DatabaseConfig db = config.getDatabase();
        String type = db.getType();
        if ("mysql".equalsIgnoreCase(type)) {
            DatabaseConfig.MysqlConfig mysql = db.getMysql();
            return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getPort() + "/" + mysql.getDatabase();
        }
        if ("sqlite".equalsIgnoreCase(type)) {
            File file = new File(plugin.getDataFolder(), db.getSqlite().getFile());
            file.getParentFile().mkdirs();
            return "jdbc:sqlite:" + file.getAbsolutePath();
        }
        throw new IllegalArgumentException("Unsupported database type: " + type + " (expected 'sqlite' or 'mysql')");
    }

    @Override
    public String getUsername() {
        return config.getDatabase().getMysql().getUsername();
    }

    @Override
    public String getPassword() {
        return config.getDatabase().getMysql().getPassword();
    }
}
