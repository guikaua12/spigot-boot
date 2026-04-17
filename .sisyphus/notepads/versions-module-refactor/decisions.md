# Decisions

## 2026-04-14T21:54:51.315Z Task: planning-baseline
- Use a full neutral rename for versioning infrastructure while keeping entity-domain nouns that still describe domain behavior.
- API/runtime roots target `tech.guilhermekaua.spigotboot.versions.*` and provider implementations target `tech.guilhermekaua.spigotboot.v1_x_x.entity`.
- Publish a thin shared consumer artifact `spigot-boot-versions`; keep provider modules separate.
- Breaking change is acceptable; no compatibility bridges.
