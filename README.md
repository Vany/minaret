# 🗼 Minaret

[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.172+-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.21.11-green.svg)](https://minecraft.net/)
[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**NeoForge mod with WebSocket API, custom blocks, mob effects, and chord keys**

Minaret is a multi-feature NeoForge mod for Minecraft 1.21.1 / 1.21.11:
- **WebSocket API** for external application integration
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
- Sub-100ms latency, thread-safe

**Spawner Agitator**
- Place above a mob spawner to increase its range and speed
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
- 200 virtual keybinding slots, assignable in Controls
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
ws.send(JSON.stringify({
  \"message\": \"Hello from external app! 👋\"
}));

// Execute server command  
ws.send(JSON.stringify({
  \"command\": \"time set day\"
}));
```

## ⚙️ Configuration

**File:** `config/minaret-server.toml`

```toml
# WebSocket server binding
websocket_url = \"localhost:8765\"

# Authentication (optional - leave empty to disable)
auth_username = \"\"
auth_password = \"\"
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `websocket_url` | `localhost:8765` | Host:port for WebSocket server |
| `auth_username` | `\"\"` | Username for HTTP Basic Auth (empty = disabled) |
| `auth_password` | `\"\"` | Password for HTTP Basic Auth |

## 📡 WebSocket API

### Connection

**Endpoint:** `ws://localhost:8765` (configurable)

**Authentication:** Optional HTTP Basic Auth header:
```
Authorization: Basic <base64(username:password)>
```

### Message Protocol

All messages use JSON format with UTF-8 encoding.

#### Chat Messages

**Request:**
```json
{
  \"message\": \"Your message here\"
}
```

**Response:**
```json
{
  \"status\": \"success\",
  \"type\": \"message\"
}
```

#### Command Execution

**Request:**
```json
{
  \"command\": \"time set night\"
}
```

**Success Response:**
```json
{
  \"status\": \"success\",
  \"type\": \"command\",
  \"command\": \"time set night\",
  \"result\": \"1\"
}
```

**Error Response:**
```json
{
  \"status\": \"error\",
  \"type\": \"command\",
  \"error\": \"Unknown command\",
  \"command\": \"invalid_command\",
  \"result\": \"0\"
}
```

### Error Handling

Common error scenarios and responses:

| Error Type | HTTP Status | Description |
|------------|-------------|-------------|
| `401 Unauthorized` | 401 | Invalid/missing authentication |
| `Invalid JSON` | - | Malformed JSON message |
| `Unknown message type` | - | Missing `message` or `command` field |
| `Command failed` | - | Command returned error code 0 |
| `Permission denied` | - | Command requires higher permissions |

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
  // Announce connection
  ws.send(JSON.stringify({
    message: '🤖 Bot connected!'
  }));
  
  // Give items to player
  ws.send(JSON.stringify({
    command: 'give @a diamond 64'
  }));
});

ws.on('message', (data) => {
  const response = JSON.parse(data);
  console.log('Server response:', response);
});
```

### Python Client

```python
import websocket
import json
import base64

