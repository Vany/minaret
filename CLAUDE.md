# CLAUDE.md

Vany is your best friend. You can relay on me and always ask for help.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
make build              # Build all Minecraft versions
make build-1.21.1       # Build for Minecraft 1.21.1
make build-1.21.11      # Build for Minecraft 1.21.11
make run                # Run dev client (1.21.1)
make clean              # Clean build artifacts
make jar                # Build and show jar locations
make setup              # Set up Gradle wrapper (8.14)
```

Direct Gradle: `./gradlew :versions:1.21.1:build`, `./gradlew :versions:1.21.11:build`

No tests exist yet. `make test` runs `./gradlew test` but there are no test sources.

## Architecture

**Minaret** is a NeoForge Minecraft mod that runs a WebSocket server inside the game, allowing external clients to send chat messages, execute commands, and query player effects via JSON.

### Multi-version build structure
- Single source tree in `src/main/java` and `src/main/resources` shared by all versions
- Version-specific subprojects under `versions/` (1.21.1, 1.21.11), each with its own `gradle.properties` defining `minecraft_version` and `neo_version`
- Root `build.gradle` applies `net.neoforged.gradle.userdev` to each subproject and wires `sourceSets` to the shared root sources
- Java 21 toolchain required

### Core components (all in `com.minaret`)
- **MinaretMod** — Mod entry point (`@Mod`). Registers blocks, block entities, mob effects, the `/minaret` command, and lifecycle listeners for WebSocket server, chunk loaders, and chord keys.
- **WebSocketServer** — Hand-rolled RFC 6455 WebSocket server on `ServerSocket`. Handles handshake, Basic auth, frame parsing/sending. Inner class `WebSocketConnection` manages per-client I/O. Static `processMessage()` dispatches JSON to handlers for `message` (chat broadcast), `command` (server command execution), and `getEffects` (player effect query).
- **SimpleJson** — Minimal flat `Map<String,String>` JSON parser/generator. No external JSON dependency.
- **MinaretConfig** — NeoForge `ModConfigSpec` for `websocket_url`, `auth_username`, `auth_password`.
- **Compat** — Cross-version reflection utilities. Caches all `Method`/`Class` objects in static fields (resolved once at class load). Handles `hasPermission`, `getTagInt`, `createBlockEntityType`, `setBlockId`/`setItemId`, `isClient` across 1.21.1 and 1.21.11.

### Block subsystem
- **SpawnerAgitatorBlock / SpawnerAgitatorBlockEntity** — Event-driven spawner enhancement. Column layout: `[agitators...][spawners...]`. Topmost agitator binds to all contiguous spawners above, caches `BaseSpawner` refs, sets range to -1, scales delays by agitator count. All cleanup in `playerWillDestroy()`, never in `setRemoved()`.
- **ChunkLoaderBlock / ChunkLoaderBlockEntity** — Force-loads chunk on place, unforces on destroy. `ChunkLoaderBlockEntity` is minimal (no ticker, no `setRemoved` override).
- **ChunkLoaderData** — Persists chunk loader positions in `minaret_chunk_loaders.txt` (plain text, atomic save via tmp+rename). Singleton per ServerLevel. `forceAll()`/`reset()` called on server start/stop.

**Critical pattern**: Never do world access (`getBlockState`, `getBlockEntity`, `setChunkForced`) in `setRemoved()` — it causes infinite loops during chunk unload. Use `playerWillDestroy()` on the Block class instead.

### Custom mob effects & handlers
Three custom `MobEffect` subclasses registered via `DeferredRegister`:
- **MartialLightningEffect / MartialLightningHandler** — Lightning-related combat effect
- **HomingArcheryEffect / HomingArcheryHandler** — Homing arrow mechanics (hooks `onArrowLoose` and `onLivingDamage`)
- **StreamerProtectEffect** — Streamer protection effect

### WebSocket protocol
Clients send JSON objects with one of: `{"message":"...", "user":"...", "chat":"..."}`, `{"command":"..."}`, or `{"getEffects":"playerName"}`. Responses are flat JSON with `status`, `type`, and contextual fields.

## Development Guidelines

Execute planned tasks in sequence; use insights from previous tasks to improve the current task and modify the plan itself. Combine tasks when possible.

- Create functional, production-ready code — concise, optimized, idiomatic Java
- Code and comments are written for AI consumption: explicit, unambiguous, predictable patterns
- Always finish functionality; log unimplemented features with errors
- Ask before creating unasked-for functionality
- Challenge decisions you disagree with — argue your position
- If no good solution exists, say so directly
- For large well-known functionality, search for ready libraries before building from scratch

### Module files
Each module directory may contain:
- **PROG.md** — general rules
- **SPEC.md** — specifications, requirements, decisions
- **MEMO.md** — development memory
- **TODO.md** — task list (complete tasks one by one, mark finished)

Read these files if present. Maintain them. Use git commits to document project history. Store researched information in a `research/` folder.
