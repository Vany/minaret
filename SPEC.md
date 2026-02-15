# SPEC.md - Minaret Mod Specification

## Overview

Minaret is a NeoForge mod (server + client) with three subsystems:
1. **WebSocket bridge** — external apps interact with Minecraft via JSON over RFC 6455
2. **Custom mob effects** — three gameplay effects applied via commands/external API
3. **Chord keys** — emacs-style key sequences that fire virtual keybindings

## Platform

| Parameter | Value |
|-----------|-------|
| Minecraft | 1.21.1, 1.21.11 (multi-version build) |
| Mod loader | NeoForge (21.1.172+ / 21.11.38-beta) |
| Java | 21+ |
| External deps | None (std lib only) |
| Side | Both (server: WebSocket/effects, client: chord keys) |

## 1. WebSocket Server

### Transport
- Manual RFC 6455 implementation on raw `ServerSocket`
- Default bind: `localhost:8765` (configurable in `minaret-server.toml`)
- Thread pool (`CachedThreadPool`) for connection I/O
- All Minecraft operations dispatched to main server thread via `mcServer.execute()`

### Authentication
- Optional HTTP Basic Auth during WebSocket handshake
- Constant-time credential comparison (`MessageDigest.isEqual`)
- 401 + `WWW-Authenticate` on failure
- Config: `auth_username` / `auth_password` (empty = disabled)

### JSON Protocol

Uses `SimpleJson` — flat `Map<String,String>` parser/generator, no nested objects or arrays (except hand-built in `getEffects` response).

#### Chat broadcast
```
-> {"message":"text", "user":"name", "chat":"channel"}
<- {"status":"success", "type":"message"}
```
`user` and `chat` are optional. Messages are formatted with section-sign color codes and sent to all players.

#### Command execution
```
-> {"command":"time set day"}
<- {"status":"success", "type":"command", "command":"time set day", "result":"1"}
<- {"status":"error", "type":"command", "error":"...", "command":"...", "result":"0"}
```
Executed via brigadier dispatcher with OP4 permissions. Result code from dispatcher returned.

#### Player effects query
```
-> {"getEffects":"PlayerName"}
<- {"status":"success", "type":"getEffects", "player":"PlayerName", "effects":[...]}
```
Returns array of `{effect, duration, amplifier}`. This is hand-built JSON (not via SimpleJson).

#### In-game command
`/minaret exec <json>` — same processing as WebSocket, requires OP4.
Permission check uses reflection fallback for 1.21.11+ API changes.

### Frame handling
- Supports text, close, ping/pong opcodes
- 16-bit extended payload supported; 64-bit payload rejected (close)
- Single-frame messages only (no continuation frames)
- Max useful payload: 65535 bytes

## 2. Custom Mob Effects

Three effects registered via `DeferredRegister<MobEffect>`. All beneficial category.

### Martial Lightning (`martial_lightning`)
Color: `#00BFFF`. Enhances melee attacks based on held weapon tier:

| Weapon | Damage mult | AoE | Secondary |
|--------|------------|-----|-----------|
| Bare hand | 10x | Yes (front cone) | — |
| Wooden tool | 5x | Yes | Poison (amp 31, 10s) |
| Stone tool | 3x | Yes | Wither (amp 3, 10s) |
| Iron tool | 1.5x | No | — |
| Other | 1x | No | — |

AoE hits entities in front hemisphere within `entityInteractionRange`. Recursive AoE prevented via `ConcurrentHashMap` guard set.

Tool categorization: prefix match on registry path (`wooden_`, `stone_`, `iron_`).

### Homing Archery (`homing_archery`)
Color: `#9B30FF`. Replaces bow arrows with homing `ShulkerBullet`:
- Cancels `ArrowLooseEvent`, spawns bullet targeting closest mob to crosshair
- Target acquisition: raycast 100 blocks, fallback to nearest entity within 50 blocks in aim cone (dot > 0.5)
- Damage: `base(2) * velocity_scale(3) * power * multiplier(3)` = up to 18 at full draw
- Bullet-to-damage mapping via `ConcurrentHashMap<UUID, TrackedBullet>`, stale entries cleaned after 60s
- Server-side only (`ServerLevel` check)

### Streamer Protect (`streamer_protect`)
Color: `#FFD700`. Indicator-only effect — logic handled externally. No handler code in mod.

## 3. Configuration

