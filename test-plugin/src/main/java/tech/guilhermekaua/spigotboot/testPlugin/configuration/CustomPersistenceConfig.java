package tech.guilhermekaua.spigotboot.testPlugin.configuration;

import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;

import java.io.File;

@Component
public class CustomPersistenceConfig implements PersistenceConfig {
    private final MainConfig config;
    private final JavaPlugin plugin;

    public CustomPersistenceConfig(MainConfig config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public String getAddress() {
        DatabaseConfig db = config.getDatabase();
        if ("mysql".equalsIgnoreCase(db.getType())) {
            DatabaseConfig.MysqlConfig mysql = db.getMysql();
            return "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getPort() + "/" + mysql.getDatabase();
        }
        File file = new File(plugin.getDataFolder(), db.getSqlite().getFile());
        file.getParentFile().mkdirs();
        return "jdbc:sqlite:" + file.getAbsolutePath();
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
