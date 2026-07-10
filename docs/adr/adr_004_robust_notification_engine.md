# ADR 004: Designing a Robust, Clustered Real-Time Notification Engine

## Status
Accepted

## Context
Swish OS requires a real-time notification pipeline to stream transactional updates (such as order picking status, rider GPS coordinates, and replenishment negotiations) to client micro-frontends (Customer, Rider, and B2B Admin). 
Building real-time delivery systems over stateful protocols like WebSockets introduces several critical architectural challenges at scale:
1. **Clustering & Horizontal Scalability**: WebSockets bind client connections to a single server instance. If a backend service publishes an event, it may target a client pinned to a different cluster node.
2. **Resource Exhaustion & Memory Leaks**: Idle, orphaned, or un-disposed connections deplete file descriptors and memory.
3. **Slow Client Backpressure**: High-volume event streams can overwhelm slow clients, causing server-side OOM conditions if buffers grow unbounded.
4. **Security Spoofing**: Attackers can spoof headers or attempt to establish unauthenticated listening sockets.
5. **Multi-Session Support**: Users frequently operate across multiple tabs or devices, necessitating concurrent message fan-out.

## Decision
We implement a highly resilient, reactive, and clustered **WebSocket Notification Engine** powered by Spring WebFlux, Project Reactor, and a Redis Pub/Sub shared backplane.

```
                          [Backend Microservices]
                                     │
                                     ▼ (Publish Event)
                             ┌───────────────┐
                             │ Apache Kafka  │
                             └───────┬───────┘
                                     │
                                     ▼ (Consume & Envelop)
                        ┌─────────────────────────┐
                        │ Notification Consumer   │
                        └───────────┬─────────────┘
                                    │
                                    ▼ (Publish to Channel)
                             ┌───────────────┐
                             │ Redis Cluster │ (Pub/Sub Backplane)
                             └─┬───────────┬─┘
       (Redis Msg)             │           │             (Redis Msg)
     ┌─────────────────────────┘           └─────────────────────────┐
     ▼                                                               ▼
┌─────────────────────────┐                                     ┌─────────────────────────┐
│ WebSocket Node 1 (BFF)  │                                     │ WebSocket Node 2 (BFF)  │
├─────────────────────────┤                                     ├─────────────────────────┤
│ Active User A (Tab 1)   │                                     │ Active User A (Tab 2)   │
│ Active User B (Mobile)  │                                     │ Active User C (Browser) │
└─────────────────────────┘                                     └─────────────────────────┘
```

The design enforces the following architectural pillars:

### 1. Redis Pub/Sub Backplane for Clustered Fan-Out
To route events to user sessions pinned across different nodes:
- When a client connects to any gateway instance, the node dynamically subscribes to a user-specific Redis channel: `notifications:b2b:{userId}`.
- When an event is processed by the consumer, it is published to the Redis backplane, which automatically distributes the payload to all gateway instances hosting active sessions for that user.

### 2. Multi-Tab Session Synchronization
- The engine maps each `userId` to a thread-safe `CopyOnWriteArraySet<SessionEntry>`.
- Any message received via Redis is fanned out to all registered sessions in the set, enabling multi-tab synchronization.

### 3. Server-Initiated Ping/Heartbeat
- The gateway initiates Ping frames every 30 seconds to verify socket health, prevent firewalls from terminating idle TCP tunnels, and detect half-open sockets.

### 4. Backpressure Buffering
- Sockets utilize a bounded unicast buffer (`Sinks.Many` with `ArrayDeque` capped at 256 messages). If a client stalls, older messages are dropped, protecting JVM heap memory from OOM issues.

### 5. Connection Cap Safeguard
- Restricts connections to a maximum of 5 concurrent sessions per user. If the limit is exceeded, the oldest session is automatically evicted and closed with status code `4001`.

### 6. Edge Header Verification
- Only allows connections that present a gateway-verified `X-Authenticated-User` header. Spoof headers are stripped at the API Gateway boundary, and anonymous connections are rejected immediately (`4003`).

## Consequences
- **Pros**:
  - Achieves seamless linear horizontal scalability by decoupling the socket layer from event publication.
  - Mitigates common real-time server vulnerabilities (OOM, socket leaks, connection flooding).
  - Guarantees proper tab synchronization.
- **Cons**:
  - Relies on Redis as a single point of failure for the real-time routing plane (mitigated by graceful local fallbacks).
