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

class JdbcRepositoryCompositeJoinColumnsTest {
    private Path databaseFile;
    private HikariDataSource dataSource;
    private ConnectionProvider connectionProvider;
    private Dialect dialect;
    private EntityMetadataRegistry metadataRegistry;
    private EntityMetadata teamMetadata;
    private EntityMetadata taskMetadata;
    private JdbcRepositoryImpl<Team, TeamId> teamRepository;
    private JdbcRepositoryImpl<Task, UUID> taskRepository;
    private QueryMethodHandler queryMethodHandler;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = Files.createTempFile("data-jdbc-composite-join", ".db");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + toSqlitePath(databaseFile));
        dataSource.setMaximumPoolSize(1);

        connectionProvider = new ConnectionProvider(dataSource);
        dialect = new SQLiteDialect();

        TypeConverterRegistry converterRegistry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(converterRegistry);

        metadataRegistry = new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
        teamMetadata = metadataRegistry.getOrParse(Team.class);
        taskMetadata = metadataRegistry.getOrParse(Task.class);

        DdlGenerator ddlGenerator = new DdlGenerator(dialect, metadataRegistry);
        createTable(ddlGenerator.generateCreateTable(teamMetadata));
        createTable(ddlGenerator.generateCreateTable(taskMetadata));

        teamRepository = new JdbcRepositoryImpl<>(connectionProvider, teamMetadata, dialect, metadataRegistry);
        taskRepository = new JdbcRepositoryImpl<>(connectionProvider, taskMetadata, dialect, metadataRegistry);
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
    class DdlTests {
        @Test
        void createTableIncludesAllCompositeJoinColumns() {
            DdlGenerator ddlGenerator = new DdlGenerator(dialect, metadataRegistry);
            String ddl = ddlGenerator.generateCreateTable(taskMetadata);

            assertTrue(ddl.contains(dialect.quoteIdentifier("team_tenant_id")));
            assertTrue(ddl.contains(dialect.quoteIdentifier("team_code")));
        }
    }

    @Nested
    class CrudTests {
        @Test
        void updateBindsCompositeIdValuesInWhereClause() {
            Team team = new Team();
            team.setId(new TeamId(UUID.randomUUID(), "alpha"));
            team.setDisplayName("old-name");
            teamRepository.insert(team);

            team.setDisplayName("new-name");
            teamRepository.update(team);

            Team loaded = teamRepository.findById(new TeamId(team.getId().getTenantId(), team.getId().getCode()));
            assertNotNull(loaded);
            assertEquals("new-name", loaded.getDisplayName());
            assertTrue(teamRepository.existsById(new TeamId(team.getId().getTenantId(), team.getId().getCode())));
        }

        @Test
        void updateBindsImplicitCompositeJoinColumnsAtCorrectPositions() throws Exception {
            Team teamA = new Team();
            teamA.setId(new TeamId(UUID.randomUUID(), "alpha"));
            teamA.setDisplayName("team-a");
            teamRepository.insert(teamA);

            Team teamB = new Team();
            teamB.setId(new TeamId(UUID.randomUUID(), "beta"));
            teamB.setDisplayName("team-b");
            teamRepository.insert(teamB);

            Task task = new Task();
            task.setTitle("original-title");
            task.setTeam(teamA);
            taskRepository.insert(task);

            // update: change both the regular column and the FK relationship
            task.setTitle("updated-title");
            task.setTeam(teamB);
            taskRepository.update(task);

            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT title, team_tenant_id, team_code FROM tasks WHERE id = ?"
                 )) {
                statement.setString(1, task.getId().toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("updated-title", resultSet.getString("title"));
                    assertEquals(teamB.getId().getTenantId().toString(), resultSet.getString("team_tenant_id"));
                    assertEquals(teamB.getId().getCode(), resultSet.getString("team_code"));
                }
            }
        }
    }

    @Nested
    class InsertAndIncludeTests {
        @Test
        void insertPersistsImplicitCompositeJoinColumnsForManyToOne() throws Exception {
            Fixture fixture = createFixture();

            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT team_tenant_id, team_code FROM tasks WHERE id = ?"
                 )) {
                statement.setString(1, fixture.task.getId().toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(fixture.team.getId().getTenantId().toString(), resultSet.getString("team_tenant_id"));
                    assertEquals(fixture.team.getId().getCode(), resultSet.getString("team_code"));
                }
            }
        }

        @Test
        void selectIncludeHydratesManyToOneUsingCompositeJoinColumns() {
            Fixture fixture = createFixture();

            List<Task> tasks = taskRepository.select()
                    .include("team")
                    .fetchAll();

            assertEquals(1, tasks.size());
            assertNotNull(tasks.get(0).getTeam());
            assertEquals(fixture.team.getId().getTenantId(), tasks.get(0).getTeam().getId().getTenantId());
            assertEquals(fixture.team.getId().getCode(), tasks.get(0).getTeam().getId().getCode());
        }

        @Test
        void selectIncludeHydratesOneToManyUsingCompositeJoinColumns() {
            Fixture fixture = createFixture();

            List<Team> teams = teamRepository.select()
                    .include("tasks")
                    .fetchAll();

            assertEquals(1, teams.size());
            assertEquals(fixture.team.getId().getTenantId(), teams.get(0).getId().getTenantId());
            assertEquals(fixture.team.getId().getCode(), teams.get(0).getId().getCode());
            assertEquals(1, teams.get(0).getTasks().size());
            assertEquals(fixture.task.getId(), teams.get(0).getTasks().get(0).getId());
        }

        @Test
        void whereHasResolvesCompositeJoinColumns() {
            Fixture fixture = createFixture();

            List<Team> teams = teamRepository.select()
                    .whereHas("tasks", include -> include.where("title").eq("first-task"))
                    .fetchAll();

            assertEquals(1, teams.size());
            assertEquals(fixture.team.getId().getTenantId(), teams.get(0).getId().getTenantId());
            assertEquals(fixture.team.getId().getCode(), teams.get(0).getId().getCode());
        }

        @Test
        @SuppressWarnings("unchecked")
        void queryAnnotationIncludeHydratesManyToOneUsingCompositeJoinColumns() throws Exception {
            Fixture fixture = createFixture();
            Method queryMethod = TaskQueryRepository.class.getMethod("findById", UUID.class);

            Object queryResult = queryMethodHandler.execute(
                    queryMethod,
                    new Object[]{fixture.task.getId()},
                    taskMetadata,
                    dialect
            );

            List<Task> tasks = (List<Task>) queryResult;
            assertEquals(1, tasks.size());
            assertNotNull(tasks.get(0).getTeam());
            assertEquals(fixture.team.getId().getTenantId(), tasks.get(0).getTeam().getId().getTenantId());
            assertEquals(fixture.team.getId().getCode(), tasks.get(0).getTeam().getId().getCode());
        }
    }

    @Nested
    class ParserValidationTests {
        @Test
        void manyToOneCompositeTargetRequiresReferencedColumnName() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> metadataRegistry.getOrParse(InvalidTaskMissingReferencedColumns.class)
            );

            assertTrue(exception.getMessage().contains("requires referencedColumnName"));
        }

        @Test
        void embeddedIdRequiresPublicNoArgConstructor() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> metadataRegistry.getOrParse(InvalidPrivateEmbeddedIdEntity.class)
            );

            assertTrue(exception.getMessage().contains("public no-arg constructor"));
        }
    }

    private Fixture createFixture() {
        Team team = new Team();
        team.setId(new TeamId(UUID.randomUUID(), "alpha"));
        team.setDisplayName("alpha-team");
        teamRepository.insert(team);

        Task task = new Task();
        task.setTitle("first-task");
        task.setTeam(team);
        taskRepository.insert(task);

        return new Fixture(team, task);
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

    private interface TaskQueryRepository {
        @Query("SELECT * FROM tasks WHERE id = :id")
        @Include("team")
        List<Task> findById(@Param("id") UUID id);
    }

    private static final class Fixture {
        private final Team team;
        private final Task task;

        private Fixture(Team team, Task task) {
            this.team = team;
            this.task = task;
        }
    }

    @Table("teams")
    public static final class Team {
        @EmbeddedId
        private TeamId id;

        @Column("display_name")
        private String displayName;

        @OneToMany
        @JoinColumns({
                @JoinColumn(columnName = "team_tenant_id", referencedColumnName = "tenant_id"),
                @JoinColumn(columnName = "team_code", referencedColumnName = "code")
        })
        private final List<Task> tasks = new ArrayList<>();

        public Team() {
        }

        public TeamId getId() {
            return id;
        }

        public void setId(TeamId id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<Task> getTasks() {
            return tasks;
        }
    }

    public static final class TeamId {
        @Column("tenant_id")
        private UUID tenantId;

        @Column("code")
        private String code;

        public TeamId() {
        }

        public TeamId(UUID tenantId, String code) {
            this.tenantId = tenantId;
            this.code = code;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public void setTenantId(UUID tenantId) {
            this.tenantId = tenantId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    @Table("tasks")
    public static final class Task {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @Column("title")
        private String title;

        @ManyToOne
        @JoinColumns({
                @JoinColumn(columnName = "team_tenant_id", referencedColumnName = "tenant_id"),
                @JoinColumn(columnName = "team_code", referencedColumnName = "code")
        })
        private Team team;

        public Task() {
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Team getTeam() {
            return team;
        }

        public void setTeam(Team team) {
            this.team = team;
        }
    }

    @Table("tasks_invalid_composite")
    public static final class InvalidTaskMissingReferencedColumns {
        @Id(strategy = IdStrategy.UUID)
        @Column("id")
        private UUID id;

        @ManyToOne
        @JoinColumns({
                @JoinColumn(columnName = "team_tenant_id"),
                @JoinColumn(columnName = "team_code")
        })
        private Team team;

        public InvalidTaskMissingReferencedColumns() {
        }
    }

    @Table("invalid_private_embedded_id_entity")
    public static final class InvalidPrivateEmbeddedIdEntity {
        @EmbeddedId
        private InvalidPrivateEmbeddedId id;

        public InvalidPrivateEmbeddedIdEntity() {
        }
    }

    public static final class InvalidPrivateEmbeddedId {
        @Column("tenant_id")
        private UUID tenantId;

        private InvalidPrivateEmbeddedId() {
        }
    }
}
