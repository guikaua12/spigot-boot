# spigot-boot-data-jdbc

`spigot-boot-data-jdbc` is a lightweight JDBC repository module for Spigot-Boot.

It provides:

- entity mapping annotations
- `JdbcRepository<T, ID>` CRUD + fluent `select()`
- explicit relationship loading via `include(...)`
- raw SQL queries via `@Query` + named params (`@Param`)
- transaction support via `TransactionManager` and `@Transactional`
- optional startup auto-DDL (`CREATE TABLE IF NOT EXISTS`)

## Setup

Add the module dependency and provide a `PersistenceConfig` implementation in your plugin package.

```java
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;

public class MyPersistenceConfig implements PersistenceConfig {
    @Override
    public String getAddress() {
        return "jdbc:sqlite:plugins/MyPlugin/data.db";
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public String getPassword() {
        return "";
    }
}
```

## Entity Mapping

```java
@Table("players")
public class Player {
    @Id(strategy = IdStrategy.UUID)
    @Column("id")
    private UUID id;

    @Column("name")
    private String name;

    @OneToMany
    @JoinColumn("player_id")
    private List<Quest> quests = new ArrayList<>();
}
```

Supported mapping annotations:

- `@Table`
- `@Column`
- `@Id`
- `@EmbeddedId`
- `@OneToMany` + `@JoinColumn`
- `@ManyToOne` + `@JoinColumn`

## Repository API

```java
public interface PlayerRepository extends JdbcRepository<Player, UUID> {
    @Query("SELECT * FROM players WHERE name = :name")
    List<Player> findByName(@Param("name") String name);

    @Query("SELECT COUNT(*) FROM players WHERE level >= :level")
    long countHighLevel(@Param("level") int level);
}
```

Use the fluent query API:

```java
List<Player> players = playerRepository.select()
        .where("level").gte(10)
        .include("quests")
        .fetchAll();
```

## Includes and Relationship Filtering

```java
List<Player> players = playerRepository.select()
        .whereHas("quests", q -> q.where("completed").eq(false))
        .include("quests", q -> q.orderBy("name").asc())
        .fetchAll();
```

For raw SQL queries:

```java
@Query("SELECT * FROM players WHERE level > :minLevel")
@Include(value = "quests", where = "completed = false", orderBy = "name ASC")
List<Player> findHighLevel(@Param("minLevel") int minLevel);
```

## Transactions

Programmatic:

```java
transactionManager.execute(() -> {
    playerRepository.save(player);
    questRepository.save(quest);
});
```

Declarative:

```java
@Component
public class PlayerService {
    @Inject private PlayerRepository playerRepository;

    @Transactional
    public void levelUp(UUID playerId) {
        // transactional operation
    }
}
```

## Auto-DDL and Schema Options

By default, auto-DDL is enabled and SQL logging is disabled.

To disable auto-DDL:

```java
context.registerBean(new JdbcSchemaOptions(false));
```

To print every SQL statement executed by data-jdbc:

```java
context.registerBean(new JdbcSchemaOptions(true, true));
```

When SQL logging is enabled, prepared statements are logged with `?` placeholders.

## SQLite Caveats

- `SQLiteDialect` uses a single-connection pool (`maximumPoolSize = 1`) to reduce `SQLITE_BUSY` issues.
- SQLite 3.7.2 upsert support is limited; this module uses `INSERT OR REPLACE` behavior when dialect upsert SQL is
  needed.

## Foreign Key Update Guidance

For `@ManyToOne` relationships, map the foreign key column explicitly when you need direct FK updates without loading
the relationship.

```java
@Column("guild_id")
private UUID guildId;

@ManyToOne
@JoinColumn("guild_id")
private Guild guild;
```
