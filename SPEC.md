# SPEC.md - Minaret Mod Specification

## TODO

- [ ] area spawn rate incrementer like warding post.
- [x] ederman teleportion inhibitor
- [ ] backpack collector
- [ ] mob damager
- [ ] change agitator to randomly recheck configuration
- [x] enchantments on enchanting table
- [x] enchanted weapons in dungeon loot
- [x] EE Clock machine accelerator block
- [x] Mega Chanter potion brewing recipe



## Overview

Minaret is a NeoForge mod (server + client) with three subsystems:
1. **WebSocket bridge** — external apps interact with Minecraft via JSON over RFC 6455
2. **Custom mob effects** — three gameplay effects applied via commands/external API
3. **Chord keys** — emacs-style key sequences that fire virtual keybindings

## Platform

| Parameter | Value |
|-----------|-------|
| Minecraft | 1.21.1, 1.21.11 (multi-version build) |
| Mod loader | NeoForge (21.1.172 / 21.11.38-beta) |
| Java | 21+ |
| External deps | None (std lib only) |
| Side | Both (server: WebSocket, client: chord keys) |

> **Note:** All blocks, mob effects, enchantments, and potions have been moved to
> the companion mod **wnir** (`/Users/vany/l/wnir`). Minaret is now a pure
> WebSocket + chord-key layer.

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

## 2. Custom Mob Effects & Potions

Three mob effects registered via `DeferredRegister<MobEffect>`. All beneficial category.
One potion registered via `DeferredRegister<Potion>`.

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

### Mega Chanter Potion (`mega_chanter`)
Potion version of the Mega Chanter mob effect (duration 3600 ticks, amplifier 0).

**Brewing recipe:** Awkward Potion + Book → Mega Chanter Potion.
Registered via `RegisterBrewingRecipesEvent` on `NeoForge.EVENT_BUS` (NOT modEventBus).

Splash and lingering variants are available via vanilla brewing — not blocked intentionally.

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

A block placed in a column below one or more vanilla mob spawners. Column layout: `[agitators...][spawners...]` — agitators at bottom, spawners on top. Modifies all spawners' behavior:
- Sets `requiredPlayerRange` to -1 (always active) on each spawner
- Reduces `minSpawnDelay` and `maxSpawnDelay` proportional to the number of stacked agitators (delays divided by agitator count)
- Stacking agitators: more agitators = faster spawning on all spawners above
- Stacking spawners: all contiguous spawners above the agitator column are agitated

**Architecture — event-driven, no ticker:**

| Event | Action |
|-------|--------|
| `onPlace` (Block) | `bindSpawner()` — walk up from topmost agitator, collect all contiguous spawners, cache `BaseSpawner` refs, set `requiredPlayerRange` to -1, apply delay scaling, store originals. `notifyColumn()` — recalc stack size for topmost agitator. |
| `playerWillDestroy` (Block) | `unbindSpawner()` — restore all spawners' original values. `notifyColumnExcluding(self)` — recalc stack skipping the removed block. |
| `onLoad` (BlockEntity) | Same as onPlace bind logic — resolves spawners on chunk load. |
| `onNeighborChanged` (BlockEntity) | Re-checks spawners above. Called when a neighbor block changes. |

**Reflection caching:**
- `requiredPlayerRange` field: resolved by name in static init, fallback probes by value 16 on first real spawner encounter
- `spawnDelay` field: resolved by name in static init, fallback probes by value 20 on first real spawner encounter
- Both `Field` objects cached in `static volatile` fields, resolved once

