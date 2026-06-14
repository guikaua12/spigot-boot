package tech.guilhermekaua.spigotboot.testPlugin.configuration;

import lombok.Data;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;

@Data
public class DatabaseConfig {
    @Comment("sqlite or mysql")
    private String type = "sqlite";

    private SqliteConfig sqlite = new SqliteConfig();
    private MysqlConfig mysql = new MysqlConfig();

    @Data
    public static class SqliteConfig {
        @Comment("Relative to plugins/ApxTrades/")
        private String file = "data.db";
    }

    @Data
    public static class MysqlConfig {
        private String host = "localhost";
        private int port = 3306;
        private String database = "apxtrades";
        private String username = "root";
        private String password = "";
    }
}
