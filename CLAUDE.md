# Repository Guidelines

## Project Structure & Module Organization
`spigot-boot` is a multi-module Maven repository. Core modules live in `core/`, `commands/`, `config/`, `data/`, and `utils/`. Spigot/Paper integrations live in `platform-spigot/`, optional adapters in `modules/`, and `test-plugin/` is the sample plugin. Use the standard Maven layout: `src/main/java`, `src/main/resources`, `src/test/java`, and `src/test/resources`.

## Build, Test, and Development Commands
Use the Maven wrapper from the repo root; on Windows prefer `mvnw.cmd`, on Unix `./mvnw`.

- `mvnw.cmd clean test`: builds all modules and runs the full suite. CI runs `mvn test -B`.
- `mvnw.cmd -pl core -am test`: tests one module plus any required upstream modules.
- `mvnw.cmd package -DskipTests`: packages jars without running tests.
- `mvnw.cmd -pl test-plugin -am package`: packages the sample plugin for local verification.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, same-line braces, `UpperCamelCase` for types, `lowerCamelCase` for methods and fields, and package names under `tech.guilhermekaua.spigotboot.*`. Keep public APIs null-safe where the codebase already does so (`@NotNull`, `Objects.requireNonNull`). Write complete Javadocs for public and protected APIs with tags such as `@param`, `@return`, `@throws`, and `@deprecated` when applicable. Normal comments must start with lowercase. Do not use fully qualified inline types such as `private final java.util.List<String> list;`; import classes with the `import` keyword instead. No formatter is enforced, so match surrounding code and preserve Java license headers.

## Testing Guidelines
Tests use JUnit 5, Mockito, and MockBukkit for Spigot-facing modules. Name test classes `*Test` or `*IntegrationTest` and place them beside the module they validate. Add focused regression coverage for every behavior change; there is no hard coverage threshold, but CI must stay green across modules.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, and `test:`. Keep commits scoped to one change. PRs should target `master` or `dev`, explain the affected modules, link the issue when applicable, and include test notes. Add screenshots only when a change is observable through the sample plugin or documentation.

## Runtime Artifacts & Local Setup
Do not commit generated output or local server state from `target/`, `logs/`, `cache/`, `plugins/`, or `libraries/`. Treat `server.properties` and local plugin jars as developer-local files unless a change explicitly updates test infrastructure.
