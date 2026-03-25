/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.data.jdbc.repository;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.core.pagination.Sort;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.*;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.ddl.DdlGenerator;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.SQLiteDialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.methodHandler.QueryMethodHandler;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.impl.JdbcRepositoryImpl;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcRepositoryManyToOneIncludeTest {
    private Path databaseFile;
    private HikariDataSource dataSource;
    private ConnectionProvider connectionProvider;
    private Dialect dialect;
    private EntityMetadataRegistry metadataRegistry;
    private EntityMetadata guildMetadata;
    private EntityMetadata playerMetadata;
    private EntityMetadata questMetadata;
    private EntityMetadata playerWithoutMappedChildFkMetadata;
    private EntityMetadata questWithoutMappedPlayerFkMetadata;
    private JdbcRepositoryImpl<Guild, UUID> guildRepository;
    private JdbcRepositoryImpl<Player, UUID> playerRepository;
    private JdbcRepositoryImpl<Quest, UUID> questRepository;
    private JdbcRepositoryImpl<PlayerWithoutMappedChildFk, UUID> playerWithoutMappedChildFkRepository;
    private JdbcRepositoryImpl<QuestWithoutMappedPlayerFk, UUID> questWithoutMappedPlayerFkRepository;
    private QueryMethodHandler queryMethodHandler;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = Files.createTempFile("data-jdbc-many-to-one", ".db");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + toSqlitePath(databaseFile));
        dataSource.setMaximumPoolSize(1);

        connectionProvider = new ConnectionProvider(dataSource);
        dialect = new SQLiteDialect();

        TypeConverterRegistry converterRegistry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(converterRegistry);

        metadataRegistry = new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
        guildMetadata = metadataRegistry.getOrParse(Guild.class);
        playerMetadata = metadataRegistry.getOrParse(Player.class);
        questMetadata = metadataRegistry.getOrParse(Quest.class);
        playerWithoutMappedChildFkMetadata = metadataRegistry.getOrParse(PlayerWithoutMappedChildFk.class);
        questWithoutMappedPlayerFkMetadata = metadataRegistry.getOrParse(QuestWithoutMappedPlayerFk.class);

        DdlGenerator ddlGenerator = new DdlGenerator(dialect, metadataRegistry);
        createTable(ddlGenerator.generateCreateTable(guildMetadata));
        createTable(ddlGenerator.generateCreateTable(playerMetadata));
        createTable(ddlGenerator.generateCreateTable(questMetadata));
        createTable(ddlGenerator.generateCreateTable(playerWithoutMappedChildFkMetadata));
        createTable(ddlGenerator.generateCreateTable(questWithoutMappedPlayerFkMetadata));

        guildRepository = new JdbcRepositoryImpl<>(connectionProvider, guildMetadata, dialect, metadataRegistry);
        playerRepository = new JdbcRepositoryImpl<>(connectionProvider, playerMetadata, dialect, metadataRegistry);
        questRepository = new JdbcRepositoryImpl<>(connectionProvider, questMetadata, dialect, metadataRegistry);
        playerWithoutMappedChildFkRepository = new JdbcRepositoryImpl<>(
                connectionProvider,
                playerWithoutMappedChildFkMetadata,
                dialect,
                metadataRegistry
        );
        questWithoutMappedPlayerFkRepository = new JdbcRepositoryImpl<>(
                connectionProvider,
                questWithoutMappedPlayerFkMetadata,
                dialect,
                metadataRegistry
        );
        queryMethodHandler = new QueryMethodHandler(connectionProvider, metadataRegistry, converterRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }

        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Nested
    class InsertTests {
        @Test
        void persistsJoinColumnWhenRelationshipFieldIsSet() throws Exception {
            Fixture fixture = createFixture();

            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT guild_id FROM players WHERE id = ?"
                 )) {
                statement.setString(1, fixture.player.getId().toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(fixture.guild.getId().toString(), resultSet.getString("guild_id"));
                }
            }
        }
    }

    @Nested
    class IncludeTests {
        @Test
        void selectIncludeHydratesManyToOneWithoutMappedForeignKeyField() {
            Fixture fixture = createFixture();

            List<Player> players = playerRepository.select()
                    .include("guild")
                    .fetchAll();

            assertEquals(1, players.size());
            assertNotNull(players.get(0).getGuild());
            assertEquals(fixture.guild.getId(), players.get(0).getGuild().getId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationIncludeHydratesManyToOneWithoutMappedForeignKeyField() throws Exception {
            Fixture fixture = createFixture();
            Method queryMethod = PlayerQueryRepository.class.getMethod("findById", UUID.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{fixture.player.getId()},
                    playerMetadata,
                    dialect
            );

            List<Player> players = (List<Player>) queryResult;
            assertEquals(1, players.size());
            assertNotNull(players.get(0).getGuild());
            assertEquals(fixture.guild.getId(), players.get(0).getGuild().getId());
        }

        @Test
        void includeFilterResolvesPropertyNameToMappedColumn() {
            QuestFixture fixture = createQuestFixture();

            List<Player> players = playerRepository.select()
                    .include("quests", include -> include.where("completed").eq(false))
                    .fetchAll();

            assertEquals(1, players.size());
            assertEquals(1, players.get(0).getQuests().size());
            assertEquals(fixture.incompleteQuest.getId(), players.get(0).getQuests().get(0).getId());
        }

        @Test
        void whereHasFilterResolvesPropertyNameToMappedColumn() {
            QuestFixture fixture = createQuestFixture();

            List<Player> players = playerRepository.select()
                    .whereHas("quests", include -> include.where("completed").eq(false))
                    .fetchAll();

            assertEquals(1, players.size());
            assertEquals(fixture.player.getId(), players.get(0).getId());
        }

        @Test
        void selectIncludeHydratesOneToManyWithoutMappedForeignKeyField() {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();

            List<PlayerWithoutMappedChildFk> players = playerWithoutMappedChildFkRepository.select()
                    .include("quests")
                    .fetchAll();

            assertEquals(1, players.size());
            assertEquals(fixture.player.getId(), players.get(0).getId());
            assertEquals(1, players.get(0).getQuests().size());
            assertEquals(fixture.quest.getId(), players.get(0).getQuests().get(0).getId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationIncludeHydratesOneToManyWithoutMappedForeignKeyField() throws Exception {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();
            Method queryMethod = PlayerWithoutMappedChildFkQueryRepository.class.getMethod("findById", UUID.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{fixture.player.getId()},
                    playerWithoutMappedChildFkMetadata,
                    dialect
            );

            List<PlayerWithoutMappedChildFk> players = (List<PlayerWithoutMappedChildFk>) queryResult;
            assertEquals(1, players.size());
            assertEquals(fixture.player.getId(), players.get(0).getId());
            assertEquals(1, players.get(0).getQuests().size());
            assertEquals(fixture.quest.getId(), players.get(0).getQuests().get(0).getId());
        }

        @Test
        void selectIncludeHydratesNestedOneToManyManyToOneWithoutMappedForeignKeyField() {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();

            List<PlayerWithoutMappedChildFk> players = playerWithoutMappedChildFkRepository.select()
                    .include("quests.player")
                    .fetchAll();

            assertEquals(1, players.size());
            PlayerWithoutMappedChildFk player = players.get(0);
            assertEquals(fixture.player.getId(), player.getId());
            assertEquals(1, player.getQuests().size());
            assertNotNull(player.getQuests().get(0).getPlayer());
            assertEquals(fixture.player.getId(), player.getQuests().get(0).getPlayer().getId());
        }

        @Test
        void selectIncludeHydratesNestedManyToOneOneToManyWithoutMappedForeignKeyField() {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();

            List<QuestWithoutMappedPlayerFk> quests = questWithoutMappedPlayerFkRepository.select()
                    .include("player.quests")
                    .fetchAll();

            assertEquals(1, quests.size());
            QuestWithoutMappedPlayerFk quest = quests.get(0);
            assertNotNull(quest.getPlayer());
            assertEquals(fixture.player.getId(), quest.getPlayer().getId());
            assertEquals(1, quest.getPlayer().getQuests().size());
            assertEquals(quest.getId(), quest.getPlayer().getQuests().get(0).getId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationIncludeHydratesNestedOneToManyManyToOneWithoutMappedForeignKeyField() throws Exception {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();
            Method queryMethod = PlayerWithoutMappedChildFkNestedQueryRepository.class.getMethod("findById", UUID.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{fixture.player.getId()},
                    playerWithoutMappedChildFkMetadata,
                    dialect
            );

            List<PlayerWithoutMappedChildFk> players = (List<PlayerWithoutMappedChildFk>) queryResult;
            assertEquals(1, players.size());
            PlayerWithoutMappedChildFk player = players.get(0);
            assertEquals(1, player.getQuests().size());
            assertNotNull(player.getQuests().get(0).getPlayer());
            assertEquals(fixture.player.getId(), player.getQuests().get(0).getPlayer().getId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationIncludeHydratesNestedManyToOneOneToManyWithoutMappedForeignKeyField() throws Exception {
            UnmappedOneToManyFixture fixture = createUnmappedOneToManyFixture();
            Method queryMethod = QuestWithoutMappedPlayerFkNestedQueryRepository.class.getMethod("findById", UUID.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{fixture.quest.getId()},
                    questWithoutMappedPlayerFkMetadata,
                    dialect
            );

            List<QuestWithoutMappedPlayerFk> quests = (List<QuestWithoutMappedPlayerFk>) queryResult;
            assertEquals(1, quests.size());
            QuestWithoutMappedPlayerFk quest = quests.get(0);
            assertNotNull(quest.getPlayer());
            assertEquals(fixture.player.getId(), quest.getPlayer().getId());
            assertEquals(1, quest.getPlayer().getQuests().size());
            assertEquals(quest.getId(), quest.getPlayer().getQuests().get(0).getId());
        }
    }

    @Nested
    class PaginationTests {
        @Test
        void selectFetchPageSupportsWhereHasWithRootOrderBy() {
            createPaginationFixture();

            Page<Player> page = playerRepository.select()
                    .whereHas("quests", include -> include.where("completed").eq(false))
                    .orderBy("name").asc()
                    .fetchPage(Pageable.of(0, 2));

            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getContent().size());
            assertEquals("amy", page.getContent().get(0).getName());
            assertEquals("bob", page.getContent().get(1).getName());
        }

        @Test
        void repositoryFindAllPageableDelegatesToSelectPagination() {
            createPaginationFixture();

            Page<Player> page = playerRepository.findAll(Pageable.of(0, 2, Sort.asc("name")));

            assertEquals(4, page.getTotalElements());
            assertEquals(2, page.getContent().size());
            assertEquals("amy", page.getContent().get(0).getName());
            assertEquals("bob", page.getContent().get(1).getName());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationPageSupportsIncludesAndPageableSorting() throws Exception {
            createPaginationFixture();
            Method queryMethod = PlayerPagedQueryRepository.class.getMethod("findByNameLike", String.class, Pageable.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{"%", Pageable.of(0, 2, Sort.asc("name"))},
                    playerMetadata,
                    dialect
            );

            Page<Player> page = (Page<Player>) queryResult;
            assertEquals(4, page.getTotalElements());
            assertEquals(2, page.getContent().size());
            assertEquals("amy", page.getContent().get(0).getName());
            assertEquals("bob", page.getContent().get(1).getName());
            assertNotNull(page.getContent().get(0).getGuild());
            assertNotNull(page.getContent().get(1).getGuild());
        }

        @Test
        void queryAnnotationPageWithoutPageableThrowsHelpfulError() throws Exception {
            createPaginationFixture();
            Method queryMethod = InvalidPagedQueryRepository.class.getMethod("findAllPaged");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> queryMethodHandler.execute(queryMethod, new Object[0], playerMetadata, dialect)
            );

            assertTrue(exception.getMessage().contains("exactly one Pageable parameter"));
        }

        @Test
        void queryAnnotationPageRejectsSqlWithLimitOffset() throws Exception {
            createPaginationFixture();
            Method queryMethod = LimitedPagedQueryRepository.class.getMethod("findAllPaged", Pageable.class);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> queryMethodHandler.execute(
                            queryMethod,
                            new Object[]{Pageable.of(0, 2, Sort.asc("name"))},
                            playerMetadata,
                            dialect
                    )
            );

            assertTrue(exception.getMessage().contains("LIMIT/OFFSET"));
        }
    }

    @Nested
    class MetadataValidationTests {
        @Test
        void oneToManyWithoutJoinColumnThrowsHelpfulError() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> metadataRegistry.getOrParse(PlayerWithInvalidOneToMany.class)
            );

            assertEquals(
                    "@OneToMany field quests on " + PlayerWithInvalidOneToMany.class.getName() + " requires @JoinColumn or @JoinColumns.",
                    exception.getMessage()
            );
        }
    }

    private Fixture createFixture() {
        Guild guild = new Guild();
        guild.setName("knights");
        guildRepository.insert(guild);

        Player player = new Player();
        player.setName("alex");
        player.setGuild(guild);
        playerRepository.insert(player);

        return new Fixture(guild, player);
    }

    private QuestFixture createQuestFixture() {
        Fixture fixture = createFixture();

        Quest completedQuest = new Quest();
        completedQuest.setPlayerId(fixture.player.getId());
        completedQuest.setCompleted(true);
        completedQuest.setTitle("completed");
        questRepository.insert(completedQuest);

        Quest incompleteQuest = new Quest();
        incompleteQuest.setPlayerId(fixture.player.getId());
        incompleteQuest.setCompleted(false);
        incompleteQuest.setTitle("incomplete");
        questRepository.insert(incompleteQuest);

        return new QuestFixture(fixture.player, completedQuest, incompleteQuest);
    }

    private UnmappedOneToManyFixture createUnmappedOneToManyFixture() {
        PlayerWithoutMappedChildFk player = new PlayerWithoutMappedChildFk();
        player.setName("sam");
        playerWithoutMappedChildFkRepository.insert(player);

        QuestWithoutMappedPlayerFk quest = new QuestWithoutMappedPlayerFk();
        quest.setTitle("main-quest");
        quest.setPlayer(player);
        questWithoutMappedPlayerFkRepository.insert(quest);

        return new UnmappedOneToManyFixture(player, quest);
    }

    private void createPaginationFixture() {
        Guild guild = new Guild();
        guild.setName("archers");
        guildRepository.insert(guild);

        insertPlayerWithQuest(guild, "amy", "amy-open", false);
        insertPlayerWithQuest(guild, "bob", "bob-open", false);
        insertPlayerWithQuest(guild, "cara", "cara-open", false);
        insertPlayerWithQuest(guild, "zed", "zed-closed", true);
    }

    private void insertPlayerWithQuest(Guild guild, String playerName, String questTitle, boolean completed) {
        Player player = new Player();
        player.setName(playerName);
        player.setGuild(guild);
        playerRepository.insert(player);

        Quest quest = new Quest();
        quest.setPlayerId(player.getId());
        quest.setTitle(questTitle);
        quest.setCompleted(completed);
        questRepository.insert(quest);
    }

    private void createTable(String ddl) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private String toSqlitePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private interface PlayerQueryRepository {
        @Query("SELECT * FROM players WHERE id = :id")
        @Include("guild")
        List<Player> findById(@Param("id") UUID id);
    }

    private interface PlayerWithoutMappedChildFkQueryRepository {
        @Query("SELECT * FROM players_unmapped_fk WHERE id = :id")
        @Include("quests")
        List<PlayerWithoutMappedChildFk> findById(@Param("id") UUID id);
    }

    private interface PlayerWithoutMappedChildFkNestedQueryRepository {
        @Query("SELECT * FROM players_unmapped_fk WHERE id = :id")
        @Include("quests.player")
        List<PlayerWithoutMappedChildFk> findById(@Param("id") UUID id);
    }

    private interface QuestWithoutMappedPlayerFkNestedQueryRepository {
        @Query("SELECT * FROM quests_unmapped_fk WHERE id = :id")
        @Include("player.quests")
        List<QuestWithoutMappedPlayerFk> findById(@Param("id") UUID id);
    }

    private interface PlayerPagedQueryRepository {
        @Query("SELECT * FROM players WHERE name LIKE :name")
        @Include("guild")
        Page<Player> findByNameLike(@Param("name") String name, Pageable pageable);
    }

    private interface InvalidPagedQueryRepository {
        @Query("SELECT * FROM players")
        Page<Player> findAllPaged();
    }

    private interface LimitedPagedQueryRepository {
        @Query("SELECT * FROM players LIMIT 1")
        Page<Player> findAllPaged(Pageable pageable);
    }

    private static final class Fixture {
        private final Guild guild;
        private final Player player;

        private Fixture(Guild guild, Player player) {
            this.guild = guild;
            this.player = player;
        }
    }

    private static final class QuestFixture {
        private final Player player;
        private final Quest completedQuest;
        private final Quest incompleteQuest;

        private QuestFixture(Player player, Quest completedQuest, Quest incompleteQuest) {
            this.player = player;
            this.completedQuest = completedQuest;
            this.incompleteQuest = incompleteQuest;
        }
    }

    private static final class UnmappedOneToManyFixture {
        private final PlayerWithoutMappedChildFk player;
        private final QuestWithoutMappedPlayerFk quest;

        private UnmappedOneToManyFixture(PlayerWithoutMappedChildFk player, QuestWithoutMappedPlayerFk quest) {
            this.player = player;
            this.quest = quest;
        }
    }

    @Table("guilds")
    public static final class Guild {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @Column("name")
        private String name;

        public Guild() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Table("players")
    public static final class Player {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @Column("name")
        private String name;

        @ManyToOne
        @JoinColumn("guild_id")
        private Guild guild;

        @OneToMany
        @JoinColumn("player_id")
        private final List<Quest> quests = new ArrayList<>();

        public Player() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Guild getGuild() {
            return guild;
        }

        public void setGuild(Guild guild) {
            this.guild = guild;
        }

        public List<Quest> getQuests() {
            return quests;
        }
    }

    @Table("players_invalid_one_to_many")
    public static final class PlayerWithInvalidOneToMany {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @OneToMany
        private final List<Quest> quests = new ArrayList<>();

        public PlayerWithInvalidOneToMany() {
        }
    }

    @Table("quests")
    public static final class Quest {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @Column("player_id")
        private UUID playerId;

        @Column("title")
        private String title;

        @Column("is_completed")
        private boolean completed;

        public Quest() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public void setPlayerId(UUID playerId) {
            this.playerId = playerId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    @Table("players_unmapped_fk")
    public static final class PlayerWithoutMappedChildFk {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @Column("name")
        private String name;

        @OneToMany
        @JoinColumn("player_id")
        private final List<QuestWithoutMappedPlayerFk> quests = new ArrayList<>();

        public PlayerWithoutMappedChildFk() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<QuestWithoutMappedPlayerFk> getQuests() {
            return quests;
        }
    }

    @Table("quests_unmapped_fk")
    public static final class QuestWithoutMappedPlayerFk {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @ManyToOne
        @JoinColumn("player_id")
        private PlayerWithoutMappedChildFk player;

        @Column("title")
        private String title;

        public QuestWithoutMappedPlayerFk() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public PlayerWithoutMappedChildFk getPlayer() {
            return player;
        }

        public void setPlayer(PlayerWithoutMappedChildFk player) {
            this.player = player;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
