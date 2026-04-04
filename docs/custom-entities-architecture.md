# Custom Entities Architecture

This repository now contains the first scaffold for a multi-version custom entity library under `versions/`.

## Layout

- `versions/api`
  - stable public abstraction for entity types, goal keys, goal definitions, capability checks, version parsing, and adapter contracts
- `versions/runtime`
  - detects the active server version, loads registered adapters with `ServiceLoader`, and exposes a single runtime entry point
- `versions/1.8.8`
  - legacy adapter scaffold for Minecraft `1.8.8`
- `versions/1.21.11`
  - modern adapter scaffold for Minecraft `1.21.11`

## Why this shape

The abstraction needs a stable API while still letting each Minecraft version translate calls into version-specific NMS behavior.
Splitting the project into `api + runtime + per-version implementations` keeps the public API clean and makes it possible to ship one plugin jar that bundles many adapters.

## Capability model

Goals are represented by logical keys such as:

- `minecraft:float`
- `minecraft:melee_attack`
- `minecraft:climb_on_top_of_powder_snow`
- `minecraft:use_item`

Each version adapter publishes the goals it supports per logical entity type.
If a caller requests a goal that does not exist for the active version, the adapter can throw `UnsupportedGoalException` with an explicit version-aware message.

## Current state

The current scaffold does these parts already:

- resolves a server version like `1.8.8-R0.1-SNAPSHOT` or `1.21.11`
- selects the matching adapter at runtime
- exposes a capability model that can reject unsupported goals cleanly
- registers `1.8.8` and `1.21.11` adapters through `ServiceLoader`

The current scaffold does not yet do these parts:

- spawn real NMS-backed entities
- translate logical goals into native Pathfinder goals
- expose typed wrappers for every vanilla entity
- bridge client-side packet features with PacketEvents

## Implementation roadmap

1. Add a native bridge layer per version that wraps the actual NMS entity classes.
2. Introduce typed entity abstractions such as `CustomZombie`, `CustomVillager`, and `CustomWolf`.
3. Move goal translation into per-version registries so each logical goal maps to the correct native implementation.
4. Add PacketEvents-backed networking only where packets are truly needed.
5. Add support modules for every targeted version or version family.

## Mapping and reverse-engineering notes

Use these sources when filling in each adapter:

- `1.8.8` to `1.12.2`: MCP CSVs and MCPBot exports for deobfuscation
- `1.13+`: `mappings.dev` for browsing named members
- `1.13+` remap/decompile pipelines: MCPConfig

As of April 4, 2026:

- `mappings.dev` publishes a `1.21.11` tree
- PacketEvents `2.11.0` announces support for Minecraft `1.21.11`

## Packet policy

Do not reach for raw NMS packet classes when a feature can be implemented through PacketEvents.
Keep packet work behind a dedicated abstraction so entity logic and networking stay separate.
