# Repository Guidelines

## Project Structure & Module Organization
`spigot-boot` is a multi-module Maven repository. Core modules live in `core/`, `commands/`, `config/`, `data/`, and `utils/`. Spigot/Paper integrations live in `platform-spigot/`, optional adapters in `modules/`, and `test-plugin/` is the sample plugin. Use the standard Maven layout: `src/main/java`, `src/main/resources`, `src/test/java`, and `src/test/resources`.

## Build, Test, and Development Commands
Use the Maven wrapper from the repo root; on Windows prefer `mvnw.cmd`, on Unix `./mvnw`.

- `mvnw.cmd clean test`: builds all modules and runs the full suite. CI runs `mvn test -B`.
- `mvnw.cmd -pl core -am test`: tests one module plus any required upstream modules.
- `mvnw.cmd package -DskipTests`: packages jars without running tests.
- `mvnw.cmd -pl test-plugin -am package`: packages the sample plugin for local verification. On a fresh clone, first run `mvnw.cmd -pl spigot-api-1_8-signature install` (the spigot-api 1.8.8 API-check signature is build-internal, never published), or skip the check with `-Danimal.sniffer.skip=true`.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, same-line braces, `UpperCamelCase` for types, `lowerCamelCase` for methods and fields, and package names under `tech.guilhermekaua.spigotboot.*`. Keep public APIs null-safe where the codebase already does so (`@NotNull`, `Objects.requireNonNull`). Write complete Javadocs for public and protected APIs with tags such as `@param`, `@return`, `@throws`, and `@deprecated` when applicable. Normal comments must start with lowercase. Do not use fully qualified inline types such as `private final java.util.List<String> list;`; import classes with the `import` keyword instead. No formatter is enforced, so match surrounding code and preserve Java license headers.

## Design Principles
When implementing new features, follow SOLID:

- `S` Single Responsibility: a class should have one reason to change.
- `O` Open/Closed: prefer new handlers, strategies, or modules instead of editing stable flows for each variation.
- `L` Liskov Substitution: implementations must honor the contract of the parent type.
- `I` Interface Segregation: expose small focused interfaces, not wide "do everything" contracts.
- `D` Dependency Inversion: depend on abstractions and inject collaborators instead of constructing concrete classes inline.

`S` Bad:
```java
class UserService {
    void save(User user) { repository.save(user); }
    void sendWelcomeEmail(User user) { mailer.send(user); }
}
```

`S` Good:
```java
class UserService {
    void save(User user) { repository.save(user); }
}

class WelcomeEmailService {
    void sendWelcomeEmail(User user) { mailer.send(user); }
}
```

`O` Bad:
```java
class CommandExecutor {
    void execute(String type) {
        if ("sync".equals(type)) runSync();
        else if ("async".equals(type)) runAsync();
    }
}
```

`O` Good:
```java
interface CommandMode {
    void execute();
}

class AsyncCommandMode implements CommandMode {
    public void execute() { runAsync(); }
}
```

`L` Bad:
```java
class FileRepository extends Repository {
    @Override
    void delete(String id) { throw new UnsupportedOperationException(); }
}
```

`L` Good:
```java
interface ReadRepository {
    Object find(String id);
}

interface WriteRepository extends ReadRepository {
    void delete(String id);
}
```

`I` Bad:
```java
interface PluginLifecycle {
    void onEnable();
    void onDisable();
    void reload();
    void migrate();
}
```

`I` Good:
```java
interface PluginStarter {
    void onEnable();
}

interface PluginStopper {
    void onDisable();
}
```

`D` Bad:
```java
class UserService {
    private final MySqlUserRepository repository = new MySqlUserRepository();
}
```

`D` Good:
```java
class UserService {
    private final UserRepository repository;

    UserService(UserRepository repository) {
        this.repository = repository;
    }
}
```

## Shading & javassist (relocation safety)
`spigot-boot-core` bundles javassist **relocated** to `tech.guilhermekaua.spigotboot.shaded.javassist` (see `core/pom.xml`). Inside a downstream plugin jar that relocated copy is the **only** javassist present — the original `javassist.*` classes are never shipped. Any *shipped* module whose compiled bytecode references the original `javassist.*` package throws `NoClassDefFoundError: javassist/util/proxy/...` on a real server, even though it passes tests (where the original javassist is still on the classpath). Green tests do **not** prove runtime safety, because shading happens in the `package` phase, after tests run.

When a module needs javassist, pick the matching rule:

- **Only detecting/inspecting proxies (no proxy creation):** do **not** `import javassist.*`. Match javassist's `ProxyObject` marker interface by name so it recognizes both the original and the relocated name. See `utils/ProxyUtils#isJavassistProxy`; keep javassist at `test` scope there.
- **Creating proxies or using the javassist API directly:** the module's own bytecode must reference the **shaded** names at runtime. Add a `maven-shade-plugin` execution that relocates `javassist` → `tech.guilhermekaua.spigotboot.shaded.javassist` and bundles nothing (`<artifactSet>` includes only the module's own `groupId:artifactId`, so `core` keeps ownership of the single bundled copy), and declare javassist as `provided`. See `platform-spigot/config-spigot/pom.xml` and `core/pom.xml`.
- **Never** add javassist as a shipped `compile`-scope dependency without one of the above — that ships un-relocated references and reintroduces the crash.

When touching proxy code, add a regression guard like `ProxyUtilsTest#proxyUtilsClassMustNotReferenceUnrelocatedJavassistPackage`, which asserts the compiled class carries no `javassist/` (slashed, internal-form) reference.

## Testing Guidelines
Tests use JUnit 5, Mockito, and MockBukkit for Spigot-facing modules. Name test classes `*Test` or `*IntegrationTest` and place them beside the module they validate. Add focused regression coverage for every behavior change; there is no hard coverage threshold, but CI must stay green across modules.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, and `test:`. Keep commits scoped to one change. PRs should target `master` or `dev`, explain the affected modules, link the issue when applicable, and include test notes. Add screenshots only when a change is observable through the sample plugin or documentation.

## Runtime Artifacts & Local Setup
Do not commit generated output or local server state from `target/`, `logs/`, `cache/`, `plugins/`, or `libraries/`. Treat `server.properties` and local plugin jars as developer-local files unless a change explicitly updates test infrastructure.
