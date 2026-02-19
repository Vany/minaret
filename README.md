# 🗼 Minaret

[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.172+-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.21.11-green.svg)](https://minecraft.net/)
[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**NeoForge mod with WebSocket API, custom blocks, mob effects, and chord keys**

Minaret is a multi-feature NeoForge mod for Minecraft 1.21.1 / 1.21.11:
- **WebSocket API** for external application integration
- **Event broadcasting** — server pushes game events to connected clients
- **Spawner Agitator** block that enhances mob spawners
- **Chunk Loader** block that keeps chunks loaded
- **Warding Post** block that repels hostile mobs
- **Custom mob effects** (Martial Lightning, Homing Archery, Streamer Protect)
- **Chord keys** (emacs-style key sequences for keybinding combos)

## Features

**WebSocket API**
- RFC 6455 compliant WebSocket server, zero external dependencies
- Optional HTTP Basic Authentication
- Chat broadcast, command execution, effect queries via JSON
- Server pushes game events to all connected clients
- Sub-100ms latency, thread-safe

**Spawner Agitator**
- Place below a mob spawner to increase its range and speed
- Stack multiple agitators for faster spawning
- Event-driven architecture with minimal per-tick overhead
- Clean shutdown behavior (no server hang)

**Chunk Loader**
- Place to keep the chunk force-loaded
- Persists across server restarts (atomic text file save)
- Automatic re-activation on server start

**Warding Post**
- Repels hostile mobs; radius scales with column height (4 blocks per post stacked)
- Stack multiple posts vertically for larger protection area
- Found as dungeon loot in temples, strongholds, and mineshafts

**Mob Effects**
- Martial Lightning: enhanced melee with AoE and tier-based damage
- Homing Archery: bow shots fire homing shulker bullets
- Streamer Protect: indicator effect for external integration

**Chord Keys** (client-side)
- Emacs-style key sequences (e.g. `f>1`, `f>f>1`)
- Targets: KeyMapping names or WebSocket JSON commands
- Trie-based state machine with timeout and overlay

## 🚀 Quick Start

### Installation

1. **Download** the latest `minaret-1.0.0.jar` from releases
2. **Place** the jar in your server's `mods/` folder
3. **Start** your NeoForge 1.21.1 server
4. **Configure** via `config/minaret-server.toml` (optional)

### Basic Usage

```javascript
// Connect to WebSocket API
const ws = new WebSocket('ws://localhost:8765');

// Send chat message
ws.send(JSON.stringify({ "message": "Hello from external app!" }));

// Execute server command
ws.send(JSON.stringify({ "command": "time set day" }));

// Receive server events
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  if (msg.event === 'player_join') console.log(msg.player + ' joined');
  if (msg.event === 'player_death') console.log(msg.player + ' died: ' + msg.cause);
};
```

## ⚙️ Configuration

**File:** `config/minaret-server.toml`

```toml
# WebSocket server binding
websocket_url = "localhost:8765"

# Authentication (optional - leave empty to disable)
auth_username = ""
auth_password = ""
```

| Option | Default | Description |
|--------|---------|-------------|
| `websocket_url` | `localhost:8765` | Host:port for WebSocket server |
| `auth_username` | `""` | Username for HTTP Basic Auth (empty = disabled) |
| `auth_password` | `""` | Password for HTTP Basic Auth |

## 📡 WebSocket API

**Endpoint:** `ws://localhost:8765` (configurable)

**Authentication:** Optional HTTP Basic Auth header:
```
Authorization: Basic <base64(username:password)>
```

All messages use JSON with UTF-8 encoding.

### Client → Server (requests)

#### Chat message
```json
{"message": "text", "user": "name", "chat": "discord"}
```
`user` and `chat` are optional.

**Response:** `{"status":"success","type":"message"}`

#### Command execution
```json
{"command": "time set day"}
```

**Response:**
```json
{"status":"success","type":"command","command":"time set day","result":"1"}
{"status":"error","type":"command","error":"...","command":"...","result":"0"}
```

#### Query player effects
```json
{"getEffects": "PlayerName"}
```

**Response:**
```json
{"status":"success","type":"getEffects","player":"PlayerName","effects":[...]}
```

### Server → Client (events)

The server pushes these events to all connected clients automatically:

| Event | Payload |
|-------|---------|
| `player_join` | `player` |
| `player_leave` | `player` |
| `player_death` | `player`, `cause` (e.g. `fall`, `drown`, `mob`) |
| `player_kill` | `player`, `mob` (e.g. `zombie`, `creeper`) |
| `player_eat` | `player`, `item`, `nutrition`, `saturation` |
| `player_heal` | `player`, `amount` — aggregated; fires at ≥10 HP or 1 min |

Examples:
```json
{"event":"player_join","player":"Steve"}
{"event":"player_death","player":"Steve","cause":"fall"}
{"event":"player_kill","player":"Steve","mob":"zombie"}
{"event":"player_eat","player":"Steve","item":"bread","nutrition":5,"saturation":6.0}
{"event":"player_heal","player":"Steve","amount":"12.5"}
```

### Error Handling

| Error | Description |
|-------|-------------|
| `401 Unauthorized` | Invalid/missing authentication |
| `Invalid JSON` | Malformed JSON message |
| `Unknown message type` | Missing `message`, `command`, or `getEffects` field |
| `Command failed` | Command returned error code 0 |

## 🛠️ Examples

### Node.js Client

```javascript
const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:8765', {
  headers: {
    'Authorization': 'Basic ' + Buffer.from('user:pass').toString('base64')
  }
});

ws.on('open', () => {
  ws.send(JSON.stringify({ message: 'Bot connected!' }));
  ws.send(JSON.stringify({ command: 'give @a diamond 64' }));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data);
  if (msg.event) {
    console.log('Event:', msg);
  } else {
    console.log('Response:', msg);
  }
});
```

### Python Client

```python
import websocket
import json
import base64

def on_message(ws, message):
    msg = json.loads(message)
    if 'event' in msg:
        print(f"Event: {msg}")
    else:
        print(f"Response: {msg}")

def on_open(ws):
    ws.send(json.dumps({"message": "Python bot online!"}))
    ws.send(json.dumps({"command": "weather clear"}))

auth = base64.b64encode(b'user:pass').decode('ascii')
ws = websocket.WebSocketApp("ws://localhost:8765",
                            header=[f"Authorization: Basic {auth}"],
                            on_open=on_open,
                            on_message=on_message)
ws.run_forever()
```

### Command Line Testing

```bash
npm install -g wscat

# Connect without authentication
wscat -c ws://localhost:8765

# Connect with authentication
wscat -c ws://localhost:8765 -H "Authorization: Basic $(echo -n 'user:pass' | base64)"

# Test messages
{"message": "Hello!"}
{"command": "say WebSocket is working!"}
{"getEffects": "PlayerName"}
```

## 🔒 Security

- **Authentication:** Optional HTTP Basic Auth during WebSocket handshake
- **Permissions:** All commands executed with OP level 4
- **Local binding:** Defaults to localhost
- **Audit logging:** Authentication attempts and commands logged

## 📊 Performance

| Metric | Value |
|--------|-------|
| Memory | ~15MB additional |
| Connections | 10+ concurrent |
| Latency | Sub-100ms |
| TPS impact | Minimal |

## 🔧 Development

### Requirements

- **Java:** 21+ (OpenJDK recommended)
- **NeoForge:** 21.1.172+ (1.21.1) or 21.11.38-beta+ (1.21.11)
- **Minecraft:** 1.21.1 or 1.21.11
- **Gradle:** 8.8+

### Building

```bash
make build          # Build all versions
make build-1.21.1   # Build for MC 1.21.1
make build-1.21.11  # Build for MC 1.21.11
# Output: versions/*/build/libs/minaret-*.jar
```

### Project Structure

```
minaret/
├── src/main/java/com/minaret/
│   ├── MinaretMod.java                  # Mod entry point, registries, lifecycle
│   ├── WebSocketServer.java             # RFC 6455 WebSocket server
│   ├── EventBroadcaster.java            # Server → client event broadcasting
│   ├── MessageDispatcher.java           # Client → server message routing
│   ├── SimpleJson.java                  # Flat JSON parser/generator
│   ├── MinaretConfig.java               # NeoForge config
│   ├── Compat.java                      # Cross-version reflection utilities
│   ├── SpawnerAgitatorBlock.java        # Spawner agitator block
│   ├── SpawnerAgitatorBlockEntity.java  # Spawner agitator logic
│   ├── ChunkLoaderBlock.java            # Chunk loader block
│   ├── ChunkLoaderBlockEntity.java      # Chunk loader block entity
│   ├── ChunkLoaderData.java             # Chunk loader persistence
│   ├── WardingPostBlock.java            # Warding post block
│   ├── WardingPostBlockEntity.java      # Warding post ticker
│   ├── ChordConfig.java                 # Chord key config
│   ├── MinaretCommands.java             # /minaret subcommands
│   ├── *Effect.java / *Handler.java     # Mob effects and handlers
│   └── client/
│       └── ChordKeyHandler.java         # Chord key state machine
├── versions/
│   ├── 1.21.1/                          # MC 1.21.1 subproject
│   └── 1.21.11/                         # MC 1.21.11 subproject
└── build.gradle                         # Multi-version build config
```

## 🤝 Support

**Connection Refused**
- Verify mod is installed and server is running
- Check `config/minaret-server.toml` for correct port

**Authentication Failures**
- Verify credentials in config file
- Review server logs for authentication errors

**Commands Not Executing**
- Ensure commands are valid Minecraft commands
- Check server logs for permission errors
- Verify JSON message format is correct

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

## Use Cases

- Remote server management via WebSocket (Discord bots, web panels, mobile apps)
- React to game events in real time (player deaths, kills, food consumption)
- Mob farm automation with spawner agitator stacks
- Persistent chunk loading for farms, machines, and redstone
- Streaming integration via streamer protect effect
- Custom keybinding combos for mod-heavy clients
