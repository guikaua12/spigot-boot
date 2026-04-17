## 2026-04-14T12:51:11Z Task: session-bootstrap
- Prompt-defined filenames are canonical and override inferred local naming habits.
- Scope is frozen to the 10 prompt-listed pages in `wiki/wiki/`.
- No fresh matrix run is required to write the docs; evidence should come from source files, scenario descriptors, and matrix scripts/config already in the repo.
- `CustomEntityDefinition` is migration-only context and must never be positioned as the recommended API.

## 2026-04-14T12:57:00Z Task: style-and-conventions
- When local wiki practice conflicts with the entity prompt, the entity prompt wins. This especially applies to Home-page structure and internal link format.
- Standardize every entity wiki link to no `.md` suffix, even though some existing local pages still include it.
- Treat Guava’s practical pattern as: curated Home page, code-first feature pages, strong `Why?` / `How?` framing, and inline contextual deep links.