File: `config/minaret-server.toml` (NeoForge `ModConfigSpec`, server type)

| Key | Default | Description |
|-----|---------|-------------|
| `websocket_url` | `localhost:8765` | host:port for WS server |
| `auth_username` | `""` | Basic auth user (empty = no auth) |
| `auth_password` | `""` | Basic auth password |

## 4. Chord Keys (client-side)

Emacs-style key sequences that fire actions. Two target types:
- **Key targets** (`key:<name>`) — trigger a `KeyMapping` by name (any mod's keybinding)
- **Command targets** (`cmd:<json>`) — dispatch JSON as a WebSocket command

### Configuration
File: `config/minaret-chords.json` (not ModConfigSpec — needs nested structure)
```json
{
  "metaKey": "f",
  "chords": {
    "f>1": "key:sophisticatedbackpacks.openbackpack",
    "f>2": "cmd:{\"command\":\"time set day\"}"
  }
}
```
- `metaKey`: the initiating key for all chord sequences (default: `f`)
- `chords`: map of sequence string → prefixed target string

### Commands
- `/minaret addkey <sequence> <action>` — bind chord to a KeyMapping name (tab-completes action names)
- `/minaret addcommand <sequence> <json>` — bind chord to a WebSocket JSON command
- `/minaret delkey <sequence>` — remove a chord
- `/minaret listkeys` — list all configured chords with targets
- `/minaret listactions [filter]` — list available KeyMapping names (optional substring filter)

### Key Consumption
`InputEvent.Key` is NOT cancelable in NeoForge. `KeyMapping.click()` fires BEFORE the event.
Solution: reflection-based `consumeKey()` resets `clickCount` to 0 and `setDown(false)` on all
`KeyMapping`s bound to the consumed key. This prevents the meta key (and subsequent keys) from
triggering game actions while a chord is in progress.

### Chord Firing
- `key:` targets: find the `KeyMapping` by name, read its bound `InputConstants.Key` via reflection,
  call `KeyMapping.click(boundKey)` to trigger whatever action is bound to that key
- `cmd:` targets: dispatch the JSON string through `MessageDispatcher.dispatch()` using
  `MinaretMod.getServer()`, same as if received from WebSocket
- Legacy: bare target strings (no prefix) treated as key targets for backwards compat

### State Machine (trie-based)
A trie is built from all configured chord sequences. On key press, walk one step:
```
Example trie for f>1, f>2:
  ROOT
   └─ f (meta) → overlay "f > _"
       ├─ 1 → fire "key:sophisticatedbackpacks.openbackpack"
       └─ 2 → fire "cmd:{\"command\":\"time set day\"}"
```
- IDLE → meta key → enter trie, show overlay
- At trie node → matching child → advance (or fire if leaf)
- At trie node → no matching child → cancel, reset to IDLE
- 1500ms timeout → reset to IDLE (fire if timed out on a leaf node)
- Chords ignored when `mc.screen != null` (chat, menus, etc.)

### Key Names
Letters `a-z`, digits `0-9`, `f1-f12`, `space`, `tab`, `minus`, `equals`,
`lbracket`, `rbracket`, `semicolon`, `comma`, `period`, `slash`.

### Meta Key
Registered as a configurable `KeyMapping` ("Chord Meta Key") in Controls under "Minaret Chords".
Cross-version `KeyMapping` construction via reflection (1.21.1: String category, 1.21.11: Category record).

### Files
- `com.minaret.client.ChordKeyHandler` — trie state machine, key consumption, chord firing
- `com.minaret.ChordConfig` — JSON persistence for chord definitions
- Initialized from `MinaretMod` constructor, guarded by reflective dist check

## 5. Block Subsystem

Three custom blocks with block entities, plus a persistence layer for chunk loaders.

### Spawner Agitator (`spawner_agitator`)

A block placed in a column above a vanilla mob spawner. Modifies the spawner's behavior:
- Sets `requiredPlayerRange` to 32767 (effectively always active while server has players)
- Accelerates `spawnDelay` proportional to the number of stacked agitators
- Stacking: multiple agitators in a vertical column multiply the acceleration

**Architecture — event-driven with minimal ticker:**

| Event | Action |
|-------|--------|
| `onPlace` (Block) | `bindSpawner()` — walk up from agitator to find spawner, cache `BaseSpawner` ref directly, set `requiredPlayerRange` to 32767, store original range. `notifyColumn()` — recalc stack size for topmost agitator. |
| `playerWillDestroy` (Block) | `unbindSpawner()` — restore original `requiredPlayerRange`. `notifyColumnExcluding(self)` — recalc stack skipping the removed block. |
| `onLoad` (BlockEntity) | Same as onPlace bind logic — resolves spawner on chunk load. |
| `onNeighborChanged` (BlockEntity) | Re-checks spawner above. Called when a neighbor block changes. |
| `serverTick` (static) | **Minimal ticker** — if `cachedSpawner != null` and `cachedStackSize > 1`: decrement `spawnDelay` by `(stackSize - 1)`. One field read + one field write via cached reflection. No world access, no block state checks. |

**Reflection caching:**
- `requiredPlayerRange` field: resolved by name in static init, fallback probes by value 16 on first real spawner encounter
- `spawnDelay` field: resolved by name in static init, fallback probes by value 20 on first real spawner encounter
- Both `Field` objects cached in `static volatile` fields, resolved once

**Key design decisions:**
- `AGITATED_RANGE = 32767` (not -1) — ensures spawner stops naturally when all players disconnect during shutdown, preventing the `while(hasWork())` save loop hang
- All cleanup in `playerWillDestroy()` (Block class), never in `setRemoved()` — world access in `setRemoved()` causes infinite loops during chunk unload (chunk reload → setRemoved → world access → chunk reload...)
- `BaseSpawner` cached directly (not `SpawnerBlockEntity`) to avoid `getSpawner()` virtual call per tick
- Stack recalc on remove uses `notifyColumnExcluding(removedPos)` to skip the block being removed (it's still in the world during `playerWillDestroy`)

**NBT:** Persists `OriginalPlayerRange` (int) so the spawner can be restored after chunk reload.

**Known limitation:** Spawner placed *after* an agitator is only detected on chunk reload (`onLoad`), not immediately. The `neighborChanged` override cannot be used cross-version (1.21.11 changed the signature to use `Orientation` instead of `BlockPos`).

### Chunk Loader (`chunk_loader`)

A block that force-loads its chunk. Positions persisted to survive server restarts.

| Event | Action |
|-------|--------|
| `onPlace` (Block) | `ChunkLoaderData.add(pos)` + `forceChunk(level, pos, true)` |
| `playerWillDestroy` (Block) | `ChunkLoaderData.remove(pos)` + `forceChunk(level, pos, false)` |
| Server starting | `ChunkLoaderData.forceAll(level)` — re-forces all saved positions |
| Server stopping | `ChunkLoaderData.reset()` — clears singleton (chunks unforce naturally) |

`ChunkLoaderBlockEntity` is minimal — just a constructor, no ticker, no `setRemoved` override.

### ChunkLoaderData

Plain text file persistence (`minaret_chunk_loaders.txt` in world save directory):
- One `X Y Z` line per loader position
- Atomic save: write to `.tmp` file, then `Files.move()` with `ATOMIC_MOVE`
- Singleton per `ServerLevel`, loaded lazily on first access
- `forceAll()` / `unforceAll()` for batch operations on server lifecycle

### Warding Post (`warding_post`)

A post block that repels hostile mobs. Horizontal radius scales with column height (stacking).

**Radius formula:** `radius = 4 * columnHeight` blocks (1 post = 4, 2 stacked = 8, 3 = 12, etc.). Vertical range is always ±2 blocks.

**Behavior:**
- Only the topmost post in a column ticks (others skip via `isTopOfColumn` flag)
- Ticks every 4 server ticks (5 times per second)
- Scans for `Monster` entities in a dynamic AABB centered on the topmost post
- Pushes each mob ~0.5 blocks outward horizontally with a slight upward boost (0.1)
- Mobs exactly at center are pushed in an arbitrary direction (+X)
- Custom collision shape: narrow 4×16×4 post (visual only — full block support shape for building on top)

**Column stacking (mirrors spawner agitator pattern):**

| Event | Action |
|-------|--------|
| `onPlace` (Block) | `notifyColumn()` — walk down to column base, walk up recalculating each post |
| `playerWillDestroy` (Block) | `notifyColumnExcluding(removed)` — recalc column skipping the removed post |
| `onLoad` (BlockEntity) | `recalcColumn()` — recompute on chunk load |

Each post caches `cachedColumnHeight` (count of posts at or below it) and `isTopOfColumn` (no warding post directly above).

**Architecture:**
- `WardingPostBlock` extends `BaseEntityBlock` with custom `VoxelShape`, `onPlace`/`playerWillDestroy` for column notification
- `WardingPostBlockEntity` has server ticker (topmost only), column height caching, no NBT persistence needed
- Found as dungeon loot (jungle temples, desert pyramids, strongholds, mineshafts, simple dungeons)

### Compat (reflection utilities)

Caches all reflected `Method` and `Class` objects in `static final` fields, resolved once at class load:
- `hasPermission(CommandSourceStack, int)` — 1.21.1 has direct method, 1.21.11+ changed API
- `getTagInt(CompoundTag, String)` — 1.21.1 returns `int` directly, 1.21.11+ returns `Optional`
- `createBlockEntityType(supplier, blocks)` — handles constructor differences
- `setBlockId` / `setItemId` — 1.21.11+ only (uses `Identifier` class)
- `isClient()` — detects client vs server via `FMLEnvironment.dist` with fallback to `getDist()`

## 6. Multi-version Build

Shared source in `src/main/` compiled by version-specific subprojects:
- `versions/1.21.1/` — MC 1.21.1, NeoForge 21.1.172
- `versions/1.21.11/` — MC 1.21.11, NeoForge 21.11.38-beta

Each subproject has `gradle.properties` with `minecraft_version`, `neo_version`, range constraints. Root `build.gradle` applies userdev plugin and rewires `sourceSets` to root source dirs.

API compatibility: `/minaret exec` permission check uses reflection to handle `hasPermission(int)` removal in 1.21.11+.

## 7. Decisions Made

1. **No external dependencies** — avoids jar bundling complexity; custom WebSocket + JSON
2. **Flat JSON only** — `SimpleJson` handles `Map<String,String>` which covers the protocol; `getEffects` array is hand-built as a one-off
3. **OP4 for all commands** — simplifies authorization; external auth layer is the access gate
4. **CachedThreadPool** — unbounded thread pool; acceptable given expected low connection count (<10)
5. **No continuation frames** — single-frame messages only; sufficient for JSON payloads under 64KB
6. **Reflection for version compat** — `hasPermission`, `FMLEnvironment.dist`/`getDist()`, `KeyMapping` constructor all differ between versions; reflection handles all three
7. **Direct KeyMapping firing for chords** — chords map directly to KeyMapping names (e.g. `key:sophisticatedbackpacks.openbackpack`) or WebSocket commands (`cmd:{...}`). No phantom/virtual keycodes needed. `KeyMapping.click(boundKey)` fires the target action directly.
8. **Separate chord config file** — `config/minaret-chords.json` instead of ModConfigSpec, because chord data is nested (map of sequence→target) and client-side only
9. **Reflection-based key consumption** — `InputEvent.Key` is not cancelable in NeoForge. Keys are consumed by resetting `clickCount` to 0 and `setDown(false)` on matching KeyMappings via reflection, since `KeyMapping.click()` fires before the event
10. **No world access in `setRemoved()`** — causes infinite loops during chunk unload. All cleanup moved to `playerWillDestroy()` on the Block class
11. **`AGITATED_RANGE = 32767` not `-1`** — `-1` makes spawner always active (bypasses player range check), which prevents clean shutdown. `32767` is large enough to be effectively infinite but stops naturally when players disconnect
12. **Plain text file for ChunkLoaderData** — `SavedData`/`SavedDataType` API differs between 1.21.1 and 1.21.11; simple text file avoids the incompatibility
13. **Atomic file save** — write to `.tmp` then `Files.move(ATOMIC_MOVE)` prevents corruption on crash mid-write
14. **Event-driven spawner binding** — spawner discovery on place/load events, not per-tick polling. Ticker only does the delay acceleration (which genuinely needs per-tick work)

## 8. Not Yet Implemented (from REQUIREMENTS.md)

- Rate limiting (connections and messages)
- TLS/SSL (WSS)
- IP whitelisting
- Event broadcasting (server events pushed to WS clients)
- Player join/leave notifications
- Batch commands
- Message queuing
- Plugin API for other mods
- WebSocket compression (deflate)
- Binary frame support
- Advanced auth (JWT/OAuth)
- Automated tests (no test sources exist)
