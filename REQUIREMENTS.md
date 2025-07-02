# 🗼 Minaret Mod Requirements

## 📋 Project Overview
**Minaret** is a server-side NeoForge mod that provides a WebSocket API bridge for Minecraft server communication. It enables external applications to send chat messages and execute server commands through a secure WebSocket connection.

## 🎯 Core Objectives
- Enable external applications to interact with Minecraft server
- Provide secure, authenticated WebSocket communication
- Support real-time chat message broadcasting  
- Allow remote server command execution with proper permissions
- Maintain server stability and security

## 🔧 Technical Requirements

### Platform & Environment
- **Minecraft Version**: 1.21.1
- **Mod Loader**: NeoForge 21.1.172+
- **Java Version**: 21+
- **Architecture**: Server-side only (no client components)
- **Dependencies**: Zero external dependencies (Java standard library only)

### WebSocket Server Implementation
- **Protocol Compliance**: RFC 6455 WebSocket standard
- **Transport**: Raw TCP sockets with manual WebSocket implementation
- **Default Endpoint**: `ws://localhost:8765` (configurable)
- **Concurrent Connections**: Support multiple simultaneous clients
- **Connection Management**: Proper lifecycle with graceful cleanup
- **Heartbeat**: Ping/Pong frame support for connection keepalive

### Authentication & Security
- **Authentication Method**: HTTP Basic Authentication (optional)
- **Security Level**: Server operator (OP level 4) permissions for commands
- **Authorization Header**: Standard `Authorization: Basic <credentials>` during handshake
- **Failure Handling**: 401 Unauthorized responses with WWW-Authenticate header
- **Security Logging**: Authentication attempts and failures logged
- **Configuration**: Username/password configurable via server config

### Message Protocol
```json
// Basic chat message format
{
  "message": "Text to broadcast to all players"
}

// User chat message format
{
  "message": "Text to broadcast to all players",
  "user": "Username of the sender (optional)"
}

// Cross-platform chat message format  
{
  "message": "Text to broadcast to all players",
  "user": "Username of the sender (optional)",
  "chat": "Source platform identifier (optional)"
}

// Command execution format  
{
  "command": "minecraft_command_without_slash"
}

// Success response format
{
  "status": "success",
  "type": "message|command", 
  "command": "executed_command",
  "result": "numeric_result"
}

// Error response format
{
  "status": "error",
  "type": "message|command",
  "error": "detailed_error_description",
  "command": "failed_command",
  "result": "0"
}
```

### Command Execution Requirements
- **Permission Level**: Execute with server operator (OP level 4) privileges
- **Command Processing**: Full Minecraft command dispatcher integration
- **Result Tracking**: Numeric result codes for success/failure detection
- **Error Handling**: Comprehensive exception catching and reporting
- **Syntax Validation**: Proper brigadier command parsing and validation
- **Thread Safety**: All commands executed on main server thread

### Chat Integration Requirements  
- **Message Broadcasting**: Send to all connected players
- **Message Formatting**: Support Minecraft text components and color codes
- **Dynamic Display Logic**:
  - Anonymous messages (no user): `§8⊞ §7message` (empty sign style)
  - User messages: `§f<user> message` (standard chat format)
  - Cross-platform messages: `§7[chat] §f<user> message` (with source identifier)
- **Cross-Platform Support**: Optional chat field for identifying message source (discord, slack, etc.)
- **Player Targeting**: Broadcast to entire player list
- **Thread Safety**: Chat messages sent on main server thread

## 📋 Functional Requirements

### Configuration Management
- **Config File**: `config/minaret-server.toml` 
- **WebSocket URL**: Configurable host:port binding
- **Authentication**: Optional username/password credentials
- **Hot Reload**: Configuration changes via NeoForge config system
- **Validation**: Input validation for all configuration values

### Error Handling & Logging
- **Command Failures**: Detailed JSON error responses
- **Connection Errors**: Graceful handling of network issues  
- **Authentication Failures**: Security event logging
- **Exception Handling**: Comprehensive try-catch coverage
- **Debug Information**: Detailed logging for troubleshooting