def on_message(ws, message):
    response = json.loads(message)
    print(f\"Server: {response}\")

def on_open(ws):
    # Send chat message
    ws.send(json.dumps({
        \"message\": \"🐍 Python bot online!\"
    }))
    
    # Execute commands
    ws.send(json.dumps({
        \"command\": \"weather clear\"
    }))

# Setup authentication header
auth = base64.b64encode(b'user:pass').decode('ascii')
headers = [f\"Authorization: Basic {auth}\"]

ws = websocket.WebSocketApp(\"ws://localhost:8765\",
                          header=headers,
                          on_open=on_open,
                          on_message=on_message)
ws.run_forever()
```

### Command Line Testing

```bash
# Install wscat globally
npm install -g wscat

# Connect without authentication
wscat -c ws://localhost:8765

# Connect with authentication  
wscat -c ws://localhost:8765 -H \"Authorization: Basic $(echo -n 'user:pass' | base64)\"

# Test messages (type these after connecting)
{\"message\": \"Hello from command line! 💻\"}
{\"command\": \"say WebSocket API is working!\"}
{\"command\": \"gamemode creative @a\"}
```

## 🔒 Security

### Authentication

- **Optional Security:** Authentication can be disabled for trusted networks
- **Standard Protocol:** Uses HTTP Basic Authentication during WebSocket handshake
- **Audit Logging:** All authentication attempts logged for security monitoring

### Permissions

- **OP Level 4:** All commands executed with maximum server privileges
- **Input Validation:** JSON parsing with comprehensive error handling
- **Command Validation:** Commands validated through Minecraft's brigadier system

### Network Security

- **Local Binding:** Defaults to localhost for security
- **Configurable Host:** Can bind to specific network interfaces as needed
- **Connection Logging:** Source IP and authentication status logged
- **Resource Protection:** Basic DoS protection through connection management

## 📊 Performance

| Metric | Specification |
|--------|---------------|
| **Memory Usage** | ~15MB additional server memory |
| **Concurrent Connections** | 10+ tested, scales with thread pool |
| **Message Latency** | Sub-100ms processing time |
| **TPS Impact** | Minimal server performance impact |
| **Resource Cleanup** | Automatic connection and thread cleanup |

## 🔧 Development

### Requirements

- **Java:** 21+ (OpenJDK recommended)
- **NeoForge:** 21.1.172+ (1.21.1) or 21.11.38-beta+ (1.21.11)
- **Minecraft:** 1.21.1 or 1.21.11
- **Gradle:** 8.8+

### Building

```bash
# Build all versions
make build

# Build specific version
make build-1.21.1
make build-1.21.11

# Output: versions/*/build/libs/minaret-*.jar
```

### Project Structure

```
minaret/
├── src/main/java/com/minaret/
│   ├── MinaretMod.java                  # Mod entry point, registries, lifecycle
│   ├── WebSocketServer.java             # RFC 6455 WebSocket server
│   ├── SimpleJson.java                  # Flat JSON parser/generator
│   ├── MinaretConfig.java               # NeoForge config
│   ├── Compat.java                      # Cross-version reflection utilities
│   ├── SpawnerAgitatorBlock.java        # Spawner agitator block
│   ├── SpawnerAgitatorBlockEntity.java  # Spawner agitator logic
│   ├── ChunkLoaderBlock.java            # Chunk loader block
│   ├── ChunkLoaderBlockEntity.java      # Chunk loader block entity
│   ├── ChunkLoaderData.java             # Chunk loader persistence
│   ├── WardingPostBlock.java            # Warding post block (mob repulsion, column notify)
│   ├── WardingPostBlockEntity.java      # Warding post ticker (radius = 4 * column height)
│   ├── ChordConfig.java                 # Chord key config
│   ├── *Effect.java / *Handler.java     # Mob effects and handlers
│   └── client/
│       └── ChordKeyHandler.java         # Chord key state machine
├── src/main/resources/META-INF/
│   └── neoforge.mods.toml              # Mod metadata
├── versions/
│   ├── 1.21.1/                          # MC 1.21.1 subproject
│   └── 1.21.11/                         # MC 1.21.11 subproject
├── build.gradle                         # Multi-version build config
└── README.md                            # This document
```

## 🤝 Support

### Common Issues

**Connection Refused**
- Verify mod is installed and server is running
- Check `config/minaret-server.toml` for correct port
- Ensure firewall allows WebSocket connections

**Authentication Failures**  
- Verify credentials in config file
- Check for typos in username/password
- Review server logs for authentication errors

**Commands Not Executing**
- Ensure commands are valid Minecraft commands
- Check server logs for permission errors
- Verify JSON message format is correct

### Getting Help

- 📖 Read the [complete documentation](REQUIREMENTS.md)
- 🐛 Report issues with detailed logs and steps to reproduce
- 💡 Feature requests welcome with clear use cases
- 🔧 Pull requests appreciated for improvements

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

## Use Cases

- Remote server management via WebSocket (Discord bots, web panels, mobile apps)
- Mob farm automation with spawner agitator stacks
- Persistent chunk loading for farms, machines, and redstone
- Streaming integration via streamer protect effect
- Custom keybinding combos for mod-heavy clients