**Key design decisions:**
- `AGITATED_RANGE = -1` — makes spawner always active (bypasses player range check)
- All cleanup in `playerWillDestroy()` (Block class), never in `setRemoved()` — world access in `setRemoved()` causes infinite loops during chunk unload
- Topmost agitator binds to all contiguous spawners above — stores parallel lists of originals per spawner
- Stack recalc on remove uses `notifyColumnExcluding(removedPos)` to skip the block being removed (it's still in the world during `playerWillDestroy`)

**NBT:** Persists `SpawnerCount` and indexed `OriginalRange_N`, `OriginalMinDelay_N`, `OriginalMaxDelay_N` ints so all spawners can be restored after chunk reload.

**Known limitation:** Spawner placed *after* an agitator is only detected on chunk reload (`onLoad`), not immediately. The `neighborChanged` override uses `Orientation` (1.21.11 signature); not currently overridden — detection deferred to `onLoad`.

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

### Teleporter Inhibitor (`teleporter_inhibitor`)

Prevents mobs (and entities) from teleporting when within the column's inhibit radius. Participates in the same mixed warding column as the Warding Post (both implement `WardingColumnBlock`).

**Radius formula:** `inhibitRadius = 4 * (wardingPostCount + inhibitorCount)` in the column.

**Two modes:**
- **Standalone inhibitor column** (no warding posts): inhibits teleports only. Top `TeleporterInhibitorBlockEntity` owns the radius.
- **Mixed column with warding posts**: the warding posts handle mob repulsion at `4 * postCount`; teleport inhibition covers `4 * (postCount + inhibitorCount)`. Top `WardingPostBlockEntity` owns both radii.

**Event:** `EntityTeleportEvent` at `LOWEST` priority. Skips `TeleportCommand` and `SpreadPlayersCommand` (player-issued `/tp` and `/spreadplayers`).

**Architecture:**
- `WardingColumnBlock` — marker interface implemented by both `WardingPostBlock` and `TeleporterInhibitorBlock`
- `ColumnHelper` — extended with interface-based mixed-column traversal (`forEachInMixedColumn`, `countInMixedColumn`, `isTopOfMixedColumn`)
- `TeleporterInhibitorBlock` — same shape/hardness as warding post, no ticker
- `TeleporterInhibitorBlockEntity` — stores `cachedInhibitorCount`, `cachedPostCount`, `isTopOfColumn`; `teleportInhibitRadius()` computes the effective radius
- `WardingPostBlockEntity` — gains `cachedInhibitorCount` field; `teleportInhibitRadius()` covers both types
- `WardingPostTeleportHandler` — scans `WardingColumnBlock` blocks in range; reads radius from top-of-column BE (either type)

### EE Clock (`ee_clock`)

A column block that accelerates the machine above or below the column. Each EE Clock in the column grants one extra tick per game tick to the machine (N clocks = N+1 total ticks).

**Column layout:** stack EE Clocks below any block-entity machine (furnace, spawner, etc.). Or place EE Clocks on top of a machine (machine below column bottom).

**Mechanics:**
- Only the topmost EE Clock ticks (others skip via `isTopOfColumn` flag)
- `serverTick`: tries `pos.above()` first; if no valid `BaseEntityBlock` found, falls back to `pos.below(columnHeight)` (block directly below column bottom)
- Calls `ticker.tick()` exactly `columnHeight` extra times per game tick
- `columnHeight`: counted by `ColumnHelper.countBelow` (includes self, so top block = full column height)

**Column events (same pattern as Warding Post):**

| Event | Action |
|-------|--------|
| `onPlace` (Block) | `notifyColumn()` — recalc all EE Clocks in column |
| `playerWillDestroy` (Block) | `notifyColumnExcluding(removed)` — recalc skipping removed block (block still in world) |
| `affectNeighborsAfterRemoval` (Block) | `notifyColumn(pos)` + `notifyColumn(pos.above())` — recalc after non-player removal (TNT, pistons, commands); block at pos is already air |
| `randomTick` (Block) | `notifyColumn()` — periodic integrity recheck |
| `onLoad` (BlockEntity) | `recalcColumn(null)` — recompute on server-side chunk load |

**Ticker helper:** `EEClockBlock.getMachineTicker(level, state, entityBlock, be)` uses `@SuppressWarnings("unchecked")` cast `(BlockEntityType<T>) be.getType()` to resolve the wildcard type. Cast is safe because the `BlockEntity` instance and its type always correspond.

**Loot:** found in End City treasure chests (weight 5, empty weight 10).

**Texture:** cube_bottom_top model with `ee_clock_top`, `ee_clock_bottom`, `ee_clock_side` textures. Material: METAL sound, COLOR_CYAN, random ticks enabled.

### Compat (reflection utilities)

Caches all reflected `Method` and `Class` objects in `static final` fields, resolved once at class load:
- `hasPermission(CommandSourceStack, int)` — 1.21.1 has direct method, 1.21.11+ changed API
- `getTagInt(CompoundTag, String)` — 1.21.1 returns `int` directly, 1.21.11+ returns `Optional`
- `createBlockEntityType(supplier, blocks)` — handles constructor differences
- `setBlockId` / `setItemId` — 1.21.11+ only (uses `Identifier` class)
- `isClient()` — detects client vs server via `FMLEnvironment.dist` with fallback to `getDist()`

## 6. Custom Enchantments & Loot

Three enchantments defined as JSON data files under `data/minaret/enchantment/`. Handlers registered via `NeoForge.EVENT_BUS`.

### Swift Strike (`swift_strike`)
Attack speed boost. Handler: `SwiftStrikeHandler::onPlayerTick`.

### Accelerate (`accelerate`)
Movement speed boost. Handler: `AccelerateHandler::onEntityJoinLevel`.

### Toughness (`toughness`)
Armor/damage resistance bonus. Handler: `ToughnessHandler::onPlayerTick`.

### Enchanting Table
All three enchantments appear in the enchanting table via the vanilla tag:
`data/minecraft/tags/enchantment/in_enchanting_table.json`

### Mob Spawn Equipment
All three enchantments can appear on weapons/armor mobs spawn with via the vanilla tag:
`data/minecraft/tags/enchantment/on_mob_spawn_equipment.json`

### Dungeon Loot
Two loot modifiers inject enchanted items into dungeon chests:

**`add_enchantment_books_loot`** — enchantment books injected into dungeon loot.
Registered in `global_loot_modifiers.json` (was defined but previously unregistered — fixed).

**`add_enchanted_weapons_loot`** — pre-enchanted weapons/armor injected into dungeons:
- Iron sword + swift_strike (weight 3)
- Bow + accelerate (weight 2)
- Iron chestplate + toughness (weight 1)
- Empty (weight 9)

Applies to: simple_dungeon, abandoned_mineshaft, stronghold_corridor, stronghold_crossing, desert_pyramid, jungle_temple.

## 7. Build

Single version: `versions/1.21.11/` — MC 1.21.11, NeoForge 21.11.38-beta.

`gradle.properties` defines `minecraft_version`, `neo_version`, range constraints. Root `build.gradle` applies userdev plugin and rewires `sourceSets` to root `src/main/` dirs.

Build: `make build-1.21.11`. Deploy: copy `versions/1.21.11/build/libs/minaret-1.21.11-*.jar` to instance mods folder.

## 8. Decisions Made

1. **No external dependencies** — avoids jar bundling complexity; custom WebSocket + JSON
2. **Flat JSON only** — `SimpleJson` handles `Map<String,String>` which covers the protocol; `getEffects` array is hand-built as a one-off
3. **OP4 for all commands** — simplifies authorization; external auth layer is the access gate
4. **Virtual threads** — `Executors.newVirtualThreadPerTaskExecutor()` (Java 21). Each connection blocks cheaply; no platform thread held per idle connection. Zero code changes beyond the executor line.
5. **No continuation frames** — single-frame messages only; sufficient for JSON payloads under 64KB
6. **Reflection for API access** — `hasPermission` removed in 1.21.11 (use reflection fallback); `FMLEnvironment.dist`/`getDist()` for client detection; `KeyMapping` constructor takes `Category` record in 1.21.11
7. **Direct KeyMapping firing for chords** — chords map directly to KeyMapping names (e.g. `key:sophisticatedbackpacks.openbackpack`) or WebSocket commands (`cmd:{...}`). No phantom/virtual keycodes needed. `KeyMapping.click(boundKey)` fires the target action directly.
8. **Separate chord config file** — `config/minaret-chords.json` instead of ModConfigSpec, because chord data is nested (map of sequence→target) and client-side only
9. **Reflection-based key consumption** — `InputEvent.Key` is not cancelable in NeoForge. Keys are consumed by resetting `clickCount` to 0 and `setDown(false)` on matching KeyMappings via reflection, since `KeyMapping.click()` fires before the event
10. **No world access in `setRemoved()`** — causes infinite loops during chunk unload. All cleanup moved to `playerWillDestroy()` on the Block class
11. **`AGITATED_RANGE = -1`** — makes spawner always active (bypasses player range check). Chunk is force-loaded while agitator is bound
12. **Plain text file for ChunkLoaderData** — simple `X Y Z` per line, atomic save via tmp+rename; avoids `SavedData` complexity
13. **Atomic file save** — write to `.tmp` then `Files.move(ATOMIC_MOVE)` prevents corruption on crash mid-write
14. **Event-driven spawner binding** — spawner discovery on place/load events, not per-tick polling. Ticker only does the delay acceleration (which genuinely needs per-tick work)
15. **EE Clock extra ticks approach** — machine speedup via calling the machine's own `BlockEntityTicker.tick()` N extra times per game tick. Chosen over capability/interface approaches because it works with any vanilla or modded block-entity machine without cooperation. Same approach used by Draconic Evolution and similar mods.
16. **`RegisterBrewingRecipesEvent` on `NeoForge.EVENT_BUS`** — in 1.21.11, this event is NOT a `IModBusEvent`. Must be registered on `NeoForge.EVENT_BUS`, not `modEventBus`. The crash error is `IllegalArgumentException: This bus only accepts subclasses of IModBusEvent`.
17. **Splash/lingering Mega Chanter potions allowed** — no brewing lock-out; vanilla brewing chain naturally produces them. Intentionally left as-is.
18. **`affectNeighborsAfterRemoval` replaces `onRemove` in 1.21.11** — `BlockBehaviour.onRemove(BlockState, Level, BlockPos, BlockState, boolean)` does not exist in 1.21.11. Use `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` instead. Key difference: block at pos is already removed when this fires. Call `notifyColumn(pos)` (finds lower fragment) AND `notifyColumn(pos.above())` (finds upper fragment if column was split). `playerWillDestroy` still handles player removal while block is still in world.
19. **`on_mob_spawn_equipment` tag for mob enchantments** — pure JSON, no Java code; much simpler than a custom `IGlobalLootModifier`. Mobs that spawn with equipment have a chance to have these enchantments on their gear.
20. **`MessageDispatcher` handler registry** — `LinkedHashMap<String, Handler>` keyed by JSON discriminant field. Adding a new message type is one `HANDLERS.put()` line; `dispatch()` is not touched. Ordered map preserves priority (first matching key wins).
21. **In-place frame accumulation buffer** — single `byte[MAX_ACCUMULATOR]` per `Connection`, reused across reads. Consumed bytes are shifted left in-place. Avoids per-read array allocation; for typical single-frame reads `remaining == 0` and no copy occurs.
22. **`Compat.isClient()` cached at class load** — environment is fixed for the JVM lifetime; result stored in `static final boolean IS_CLIENT`. The reflection runs exactly once.
23. **`ChordConfig` observer pattern** — `setOnChanged(Runnable)` wired to `ChordKeyHandler::rebuildTrie` in `init()`. All mutations (`addChord`, `removeChord`, `setMetaKey`) call `notifyChanged()` after `save()`. Callers (e.g. `MinaretCommands`) no longer need to manually trigger trie rebuilds — the coupling is internal to the config.

## 9. Event Broadcasting

Server-side events are pushed to all connected WebSocket clients as JSON messages.
Implemented in `EventBroadcaster.java`. Registered via `NeoForge.EVENT_BUS.addListener()` in `MinaretMod`.

### Events

#### `player_join`
```json
{"event":"player_join","player":"PlayerName"}
```
NeoForge: `PlayerEvent.PlayerLoggedInEvent`

#### `player_leave`
```json
{"event":"player_leave","player":"PlayerName"}
```
NeoForge: `PlayerEvent.PlayerLoggedOutEvent`

#### `player_death`
```json
{"event":"player_death","player":"PlayerName","cause":"fall"}
```
`cause` is `DamageSource.getMsgId()` (e.g. `fall`, `drown`, `mob`, `player`, `magic`).
NeoForge: `LivingDeathEvent` where entity is `ServerPlayer`

#### `player_kill`
```json
{"event":"player_kill","player":"PlayerName","mob":"zombie"}
```
`mob` is the short registry path of the killed entity type.
NeoForge: `LivingDeathEvent` where `DamageSource.getEntity()` is a `Player`

#### `player_eat`
```json
{"event":"player_eat","player":"PlayerName","item":"bread","nutrition":5,"saturation":6.0}
```
Only fires for items with `FoodProperties` (not potions, bows, etc.).
NeoForge: `LivingEntityUseItemEvent.Finish` where entity is `ServerPlayer` and item is food.

#### `player_heal`
```json
{"event":"player_heal","player":"PlayerName","amount":10.5}
```
Aggregated — fires when accumulated HP ≥ 10, or ≥ 1 minute since last broadcast (whichever comes first).
Prevents spam from natural regen (~1 HP every 0.5s).
NeoForge: `LivingHealEvent` where entity is `ServerPlayer`.

## 10. Out of Scope

- TLS/SSL (WSS) — local use only
- IP whitelisting / firewall — local use only
- Rate limiting — local use only
- Advanced auth (JWT/OAuth) — Basic Auth sufficient
- Message queuing — not needed
- Plugin API for other mods — not needed
- WebSocket compression (deflate) — not needed
- Binary frame support — not needed
- Automated tests — not planned