### Performance Requirements
- **Connection Limit**: Support 10+ concurrent WebSocket connections
- **Latency Target**: Sub-100ms message processing time
- **Memory Usage**: <15MB additional server memory
- **TPS Impact**: Minimal impact on server tick rate
- **Resource Cleanup**: Proper connection and thread cleanup

## 🔒 Security Requirements

### Access Control
- **Authentication**: HTTP Basic Auth during WebSocket handshake
- **Authorization**: Server operator level command execution
- **Input Validation**: Sanitization of all incoming messages
- **Command Filtering**: Validation through Minecraft command system
- **Rate Limiting**: Basic protection against connection flooding

### Data Security  
- **Credential Storage**: Secure config file storage
- **Error Message Sanitization**: No sensitive data in error responses
- **Connection Logging**: Source IP and authentication status logging
- **Audit Trail**: Command execution logging with source tracking

### Network Security
- **Default Binding**: Local network only (localhost)
- **Configurable Host**: Allow custom host binding for network access
- **Connection Limits**: Basic DoS protection through connection management
- **Protocol Security**: Standard WebSocket security practices

## 🏗️ Implementation Constraints

### Architecture Limitations
- **No External Dependencies**: Java standard library only
- **No Third-Party Libraries**: Custom WebSocket and JSON implementation
- **Server-Side Only**: No client-side components or conflicts
- **NeoForge Integration**: Standard mod lifecycle and configuration

### Performance Constraints
- **Single-Threaded**: All Minecraft operations on main server thread
- **Memory Efficient**: Minimal object allocation in message processing
- **Non-Blocking**: WebSocket operations in separate thread pool
- **Resource Limits**: Graceful degradation under high load

### Compatibility Requirements
- **NeoForge Version**: 21.1.172 minimum compatibility
- **Java Version**: Java 21+ requirement
- **Minecraft Version**: 1.21.1 specific targeting
- **Mod Conflicts**: No conflicts with existing server-side mods

## 📊 Quality Assurance

### Testing Requirements
- **Functional Testing**: WebSocket connection, authentication, message processing
- **Security Testing**: Authentication bypass attempts, malformed requests
- **Performance Testing**: Multiple concurrent connections, command throughput
- **Integration Testing**: NeoForge mod loading, Minecraft server integration
- **Error Testing**: Invalid commands, network failures, malformed JSON

### Monitoring & Observability  
- **Connection Metrics**: Active connection count and status
- **Command Metrics**: Success/failure rates and execution times
- **Error Tracking**: Detailed error logs with context
- **Performance Monitoring**: Memory usage and processing latency
- **Security Monitoring**: Authentication failure tracking

## 📝 Documentation Requirements

### User Documentation
- **Installation Guide**: Mod installation and configuration steps
- **API Documentation**: WebSocket protocol and message formats
- **Configuration Reference**: All config options and examples
- **Troubleshooting Guide**: Common issues and solutions
- **Security Guide**: Authentication setup and best practices

### Developer Documentation  
- **Architecture Overview**: Code structure and design decisions
- **API Reference**: Complete WebSocket API specification
- **Example Implementations**: Sample client code in multiple languages
- **Extension Guide**: How to modify or extend functionality
- **Testing Guide**: How to test and validate functionality

## 🔄 Future Enhancement Considerations

### Protocol Enhancements
- **WebSocket Compression**: Deflate extension support
- **Binary Messages**: Support for binary WebSocket frames
- **Sub-Protocols**: Custom WebSocket sub-protocol support
- **Advanced Authentication**: JWT or OAuth integration

### Performance Optimizations
- **Connection Pooling**: Efficient connection reuse
- **Message Queuing**: Async message processing queues
- **Batch Operations**: Multiple commands in single request
- **Caching**: Response caching for common operations

### Security Enhancements
- **TLS/SSL Support**: Encrypted WebSocket connections (WSS)
- **Rate Limiting**: Advanced DoS protection
- **IP Whitelisting**: Connection source restrictions
- **Audit Logging**: Comprehensive security event logging

### Integration Features
- **Plugin API**: Extensibility for other mods
- **Event Broadcasting**: Server events pushed to WebSocket clients
- **Player Management**: Player join/leave notifications
- **World Data**: Real-time world state information
- **Cross-Platform Chat**: Native support for Discord, Slack, and other chat platforms
- **Message Attribution**: User and source identification in chat messages
